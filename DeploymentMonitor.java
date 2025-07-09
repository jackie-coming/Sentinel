package com.alibaba.csp.sentinel.demo.deployment;

import com.alibaba.csp.sentinel.cluster.client.ClusterTokenClient;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server发布期间Client异常情况实时监控工具
 * 
 * 监控重点：
 * 1. 连接状态变化
 * 2. 降级使用率
 * 3. 异常率统计
 * 4. 发布阶段推断
 * 5. 业务影响评估
 */
public class DeploymentMonitor {
    
    private static final AtomicInteger checkCount = new AtomicInteger(0);
    private static final AtomicInteger connectionFailures = new AtomicInteger(0);
    private static final AtomicInteger degradationCount = new AtomicInteger(0);
    private static final AtomicLong firstFailureTime = new AtomicLong(0);
    private static final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    private static final AtomicLong currentDowntime = new AtomicLong(0);
    
    private static boolean isDeploymentDetected = false;
    private static String currentDeploymentPhase = "正常运行";
    
    public static void main(String[] args) {
        System.out.println("=== Sentinel集群Server发布期间Client异常监控工具 ===");
        System.out.println("监控启动时间: " + new Date());
        System.out.println("==================================================\n");
        
        // 启动监控
        startMonitoring();
        
        // 保持运行
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 启动监控
     */
    private static void startMonitoring() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
        
        // 1. 连接状态监控 - 每5秒检查一次
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                monitorConnectionStatus();
            } catch (Exception e) {
                System.err.println("🔴 连接监控异常: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
        
        // 2. 发布阶段分析 - 每10秒分析一次
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                analyzeDeploymentPhase();
            } catch (Exception e) {
                System.err.println("🔴 阶段分析异常: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
        
        // 3. 综合报告 - 每30秒生成一次
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                generateMonitoringReport();
            } catch (Exception e) {
                System.err.println("🔴 报告生成异常: " + e.getMessage());
            }
        }, 10, 30, TimeUnit.SECONDS);
        
        System.out.println("✅ 监控服务已启动");
        System.out.println("监控频率: 连接状态(5s) | 阶段分析(10s) | 综合报告(30s)");
        System.out.println("─────────────────────────────────────────────────\n");
    }
    
    /**
     * 1. 监控连接状态
     */
    private static void monitorConnectionStatus() {
        checkCount.incrementAndGet();
        
        try {
            ClusterTokenClient client = TokenClientProvider.getClient();
            
            if (client == null) {
                recordFailure("Client未初始化");
                return;
            }
            
            int clientState = client.getState();
            
            if (clientState == 1) {
                // 连接正常
                recordSuccess();
                
                // 如果之前有发布，现在恢复了
                if (isDeploymentDetected) {
                    long recoveryTime = System.currentTimeMillis() - firstFailureTime.get();
                    System.out.println("🎉 发布恢复检测:");
                    System.out.println("   🔗 Client连接恢复正常");
                    System.out.println("   ⏱️  总发布时长: " + (recoveryTime / 1000) + "秒");
                    System.out.println("   📊 发布期间失败次数: " + connectionFailures.get());
                    System.out.println("   📊 发布期间降级次数: " + degradationCount.get());
                    System.out.println("   ─────────────────────────────");
                    
                    // 重置发布检测状态
                    isDeploymentDetected = false;
                    firstFailureTime.set(0);
                    currentDowntime.set(0);
                }
                
            } else {
                // 连接失败
                recordFailure("Client状态异常: " + clientState);
                
                // 如果之前正常，现在失败，可能是发布开始
                if (!isDeploymentDetected) {
                    isDeploymentDetected = true;
                    firstFailureTime.set(System.currentTimeMillis());
                    System.out.println("🚨 发布检测:");
                    System.out.println("   🔴 检测到Client连接失败");
                    System.out.println("   🕐 发布开始时间: " + new Date());
                    System.out.println("   ─────────────────────────────");
                }
                
                // 更新发布持续时间
                currentDowntime.set(System.currentTimeMillis() - firstFailureTime.get());
            }
            
        } catch (Exception e) {
            recordFailure("连接监控异常: " + e.getMessage());
        }
    }
    
    /**
     * 记录连接成功
     */
    private static void recordSuccess() {
        lastSuccessTime.set(System.currentTimeMillis());
        
        // 实时状态输出
        System.out.printf("🟢 %s | 连接正常 | 检查次数: %d | 失败次数: %d | 成功率: %.2f%%\n",
            getCurrentTime(),
            checkCount.get(),
            connectionFailures.get(),
            getSuccessRate());
    }
    
    /**
     * 记录连接失败
     */
    private static void recordFailure(String reason) {
        connectionFailures.incrementAndGet();
        degradationCount.incrementAndGet();
        
        // 实时状态输出
        System.out.printf("🔴 %s | 连接失败 | 原因: %s | 失败次数: %d | 成功率: %.2f%%\n",
            getCurrentTime(),
            reason,
            connectionFailures.get(),
            getSuccessRate());
    }
    
    /**
     * 2. 分析发布阶段
     */
    private static void analyzeDeploymentPhase() {
        if (!isDeploymentDetected) {
            currentDeploymentPhase = "正常运行";
            return;
        }
        
        long downtimeSeconds = currentDowntime.get() / 1000;
        String previousPhase = currentDeploymentPhase;
        
        if (downtimeSeconds < 30) {
            currentDeploymentPhase = "发布准备";
        } else if (downtimeSeconds < 60) {
            currentDeploymentPhase = "停止服务";
        } else if (downtimeSeconds < 120) {
            currentDeploymentPhase = "服务不可用";
        } else if (downtimeSeconds < 150) {
            currentDeploymentPhase = "启动服务";
        } else if (downtimeSeconds < 180) {
            currentDeploymentPhase = "服务恢复";
        } else {
            currentDeploymentPhase = "发布异常";
        }
        
        // 阶段变化通知
        if (!previousPhase.equals(currentDeploymentPhase)) {
            System.out.println("📍 发布阶段变化:");
            System.out.println("   🔄 从 [" + previousPhase + "] 变为 [" + currentDeploymentPhase + "]");
            System.out.println("   ⏱️  发布耗时: " + downtimeSeconds + "秒");
            System.out.println("   📊 当前阶段预期:");
            
            switch (currentDeploymentPhase) {
                case "停止服务":
                    System.out.println("   - ❌ 预期: 连接开始失败");
                    System.out.println("   - 🔄 预期: 自动降级到本地限流");
                    System.out.println("   - ⏱️  预期持续: 30秒");
                    break;
                case "服务不可用":
                    System.out.println("   - ❌ 预期: 完全无法连接");
                    System.out.println("   - 🔄 预期: 完全使用本地限流");
                    System.out.println("   - ⏱️  预期持续: 60秒");
                    break;
                case "启动服务":
                    System.out.println("   - 🟡 预期: 间歇性连接失败");
                    System.out.println("   - 🔄 预期: 部分恢复集群限流");
                    System.out.println("   - ⏱️  预期持续: 30秒");
                    break;
                case "服务恢复":
                    System.out.println("   - 🟢 预期: 连接逐渐恢复");
                    System.out.println("   - 🔄 预期: 集群限流恢复");
                    System.out.println("   - ⏱️  预期持续: 30秒");
                    break;
                case "发布异常":
                    System.out.println("   - 🚨 警告: 发布时间过长");
                    System.out.println("   - 🔧 建议: 检查Server状态");
                    System.out.println("   - 🔧 建议: 考虑回滚方案");
                    break;
            }
            System.out.println("   ─────────────────────────────");
        }
    }
    
    /**
     * 3. 生成监控报告
     */
    private static void generateMonitoringReport() {
        System.out.println("\n📊 发布期间监控报告 - " + new Date());
        System.out.println("═══════════════════════════════════════════════════");
        
        // 基础统计
        System.out.println("📈 基础统计:");
        System.out.println("   🔢 总检查次数: " + checkCount.get());
        System.out.println("   ❌ 连接失败次数: " + connectionFailures.get());
        System.out.println("   ✅ 连接成功率: " + String.format("%.2f%%", getSuccessRate()));
        System.out.println("   🔄 降级次数: " + degradationCount.get());
        
        // 发布状态
        System.out.println("\n🎯 发布状态:");
        System.out.println("   📍 当前阶段: " + currentDeploymentPhase);
        System.out.println("   🔍 发布检测: " + (isDeploymentDetected ? "是 ✅" : "否 ❌"));
        
        if (isDeploymentDetected) {
            long downtimeSeconds = currentDowntime.get() / 1000;
            System.out.println("   ⏱️  发布耗时: " + downtimeSeconds + "秒");
            System.out.println("   🕐 开始时间: " + new Date(firstFailureTime.get()));
        }
        
        // 业务影响评估
        System.out.println("\n💼 业务影响评估:");
        evaluateBusinessImpact();
        
        // 建议和告警
        System.out.println("\n🔧 建议和告警:");
        provideRecommendations();
        
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("下次报告时间: " + new Date(System.currentTimeMillis() + 30000));
        System.out.println();
    }
    
    /**
     * 评估业务影响
     */
    private static void evaluateBusinessImpact() {
        double successRate = getSuccessRate();
        long downtimeSeconds = currentDowntime.get() / 1000;
        
        if (successRate >= 95) {
            System.out.println("   🟢 影响级别: 无影响");
            System.out.println("   📊 连接正常，集群限流工作正常");
        } else if (successRate >= 80) {
            System.out.println("   🟡 影响级别: 轻微影响");
            System.out.println("   📊 部分降级，但业务基本正常");
        } else if (successRate >= 50) {
            System.out.println("   🧡 影响级别: 中等影响");
            System.out.println("   📊 较多降级，限流精度下降");
        } else {
            System.out.println("   🔴 影响级别: 严重影响");
            System.out.println("   📊 大量降级，集群限流失效");
        }
        
        // 影响时长分析
        if (downtimeSeconds > 180) {
            System.out.println("   ⏰ 影响时长: 过长 (超过3分钟)");
            System.out.println("   🚨 建议: 检查发布流程");
        } else if (downtimeSeconds > 120) {
            System.out.println("   ⏰ 影响时长: 较长 (超过2分钟)");
            System.out.println("   ⚠️  建议: 关注发布进度");
        } else if (downtimeSeconds > 60) {
            System.out.println("   ⏰ 影响时长: 正常 (1-2分钟)");
            System.out.println("   ✅ 在预期范围内");
        } else {
            System.out.println("   ⏰ 影响时长: 较短 (不足1分钟)");
            System.out.println("   ✅ 影响很小");
        }
        
        // 恢复预期
        if (isDeploymentDetected) {
            System.out.println("   🔮 恢复预期: " + getRecoveryExpectation());
        }
    }
    
    /**
     * 提供建议和告警
     */
    private static void provideRecommendations() {
        double successRate = getSuccessRate();
        long downtimeSeconds = currentDowntime.get() / 1000;
        
        if (successRate < 50) {
            System.out.println("   🚨 高优先级告警: 连接成功率过低");
            System.out.println("   🔧 建议: 立即检查Server状态");
        }
        
        if (downtimeSeconds > 300) {
            System.out.println("   🚨 高优先级告警: 发布时间过长");
            System.out.println("   🔧 建议: 考虑回滚方案");
        }
        
        if (currentDeploymentPhase.equals("发布异常")) {
            System.out.println("   🚨 高优先级告警: 发布可能异常");
            System.out.println("   🔧 建议: 人工介入检查");
        }
        
        // 监控建议
        System.out.println("   📊 监控建议:");
        System.out.println("     - 关注Client连接恢复情况");
        System.out.println("     - 关注业务请求成功率");
        System.out.println("     - 关注响应时间变化");
        System.out.println("     - 关注降级使用率");
    }
    
    /**
     * 获取恢复预期
     */
    private static String getRecoveryExpectation() {
        long downtimeSeconds = currentDowntime.get() / 1000;
        
        if (downtimeSeconds < 30) {
            return "30秒内完成";
        } else if (downtimeSeconds < 60) {
            return "1-2分钟内完成";
        } else if (downtimeSeconds < 120) {
            return "30秒内完成";
        } else if (downtimeSeconds < 150) {
            return "30秒内完成";
        } else if (downtimeSeconds < 180) {
            return "即将完成";
        } else {
            return "发布可能异常，需要人工介入";
        }
    }
    
    /**
     * 获取成功率
     */
    private static double getSuccessRate() {
        int total = checkCount.get();
        if (total == 0) return 100.0;
        
        int failures = connectionFailures.get();
        return (double) (total - failures) / total * 100;
    }
    
    /**
     * 获取当前时间字符串
     */
    private static String getCurrentTime() {
        return new Date().toString().substring(11, 19);
    }
    
    /**
     * 快速检查当前状态
     */
    public static void quickCheck() {
        System.out.println("🔍 快速状态检查:");
        System.out.println("─────────────────");
        
        try {
            ClusterTokenClient client = TokenClientProvider.getClient();
            
            if (client == null) {
                System.out.println("❌ Client未初始化");
                return;
            }
            
            int clientState = client.getState();
            System.out.println("🔗 Client状态: " + (clientState == 1 ? "正常 ✅" : "异常 ❌"));
            
            if (client.currentServer() != null) {
                System.out.println("📡 连接服务器: " + client.currentServer());
            } else {
                System.out.println("📡 连接服务器: 无连接");
            }
            
            ClusterClientConfig config = ClusterClientConfigManager.getGlobalConfig();
            if (config != null) {
                System.out.println("⚙️  请求超时: " + config.getRequestTimeout() + "ms");
            }
            
        } catch (Exception e) {
            System.out.println("❌ 检查失败: " + e.getMessage());
        }
    }
} 