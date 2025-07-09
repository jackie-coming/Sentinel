package com.alibaba.csp.sentinel.demo.priority;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.PriorityWaitException;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高优资源使用示例
 * 
 * 高优资源的特点：
 * 1. 在QPS流控规则下，当超过阈值时，可以通过占用未来的配额来获得通过权限
 * 2. 高优资源会等待一定时间，如果等待时间在超时范围内，就会通过
 * 3. 通过PriorityWaitException异常来处理等待逻辑
 */
public class PriorityResourceDemo {

    private static final String RESOURCE_KEY = "testResource";
    private static final String HIGH_PRIORITY_RESOURCE_KEY = "highPriorityResource";
    
    private static AtomicInteger normalPass = new AtomicInteger();
    private static AtomicInteger normalBlock = new AtomicInteger();
    private static AtomicInteger priorityPass = new AtomicInteger();
    private static AtomicInteger priorityBlock = new AtomicInteger();
    private static AtomicInteger priorityWait = new AtomicInteger();
    
    private static volatile boolean stop = false;
    
    public static void main(String[] args) throws Exception {
        // 初始化流控规则
        initFlowRules();
        
        // 启动监控线程
        startMonitorThread();
        
        // 启动普通资源测试线程
        startNormalResourceThreads();
        
        // 启动高优资源测试线程
        startPriorityResourceThreads();
        
        // 运行60秒
        Thread.sleep(60000);
        stop = true;
        
        System.out.println("=== 最终统计 ===");
        System.out.println("普通资源 - 通过: " + normalPass.get() + ", 阻塞: " + normalBlock.get());
        System.out.println("高优资源 - 通过: " + priorityPass.get() + ", 阻塞: " + priorityBlock.get() + ", 等待: " + priorityWait.get());
    }
    
    private static void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        // 普通资源流控规则 - QPS限制为5
        FlowRule normalRule = new FlowRule();
        normalRule.setResource(RESOURCE_KEY);
        normalRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        normalRule.setCount(5);
        normalRule.setLimitApp("default");
        normalRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(normalRule);
        
        // 高优资源流控规则 - QPS限制为3
        FlowRule priorityRule = new FlowRule();
        priorityRule.setResource(HIGH_PRIORITY_RESOURCE_KEY);
        priorityRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        priorityRule.setCount(3);
        priorityRule.setLimitApp("default");
        priorityRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(priorityRule);
        
        FlowRuleManager.loadRules(rules);
        System.out.println("流控规则已加载");
    }
    
    private static void startMonitorThread() {
        Thread monitorThread = new Thread(() -> {
            while (!stop) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                    long currentTime = TimeUtil.currentTimeMillis();
                    System.out.println("[" + currentTime + "] 普通资源 - 通过: " + normalPass.get() + 
                                     ", 阻塞: " + normalBlock.get() + 
                                     " | 高优资源 - 通过: " + priorityPass.get() + 
                                     ", 阻塞: " + priorityBlock.get() + 
                                     ", 等待: " + priorityWait.get());
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    private static void startNormalResourceThreads() {
        // 启动多个线程测试普通资源
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(new NormalResourceTask());
            thread.setName("NormalResource-" + i);
            thread.start();
        }
    }
    
    private static void startPriorityResourceThreads() {
        // 启动多个线程测试高优资源
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(new PriorityResourceTask());
            thread.setName("PriorityResource-" + i);
            thread.start();
        }
    }
    
    static class NormalResourceTask implements Runnable {
        @Override
        public void run() {
            while (!stop) {
                Entry entry = null;
                try {
                    // 使用普通的entry方法
                    entry = SphU.entry(RESOURCE_KEY);
                    normalPass.incrementAndGet();
                    
                    // 模拟业务处理
                    TimeUnit.MILLISECONDS.sleep(10);
                    
                } catch (BlockException e) {
                    normalBlock.incrementAndGet();
                } catch (Exception e) {
                    // 业务异常处理
                } finally {
                    if (entry != null) {
                        entry.exit();
                    }
                }
                
                try {
                    // 随机间隔
                    TimeUnit.MILLISECONDS.sleep(50 + (int)(Math.random() * 100));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
    
    static class PriorityResourceTask implements Runnable {
        @Override
        public void run() {
            while (!stop) {
                Entry entry = null;
                try {
                    // 使用高优资源的entry方法
                    entry = SphU.entryWithPriority(HIGH_PRIORITY_RESOURCE_KEY, EntryType.OUT, 1, true);
                    priorityPass.incrementAndGet();
                    
                    // 模拟业务处理
                    TimeUnit.MILLISECONDS.sleep(10);
                    
                } catch (PriorityWaitException e) {
                    // 高优资源等待异常 - 这意味着请求会在等待后通过
                    priorityWait.incrementAndGet();
                    System.out.println("高优资源等待 " + e.getWaitInMs() + "ms 后通过");
                } catch (BlockException e) {
                    priorityBlock.incrementAndGet();
                } catch (Exception e) {
                    // 业务异常处理
                } finally {
                    if (entry != null) {
                        entry.exit();
                    }
                }
                
                try {
                    // 随机间隔
                    TimeUnit.MILLISECONDS.sleep(30 + (int)(Math.random() * 70));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
} 