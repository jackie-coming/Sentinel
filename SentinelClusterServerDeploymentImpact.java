package com.alibaba.csp.sentinel.demo.deployment;

import com.alibaba.csp.sentinel.cluster.client.ClusterTokenClient;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sentinel集群Server节点发布期间Client节点异常情况详解
 * 
 * 这个文档详细分析了Server节点发布、更新、重启期间，Client节点可能遇到的各种异常情况，
 * 以及相应的监控、感知和处理机制。
 * 
 * 🚀 发布流程时间线：
 * 1. 发布前准备阶段
 * 2. 停止服务阶段
 * 3. 服务不可用阶段
 * 4. 启动服务阶段
 * 5. 服务恢复阶段
 * 6. 发布后稳定阶段
 */
public class SentinelClusterServerDeploymentImpact {
    
    private static final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private static final AtomicInteger connectionFailures = new AtomicInteger(0);
    private static final AtomicInteger fallbackCount = new AtomicInteger(0);
    private static final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    
    public static void main(String[] args) {
        System.out.println("=== Sentinel集群Server发布期间Client异常情况分析 ===\n");
        
        // 1. 发布时间线分析
        analyzeDeploymentTimeline();
        
        // 2. 各阶段异常情况分析
        analyzeExceptionsByPhase();
        
        // 3. 启动实时监控
        startDeploymentMonitoring();
        
        // 4. 模拟发布期间的异常情况
        simulateDeploymentScenarios();
        
        // 保持运行
        try {
            Thread.sleep(180000); // 运行3分钟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 1. 发布时间线分析
     */
    private static void analyzeDeploymentTimeline() {
        System.out.println("🚀 1. Server节点发布时间线分析:\n");
        
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│                     Server节点发布时间线                        │");
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        System.out.println("│ 1. 发布前准备 (0-30s)                                           │");
        System.out.println("│    - 发布通知、健康检查、预热准备                               │");
        System.out.println("│    - Client状态: 🟢 正常连接                                   │");
        System.out.println("│                                                                 │");
        System.out.println("│ 2. 停止服务 (30-60s)                                           │");
        System.out.println("│    - 停止接收新连接、优雅关闭                                   │");
        System.out.println("│    - Client状态: 🔴 连接开始失败                               │");
        System.out.println("│                                                                 │");
        System.out.println("│ 3. 服务不可用 (60-120s)                                        │");
        System.out.println("│    - 进程停止、更新部署、配置更新                               │");
        System.out.println("│    - Client状态: 🔴 完全不可用                                 │");
        System.out.println("│                                                                 │");
        System.out.println("│ 4. 启动服务 (120-150s)                                         │");
        System.out.println("│    - 启动进程、加载配置、端口监听                               │");
        System.out.println("│    - Client状态: 🟡 尝试连接                                   │");
        System.out.println("│                                                                 │");
        System.out.println("│ 5. 服务恢复 (150-180s)                                         │");
        System.out.println("│    - 处理连接、规则同步、预热完成                               │");
        System.out.println("│    - Client状态: 🟢 逐渐恢复                                   │");
        System.out.println("│                                                                 │");
        System.out.println("│ 6. 发布完成 (180s+)                                            │");
        System.out.println("│    - 服务稳定、监控正常、性能达标                               │");
        System.out.println("│    - Client状态: 🟢 完全恢复                                   │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }
    
    /**
     * 2. 各阶段异常情况分析
     */
    private static void analyzeExceptionsByPhase() {
        System.out.println("🔍 2. 各阶段Client异常情况详解:\n");
        
        // 阶段1: 发布前准备阶段
        analyzePhase1();
        
        // 阶段2: 停止服务阶段
        analyzePhase2();
        
        // 阶段3: 服务不可用阶段
        analyzePhase3();
        
        // 阶段4: 启动服务阶段
        analyzePhase4();
        
        // 阶段5: 服务恢复阶段
        analyzePhase5();
        
        // 阶段6: 发布后稳定阶段
        analyzePhase6();
    }
    
    /**
     * 阶段1: 发布前准备阶段 (0-30s)
     */
    private static void analyzePhase1() {
        System.out.println("📍 阶段1: 发布前准备阶段 (0-30s)");
        System.out.println("──────────────────────────────");
        
        System.out.println("🔹 预期行为:");
        System.out.println("  - Server正常运行，处理请求");
        System.out.println("  - Client连接稳定，集群限流正常");
        System.out.println("  - 运维团队准备发布");
        
        System.out.println("🔹 可能出现的异常:");
        System.out.println("  - 如果有发布前检查，可能短暂影响性能");
        System.out.println("  - 某些监控系统可能发出预警");
        
        System.out.println("🔹 Client感知方式:");
        System.out.println("  - 监控连接响应时间变化");
        System.out.println("  - 观察请求处理延迟");
        
        System.out.println("🔹 处理策略:");
        System.out.println("  - 无需特殊处理，保持正常监控");
        System.out.println("  - 建议: 发布前通知业务方");
        
        System.out.println();
    }
    
    /**
     * 阶段2: 停止服务阶段 (30-60s)
     */
    private static void analyzePhase2() {
        System.out.println("📍 阶段2: 停止服务阶段 (30-60s)");
        System.out.println("──────────────────────────────");
        
        System.out.println("🔹 Server行为:");
        System.out.println("  - 优雅关闭: 停止接收新连接");
        System.out.println("  - 处理完现有请求");
        System.out.println("  - 释放资源，准备停止");
        
        System.out.println("🔹 Client异常表现:");
        System.out.println("  ❌ 新连接失败: ConnectException");
        System.out.println("  ❌ 请求超时: SocketTimeoutException");
        System.out.println("  ❌ 连接断开: Connection reset by peer");
        System.out.println("  ⚠️  响应时间变长");
        
        System.out.println("🔹 Sentinel内部状态变化:");
        System.out.println("  - TokenClient.getState() 从 1 变为 0");
        System.out.println("  - 连接池开始报告连接失败");
        System.out.println("  - 重连机制开始激活");
        
        System.out.println("🔹 业务影响:");
        System.out.println("  - 🔄 触发降级: 自动切换到本地限流");
        System.out.println("  - 🔄 限流规则变化: 从集群规则切换到本地规则");
        System.out.println("  - 🔄 QPS变化: 限流阈值可能发生变化");
        
        System.out.println();
    }
    
    /**
     * 阶段3: 服务不可用阶段 (60-120s)
     */
    private static void analyzePhase3() {
        System.out.println("📍 阶段3: 服务不可用阶段 (60-120s)");
        System.out.println("────────────────────────────────");
        
        System.out.println("🔹 Server状态:");
        System.out.println("  - 进程完全停止");
        System.out.println("  - 端口不再监听");
        System.out.println("  - 无法处理任何请求");
        
        System.out.println("🔹 Client异常表现:");
        System.out.println("  ❌ 连接拒绝: Connection refused");
        System.out.println("  ❌ 无法解析: Host unreachable");
        System.out.println("  ❌ 端口不可达: No route to host");
        System.out.println("  ❌ 持续重连失败");
        
        System.out.println("🔹 Sentinel内部行为:");
        System.out.println("  - 🔄 自动重连: 每2秒尝试重连");
        System.out.println("  - 🔄 指数退避: 重连间隔逐渐增加");
        System.out.println("  - 🔄 降级保护: 完全使用本地限流");
        System.out.println("  - 🔄 状态管理: Client状态保持为0");
        
        System.out.println("🔹 业务影响分析:");
        System.out.println("  - 🟢 正面: 降级保护确保服务可用");
        System.out.println("  - 🟡 中性: 限流精度下降(无全局协调)");
        System.out.println("  - 🔴 负面: 可能出现不同节点限流不均");
        
        System.out.println("🔹 监控告警:");
        System.out.println("  - 🚨 集群连接失败率100%");
        System.out.println("  - 🚨 降级使用率100%");
        System.out.println("  - 🚨 集群限流服务不可用");
        
        System.out.println();
    }
    
    /**
     * 阶段4: 启动服务阶段 (120-150s)
     */
    private static void analyzePhase4() {
        System.out.println("📍 阶段4: 启动服务阶段 (120-150s)");
        System.out.println("────────────────────────────────");
        
        System.out.println("🔹 Server启动过程:");
        System.out.println("  1. 进程启动 (0-10s)");
        System.out.println("  2. 配置加载 (10-15s)");
        System.out.println("  3. 端口监听 (15-20s)");
        System.out.println("  4. 组件初始化 (20-30s)");
        
        System.out.println("🔹 Client异常表现:");
        System.out.println("  ❌ 间歇性连接失败: 服务还在启动");
        System.out.println("  ❌ 连接建立后立即断开: 服务未就绪");
        System.out.println("  ❌ 请求处理异常: 内部组件未完全初始化");
        System.out.println("  ⚠️  响应时间不稳定");
        
        System.out.println("🔹 Sentinel重连行为:");
        System.out.println("  - 🔄 检测到端口开放，尝试连接");
        System.out.println("  - 🔄 连接成功但立即断开");
        System.out.println("  - 🔄 继续使用本地降级");
        System.out.println("  - 🔄 等待服务完全就绪");
        
        System.out.println("🔹 这个阶段的关键问题:");
        System.out.println("  - 🎯 启动时间过长影响业务");
        System.out.println("  - 🎯 启动过程中的不稳定状态");
        System.out.println("  - 🎯 Client频繁尝试连接消耗资源");
        
        System.out.println();
    }
    
    /**
     * 阶段5: 服务恢复阶段 (150-180s)
     */
    private static void analyzePhase5() {
        System.out.println("📍 阶段5: 服务恢复阶段 (150-180s)");
        System.out.println("────────────────────────────────");
        
        System.out.println("🔹 Server恢复过程:");
        System.out.println("  - 接受Client连接");
        System.out.println("  - 处理集群限流请求");
        System.out.println("  - 同步限流规则");
        System.out.println("  - 预热完成");
        
        System.out.println("🔹 Client恢复表现:");
        System.out.println("  ✅ 连接成功建立");
        System.out.println("  ✅ Client状态从0变为1");
        System.out.println("  ✅ 开始使用集群限流");
        System.out.println("  ⚠️  性能逐渐恢复正常");
        
        System.out.println("🔹 可能出现的问题:");
        System.out.println("  - 🔄 短暂的限流规则不同步");
        System.out.println("  - 🔄 性能未达到发布前水平");
        System.out.println("  - 🔄 部分Client仍在使用本地限流");
        System.out.println("  - 🔄 请求分布不均匀");
        
        System.out.println("🔹 监控关注点:");
        System.out.println("  - 📊 连接成功率逐渐上升");
        System.out.println("  - 📊 降级使用率逐渐下降");
        System.out.println("  - 📊 集群限流QPS逐渐恢复");
        System.out.println("  - 📊 响应时间逐渐稳定");
        
        System.out.println();
    }
    
    /**
     * 阶段6: 发布后稳定阶段 (180s+)
     */
    private static void analyzePhase6() {
        System.out.println("📍 阶段6: 发布后稳定阶段 (180s+)");
        System.out.println("─────────────────────────────────");
        
        System.out.println("🔹 预期正常状态:");
        System.out.println("  ✅ 所有Client连接正常");
        System.out.println("  ✅ 集群限流完全恢复");
        System.out.println("  ✅ 性能达到预期水平");
        System.out.println("  ✅ 监控指标正常");
        
        System.out.println("🔹 可能的遗留问题:");
        System.out.println("  - 🔍 某些Client仍未连接");
        System.out.println("  - 🔍 性能略有下降");
        System.out.println("  - 🔍 配置不一致");
        System.out.println("  - 🔍 监控数据异常");
        
        System.out.println("🔹 验证检查点:");
        System.out.println("  - ✅ Client连接率 > 95%");
        System.out.println("  - ✅ 降级使用率 < 5%");
        System.out.println("  - ✅ 响应时间 < 100ms");
        System.out.println("  - ✅ 错误率 < 0.1%");
        
        System.out.println();
    }
    
    /**
     * 3. 启动发布期间的监控
     */
    private static void startDeploymentMonitoring() {
        System.out.println("🔍 3. 启动发布期间监控:\n");
        
        ScheduledExecutorService monitoringService = Executors.newScheduledThreadPool(2);
        
        // 监控1: 连接状态监控
        monitoringService.scheduleWithFixedDelay(() -> {
            monitorClientConnection();
        }, 0, 10, TimeUnit.SECONDS);
        
        // 监控2: 业务指标监控
        monitoringService.scheduleWithFixedDelay(() -> {
            monitorBusinessMetrics();
        }, 0, 15, TimeUnit.SECONDS);
        
        System.out.println("✅ 发布期间监控已启动");
        System.out.println();
    }
    
    /**
     * 监控Client连接状态
     */
    private static void monitorClientConnection() {
        try {
            ClusterTokenClient client = TokenClientProvider.getClient();
            
            System.out.println("🔗 Client连接状态监控:");
            
            if (client == null) {
                System.out.println("  ❌ Token客户端未初始化");
                return;
            }
            
            int clientState = client.getState();
            connectionAttempts.incrementAndGet();
            
            switch (clientState) {
                case 0:
                    connectionFailures.incrementAndGet();
                    System.out.println("  ❌ 连接状态: 已断开 (发布期间正常)");
                    break;
                case 1:
                    System.out.println("  ✅ 连接状态: 正常连接");
                    lastSuccessTime.set(System.currentTimeMillis());
                    break;
                default:
                    System.out.println("  ❓ 连接状态: 未知状态 " + clientState);
                    break;
            }
            
            // 服务器信息
            if (client.currentServer() != null) {
                System.out.println("  📡 连接服务器: " + client.currentServer());
            } else {
                System.out.println("  📡 连接服务器: 无");
            }
            
            // 连接统计
            long failureRate = connectionAttempts.get() > 0 ? 
                (connectionFailures.get() * 100L / connectionAttempts.get()) : 0;
            System.out.println("  📊 连接失败率: " + failureRate + "%");
            
            // 距离上次成功时间
            long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime.get();
            System.out.println("  ⏱️  距离上次成功: " + (timeSinceLastSuccess / 1000) + "秒");
            
        } catch (Exception e) {
            System.out.println("  ❌ 连接监控异常: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * 监控业务指标
     */
    private static void monitorBusinessMetrics() {
        try {
            System.out.println("📊 业务指标监控:");
            
            // 获取Client配置
            ClusterClientConfig config = ClusterClientConfigManager.getGlobalConfig();
            if (config != null) {
                System.out.println("  📋 请求超时配置: " + config.getRequestTimeout() + "ms");
            }
            
            // 降级使用统计
            int totalRequests = connectionAttempts.get();
            int fallbacks = fallbackCount.get();
            
            if (totalRequests > 0) {
                double fallbackRate = (double) fallbacks / totalRequests * 100;
                System.out.println("  🔄 降级使用率: " + String.format("%.2f%%", fallbackRate));
                
                if (fallbackRate > 80) {
                    System.out.println("  🚨 告警: 降级使用率过高，可能Server发布中");
                }
            }
            
            // 发布期间影响评估
            evaluateDeploymentImpact();
            
        } catch (Exception e) {
            System.out.println("  ❌ 业务监控异常: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * 评估发布期间的影响
     */
    private static void evaluateDeploymentImpact() {
        long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime.get();
        long disconnectionDuration = timeSinceLastSuccess / 1000;
        
        System.out.println("  🎯 发布影响评估:");
        
        if (disconnectionDuration < 30) {
            System.out.println("    ✅ 影响轻微: 连接正常或刚刚断开");
        } else if (disconnectionDuration < 120) {
            System.out.println("    ⚠️  影响中等: 可能正在发布中");
        } else if (disconnectionDuration < 300) {
            System.out.println("    🚨 影响严重: 发布时间过长");
        } else {
            System.out.println("    💥 影响严重: 可能发布失败");
        }
        
        // 发布阶段判断
        if (disconnectionDuration >= 30 && disconnectionDuration < 60) {
            System.out.println("    📍 推测阶段: 停止服务阶段");
        } else if (disconnectionDuration >= 60 && disconnectionDuration < 120) {
            System.out.println("    📍 推测阶段: 服务不可用阶段");
        } else if (disconnectionDuration >= 120 && disconnectionDuration < 150) {
            System.out.println("    📍 推测阶段: 启动服务阶段");
        } else if (disconnectionDuration >= 150 && disconnectionDuration < 180) {
            System.out.println("    📍 推测阶段: 服务恢复阶段");
        }
    }
    
    /**
     * 4. 模拟发布期间的异常情况
     */
    private static void simulateDeploymentScenarios() {
        System.out.println("🎭 4. 模拟发布期间异常情况:\n");
        
        ScheduledExecutorService simulationService = Executors.newScheduledThreadPool(1);
        
        // 模拟不同发布阶段的异常
        simulationService.scheduleWithFixedDelay(() -> {
            simulateDeploymentPhase();
        }, 30, 60, TimeUnit.SECONDS);
        
        System.out.println("✅ 发布异常模拟已启动");
        System.out.println();
    }
    
    /**
     * 模拟发布阶段
     */
    private static void simulateDeploymentPhase() {
        System.out.println("🎭 模拟发布阶段异常:");
        
        // 模拟连接失败
        connectionFailures.addAndGet(5);
        fallbackCount.addAndGet(8);
        
        System.out.println("  - 模拟连接失败增加");
        System.out.println("  - 模拟降级使用增加");
        System.out.println("  - 模拟异常检测触发");
        
        // 发布恢复模拟
        try {
            Thread.sleep(30000); // 30秒后恢复
            System.out.println("  ✅ 模拟发布恢复");
            lastSuccessTime.set(System.currentTimeMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
    
    /**
     * 发布期间Client异常感知和处理的最佳实践
     */
    public static class DeploymentBestPractices {
        
        /**
         * 发布前检查
         */
        public static void preDeploymentCheck() {
            System.out.println("📋 发布前检查清单:");
            System.out.println("  ✅ 确认Client连接状态正常");
            System.out.println("  ✅ 确认降级规则配置正确");
            System.out.println("  ✅ 确认监控告警正常");
            System.out.println("  ✅ 通知业务方发布时间窗口");
            System.out.println();
        }
        
        /**
         * 发布期间监控
         */
        public static void deploymentMonitoring() {
            System.out.println("🔍 发布期间监控要点:");
            System.out.println("  - 📊 Client连接成功率");
            System.out.println("  - 📊 降级使用率");
            System.out.println("  - 📊 业务请求成功率");
            System.out.println("  - 📊 响应时间变化");
            System.out.println("  - 📊 错误率统计");
            System.out.println();
        }
        
        /**
         * 发布后验证
         */
        public static void postDeploymentVerification() {
            System.out.println("✅ 发布后验证清单:");
            System.out.println("  ✅ Client连接率 > 95%");
            System.out.println("  ✅ 降级使用率 < 5%");
            System.out.println("  ✅ 响应时间恢复正常");
            System.out.println("  ✅ 业务指标正常");
            System.out.println("  ✅ 无异常告警");
            System.out.println();
        }
        
        /**
         * 异常处理建议
         */
        public static void exceptionHandling() {
            System.out.println("🚨 异常处理建议:");
            System.out.println("  - 🔧 发布时间过长: 检查启动配置");
            System.out.println("  - 🔧 Client连接失败: 检查网络和端口");
            System.out.println("  - 🔧 降级率过高: 检查Server状态");
            System.out.println("  - 🔧 性能下降: 检查资源配置");
            System.out.println("  - 🔧 告警频发: 调整告警阈值");
            System.out.println();
        }
    }
} 