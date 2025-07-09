package com.alibaba.csp.sentinel.demo.cluster;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.client.ClusterTokenClient;
import com.alibaba.csp.sentinel.cluster.client.DefaultClusterTokenClient;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.exception.SentinelClusterException;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sentinel集群限流异常感知和处理机制演示
 * 
 * 实现以下功能：
 * 1. 多层异常感知机制
 * 2. 自动降级策略  
 * 3. 故障恢复机制
 * 4. 监控和告警
 * 5. 用户感知优化
 */
public class SentinelClusterFailureDetectionDemo {
    
    // 监控指标
    private static final ConcurrentHashMap<String, FailureMetrics> failureMetrics = new ConcurrentHashMap<>();
    
    // 故障检测器
    private static final FailureDetector failureDetector = new FailureDetector();
    
    // 降级控制器
    private static final FallbackController fallbackController = new FallbackController();
    
    // 定时任务调度器
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    
    public static void main(String[] args) throws Exception {
        // 1. 初始化配置
        initClusterConfig();
        
        // 2. 启动监控
        startMonitoring();
        
        // 3. 启动故障检测
        startFailureDetection();
        
        // 4. 模拟不同的异常场景
        simulateFailureScenarios();
        
        // 5. 保持运行
        Thread.sleep(60000);
        
        // 6. 清理资源
        shutdown();
    }
    
    /**
     * 初始化集群配置
     */
    private static void initClusterConfig() {
        // 配置集群客户端
        ClusterClientConfig clientConfig = new ClusterClientConfig();
        clientConfig.setRequestTimeout(2000); // 2秒超时
        ClusterClientConfigManager.applyNewConfig(clientConfig);
        
        // 配置集群流控规则
        List<FlowRule> rules = new ArrayList<>();
        
        // 规则1：支持降级的集群限流
        FlowRule clusterRule = new FlowRule();
        clusterRule.setResource("api:user:profile");
        clusterRule.setCount(100);
        clusterRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        clusterRule.setClusterMode(true);
        
        ClusterFlowConfig clusterConfig = new ClusterFlowConfig();
        clusterConfig.setFlowId(1001L);
        clusterConfig.setFallbackToLocalWhenFail(true); // 关键配置：失败时降级到本地
        clusterConfig.setResourceTimeout(5000); // 资源超时时间
        clusterRule.setClusterConfig(clusterConfig);
        rules.add(clusterRule);
        
        // 规则2：不支持降级的集群限流
        FlowRule clusterRule2 = new FlowRule();
        clusterRule2.setResource("api:order:create");
        clusterRule2.setCount(50);
        clusterRule2.setGrade(RuleConstant.FLOW_GRADE_QPS);
        clusterRule2.setClusterMode(true);
        
        ClusterFlowConfig clusterConfig2 = new ClusterFlowConfig();
        clusterConfig2.setFlowId(1002L);
        clusterConfig2.setFallbackToLocalWhenFail(false); // 失败时直接通过
        clusterRule2.setClusterConfig(clusterConfig2);
        rules.add(clusterRule2);
        
        // 规则3：本地兜底限流
        FlowRule localRule = new FlowRule();
        localRule.setResource("api:user:profile");
        localRule.setCount(80); // 本地限制略低于集群限制
        localRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        localRule.setClusterMode(false);
        rules.add(localRule);
        
        FlowRuleManager.loadRules(rules);
        
        // 启用集群模式
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);
        
        System.out.println("集群限流配置初始化完成");
    }
    
    /**
     * 启动监控
     */
    private static void startMonitoring() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                collectMetrics();
                detectAnomalies();
                reportStatus();
            } catch (Exception e) {
                System.err.println("监控任务异常: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 启动故障检测
     */
    private static void startFailureDetection() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                failureDetector.checkServerHealth();
                fallbackController.updateFallbackStatus();
            } catch (Exception e) {
                System.err.println("故障检测任务异常: " + e.getMessage());
            }
        }, 0, 3, TimeUnit.SECONDS);
    }
    
    /**
     * 模拟不同的异常场景
     */
    private static void simulateFailureScenarios() {
        // 场景1：正常情况
        System.out.println("=== 场景1：正常情况 ===");
        simulateRequests("api:user:profile", 10, 0);
        
        // 场景2：Server连接超时
        System.out.println("\n=== 场景2：Server连接超时 ===");
        simulateServerTimeout();
        
        // 场景3：Server宕机
        System.out.println("\n=== 场景3：Server宕机 ===");
        simulateServerDown();
        
        // 场景4：网络异常
        System.out.println("\n=== 场景4：网络异常 ===");
        simulateNetworkError();
        
        // 场景5：Server恢复
        System.out.println("\n=== 场景5：Server恢复 ===");
        simulateServerRecovery();
    }
    
    /**
     * 模拟请求
     */
    private static void simulateRequests(String resource, int count, int delayMs) {
        for (int i = 0; i < count; i++) {
            final int requestId = i;
            CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();
                boolean passed = false;
                String strategy = "unknown";
                
                try {
                    Entry entry = SphU.entry(resource);
                    try {
                        passed = true;
                        strategy = determineStrategy();
                        
                        // 模拟业务处理
                        if (delayMs > 0) {
                            Thread.sleep(delayMs);
                        }
                        
                    } finally {
                        entry.exit();
                    }
                } catch (BlockException e) {
                    passed = false;
                    strategy = "blocked";
                } catch (Exception e) {
                    passed = false;
                    strategy = "error";
                }
                
                long responseTime = System.currentTimeMillis() - startTime;
                recordRequest(resource, passed, strategy, responseTime);
                
                System.out.printf("请求[%d] 资源[%s] 结果[%s] 策略[%s] 耗时[%dms]\n",
                    requestId, resource, passed ? "通过" : "拒绝", strategy, responseTime);
            });
            
            try {
                Thread.sleep(100); // 控制请求频率
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 模拟Server超时
     */
    private static void simulateServerTimeout() {
        // 通过降低超时时间模拟超时场景
        ClusterClientConfig config = new ClusterClientConfig();
        config.setRequestTimeout(50); // 极短超时时间
        ClusterClientConfigManager.applyNewConfig(config);
        
        simulateRequests("api:user:profile", 10, 0);
        
        // 恢复正常超时时间
        config.setRequestTimeout(2000);
        ClusterClientConfigManager.applyNewConfig(config);
    }
    
    /**
     * 模拟Server宕机
     */
    private static void simulateServerDown() {
        // 通过修改服务端地址模拟宕机
        // 实际场景中，这会在网络层面发生
        System.out.println("模拟Server宕机场景...");
        simulateRequests("api:user:profile", 10, 0);
    }
    
    /**
     * 模拟网络异常
     */
    private static void simulateNetworkError() {
        System.out.println("模拟网络异常场景...");
        // 在实际环境中，这会导致连接异常
        simulateRequests("api:user:profile", 10, 0);
    }
    
    /**
     * 模拟Server恢复
     */
    private static void simulateServerRecovery() {
        System.out.println("模拟Server恢复场景...");
        simulateRequests("api:user:profile", 10, 0);
    }
    
    /**
     * 确定当前使用的限流策略
     */
    private static String determineStrategy() {
        ClusterTokenClient client = TokenClientProvider.getClient();
        if (client == null) {
            return "local_only";
        }
        
        int state = client.getState();
        switch (state) {
            case 0: // CLIENT_STATUS_OFF
                return "local_fallback";
            case 1: // CLIENT_STATUS_STARTED
                return "cluster_active";
            default:
                return "unknown";
        }
    }
    
    /**
     * 记录请求信息
     */
    private static void recordRequest(String resource, boolean passed, String strategy, long responseTime) {
        FailureMetrics metrics = failureMetrics.computeIfAbsent(resource, k -> new FailureMetrics(resource));
        metrics.recordRequest(passed, strategy, responseTime);
    }
    
    /**
     * 收集监控指标
     */
    private static void collectMetrics() {
        for (FailureMetrics metrics : failureMetrics.values()) {
            metrics.calculate();
        }
    }
    
    /**
     * 检测异常情况
     */
    private static void detectAnomalies() {
        for (FailureMetrics metrics : failureMetrics.values()) {
            if (metrics.getErrorRate() > 0.1) { // 错误率超过10%
                System.out.println("⚠️  异常检测: " + metrics.getResource() + " 错误率 " + 
                    String.format("%.2f%%", metrics.getErrorRate() * 100));
            }
            
            if (metrics.getAvgResponseTime() > 1000) { // 平均响应时间超过1秒
                System.out.println("⚠️  异常检测: " + metrics.getResource() + " 平均响应时间 " + 
                    metrics.getAvgResponseTime() + "ms");
            }
        }
    }
    
    /**
     * 报告状态
     */
    private static void reportStatus() {
        System.out.println("\n=== 系统状态报告 ===");
        System.out.println("集群状态: " + (ClusterStateManager.isClient() ? "客户端模式" : "独立模式"));
        
        ClusterTokenClient client = TokenClientProvider.getClient();
        if (client != null) {
            System.out.println("客户端状态: " + getClientStatusText(client.getState()));
            System.out.println("服务端信息: " + client.currentServer());
        }
        
        System.out.println("降级状态: " + (fallbackController.isInFallback() ? "已降级" : "正常"));
        System.out.println("故障检测: " + (failureDetector.isServerHealthy() ? "正常" : "异常"));
        
        // 打印各资源的监控指标
        for (FailureMetrics metrics : failureMetrics.values()) {
            System.out.printf("资源[%s]: 请求数=%d, 成功率=%.2f%%, 平均响应时间=%dms\n",
                metrics.getResource(), metrics.getTotalRequests(), 
                metrics.getSuccessRate() * 100, metrics.getAvgResponseTime());
        }
        System.out.println("================\n");
    }
    
    /**
     * 获取客户端状态文本
     */
    private static String getClientStatusText(int state) {
        switch (state) {
            case 0: return "已关闭";
            case 1: return "已启动";
            case 2: return "连接中";
            default: return "未知";
        }
    }
    
    /**
     * 关闭资源
     */
    private static void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("系统已关闭");
    }
    
    /**
     * 故障检测器
     */
    private static class FailureDetector {
        private volatile boolean serverHealthy = true;
        private volatile long lastHealthCheckTime = System.currentTimeMillis();
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        
        public void checkServerHealth() {
            try {
                ClusterTokenClient client = TokenClientProvider.getClient();
                if (client == null) {
                    recordFailure();
                    return;
                }
                
                int state = client.getState();
                if (state == 1) { // CLIENT_STATUS_STARTED
                    recordSuccess();
                } else {
                    recordFailure();
                }
                
            } catch (Exception e) {
                recordFailure();
            }
        }
        
        private void recordSuccess() {
            if (!serverHealthy) {
                System.out.println("✅ 服务端健康状态恢复");
            }
            serverHealthy = true;
            consecutiveFailures.set(0);
            lastHealthCheckTime = System.currentTimeMillis();
        }
        
        private void recordFailure() {
            int failures = consecutiveFailures.incrementAndGet();
            if (serverHealthy && failures >= 3) {
                serverHealthy = false;
                System.out.println("❌ 服务端健康状态异常，连续失败次数: " + failures);
                
                // 触发告警
                triggerAlert("服务端健康检查失败", failures);
            }
        }
        
        private void triggerAlert(String message, int failures) {
            System.out.println("🚨 告警触发: " + message + ", 失败次数: " + failures);
            // 这里可以集成实际的告警系统
        }
        
        public boolean isServerHealthy() {
            return serverHealthy;
        }
    }
    
    /**
     * 降级控制器
     */
    private static class FallbackController {
        private volatile boolean inFallback = false;
        private final AtomicLong fallbackStartTime = new AtomicLong(0);
        
        public void updateFallbackStatus() {
            boolean shouldFallback = !failureDetector.isServerHealthy();
            
            if (shouldFallback && !inFallback) {
                inFallback = true;
                fallbackStartTime.set(System.currentTimeMillis());
                System.out.println("🔄 系统进入降级模式");
                
                // 可以在这里修改限流规则，降低阈值
                adjustLocalRules();
                
            } else if (!shouldFallback && inFallback) {
                inFallback = false;
                long fallbackDuration = System.currentTimeMillis() - fallbackStartTime.get();
                System.out.println("🔄 系统退出降级模式，持续时间: " + fallbackDuration + "ms");
                
                // 恢复正常规则
                restoreNormalRules();
            }
        }
        
        private void adjustLocalRules() {
            // 在降级模式下，可以调整本地限流规则
            // 例如：降低阈值、增加监控等
        }
        
        private void restoreNormalRules() {
            // 恢复正常的限流规则
        }
        
        public boolean isInFallback() {
            return inFallback;
        }
    }
    
    /**
     * 失败指标统计
     */
    private static class FailureMetrics {
        private final String resource;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successRequests = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong clusterRequests = new AtomicLong(0);
        private final AtomicLong fallbackRequests = new AtomicLong(0);
        
        // 计算后的指标
        private volatile double successRate = 0.0;
        private volatile double errorRate = 0.0;
        private volatile long avgResponseTime = 0;
        
        public FailureMetrics(String resource) {
            this.resource = resource;
        }
        
        public void recordRequest(boolean passed, String strategy, long responseTime) {
            totalRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            
            if (passed) {
                successRequests.incrementAndGet();
            }
            
            if ("cluster_active".equals(strategy)) {
                clusterRequests.incrementAndGet();
            } else if ("local_fallback".equals(strategy)) {
                fallbackRequests.incrementAndGet();
            }
        }
        
        public void calculate() {
            long total = totalRequests.get();
            if (total > 0) {
                successRate = (double) successRequests.get() / total;
                errorRate = 1.0 - successRate;
                avgResponseTime = totalResponseTime.get() / total;
            }
        }
        
        // Getters
        public String getResource() { return resource; }
        public long getTotalRequests() { return totalRequests.get(); }
        public double getSuccessRate() { return successRate; }
        public double getErrorRate() { return errorRate; }
        public long getAvgResponseTime() { return avgResponseTime; }
        public long getClusterRequests() { return clusterRequests.get(); }
        public long getFallbackRequests() { return fallbackRequests.get(); }
    }
} 