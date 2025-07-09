package com.alibaba.csp.sentinel.demo.cluster;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis集群限流与Sentinel集成示例
 * 
 * 集成方式：
 * 1. 作为Sentinel的外部限流补充
 * 2. 用于跨集群的全局限流
 * 3. 提供降级和监控能力
 * 4. 支持动态配置更新
 */
public class RedisClusterRateLimitIntegration {
    
    private final RedisClusterRateLimiter redisLimiter;
    private final RedisRateLimitConfig configManager;
    private final LocalFallbackLimiter localFallback;
    
    // 性能统计
    private final ConcurrentHashMap<String, RequestStats> statsMap = new ConcurrentHashMap<>();
    
    public RedisClusterRateLimitIntegration(String redisHost, int redisPort) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(200);
        config.setMaxIdle(50);
        config.setMinIdle(10);
        
        JedisPool jedisPool = new JedisPool(config, redisHost, redisPort);
        
        this.redisLimiter = new RedisClusterRateLimiter(jedisPool, "sentinel:cluster:ratelimit");
        this.configManager = new RedisRateLimitConfig(jedisPool, 
            "sentinel:cluster:config", "sentinel:cluster:config:channel");
        this.localFallback = new LocalFallbackLimiter();
        
        // 初始化Sentinel规则
        initSentinelRules();
    }
    
    /**
     * 初始化Sentinel规则
     */
    private void initSentinelRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        // 本地限流规则作为第一道防线
        FlowRule localRule = new FlowRule();
        localRule.setResource("api:user:profile");
        localRule.setCount(200); // 本地限制200 QPS
        localRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        localRule.setLimitApp("default");
        rules.add(localRule);
        
        FlowRule localRule2 = new FlowRule();
        localRule2.setResource("api:order:create");
        localRule2.setCount(100); // 本地限制100 QPS
        localRule2.setGrade(RuleConstant.FLOW_GRADE_QPS);
        localRule2.setLimitApp("default");
        rules.add(localRule2);
        
        FlowRuleManager.loadRules(rules);
    }
    
    /**
     * 多级限流检查
     * 
     * @param resource 资源名称
     * @param count 请求数量
     * @return 是否允许通过
     */
    public boolean checkRateLimit(String resource, int count) {
        long startTime = System.currentTimeMillis();
        boolean allowed = false;
        String strategy = "unknown";
        
        try {
            // 第一级：Sentinel本地限流
            Entry entry = null;
            try {
                entry = SphU.entry(resource);
                strategy = "sentinel_local";
                
                // 第二级：Redis集群限流
                allowed = checkRedisRateLimit(resource, count);
                if (allowed) {
                    strategy = "redis_cluster";
                }
                
            } catch (BlockException e) {
                // Sentinel本地限流触发，检查是否需要降级
                allowed = handleSentinelBlock(resource, count);
                if (allowed) {
                    strategy = "sentinel_fallback";
                }
            } finally {
                if (entry != null) {
                    entry.exit();
                }
            }
            
        } catch (Exception e) {
            // Redis或其他异常，使用本地降级策略
            allowed = localFallback.checkLimit(resource, count);
            strategy = "local_fallback";
        } finally {
            // 记录统计信息
            recordStats(resource, allowed, strategy, System.currentTimeMillis() - startTime);
        }
        
        return allowed;
    }
    
    /**
     * Redis集群限流检查
     */
    private boolean checkRedisRateLimit(String resource, int count) {
        // 检查Redis健康状态
        if (!configManager.isRedisHealthy()) {
            return localFallback.checkLimit(resource, count);
        }
        
        // 获取限流规则
        RedisRateLimitConfig.RateLimitRule rule = configManager.getRule(resource);
        if (rule == null || !rule.isEnabled()) {
            return true; // 没有规则或规则未启用，直接通过
        }
        
        // 根据算法类型执行限流
        RedisClusterRateLimiter.RateLimitResult result;
        switch (rule.getAlgorithm()) {
            case "TOKEN_BUCKET":
                result = redisLimiter.tokenBucket(
                    resource, rule.getLimit(), rule.getRefillTokens(), 
                    rule.getRefillInterval(), count
                );
                break;
                
            case "SLIDING_WINDOW":
                result = redisLimiter.slidingWindow(
                    resource, rule.getWindowSizeMs(), rule.getLimit(), count
                );
                break;
                
            case "LEAKY_BUCKET":
                result = redisLimiter.leakyBucket(
                    resource, rule.getLimit(), rule.getRefillTokens(), count
                );
                break;
                
            case "FIXED_WINDOW":
                result = redisLimiter.fixedWindow(
                    resource, rule.getWindowSizeMs(), rule.getLimit(), count
                );
                break;
                
            default:
                return true; // 未知算法，直接通过
        }
        
        return result.isAllowed();
    }
    
    /**
     * 处理Sentinel阻断情况
     */
    private boolean handleSentinelBlock(String resource, int count) {
        // 获取降级策略
        RedisRateLimitConfig.FallbackStrategy fallback = configManager.getFallbackStrategy(resource);
        if (fallback == null || !fallback.isEnabled()) {
            return false; // 没有降级策略，直接拒绝
        }
        
        switch (fallback.getType()) {
            case "PASS_ALL":
                return true; // 降级时全部通过
                
            case "BLOCK_ALL":
                return false; // 降级时全部拒绝
                
            case "LOCAL_LIMIT":
                // 使用本地限流作为降级策略
                return localFallback.checkLimit(resource, count, 
                    fallback.getLocalLimit(), fallback.getLocalWindowMs());
                
            default:
                return false;
        }
    }
    
    /**
     * 记录统计信息
     */
    private void recordStats(String resource, boolean allowed, String strategy, long responseTime) {
        RequestStats stats = statsMap.computeIfAbsent(resource, k -> new RequestStats(resource));
        stats.record(allowed, strategy, responseTime);
    }
    
    /**
     * 获取统计信息
     */
    public RequestStats getStats(String resource) {
        return statsMap.get(resource);
    }
    
    /**
     * 获取监控指标
     */
    public RedisRateLimitConfig.RateLimitMetrics getMetrics(String resource) {
        return configManager.getMetrics(resource);
    }
    
    /**
     * 动态更新配置
     */
    public void updateConfig(RedisRateLimitConfig.RateLimitConfig config) {
        configManager.publishConfig(config);
    }
    
    /**
     * 关闭集成组件
     */
    public void shutdown() {
        redisLimiter.close();
        configManager.shutdown();
    }
    
    /**
     * 本地降级限流器
     */
    private static class LocalFallbackLimiter {
        private final ConcurrentHashMap<String, TokenBucket> localBuckets = new ConcurrentHashMap<>();
        
        public boolean checkLimit(String resource, int count) {
            return checkLimit(resource, count, 50, 1000); // 默认50 QPS
        }
        
        public boolean checkLimit(String resource, int count, int limit, int windowMs) {
            TokenBucket bucket = localBuckets.computeIfAbsent(resource, 
                k -> new TokenBucket(limit, limit, windowMs));
            return bucket.tryConsume(count);
        }
        
        /**
         * 简单的令牌桶实现
         */
        private static class TokenBucket {
            private final int capacity;
            private final int refillRate;
            private final int refillInterval;
            private volatile int tokens;
            private volatile long lastRefillTime;
            
            public TokenBucket(int capacity, int refillRate, int refillInterval) {
                this.capacity = capacity;
                this.refillRate = refillRate;
                this.refillInterval = refillInterval;
                this.tokens = capacity;
                this.lastRefillTime = System.currentTimeMillis();
            }
            
            public synchronized boolean tryConsume(int count) {
                refill();
                if (tokens >= count) {
                    tokens -= count;
                    return true;
                }
                return false;
            }
            
            private void refill() {
                long now = System.currentTimeMillis();
                long elapsed = now - lastRefillTime;
                if (elapsed >= refillInterval) {
                    int newTokens = (int) (elapsed / refillInterval * refillRate);
                    tokens = Math.min(capacity, tokens + newTokens);
                    lastRefillTime = now;
                }
            }
        }
    }
    
    /**
     * 请求统计
     */
    public static class RequestStats {
        private final String resource;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong allowedRequests = new AtomicLong(0);
        private final AtomicLong blockedRequests = new AtomicLong(0);
        private final AtomicLong sentinelBlocked = new AtomicLong(0);
        private final AtomicLong redisBlocked = new AtomicLong(0);
        private final AtomicLong fallbackUsed = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        
        public RequestStats(String resource) {
            this.resource = resource;
        }
        
        public void record(boolean allowed, String strategy, long responseTime) {
            totalRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            
            if (allowed) {
                allowedRequests.incrementAndGet();
            } else {
                blockedRequests.incrementAndGet();
            }
            
            switch (strategy) {
                case "sentinel_local":
                    if (!allowed) sentinelBlocked.incrementAndGet();
                    break;
                case "redis_cluster":
                    if (!allowed) redisBlocked.incrementAndGet();
                    break;
                case "local_fallback":
                case "sentinel_fallback":
                    fallbackUsed.incrementAndGet();
                    break;
            }
        }
        
        public double getPassRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) allowedRequests.get() / total : 0;
        }
        
        public double getBlockRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) blockedRequests.get() / total : 0;
        }
        
        public double getAverageResponseTime() {
            long total = totalRequests.get();
            return total > 0 ? (double) totalResponseTime.get() / total : 0;
        }
        
        public double getFallbackRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) fallbackUsed.get() / total : 0;
        }
        
        // Getters
        public String getResource() { return resource; }
        public long getTotalRequests() { return totalRequests.get(); }
        public long getAllowedRequests() { return allowedRequests.get(); }
        public long getBlockedRequests() { return blockedRequests.get(); }
        public long getSentinelBlocked() { return sentinelBlocked.get(); }
        public long getRedisBlocked() { return redisBlocked.get(); }
        public long getFallbackUsed() { return fallbackUsed.get(); }
        
        @Override
        public String toString() {
            return String.format(
                "RequestStats{resource='%s', total=%d, allowed=%d, blocked=%d, " +
                "passRate=%.2f%%, blockRate=%.2f%%, fallbackRate=%.2f%%, avgResponseTime=%.2f ms}",
                resource, getTotalRequests(), getAllowedRequests(), getBlockedRequests(),
                getPassRate() * 100, getBlockRate() * 100, getFallbackRate() * 100,
                getAverageResponseTime()
            );
        }
    }
    
    /**
     * 集成使用示例
     */
    public static void main(String[] args) throws Exception {
        // 创建集成实例
        RedisClusterRateLimitIntegration integration = new RedisClusterRateLimitIntegration(
            "localhost", 6379
        );
        
        // 配置示例
        configureRateLimitRules(integration);
        
        // 模拟请求
        simulateRequests(integration);
        
        // 查看统计信息
        printStatistics(integration);
        
        // 关闭
        integration.shutdown();
    }
    
    /**
     * 配置限流规则
     */
    private static void configureRateLimitRules(RedisClusterRateLimitIntegration integration) {
        RedisRateLimitConfig.RateLimitConfig config = new RedisRateLimitConfig.RateLimitConfig();
        
        // 用户API限流规则
        RedisRateLimitConfig.RateLimitRule userRule = new RedisRateLimitConfig.RateLimitRule();
        userRule.setResource("api:user:profile");
        userRule.setAlgorithm("TOKEN_BUCKET");
        userRule.setLimit(100);
        userRule.setRefillTokens(10);
        userRule.setRefillInterval(1000);
        userRule.setEnabled(true);
        config.addRule(userRule);
        
        // 订单API限流规则
        RedisRateLimitConfig.RateLimitRule orderRule = new RedisRateLimitConfig.RateLimitRule();
        orderRule.setResource("api:order:create");
        orderRule.setAlgorithm("SLIDING_WINDOW");
        orderRule.setLimit(50);
        orderRule.setWindowSizeMs(5000);
        orderRule.setEnabled(true);
        config.addRule(orderRule);
        
        // 全局降级策略
        RedisRateLimitConfig.FallbackStrategy globalFallback = new RedisRateLimitConfig.FallbackStrategy();
        globalFallback.setType("LOCAL_LIMIT");
        globalFallback.setLocalLimit(30);
        globalFallback.setLocalWindowMs(1000);
        globalFallback.setEnabled(true);
        config.setGlobalFallback(globalFallback);
        
        // 发布配置
        integration.updateConfig(config);
        
        System.out.println("限流规则配置完成");
    }
    
    /**
     * 模拟请求
     */
    private static void simulateRequests(RedisClusterRateLimitIntegration integration) 
            throws InterruptedException {
        System.out.println("开始模拟请求...");
        
        String[] resources = {"api:user:profile", "api:order:create"};
        
        for (int i = 0; i < 100; i++) {
            for (String resource : resources) {
                boolean allowed = integration.checkRateLimit(resource, 1);
                if (i % 20 == 0) {
                    System.out.printf("请求%d - %s: %s\n", i, resource, allowed ? "通过" : "拒绝");
                }
            }
            Thread.sleep(10);
        }
        
        System.out.println("请求模拟完成");
    }
    
    /**
     * 打印统计信息
     */
    private static void printStatistics(RedisClusterRateLimitIntegration integration) {
        System.out.println("\n=== 统计信息 ===");
        
        String[] resources = {"api:user:profile", "api:order:create"};
        
        for (String resource : resources) {
            RequestStats stats = integration.getStats(resource);
            if (stats != null) {
                System.out.println(stats);
            }
            
            RedisRateLimitConfig.RateLimitMetrics metrics = integration.getMetrics(resource);
            if (metrics != null) {
                System.out.println(metrics);
            }
        }
    }
} 