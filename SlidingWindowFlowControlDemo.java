package com.alibaba.csp.sentinel.demo.window;

import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;
import com.alibaba.csp.sentinel.slots.statistic.metric.BucketLeapArray;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 滑动窗口在流控中的应用示例
 * 
 * 演示如何使用滑动窗口来实现QPS流控
 */
public class SlidingWindowFlowControlDemo {
    
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
    
    // 流控配置
    private static final int QPS_LIMIT = 5;  // QPS限制为5
    private static final int WINDOW_SAMPLE_COUNT = 10;  // 10个采样窗口
    private static final int WINDOW_INTERVAL_MS = 1000; // 1秒统计窗口
    
    private final BucketLeapArray statisticArray;
    
    public SlidingWindowFlowControlDemo() {
        this.statisticArray = new BucketLeapArray(WINDOW_SAMPLE_COUNT, WINDOW_INTERVAL_MS);
    }
    
    public static void main(String[] args) throws Exception {
        SlidingWindowFlowControlDemo demo = new SlidingWindowFlowControlDemo();
        
        System.out.println("=== 滑动窗口流控演示 ===");
        System.out.println("配置: QPS限制=" + QPS_LIMIT + ", 统计窗口=" + WINDOW_INTERVAL_MS + "ms");
        System.out.println("采样: " + WINDOW_SAMPLE_COUNT + "个bucket，每个" + (WINDOW_INTERVAL_MS/WINDOW_SAMPLE_COUNT) + "ms\n");
        
        // 模拟不同的请求模式
        demo.simulateNormalTraffic();
        Thread.sleep(2000);
        
        demo.simulateBurstTraffic();
        Thread.sleep(2000);
        
        demo.simulateGradualIncrease();
    }
    
    /**
     * 模拟正常流量
     */
    private void simulateNormalTraffic() throws Exception {
        System.out.println("=== 模拟正常流量 (3 QPS) ===");
        
        for (int i = 0; i < 10; i++) {
            boolean allowed = checkAndRecordRequest();
            long currentTime = TimeUtil.currentTimeMillis();
            
            System.out.println(sdf.format(new Date(currentTime)) + 
                             " - 请求" + (i+1) + ": " + (allowed ? "通过" : "阻塞") +
                             " (当前QPS: " + getCurrentQPS() + ")");
            
            Thread.sleep(330); // 约3 QPS
        }
        
        printWindowDetails("正常流量结束");
    }
    
    /**
     * 模拟突发流量
     */
    private void simulateBurstTraffic() throws Exception {
        System.out.println("\n=== 模拟突发流量 (快速连续请求) ===");
        
        for (int i = 0; i < 15; i++) {
            boolean allowed = checkAndRecordRequest();
            long currentTime = TimeUtil.currentTimeMillis();
            
            System.out.println(sdf.format(new Date(currentTime)) + 
                             " - 突发请求" + (i+1) + ": " + (allowed ? "通过" : "阻塞") +
                             " (当前QPS: " + getCurrentQPS() + ")");
            
            Thread.sleep(50); // 20 QPS的速率
        }
        
        printWindowDetails("突发流量结束");
    }
    
    /**
     * 模拟流量逐渐增加
     */
    private void simulateGradualIncrease() throws Exception {
        System.out.println("\n=== 模拟流量逐渐增加 ===");
        
        int[] intervals = {400, 300, 200, 150, 100, 80, 60, 50}; // 间隔逐渐减小
        
        for (int i = 0; i < intervals.length; i++) {
            for (int j = 0; j < 3; j++) { // 每个间隔发送3个请求
                boolean allowed = checkAndRecordRequest();
                long currentTime = TimeUtil.currentTimeMillis();
                
                System.out.println(sdf.format(new Date(currentTime)) + 
                                 " - 请求(间隔" + intervals[i] + "ms): " + (allowed ? "通过" : "阻塞") +
                                 " (当前QPS: " + getCurrentQPS() + ")");
                
                Thread.sleep(intervals[i]);
            }
        }
        
        printWindowDetails("逐渐增加流量结束");
    }
    
    /**
     * 检查请求是否允许通过，并记录统计数据
     */
    private boolean checkAndRecordRequest() {
        long currentTime = TimeUtil.currentTimeMillis();
        
        // 获取当前QPS
        double currentQPS = getCurrentQPS();
        
        // 判断是否超过限制
        boolean allowed = currentQPS < QPS_LIMIT;
        
        // 获取当前窗口并记录统计
        WindowWrap<MetricBucket> currentWindow = statisticArray.currentWindow(currentTime);
        
        if (allowed) {
            // 请求通过，记录pass
            currentWindow.value().addPass(1);
            currentWindow.value().addSuccess(1);
            currentWindow.value().addRT(10 + (int)(Math.random() * 50)); // 模拟响应时间
        } else {
            // 请求被阻塞，记录block
            currentWindow.value().addBlock(1);
        }
        
        return allowed;
    }
    
    /**
     * 获取当前QPS
     */
    private double getCurrentQPS() {
        long currentTime = TimeUtil.currentTimeMillis();
        List<WindowWrap<MetricBucket>> validWindows = statisticArray.list(currentTime);
        
        long totalPass = 0;
        for (WindowWrap<MetricBucket> window : validWindows) {
            totalPass += window.value().pass();
        }
        
        // QPS = 总通过数 * 1000 / 统计窗口时长(ms)
        return totalPass * 1000.0 / WINDOW_INTERVAL_MS;
    }
    
    /**
     * 获取当前统计数据
     */
    private StatisticData getCurrentStatistics() {
        long currentTime = TimeUtil.currentTimeMillis();
        List<WindowWrap<MetricBucket>> validWindows = statisticArray.list(currentTime);
        
        long totalPass = 0;
        long totalBlock = 0;
        long totalSuccess = 0;
        long totalRT = 0;
        
        for (WindowWrap<MetricBucket> window : validWindows) {
            MetricBucket bucket = window.value();
            totalPass += bucket.pass();
            totalBlock += bucket.block();
            totalSuccess += bucket.success();
            totalRT += bucket.rt();
        }
        
        return new StatisticData(totalPass, totalBlock, totalSuccess, totalRT);
    }
    
    /**
     * 打印窗口详细信息
     */
    private void printWindowDetails(String phase) {
        System.out.println("\n--- " + phase + " ---");
        long currentTime = TimeUtil.currentTimeMillis();
        List<WindowWrap<MetricBucket>> windows = statisticArray.list(currentTime);
        
        System.out.println("当前有效窗口数: " + windows.size());
        
        for (int i = 0; i < windows.size(); i++) {
            WindowWrap<MetricBucket> window = windows.get(i);
            MetricBucket bucket = window.value();
            
            System.out.println("  窗口" + (i+1) + "[" + 
                             sdf.format(new Date(window.windowStart())) + 
                             "] Pass:" + bucket.pass() + 
                             " Block:" + bucket.block() + 
                             " Success:" + bucket.success() +
                             " AvgRT:" + (bucket.success() > 0 ? bucket.rt()/bucket.success() : 0));
        }
        
        StatisticData stats = getCurrentStatistics();
        System.out.println("总计: Pass=" + stats.totalPass + 
                         " Block=" + stats.totalBlock + 
                         " QPS=" + String.format("%.2f", stats.totalPass * 1000.0 / WINDOW_INTERVAL_MS) +
                         " AvgRT=" + String.format("%.2f", stats.getAvgRT()) + "ms");
        System.out.println();
    }
    
    /**
     * 统计数据封装类
     */
    private static class StatisticData {
        final long totalPass;
        final long totalBlock;
        final long totalSuccess;
        final long totalRT;
        
        StatisticData(long totalPass, long totalBlock, long totalSuccess, long totalRT) {
            this.totalPass = totalPass;
            this.totalBlock = totalBlock;
            this.totalSuccess = totalSuccess;
            this.totalRT = totalRT;
        }
        
        double getAvgRT() {
            return totalSuccess > 0 ? (double)totalRT / totalSuccess : 0;
        }
    }
} 