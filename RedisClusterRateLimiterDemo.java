package com.alibaba.csp.sentinel.demo.cluster;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis集群限流器演示
 * 
 * 演示内容：
 * 1. 不同算法的限流效果对比
 * 2. 并发场景下的限流表现
 * 3. 性能压测
 * 4. 降级策略演示
 */
public class RedisClusterRateLimiterDemo {
    
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String KEY_PREFIX = "sentinel:ratelimit";
    
    public static void main(String[] args) throws Exception {
        RedisClusterRateLimiter limiter = new RedisClusterRateLimiter(
            REDIS_HOST, REDIS_PORT, KEY_PREFIX
        );
        
        System.out.println("=== Redis集群限流器演示 ===\n");
        
        // 1. 基本功能演示
        basicFunctionDemo(limiter);
        
        // 2. 算法对比演示
        algorithmComparisonDemo(limiter);
        
        // 3. 并发性能测试
        concurrentPerformanceTest(limiter);
        
        // 4. 实际应用场景演示
        realWorldScenarioDemo(limiter);
        
        limiter.close();
    }
    
    /**
     * 基本功能演示
     */
    private static void basicFunctionDemo(RedisClusterRateLimiter limiter) {
        System.out.println("=== 1. 基本功能演示 ===");
        
        String resource = "api:user:info";
        
        // 令牌桶算法演示
        System.out.println("\n--- 令牌桶算法演示 ---");
        for (int i = 0; i < 5; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.tokenBucket(
                resource, 10, 2, 1000, 1
            );
            System.out.printf("请求%d: %s\n", i + 1, result);
        }
        
        // 滑动窗口算法演示
        System.out.println("\n--- 滑动窗口算法演示 ---");
        for (int i = 0; i < 5; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.slidingWindow(
                resource, 5000, 3, 1
            );
            System.out.printf("请求%d: %s\n", i + 1, result);
        }
        
        // 漏桶算法演示
        System.out.println("\n--- 漏桶算法演示 ---");
        for (int i = 0; i < 5; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.leakyBucket(
                resource, 5, 1.0, 1
            );
            System.out.printf("请求%d: %s\n", i + 1, result);
        }
        
        // 固定窗口算法演示
        System.out.println("\n--- 固定窗口算法演示 ---");
        for (int i = 0; i < 5; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.fixedWindow(
                resource, 2000, 3, 1
            );
            System.out.printf("请求%d: %s\n", i + 1, result);
        }
        
        System.out.println();
    }
    
    /**
     * 算法对比演示
     */
    private static void algorithmComparisonDemo(RedisClusterRateLimiter limiter) {
        System.out.println("=== 2. 算法对比演示 ===");
        
        String resource = "api:order:create";
        
        // 突发请求处理能力对比
        System.out.println("\n--- 突发请求处理能力对比 ---");
        
        // 令牌桶：允许一定的突发
        System.out.println("令牌桶(容量10，每秒补充5个):");
        for (int i = 0; i < 8; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.tokenBucket(
                resource + ":token", 10, 5, 1000, 1
            );
            System.out.printf("  请求%d: %s\n", i + 1, result);
        }
        
        // 固定窗口：可能有边界突发问题
        System.out.println("\n固定窗口(每秒限制5个):");
        for (int i = 0; i < 8; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.fixedWindow(
                resource + ":fixed", 1000, 5, 1
            );
            System.out.printf("  请求%d: %s\n", i + 1, result);
        }
        
        // 滑动窗口：更平滑的限流
        System.out.println("\n滑动窗口(5秒内限制5个):");
        for (int i = 0; i < 8; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.slidingWindow(
                resource + ":sliding", 5000, 5, 1
            );
            System.out.printf("  请求%d: %s\n", i + 1, result);
        }
        
        System.out.println();
    }
    
    /**
     * 并发性能测试
     */
    private static void concurrentPerformanceTest(RedisClusterRateLimiter limiter) 
            throws Exception {
        System.out.println("=== 3. 并发性能测试 ===");
        
        String resource = "api:concurrent:test";
        int threadCount = 10;
        int requestsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger allowedRequests = new AtomicInteger(0);
        AtomicInteger blockedRequests = new AtomicInteger(0);
        
        // 创建测试任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        totalRequests.incrementAndGet();
                        
                        // 使用令牌桶算法测试
                        RedisClusterRateLimiter.RateLimitResult result = limiter.tokenBucket(
                            resource, 50, 10, 1000, 1
                        );
                        
                        if (result.isAllowed()) {
                            allowedRequests.incrementAndGet();
                        } else {
                            blockedRequests.incrementAndGet();
                        }
                        
                        // 模拟请求处理时间
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await();
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        System.out.printf("并发测试结果：\n");
        System.out.printf("  线程数: %d\n", threadCount);
        System.out.printf("  每线程请求数: %d\n", requestsPerThread);
        System.out.printf("  总请求数: %d\n", totalRequests.get());
        System.out.printf("  允许通过: %d\n", allowedRequests.get());
        System.out.printf("  被拒绝: %d\n", blockedRequests.get());
        System.out.printf("  通过率: %.2f%%\n", 
                         allowedRequests.get() * 100.0 / totalRequests.get());
        System.out.printf("  总耗时: %d ms\n", endTime - startTime);
        System.out.printf("  平均QPS: %.2f\n", 
                         totalRequests.get() * 1000.0 / (endTime - startTime));
        System.out.println();
    }
    
    /**
     * 实际应用场景演示
     */
    private static void realWorldScenarioDemo(RedisClusterRateLimiter limiter) {
        System.out.println("=== 4. 实际应用场景演示 ===");
        
        // 场景1：API限流
        System.out.println("\n--- 场景1：API限流 ---");
        simulateApiRateLimit(limiter);
        
        // 场景2：用户行为限流
        System.out.println("\n--- 场景2：用户行为限流 ---");
        simulateUserBehaviorLimit(limiter);
        
        // 场景3：系统保护
        System.out.println("\n--- 场景3：系统保护 ---");
        simulateSystemProtection(limiter);
        
        System.out.println();
    }
    
    /**
     * 模拟API限流
     */
    private static void simulateApiRateLimit(RedisClusterRateLimiter limiter) {
        // 不同级别的API限流
        String[] apis = {
            "api:public:search",      // 公开API，限制较松
            "api:user:profile",       // 用户API，中等限制
            "api:admin:config"        // 管理API，严格限制
        };
        
        int[] limits = {100, 50, 10};
        
        for (int i = 0; i < apis.length; i++) {
            System.out.printf("%s (限制: %d/分钟):\n", apis[i], limits[i]);
            
            // 模拟请求
            for (int j = 0; j < 5; j++) {
                RedisClusterRateLimiter.RateLimitResult result = limiter.slidingWindow(
                    apis[i], 60000, limits[i], 1
                );
                System.out.printf("  请求%d: %s\n", j + 1, result);
            }
        }
    }
    
    /**
     * 模拟用户行为限流
     */
    private static void simulateUserBehaviorLimit(RedisClusterRateLimiter limiter) {
        // 用户行为限流
        String[] behaviors = {
            "user:123:login",         // 登录限流
            "user:123:comment",       // 评论限流
            "user:123:upload"         // 上传限流
        };
        
        System.out.println("用户行为限流演示:");
        
        for (String behavior : behaviors) {
            RedisClusterRateLimiter.RateLimitResult result;
            
            if (behavior.contains("login")) {
                // 登录失败限流：5分钟内最多5次
                result = limiter.slidingWindow(behavior, 300000, 5, 1);
            } else if (behavior.contains("comment")) {
                // 评论限流：每分钟最多10条
                result = limiter.slidingWindow(behavior, 60000, 10, 1);
            } else {
                // 上传限流：使用漏桶算法，平滑处理
                result = limiter.leakyBucket(behavior, 5, 0.5, 1);
            }
            
            System.out.printf("  %s: %s\n", behavior, result);
        }
    }
    
    /**
     * 模拟系统保护
     */
    private static void simulateSystemProtection(RedisClusterRateLimiter limiter) {
        // 系统级别保护
        System.out.println("系统级别保护演示:");
        
        // 全局QPS限制
        String globalKey = "system:global:qps";
        for (int i = 0; i < 3; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.tokenBucket(
                globalKey, 1000, 100, 1000, 10
            );
            System.out.printf("  全局QPS检查%d: %s\n", i + 1, result);
        }
        
        // 数据库连接限制
        String dbKey = "system:db:connections";
        for (int i = 0; i < 3; i++) {
            RedisClusterRateLimiter.RateLimitResult result = limiter.fixedWindow(
                dbKey, 1000, 50, 1
            );
            System.out.printf("  数据库连接检查%d: %s\n", i + 1, result);
        }
    }
    
    /**
     * 性能压测工具
     */
    static class PerformanceTester {
        private final RedisClusterRateLimiter limiter;
        private final String resource;
        
        public PerformanceTester(RedisClusterRateLimiter limiter, String resource) {
            this.limiter = limiter;
            this.resource = resource;
        }
        
        public void testTokenBucket(int threads, int requests) throws Exception {
            System.out.printf("令牌桶算法性能测试: %d线程 x %d请求\n", threads, requests);
            
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < requests; j++) {
                            limiter.tokenBucket(resource, 100, 10, 1000, 1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            long endTime = System.currentTimeMillis();
            
            executor.shutdown();
            
            long totalRequests = (long) threads * requests;
            long duration = endTime - startTime;
            
            System.out.printf("  总请求数: %d\n", totalRequests);
            System.out.printf("  总耗时: %d ms\n", duration);
            System.out.printf("  平均QPS: %.2f\n", totalRequests * 1000.0 / duration);
            System.out.printf("  平均延迟: %.2f ms\n", duration * 1.0 / totalRequests);
        }
    }
} 