package com.alibaba.csp.sentinel.demo.window;

import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;
import com.alibaba.csp.sentinel.slots.statistic.metric.BucketLeapArray;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * currentWindow方法性能分析演示
 * 
 * 分析不同场景下的性能表现：
 * 1. 单线程性能测试
 * 2. 多线程并发性能测试
 * 3. 不同bucket配置的性能对比
 * 4. 缓存命中率分析
 */
public class CurrentWindowPerformanceAnalysis {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== currentWindow方法性能分析 ===\n");
        
        // 1. 单线程性能测试
        testSingleThreadPerformance();
        
        // 2. 多线程并发性能测试
        testConcurrentPerformance();
        
        // 3. 不同配置的性能对比
        testDifferentConfigurations();
        
        // 4. 缓存命中率分析
        testCacheHitRate();
    }
    
    /**
     * 单线程性能测试
     */
    private static void testSingleThreadPerformance() {
        System.out.println("=== 1. 单线程性能测试 ===");
        
        int sampleCount = 10;
        int intervalInMs = 1000;
        BucketLeapArray leapArray = new BucketLeapArray(sampleCount, intervalInMs);
        
        int iterations = 1_000_000;
        long startTime = System.nanoTime();
        long baseTime = TimeUtil.currentTimeMillis();
        
        // 测试连续访问当前时间窗口（最佳情况 - 缓存命中）
        for (int i = 0; i < iterations; i++) {
            WindowWrap<MetricBucket> window = leapArray.currentWindow(baseTime);
            window.value().addPass(1); // 添加操作以模拟真实使用
        }
        
        long endTime = System.nanoTime();
        long durationNs = endTime - startTime;
        
        System.out.println("测试场景: 连续访问同一时间窗口");
        System.out.println("迭代次数: " + iterations);
        System.out.println("总耗时: " + durationNs / 1_000_000 + "ms");
        System.out.println("平均耗时: " + durationNs / iterations + "ns/op");
        System.out.println("吞吐量: " + String.format("%.2f", iterations * 1_000_000_000.0 / durationNs) + " ops/sec");
        System.out.println();
        
        // 测试跨时间窗口访问（需要创建新窗口）
        startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            long time = baseTime + i * 50; // 每50ms一个请求
            WindowWrap<MetricBucket> window = leapArray.currentWindow(time);
            window.value().addPass(1);
        }
        endTime = System.nanoTime();
        durationNs = endTime - startTime;
        
        System.out.println("测试场景: 跨时间窗口访问");
        System.out.println("迭代次数: 1000");
        System.out.println("总耗时: " + durationNs / 1_000_000 + "ms");
        System.out.println("平均耗时: " + durationNs / 1000 + "ns/op");
        System.out.println();
    }
    
    /**
     * 多线程并发性能测试
     */
    private static void testConcurrentPerformance() throws Exception {
        System.out.println("=== 2. 多线程并发性能测试 ===");
        
        int sampleCount = 20;
        int intervalInMs = 2000;
        BucketLeapArray leapArray = new BucketLeapArray(sampleCount, intervalInMs);
        
        int threadCount = 10;
        int operationsPerThread = 100_000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong totalTime = new AtomicLong(0);
        
        // 创建并发测试任务
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    
                    long threadStartTime = System.nanoTime();
                    long baseTime = TimeUtil.currentTimeMillis();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        // 模拟不同的时间访问模式
                        long time = baseTime + (threadId * 100) + (j % 200);
                        WindowWrap<MetricBucket> window = leapArray.currentWindow(time);
                        window.value().addPass(1);
                    }
                    
                    long threadEndTime = System.nanoTime();
                    totalOperations.addAndGet(operationsPerThread);
                    totalTime.addAndGet(threadEndTime - threadStartTime);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        long overallStartTime = System.nanoTime();
        startLatch.countDown(); // 启动所有线程
        doneLatch.await(); // 等待所有线程完成
        long overallEndTime = System.nanoTime();
        
        executor.shutdown();
        
        long overallDuration = overallEndTime - overallStartTime;
        
        System.out.println("并发配置: " + threadCount + "个线程，每线程" + operationsPerThread + "次操作");
        System.out.println("总操作数: " + totalOperations.get());
        System.out.println("总耗时: " + overallDuration / 1_000_000 + "ms");
        System.out.println("平均线程耗时: " + totalTime.get() / threadCount / 1_000_000 + "ms");
        System.out.println("整体吞吐量: " + String.format("%.2f", totalOperations.get() * 1_000_000_000.0 / overallDuration) + " ops/sec");
        System.out.println("并发效率: " + String.format("%.2f", (double)overallDuration / (totalTime.get() / threadCount) * 100) + "%");
        System.out.println();
    }
    
    /**
     * 不同配置的性能对比
     */
    private static void testDifferentConfigurations() {
        System.out.println("=== 3. 不同配置的性能对比 ===");
        
        // 测试不同的bucket配置
        int[][] configs = {
            {2, 1000},    // 2个bucket，1秒窗口
            {10, 1000},   // 10个bucket，1秒窗口
            {20, 1000},   // 20个bucket，1秒窗口
            {60, 1000},   // 60个bucket，1秒窗口
        };
        
        int iterations = 100_000;
        
        for (int[] config : configs) {
            int sampleCount = config[0];
            int intervalInMs = config[1];
            
            BucketLeapArray leapArray = new BucketLeapArray(sampleCount, intervalInMs);
            
            long startTime = System.nanoTime();
            long baseTime = TimeUtil.currentTimeMillis();
            
            // 模拟随机时间访问
            for (int i = 0; i < iterations; i++) {
                long time = baseTime + (i % 2000); // 在2秒范围内随机访问
                WindowWrap<MetricBucket> window = leapArray.currentWindow(time);
                window.value().addPass(1);
            }
            
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            
            System.out.println("配置: " + sampleCount + "个bucket，" + intervalInMs + "ms窗口");
            System.out.println("  平均耗时: " + duration / iterations + "ns/op");
            System.out.println("  吞吐量: " + String.format("%.2f", iterations * 1_000_000_000.0 / duration) + " ops/sec");
        }
        System.out.println();
    }
    
    /**
     * 缓存命中率分析
     */
    private static void testCacheHitRate() {
        System.out.println("=== 4. 缓存命中率分析 ===");
        
        int sampleCount = 10;
        int intervalInMs = 1000;
        TestableLeapArray leapArray = new TestableLeapArray(sampleCount, intervalInMs);
        
        long baseTime = TimeUtil.currentTimeMillis();
        int totalRequests = 10000;
        
        // 模拟不同的访问模式
        String[] patterns = {"连续访问", "随机访问", "周期性访问"};
        
        for (int patternIdx = 0; patternIdx < patterns.length; patternIdx++) {
            leapArray.resetCounters();
            
            for (int i = 0; i < totalRequests; i++) {
                long time;
                switch (patternIdx) {
                    case 0: // 连续访问同一时间段
                        time = baseTime + (i / 100) * 50; // 每100个请求推进50ms
                        break;
                    case 1: // 随机访问
                        time = baseTime + (long)(Math.random() * 2000);
                        break;
                    case 2: // 周期性访问
                        time = baseTime + (i % 500) * 2; // 每500个请求为一个周期
                        break;
                    default:
                        time = baseTime;
                }
                
                leapArray.currentWindow(time);
            }
            
            System.out.println("访问模式: " + patterns[patternIdx]);
            System.out.println("  总请求数: " + totalRequests);
            System.out.println("  缓存命中: " + leapArray.cacheHits + " 次");
            System.out.println("  缓存未命中: " + leapArray.cacheMisses + " 次");
            System.out.println("  命中率: " + String.format("%.2f", leapArray.cacheHits * 100.0 / totalRequests) + "%");
            System.out.println("  锁竞争: " + leapArray.lockContentions + " 次");
            System.out.println();
        }
    }
    
    /**
     * 可测试的LeapArray，用于统计缓存命中率
     */
    private static class TestableLeapArray extends BucketLeapArray {
        volatile int cacheHits = 0;
        volatile int cacheMisses = 0;
        volatile int lockContentions = 0;
        
        public TestableLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }
        
        @Override
        public WindowWrap<MetricBucket> currentWindow(long timeMillis) {
            if (timeMillis < 0) {
                return null;
            }

            int idx = calculateTimeIdx(timeMillis);
            long windowStart = calculateWindowStart(timeMillis);

            while (true) {
                WindowWrap<MetricBucket> old = array.get(idx);
                if (old == null) {
                    cacheMisses++;
                    WindowWrap<MetricBucket> window = new WindowWrap<>(
                        getIntervalInMs() / getSampleCount(), 
                        windowStart, 
                        new MetricBucket()
                    );
                    if (array.compareAndSet(idx, null, window)) {
                        return window;
                    } else {
                        Thread.yield();
                    }
                } else if (windowStart == old.windowStart()) {
                    cacheHits++; // 缓存命中
                    return old;
                } else if (windowStart > old.windowStart()) {
                    cacheMisses++;
                    if (updateLock.tryLock()) {
                        try {
                            return resetWindowTo(old, windowStart);
                        } finally {
                            updateLock.unlock();
                        }
                    } else {
                        lockContentions++; // 锁竞争
                        Thread.yield();
                    }
                } else {
                    return new WindowWrap<>(
                        getIntervalInMs() / getSampleCount(), 
                        windowStart, 
                        new MetricBucket()
                    );
                }
            }
        }
        
        private int calculateTimeIdx(long timeMillis) {
            long timeId = timeMillis / (getIntervalInMs() / getSampleCount());
            return (int)(timeId % getSampleCount());
        }
        
        private long calculateWindowStart(long timeMillis) {
            int windowLength = getIntervalInMs() / getSampleCount();
            return timeMillis - timeMillis % windowLength;
        }
        
        void resetCounters() {
            cacheHits = 0;
            cacheMisses = 0;
            lockContentions = 0;
        }
    }
} 