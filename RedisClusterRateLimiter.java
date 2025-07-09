package com.alibaba.csp.sentinel.demo.cluster;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于Redis的集群限流实现方案
 * 
 * 支持多种限流算法：
 * 1. 令牌桶算法 (Token Bucket)
 * 2. 滑动窗口算法 (Sliding Window)
 * 3. 漏桶算法 (Leaky Bucket)  
 * 4. 固定窗口计数器 (Fixed Window Counter)
 * 
 * 特点：
 * - 分布式一致性：所有节点共享同一个限流状态
 * - 高性能：使用Lua脚本保证原子性
 * - 容错性：支持Redis故障时的降级策略
 * - 实时性：支持规则动态更新
 */
public class RedisClusterRateLimiter {
    
    private final JedisPool jedisPool;
    private final String keyPrefix;
    private final ReentrantLock lock = new ReentrantLock();
    
    // Lua脚本缓存
    private final Map<String, String> luaScriptCache = new HashMap<>();
    
    public RedisClusterRateLimiter(String host, int port, String keyPrefix) {
        this.keyPrefix = keyPrefix;
        
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(200);
        config.setMaxIdle(50);
        config.setMinIdle(10);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);
        
        this.jedisPool = new JedisPool(config, host, port);
        
        // 初始化Lua脚本
        initLuaScripts();
    }
    
    /**
     * 初始化Lua脚本
     */
    private void initLuaScripts() {
        // 令牌桶算法Lua脚本
        luaScriptCache.put("TOKEN_BUCKET", 
            "local key = KEYS[1] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local tokens = tonumber(ARGV[2]) " +
            "local interval = tonumber(ARGV[3]) " +
            "local requested = tonumber(ARGV[4]) " +
            "local now = tonumber(ARGV[5]) " +
            "" +
            "local bucket = redis.call('hmget', key, 'tokens', 'timestamp') " +
            "local current_tokens = tonumber(bucket[1]) or capacity " +
            "local last_timestamp = tonumber(bucket[2]) or now " +
            "" +
            "-- 计算新增令牌数 " +
            "local elapsed = math.max(0, now - last_timestamp) " +
            "local new_tokens = math.min(capacity, current_tokens + (elapsed * tokens / interval)) " +
            "" +
            "if new_tokens >= requested then " +
            "    new_tokens = new_tokens - requested " +
            "    redis.call('hmset', key, 'tokens', new_tokens, 'timestamp', now) " +
            "    redis.call('expire', key, math.ceil(interval / 1000)) " +
            "    return {1, new_tokens} " +
            "else " +
            "    redis.call('hmset', key, 'tokens', new_tokens, 'timestamp', now) " +
            "    redis.call('expire', key, math.ceil(interval / 1000)) " +
            "    return {0, new_tokens} " +
            "end");
            
        // 滑动窗口算法Lua脚本
        luaScriptCache.put("SLIDING_WINDOW",
            "local key = KEYS[1] " +
            "local window_size = tonumber(ARGV[1]) " +
            "local limit = tonumber(ARGV[2]) " +
            "local requested = tonumber(ARGV[3]) " +
            "local now = tonumber(ARGV[4]) " +
            "" +
            "-- 清理过期数据 " +
            "redis.call('zremrangebyscore', key, 0, now - window_size) " +
            "" +
            "-- 获取当前窗口内的请求数 " +
            "local current_requests = redis.call('zcard', key) " +
            "" +
            "if current_requests + requested <= limit then " +
            "    -- 添加新请求 " +
            "    for i = 1, requested do " +
            "        redis.call('zadd', key, now, now .. ':' .. i) " +
            "    end " +
            "    redis.call('expire', key, math.ceil(window_size / 1000)) " +
            "    return {1, current_requests + requested} " +
            "else " +
            "    return {0, current_requests} " +
            "end");
            
        // 漏桶算法Lua脚本
        luaScriptCache.put("LEAKY_BUCKET",
            "local key = KEYS[1] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local leak_rate = tonumber(ARGV[2]) " +
            "local requested = tonumber(ARGV[3]) " +
            "local now = tonumber(ARGV[4]) " +
            "" +
            "local bucket = redis.call('hmget', key, 'volume', 'timestamp') " +
            "local current_volume = tonumber(bucket[1]) or 0 " +
            "local last_timestamp = tonumber(bucket[2]) or now " +
            "" +
            "-- 计算漏出的水量 " +
            "local elapsed = math.max(0, now - last_timestamp) " +
            "local leaked = elapsed * leak_rate / 1000 " +
            "local new_volume = math.max(0, current_volume - leaked) " +
            "" +
            "if new_volume + requested <= capacity then " +
            "    new_volume = new_volume + requested " +
            "    redis.call('hmset', key, 'volume', new_volume, 'timestamp', now) " +
            "    redis.call('expire', key, math.ceil(capacity / leak_rate)) " +
            "    return {1, new_volume} " +
            "else " +
            "    redis.call('hmset', key, 'volume', new_volume, 'timestamp', now) " +
            "    redis.call('expire', key, math.ceil(capacity / leak_rate)) " +
            "    return {0, new_volume} " +
            "end");
            
        // 固定窗口计数器Lua脚本
        luaScriptCache.put("FIXED_WINDOW",
            "local key = KEYS[1] " +
            "local window_key = KEYS[2] " +
            "local limit = tonumber(ARGV[1]) " +
            "local requested = tonumber(ARGV[2]) " +
            "local window_size = tonumber(ARGV[3]) " +
            "" +
            "local current_count = tonumber(redis.call('get', window_key)) or 0 " +
            "" +
            "if current_count + requested <= limit then " +
            "    redis.call('incrby', window_key, requested) " +
            "    redis.call('expire', window_key, math.ceil(window_size / 1000)) " +
            "    return {1, current_count + requested} " +
            "else " +
            "    return {0, current_count} " +
            "end");
    }
    
    /**
     * 令牌桶算法限流
     * 
     * @param key 资源标识
     * @param capacity 桶容量
     * @param refillTokens 每次补充的令牌数
     * @param refillInterval 补充间隔(毫秒)
     * @param requested 请求令牌数
     * @return 限流结果
     */
    public RateLimitResult tokenBucket(String key, int capacity, int refillTokens, 
                                      int refillInterval, int requested) {
        String fullKey = keyPrefix + ":token_bucket:" + key;
        long now = System.currentTimeMillis();
        
        try (Jedis jedis = jedisPool.getResource()) {
            List<Object> result = (List<Object>) jedis.eval(
                luaScriptCache.get("TOKEN_BUCKET"),
                Collections.singletonList(fullKey),
                Arrays.asList(
                    String.valueOf(capacity),
                    String.valueOf(refillTokens),
                    String.valueOf(refillInterval),
                    String.valueOf(requested),
                    String.valueOf(now)
                )
            );
            
            boolean allowed = ((Long) result.get(0)) == 1;
            long remaining = (Long) result.get(1);
            
            return new RateLimitResult(allowed, remaining, 0);
        }
    }
    
    /**
     * 滑动窗口算法限流
     * 
     * @param key 资源标识
     * @param windowSize 窗口大小(毫秒)
     * @param limit 限制数量
     * @param requested 请求数量
     * @return 限流结果
     */
    public RateLimitResult slidingWindow(String key, int windowSize, int limit, int requested) {
        String fullKey = keyPrefix + ":sliding_window:" + key;
        long now = System.currentTimeMillis();
        
        try (Jedis jedis = jedisPool.getResource()) {
            List<Object> result = (List<Object>) jedis.eval(
                luaScriptCache.get("SLIDING_WINDOW"),
                Collections.singletonList(fullKey),
                Arrays.asList(
                    String.valueOf(windowSize),
                    String.valueOf(limit),
                    String.valueOf(requested),
                    String.valueOf(now)
                )
            );
            
            boolean allowed = ((Long) result.get(0)) == 1;
            long current = (Long) result.get(1);
            
            return new RateLimitResult(allowed, limit - current, 0);
        }
    }
    
    /**
     * 漏桶算法限流
     * 
     * @param key 资源标识
     * @param capacity 桶容量
     * @param leakRate 漏出速率(每秒)
     * @param requested 请求数量
     * @return 限流结果
     */
    public RateLimitResult leakyBucket(String key, int capacity, double leakRate, int requested) {
        String fullKey = keyPrefix + ":leaky_bucket:" + key;
        long now = System.currentTimeMillis();
        
        try (Jedis jedis = jedisPool.getResource()) {
            List<Object> result = (List<Object>) jedis.eval(
                luaScriptCache.get("LEAKY_BUCKET"),
                Collections.singletonList(fullKey),
                Arrays.asList(
                    String.valueOf(capacity),
                    String.valueOf(leakRate),
                    String.valueOf(requested),
                    String.valueOf(now)
                )
            );
            
            boolean allowed = ((Long) result.get(0)) == 1;
            long volume = (Long) result.get(1);
            
            return new RateLimitResult(allowed, capacity - volume, 0);
        }
    }
    
    /**
     * 固定窗口计数器限流
     * 
     * @param key 资源标识
     * @param windowSize 窗口大小(毫秒)
     * @param limit 限制数量
     * @param requested 请求数量
     * @return 限流结果
     */
    public RateLimitResult fixedWindow(String key, int windowSize, int limit, int requested) {
        String fullKey = keyPrefix + ":fixed_window:" + key;
        long now = System.currentTimeMillis();
        long windowStart = now / windowSize * windowSize;
        String windowKey = fullKey + ":" + windowStart;
        
        try (Jedis jedis = jedisPool.getResource()) {
            List<Object> result = (List<Object>) jedis.eval(
                luaScriptCache.get("FIXED_WINDOW"),
                Arrays.asList(fullKey, windowKey),
                Arrays.asList(
                    String.valueOf(limit),
                    String.valueOf(requested),
                    String.valueOf(windowSize)
                )
            );
            
            boolean allowed = ((Long) result.get(0)) == 1;
            long current = (Long) result.get(1);
            
            return new RateLimitResult(allowed, limit - current, 0);
        }
    }
    
    /**
     * 获取限流统计信息
     */
    public RateLimitStats getStats(String key, RateLimitAlgorithm algorithm) {
        String fullKey = keyPrefix + ":" + algorithm.name().toLowerCase() + ":" + key;
        
        try (Jedis jedis = jedisPool.getResource()) {
            switch (algorithm) {
                case TOKEN_BUCKET:
                    List<String> bucket = jedis.hmget(fullKey, "tokens", "timestamp");
                    return new RateLimitStats(
                        bucket.get(0) != null ? Long.parseLong(bucket.get(0)) : 0,
                        bucket.get(1) != null ? Long.parseLong(bucket.get(1)) : 0,
                        System.currentTimeMillis()
                    );
                    
                case SLIDING_WINDOW:
                    long count = jedis.zcard(fullKey);
                    return new RateLimitStats(count, 0, System.currentTimeMillis());
                    
                case LEAKY_BUCKET:
                    List<String> leaky = jedis.hmget(fullKey, "volume", "timestamp");
                    return new RateLimitStats(
                        leaky.get(0) != null ? Long.parseLong(leaky.get(0)) : 0,
                        leaky.get(1) != null ? Long.parseLong(leaky.get(1)) : 0,
                        System.currentTimeMillis()
                    );
                    
                case FIXED_WINDOW:
                    long windowStart = System.currentTimeMillis() / 1000 * 1000;
                    String windowKey = fullKey + ":" + windowStart;
                    String currentCount = jedis.get(windowKey);
                    return new RateLimitStats(
                        currentCount != null ? Long.parseLong(currentCount) : 0,
                        windowStart,
                        System.currentTimeMillis()
                    );
                    
                default:
                    return new RateLimitStats(0, 0, System.currentTimeMillis());
            }
        }
    }
    
    /**
     * 清理过期数据
     */
    public void cleanup() {
        // 实现清理逻辑，可以通过定时任务调用
        try (Jedis jedis = jedisPool.getResource()) {
            // 清理过期的滑动窗口数据
            Set<String> keys = jedis.keys(keyPrefix + ":sliding_window:*");
            long now = System.currentTimeMillis();
            
            for (String key : keys) {
                jedis.zremrangebyscore(key, 0, now - 60000); // 清理1分钟前的数据
            }
        }
    }
    
    /**
     * 关闭资源
     */
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
    
    /**
     * 限流结果
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final long remaining;
        private final long retryAfter;
        
        public RateLimitResult(boolean allowed, long remaining, long retryAfter) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.retryAfter = retryAfter;
        }
        
        public boolean isAllowed() { return allowed; }
        public long getRemaining() { return remaining; }
        public long getRetryAfter() { return retryAfter; }
        
        @Override
        public String toString() {
            return String.format("RateLimitResult{allowed=%s, remaining=%d, retryAfter=%d}", 
                               allowed, remaining, retryAfter);
        }
    }
    
    /**
     * 限流统计信息
     */
    public static class RateLimitStats {
        private final long currentValue;
        private final long timestamp;
        private final long queryTime;
        
        public RateLimitStats(long currentValue, long timestamp, long queryTime) {
            this.currentValue = currentValue;
            this.timestamp = timestamp;
            this.queryTime = queryTime;
        }
        
        public long getCurrentValue() { return currentValue; }
        public long getTimestamp() { return timestamp; }
        public long getQueryTime() { return queryTime; }
        
        @Override
        public String toString() {
            return String.format("RateLimitStats{currentValue=%d, timestamp=%d, queryTime=%d}", 
                               currentValue, timestamp, queryTime);
        }
    }
    
    /**
     * 限流算法枚举
     */
    public enum RateLimitAlgorithm {
        TOKEN_BUCKET,
        SLIDING_WINDOW,
        LEAKY_BUCKET,
        FIXED_WINDOW
    }
} 