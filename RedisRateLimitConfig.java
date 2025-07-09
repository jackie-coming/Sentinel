package com.alibaba.csp.sentinel.demo.cluster;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Redis限流配置管理和容错机制
 * 
 * 功能特点：
 * 1. 动态配置更新
 * 2. 降级策略
 * 3. 监控和告警
 * 4. 故障恢复
 * 5. 配置持久化
 */
public class RedisRateLimitConfig {
    
    private final JedisPool jedisPool;
    private final String configKey;
    private final String configChannel;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 配置缓存
    private final ConcurrentHashMap<String, RateLimitRule> ruleCache = new ConcurrentHashMap<>();
    
    // 降级策略
    private final ConcurrentHashMap<String, FallbackStrategy> fallbackStrategies = new ConcurrentHashMap<>();
    
    // 监控数据
    private final ConcurrentHashMap<String, RateLimitMetrics> metricsCache = new ConcurrentHashMap<>();
    
    // 定时任务
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // 健康检查
    private volatile boolean redisHealthy = true;
    private volatile long lastHealthCheckTime = System.currentTimeMillis();
    
    public RedisRateLimitConfig(JedisPool jedisPool, String configKey, String configChannel) {
        this.jedisPool = jedisPool;
        this.configKey = configKey;
        this.configChannel = configChannel;
        
        init();
    }
    
    /**
     * 初始化配置管理器
     */
    private void init() {
        // 加载初始配置
        loadInitialConfig();
        
        // 启动配置监听
        startConfigListener();
        
        // 启动健康检查
        startHealthCheck();
        
        // 启动指标收集
        startMetricsCollection();
    }
    
    /**
     * 加载初始配置
     */
    private void loadInitialConfig() {
        try (Jedis jedis = jedisPool.getResource()) {
            String configJson = jedis.get(configKey);
            if (configJson != null) {
                RateLimitConfig config = JSON.parseObject(configJson, RateLimitConfig.class);
                updateRules(config);
            }
        } catch (Exception e) {
            System.err.println("Failed to load initial config: " + e.getMessage());
            // 使用默认配置
            loadDefaultConfig();
        }
    }
    
    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        RateLimitConfig defaultConfig = new RateLimitConfig();
        
        // 添加默认规则
        RateLimitRule defaultRule = new RateLimitRule();
        defaultRule.setResource("default");
        defaultRule.setAlgorithm("TOKEN_BUCKET");
        defaultRule.setLimit(100);
        defaultRule.setWindowSizeMs(1000);
        defaultRule.setEnabled(true);
        
        defaultConfig.addRule(defaultRule);
        
        // 添加默认降级策略
        FallbackStrategy defaultFallback = new FallbackStrategy();
        defaultFallback.setType("PASS_ALL");
        defaultFallback.setEnabled(true);
        defaultConfig.setGlobalFallback(defaultFallback);
        
        updateRules(defaultConfig);
    }
    
    /**
     * 启动配置监听
     */
    private void startConfigListener() {
        Thread listenerThread = new Thread(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        if (configChannel.equals(channel)) {
                            try {
                                RateLimitConfig config = JSON.parseObject(message, RateLimitConfig.class);
                                updateRules(config);
                                System.out.println("Configuration updated: " + message);
                            } catch (Exception e) {
                                System.err.println("Failed to parse config: " + e.getMessage());
                            }
                        }
                    }
                }, configChannel);
            } catch (Exception e) {
                System.err.println("Config listener error: " + e.getMessage());
            }
        });
        
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        scheduler.scheduleWithFixedDelay(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                redisHealthy = true;
                lastHealthCheckTime = System.currentTimeMillis();
            } catch (Exception e) {
                redisHealthy = false;
                System.err.println("Redis health check failed: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 启动指标收集
     */
    private void startMetricsCollection() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                collectMetrics();
            } catch (Exception e) {
                System.err.println("Metrics collection failed: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 更新规则
     */
    private void updateRules(RateLimitConfig config) {
        lock.writeLock().lock();
        try {
            // 清除旧规则
            ruleCache.clear();
            fallbackStrategies.clear();
            
            // 添加新规则
            if (config.getRules() != null) {
                for (RateLimitRule rule : config.getRules()) {
                    ruleCache.put(rule.getResource(), rule);
                }
            }
            
            // 添加降级策略
            if (config.getGlobalFallback() != null) {
                fallbackStrategies.put("global", config.getGlobalFallback());
            }
            
            if (config.getResourceFallbacks() != null) {
                fallbackStrategies.putAll(config.getResourceFallbacks());
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取限流规则
     */
    public RateLimitRule getRule(String resource) {
        lock.readLock().lock();
        try {
            return ruleCache.get(resource);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取降级策略
     */
    public FallbackStrategy getFallbackStrategy(String resource) {
        lock.readLock().lock();
        try {
            FallbackStrategy strategy = fallbackStrategies.get(resource);
            if (strategy == null) {
                strategy = fallbackStrategies.get("global");
            }
            return strategy;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 检查Redis健康状态
     */
    public boolean isRedisHealthy() {
        return redisHealthy && (System.currentTimeMillis() - lastHealthCheckTime) < 30000;
    }
    
    /**
     * 收集指标
     */
    private void collectMetrics() {
        lock.readLock().lock();
        try {
            for (String resource : ruleCache.keySet()) {
                RateLimitMetrics metrics = metricsCache.computeIfAbsent(resource, 
                    k -> new RateLimitMetrics(resource));
                
                // 这里可以从Redis获取实际的统计数据
                // 为了演示，我们使用模拟数据
                metrics.updateMetrics(
                    System.currentTimeMillis(),
                    (long) (Math.random() * 100), // 总请求数
                    (long) (Math.random() * 20),  // 被拒绝请求数
                    (long) (Math.random() * 10)   // 平均响应时间
                );
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取监控指标
     */
    public RateLimitMetrics getMetrics(String resource) {
        return metricsCache.get(resource);
    }
    
    /**
     * 发布配置更新
     */
    public void publishConfig(RateLimitConfig config) {
        try (Jedis jedis = jedisPool.getResource()) {
            String configJson = JSON.toJSONString(config);
            jedis.multi();
            jedis.set(configKey, configJson);
            jedis.publish(configChannel, configJson);
            jedis.exec();
            System.out.println("Config published: " + configJson);
        } catch (Exception e) {
            System.err.println("Failed to publish config: " + e.getMessage());
        }
    }
    
    /**
     * 关闭配置管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
    
    /**
     * 限流规则
     */
    public static class RateLimitRule {
        private String resource;
        private String algorithm;
        private int limit;
        private int windowSizeMs;
        private int refillTokens;
        private int refillInterval;
        private boolean enabled;
        
        // Getters and Setters
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        
        public int getWindowSizeMs() { return windowSizeMs; }
        public void setWindowSizeMs(int windowSizeMs) { this.windowSizeMs = windowSizeMs; }
        
        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
        
        public int getRefillInterval() { return refillInterval; }
        public void setRefillInterval(int refillInterval) { this.refillInterval = refillInterval; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    /**
     * 降级策略
     */
    public static class FallbackStrategy {
        private String type; // PASS_ALL, BLOCK_ALL, LOCAL_LIMIT
        private boolean enabled;
        private int localLimit;
        private int localWindowMs;
        
        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getLocalLimit() { return localLimit; }
        public void setLocalLimit(int localLimit) { this.localLimit = localLimit; }
        
        public int getLocalWindowMs() { return localWindowMs; }
        public void setLocalWindowMs(int localWindowMs) { this.localWindowMs = localWindowMs; }
    }
    
    /**
     * 监控指标
     */
    public static class RateLimitMetrics {
        private final String resource;
        private volatile long totalRequests;
        private volatile long blockedRequests;
        private volatile long averageResponseTime;
        private volatile long lastUpdateTime;
        
        public RateLimitMetrics(String resource) {
            this.resource = resource;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void updateMetrics(long timestamp, long totalRequests, 
                                 long blockedRequests, long averageResponseTime) {
            this.totalRequests = totalRequests;
            this.blockedRequests = blockedRequests;
            this.averageResponseTime = averageResponseTime;
            this.lastUpdateTime = timestamp;
        }
        
        public double getBlockedRatio() {
            return totalRequests > 0 ? (double) blockedRequests / totalRequests : 0;
        }
        
        public double getPassRatio() {
            return 1.0 - getBlockedRatio();
        }
        
        // Getters
        public String getResource() { return resource; }
        public long getTotalRequests() { return totalRequests; }
        public long getBlockedRequests() { return blockedRequests; }
        public long getAverageResponseTime() { return averageResponseTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        @Override
        public String toString() {
            return String.format("RateLimitMetrics{resource='%s', totalRequests=%d, " +
                               "blockedRequests=%d, blockedRatio=%.2f%%, avgResponseTime=%d ms}",
                               resource, totalRequests, blockedRequests, 
                               getBlockedRatio() * 100, averageResponseTime);
        }
    }
    
    /**
     * 配置对象
     */
    public static class RateLimitConfig {
        @JSONField(name = "rules")
        private java.util.List<RateLimitRule> rules;
        
        @JSONField(name = "globalFallback")
        private FallbackStrategy globalFallback;
        
        @JSONField(name = "resourceFallbacks")
        private java.util.Map<String, FallbackStrategy> resourceFallbacks;
        
        public RateLimitConfig() {
            this.rules = new java.util.ArrayList<>();
            this.resourceFallbacks = new java.util.HashMap<>();
        }
        
        public void addRule(RateLimitRule rule) {
            this.rules.add(rule);
        }
        
        // Getters and Setters
        public java.util.List<RateLimitRule> getRules() { return rules; }
        public void setRules(java.util.List<RateLimitRule> rules) { this.rules = rules; }
        
        public FallbackStrategy getGlobalFallback() { return globalFallback; }
        public void setGlobalFallback(FallbackStrategy globalFallback) { this.globalFallback = globalFallback; }
        
        public java.util.Map<String, FallbackStrategy> getResourceFallbacks() { return resourceFallbacks; }
        public void setResourceFallbacks(java.util.Map<String, FallbackStrategy> resourceFallbacks) { 
            this.resourceFallbacks = resourceFallbacks; 
        }
    }
} 