package com.alibaba.csp.sentinel.demo.priority;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.PriorityWaitException;

import java.util.ArrayList;
import java.util.List;

/**
 * 集群模式下的高优资源使用示例
 * 
 * 在集群模式下，高优资源同样可以使用预占用机制
 * 但需要注意集群环境的配置
 */
public class PriorityResourceClusterDemo {
    
    private static final String RESOURCE_KEY = "cluster-priority-resource";
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== 集群模式下的高优资源使用示例 ===\n");
        
        // 配置集群流控规则
        configureClusterFlowRules();
        
        // 演示集群高优资源的使用
        demonstrateClusterPriorityResource();
    }
    
    /**
     * 配置集群流控规则
     */
    private static void configureClusterFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        FlowRule rule = new FlowRule();
        rule.setResource(RESOURCE_KEY);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(10);  // 集群总QPS限制为10
        rule.setLimitApp("default");
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        
        // 设置为集群模式（在实际应用中需要配置集群服务器）
        rule.setClusterMode(true);
        
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
        
        System.out.println("集群流控规则已配置:");
        System.out.println("- 资源名: " + rule.getResource());
        System.out.println("- 集群模式: " + rule.isClusterMode());
        System.out.println("- QPS限制: " + rule.getCount());
        System.out.println();
    }
    
    /**
     * 演示集群高优资源的使用
     */
    private static void demonstrateClusterPriorityResource() throws Exception {
        System.out.println("演示集群高优资源的使用:");
        
        // 模拟多个客户端同时访问
        for (int clientId = 1; clientId <= 3; clientId++) {
            System.out.println("\n--- 客户端 " + clientId + " ---");
            simulateClientRequests(clientId);
        }
        
        // 演示高优资源在集群中的行为
        System.out.println("\n--- 高优资源在集群中的特殊行为 ---");
        demonstrateClusterPriorityBehavior();
    }
    
    /**
     * 模拟客户端请求
     */
    private static void simulateClientRequests(int clientId) throws Exception {
        // 模拟客户端快速发送请求
        for (int i = 1; i <= 5; i++) {
            final int requestId = i;
            
            // 普通资源请求
            testNormalResourceInCluster(clientId, requestId);
            Thread.sleep(50);
            
            // 高优资源请求
            testPriorityResourceInCluster(clientId, requestId);
            Thread.sleep(50);
        }
    }
    
    /**
     * 测试集群中的普通资源
     */
    private static void testNormalResourceInCluster(int clientId, int requestId) {
        Entry entry = null;
        try {
            entry = SphU.entry(RESOURCE_KEY);
            System.out.println("客户端" + clientId + "-普通请求" + requestId + ": 通过");
        } catch (BlockException e) {
            System.out.println("客户端" + clientId + "-普通请求" + requestId + ": 被阻塞 (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    /**
     * 测试集群中的高优资源
     */
    private static void testPriorityResourceInCluster(int clientId, int requestId) {
        Entry entry = null;
        try {
            entry = SphU.entryWithPriority(RESOURCE_KEY, EntryType.OUT, 1, true);
            System.out.println("客户端" + clientId + "-高优请求" + requestId + ": 通过");
        } catch (PriorityWaitException e) {
            System.out.println("客户端" + clientId + "-高优请求" + requestId + ": 等待" + e.getWaitInMs() + "ms后通过");
        } catch (BlockException e) {
            System.out.println("客户端" + clientId + "-高优请求" + requestId + ": 被阻塞 (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    /**
     * 演示高优资源在集群中的特殊行为
     */
    private static void demonstrateClusterPriorityBehavior() throws Exception {
        System.out.println("在集群模式下，高优资源具有以下特点：");
        System.out.println("1. 可以预占用集群配额");
        System.out.println("2. 等待时间由集群服务器计算");
        System.out.println("3. 支持跨节点的优先级保证");
        System.out.println();
        
        // 批量测试
        System.out.println("批量测试高优资源行为:");
        for (int i = 1; i <= 10; i++) {
            testBatchPriorityResource(i);
            Thread.sleep(80);
        }
    }
    
    /**
     * 批量测试高优资源
     */
    private static void testBatchPriorityResource(int batchId) {
        Entry entry = null;
        long startTime = System.currentTimeMillis();
        
        try {
            // 请求批量token
            entry = SphU.entryWithPriority(RESOURCE_KEY, EntryType.OUT, 2, true);
            long endTime = System.currentTimeMillis();
            System.out.println("批次" + batchId + ": 通过 (耗时: " + (endTime - startTime) + "ms)");
            
        } catch (PriorityWaitException e) {
            long endTime = System.currentTimeMillis();
            System.out.println("批次" + batchId + ": 等待" + e.getWaitInMs() + "ms后通过 (总耗时: " + (endTime - startTime) + "ms)");
            
        } catch (BlockException e) {
            long endTime = System.currentTimeMillis();
            System.out.println("批次" + batchId + ": 被阻塞 (耗时: " + (endTime - startTime) + "ms, 原因: " + e.getClass().getSimpleName() + ")");
            
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
    
    /**
     * 集群配置说明
     */
    private static void printClusterConfigurationGuide() {
        System.out.println("\n=== 集群配置说明 ===");
        System.out.println("1. 集群服务器配置:");
        System.out.println("   - 启动集群服务器");
        System.out.println("   - 配置集群规则");
        System.out.println("   - 设置占用比例和超时时间");
        System.out.println();
        
        System.out.println("2. 客户端配置:");
        System.out.println("   - 连接到集群服务器");
        System.out.println("   - 配置客户端ID和命名空间");
        System.out.println("   - 启用集群模式");
        System.out.println();
        
        System.out.println("3. 高优资源在集群中的优势:");
        System.out.println("   - 全局优先级保证");
        System.out.println("   - 跨节点配额共享");
        System.out.println("   - 统一的等待时间计算");
        System.out.println("   - 更精确的流控效果");
    }
} 