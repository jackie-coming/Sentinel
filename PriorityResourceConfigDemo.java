package com.alibaba.csp.sentinel.demo.priority;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.node.OccupyTimeoutProperty;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.PriorityWaitException;

import java.util.ArrayList;
import java.util.List;

/**
 * 高优资源配置示例
 * 
 * 展示如何配置高优资源的各种参数
 */
public class PriorityResourceConfigDemo {
    
    private static final String RESOURCE_KEY = "priorityResource";
    
    public static void main(String[] args) throws Exception {
        
        // 1. 配置占用超时时间（默认500ms）
        configureOccupyTimeout();
        
        // 2. 配置流控规则
        configureFlowRules();
        
        // 3. 演示不同的高优资源使用方式
        demonstrateUsage();
    }
    
    /**
     * 配置占用超时时间
     * 高优资源可以预占用未来的配额，但有时间限制
     */
    private static void configureOccupyTimeout() {
        // 设置占用超时时间为1000ms（默认500ms）
        OccupyTimeoutProperty.updateTimeout(1000);
        System.out.println("占用超时时间设置为: " + OccupyTimeoutProperty.getOccupyTimeout() + "ms");
    }
    
    /**
     * 配置流控规则
     */
    private static void configureFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        FlowRule rule = new FlowRule();
        rule.setResource(RESOURCE_KEY);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);  // QPS模式
        rule.setCount(2);  // 每秒允许2个请求
        rule.setLimitApp("default");
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);  // 默认控制行为
        
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
        System.out.println("流控规则已配置 - QPS限制: " + rule.getCount());
    }
    
    /**
     * 演示高优资源的不同使用方式
     */
    private static void demonstrateUsage() throws Exception {
        System.out.println("\n=== 高优资源使用方式演示 ===");
        
        // 方式1：最简单的高优资源使用
        System.out.println("\n1. 最简单的高优资源使用:");
        useSimplePriorityResource();
        
        // 方式2：带EntryType的高优资源
        System.out.println("\n2. 带EntryType的高优资源:");
        usePriorityResourceWithEntryType();
        
        // 方式3：带批量计数的高优资源
        System.out.println("\n3. 带批量计数的高优资源:");
        usePriorityResourceWithBatchCount();
        
        // 方式4：带参数的高优资源
        System.out.println("\n4. 带参数的高优资源:");
        usePriorityResourceWithArgs();
        
        // 演示优先级差异
        System.out.println("\n5. 优先级差异演示:");
        demonstratePriorityDifference();
    }
    
    /**
     * 方式1：最简单的高优资源使用
     */
    private static void useSimplePriorityResource() {
        Entry entry = null;
        try {
            entry = SphU.entryWithPriority(RESOURCE_KEY);
            System.out.println("高优资源访问成功");
        } catch (PriorityWaitException e) {
            System.out.println("高优资源等待 " + e.getWaitInMs() + "ms 后通过");
        } catch (BlockException e) {
            System.out.println("高优资源被阻塞: " + e.getMessage());
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    /**
     * 方式2：带EntryType的高优资源
     */
    private static void usePriorityResourceWithEntryType() {
        Entry entry = null;
        try {
            entry = SphU.entryWithPriority(RESOURCE_KEY, EntryType.IN);
            System.out.println("带EntryType的高优资源访问成功");
        } catch (PriorityWaitException e) {
            System.out.println("带EntryType的高优资源等待 " + e.getWaitInMs() + "ms 后通过");
        } catch (BlockException e) {
            System.out.println("带EntryType的高优资源被阻塞: " + e.getMessage());
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    /**
     * 方式3：带批量计数的高优资源
     */
    private static void usePriorityResourceWithBatchCount() {
        Entry entry = null;
        try {
            // 请求2个token
            entry = SphU.entryWithPriority(RESOURCE_KEY, EntryType.OUT, 2, true);
            System.out.println("带批量计数的高优资源访问成功");
        } catch (PriorityWaitException e) {
            System.out.println("带批量计数的高优资源等待 " + e.getWaitInMs() + "ms 后通过");
        } catch (BlockException e) {
            System.out.println("带批量计数的高优资源被阻塞: " + e.getMessage());
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    /**
     * 方式4：带参数的高优资源
     */
    private static void usePriorityResourceWithArgs() {
        Entry entry = null;
        try {
            // 带参数的高优资源（用于参数流控）
            entry = SphU.entryWithPriority(RESOURCE_KEY, EntryType.OUT, 1, true, "param1", "param2");
            System.out.println("带参数的高优资源访问成功");
        } catch (PriorityWaitException e) {
            System.out.println("带参数的高优资源等待 " + e.getWaitInMs() + "ms 后通过");
        } catch (BlockException e) {
            System.out.println("带参数的高优资源被阻塞: " + e.getMessage());
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    /**
     * 演示优先级差异
     */
    private static void demonstratePriorityDifference() throws Exception {
        System.out.println("快速发送多个请求，观察优先级差异...");
        
        // 快速发送多个请求
        for (int i = 0; i < 5; i++) {
            final int requestId = i + 1;
            
            // 普通资源
            testNormalResource(requestId);
            
            // 高优资源
            testPriorityResource(requestId);
            
            Thread.sleep(100); // 短暂间隔
        }
    }
    
    private static void testNormalResource(int requestId) {
        Entry entry = null;
        try {
            entry = SphU.entry(RESOURCE_KEY);
            System.out.println("普通资源请求 " + requestId + " 通过");
        } catch (BlockException e) {
            System.out.println("普通资源请求 " + requestId + " 被阻塞");
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    private static void testPriorityResource(int requestId) {
        Entry entry = null;
        try {
            entry = SphU.entryWithPriority(RESOURCE_KEY, EntryType.OUT, 1, true);
            System.out.println("高优资源请求 " + requestId + " 通过");
        } catch (PriorityWaitException e) {
            System.out.println("高优资源请求 " + requestId + " 等待 " + e.getWaitInMs() + "ms 后通过");
        } catch (BlockException e) {
            System.out.println("高优资源请求 " + requestId + " 被阻塞");
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
} 