package com.alibaba.csp.sentinel.demo.window;

import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;
import com.alibaba.csp.sentinel.slots.statistic.metric.BucketLeapArray;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Sentinel滑动窗口使用示例
 */
public class SlidingWindowExample {
    
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
    
    public static void main(String[] args) throws Exception {
        // 创建滑动窗口：10个bucket，总窗口5秒，每个bucket 500ms
        int sampleCount = 10;
        int intervalInMs = 5000;
        BucketLeapArray leapArray = new BucketLeapArray(sampleCount, intervalInMs);
        
        System.out.println("=== 滑动窗口示例 ===");
        System.out.println("配置: " + sampleCount + "个bucket，总窗口" + intervalInMs + "ms");
        System.out.println("每个bucket时长: " + (intervalInMs/sampleCount) + "ms\n");
        
        // 模拟请求
        for (int i = 0; i < 15; i++) {
            long currentTime = TimeUtil.currentTimeMillis();
            
            // 获取当前窗口并添加数据
            WindowWrap<MetricBucket> currentWindow = leapArray.currentWindow(currentTime);
            currentWindow.value().addPass(1);
            currentWindow.value().addRT(50 + i * 5);
            
            // 如果是异常请求，添加异常统计
            if (i % 4 == 0) {
                currentWindow.value().addException(1);
            }
            
            // 如果是阻塞请求，添加阻塞统计  
            if (i % 3 == 0) {
                currentWindow.value().addBlock(1);
            }
            
            // 打印当前窗口统计
            printCurrentStatistics(leapArray, currentTime, i + 1);
            
            Thread.sleep(400); // 每400ms一个请求
        }
    }
    
    private static void printCurrentStatistics(BucketLeapArray leapArray, long currentTime, int requestNum) {
        System.out.println("请求 " + requestNum + " [" + sdf.format(new Date(currentTime)) + "]:");
        
        // 获取所有有效窗口
        List<WindowWrap<MetricBucket>> validWindows = leapArray.list(currentTime);
        
        long totalPass = 0;
        long totalBlock = 0;
        long totalException = 0;
        long totalRT = 0;
        
        System.out.println("  有效窗口数: " + validWindows.size());
        
        for (WindowWrap<MetricBucket> window : validWindows) {
            MetricBucket bucket = window.value();
            totalPass += bucket.pass();
            totalBlock += bucket.block();
            totalException += bucket.exception();
            totalRT += bucket.rt();
            
            System.out.println("    窗口[" + sdf.format(new Date(window.windowStart())) + 
                             "] Pass:" + bucket.pass() + 
                             " Block:" + bucket.block() + 
                             " Exception:" + bucket.exception() + 
                             " RT:" + bucket.rt());
        }
        
        // 计算QPS和平均RT
        double qps = totalPass * 1000.0 / leapArray.getIntervalInMs();
        double avgRT = totalPass > 0 ? (double)totalRT / totalPass : 0;
        
        System.out.println("  统计汇总:");
        System.out.println("    总通过: " + totalPass + " (QPS: " + String.format("%.2f", qps) + ")");
        System.out.println("    总阻塞: " + totalBlock);
        System.out.println("    总异常: " + totalException);
        System.out.println("    平均RT: " + String.format("%.2f", avgRT) + "ms");
        System.out.println();
    }
} 