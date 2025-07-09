package com.alibaba.csp.sentinel.demo.window;

import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;
import com.alibaba.csp.sentinel.slots.statistic.metric.occupy.OccupiableBucketLeapArray;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 可占用滑动窗口示例 - 高优资源使用的窗口类型
 * 
 * 可占用窗口的特点：
 * 1. 支持预占用未来时间窗口的配额
 * 2. 有单独的borrowArray记录预占用的配额
 * 3. 当创建新窗口时，会从borrowArray中获取预占用的数据
 */
public class OccupiableSlidingWindowExample {
    
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
    
    public static void main(String[] args) throws Exception {
        // 创建可占用滑动窗口：5个bucket，总窗口2.5秒，每个bucket 500ms
        int sampleCount = 5;
        int intervalInMs = 2500;
        OccupiableBucketLeapArray occupiableArray = new OccupiableBucketLeapArray(sampleCount, intervalInMs);
        
        System.out.println("=== 可占用滑动窗口示例 ===");
        System.out.println("配置: " + sampleCount + "个bucket，总窗口" + intervalInMs + "ms");
        System.out.println("每个bucket时长: " + (intervalInMs/sampleCount) + "ms\n");
        
        long baseTime = TimeUtil.currentTimeMillis();
        
        // 第一阶段：正常添加数据
        System.out.println("=== 第一阶段：正常添加数据 ===");
        for (int i = 0; i < 3; i++) {
            long currentTime = baseTime + i * 300;
            
            WindowWrap<MetricBucket> window = occupiableArray.currentWindow(currentTime);
            window.value().addPass(2);
            window.value().addRT(50);
            
            System.out.println("时间 " + sdf.format(new Date(currentTime)) + 
                             " - 添加数据: Pass=2, RT=50");
        }
        
        printCurrentStatus(occupiableArray, baseTime + 600, "第一阶段结束");
        
        // 第二阶段：预占用未来窗口
        System.out.println("\n=== 第二阶段：预占用未来窗口 ===");
        long currentTime = baseTime + 800;
        
        // 预占用未来1秒的窗口（相当于预占用2个bucket）
        long futureTime1 = currentTime + 500;  // 下一个bucket
        long futureTime2 = currentTime + 1000; // 再下一个bucket
        
        System.out.println("当前时间: " + sdf.format(new Date(currentTime)));
        System.out.println("预占用时间1: " + sdf.format(new Date(futureTime1)));
        System.out.println("预占用时间2: " + sdf.format(new Date(futureTime2)));
        
        // 执行预占用操作
        occupiableArray.addWaiting(futureTime1, 3);
        occupiableArray.addWaiting(futureTime2, 2);
        
        System.out.println("预占用配额 - 时间1: 3个, 时间2: 2个");
        System.out.println("当前等待的配额总数: " + occupiableArray.currentWaiting());
        
        printCurrentStatus(occupiableArray, currentTime, "预占用后");
        
        // 第三阶段：时间推进，观察预占用配额的生效
        System.out.println("\n=== 第三阶段：时间推进，预占用配额生效 ===");
        
        // 推进到第一个预占用时间点
        long time1 = futureTime1 + 100;
        WindowWrap<MetricBucket> window1 = occupiableArray.currentWindow(time1);
        
        System.out.println("推进到时间: " + sdf.format(new Date(time1)));
        System.out.println("当前窗口预设Pass数: " + window1.value().pass());
        
        // 在这个窗口再添加一些数据
        window1.value().addPass(1);
        window1.value().addRT(80);
        
        System.out.println("额外添加Pass=1后，窗口Pass数: " + window1.value().pass());
        
        printCurrentStatus(occupiableArray, time1, "第一个预占用时间点");
        
        // 推进到第二个预占用时间点
        long time2 = futureTime2 + 100;
        WindowWrap<MetricBucket> window2 = occupiableArray.currentWindow(time2);
        
        System.out.println("\n推进到时间: " + sdf.format(new Date(time2)));
        System.out.println("当前窗口预设Pass数: " + window2.value().pass());
        System.out.println("剩余等待配额: " + occupiableArray.currentWaiting());
        
        printCurrentStatus(occupiableArray, time2, "第二个预占用时间点");
        
        // 第四阶段：验证预占用的影响
        System.out.println("\n=== 第四阶段：验证预占用对统计的影响 ===");
        
        long finalTime = time2 + 200;
        List<WindowWrap<MetricBucket>> allWindows = occupiableArray.list(finalTime);
        
        long totalPass = 0;
        long totalRT = 0;
        
        for (WindowWrap<MetricBucket> window : allWindows) {
            totalPass += window.value().pass();
            totalRT += window.value().rt();
        }
        
        System.out.println("最终统计:");
        System.out.println("  总Pass数: " + totalPass);
        System.out.println("  总RT: " + totalRT);
        System.out.println("  平均RT: " + (totalPass > 0 ? totalRT / totalPass : 0));
        System.out.println("  QPS: " + String.format("%.2f", totalPass * 1000.0 / intervalInMs));
    }
    
    private static void printCurrentStatus(OccupiableBucketLeapArray array, long time, String phase) {
        System.out.println("\n--- " + phase + " ---");
        System.out.println("检查时间: " + sdf.format(new Date(time)));
        
        List<WindowWrap<MetricBucket>> windows = array.list(time);
        System.out.println("有效窗口数: " + windows.size());
        
        long totalPass = 0;
        long totalRT = 0;
        
        for (int i = 0; i < windows.size(); i++) {
            WindowWrap<MetricBucket> window = windows.get(i);
            MetricBucket bucket = window.value();
            
            totalPass += bucket.pass();
            totalRT += bucket.rt();
            
            System.out.println("  窗口" + (i+1) + "[" + 
                             sdf.format(new Date(window.windowStart())) + 
                             " - " +
                             sdf.format(new Date(window.windowStart() + window.windowLength())) +
                             "] Pass:" + bucket.pass() + " RT:" + bucket.rt());
        }
        
        System.out.println("当前等待配额: " + array.currentWaiting());
        System.out.println("汇总 - Pass:" + totalPass + " RT:" + totalRT);
    }
} 