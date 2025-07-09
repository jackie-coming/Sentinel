package com.alibaba.csp.sentinel.demo.analysis;

import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.client.ClusterTokenClient;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.exception.SentinelClusterException;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sentinel集群限流异常感知机制深度分析
 * 
 * 本文档详细分析了Sentinel集群限流架构中，当Token Server节点发生异常时，
 * 用户（客户端）如何感知这些异常，以及相应的处理策略。
 * 
 * 关键知识点：
 * 1. 异常感知的时间维度
 * 2. 异常类型和感知方式
 * 3. 降级策略的触发机制
 * 4. 用户体验优化方案
 * 
 * @author Sentinel Team
 */
public class SentinelClusterExceptionAnalysis {
    
    /**
     * 1. 异常感知的核心流程
     * 
     * 基于 FlowRuleChecker.passClusterCheck() 方法分析：
     * 
     * try {
     *     TokenService clusterService = pickClusterService();
     *     if (clusterService == null) {
     *         // 感知点1：Token Client不可用
     *         return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
     *     }
     *     
     *     long flowId = rule.getClusterConfig().getFlowId();
     *     TokenResult result = clusterService.requestToken(flowId, acquireCount, prioritized);
     *     return applyTokenResult(result, rule, context, node, acquireCount, prioritized);
     *     
     * } catch (Throwable ex) {
     *     // 感知点2：请求异常
     *     RecordLog.warn("[FlowRuleChecker] Request cluster token unexpected failed", ex);
     * }
     * 
     * // 感知点3：最终降级
     * return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
     */
    
    /**
     * 2. 异常感知的具体实现
     * 
     * 在 NettyTransportClient.sendRequest() 方法中：
     * 
     * if (!isReady()) {
     *     // 感知点：客户端未就绪
     *     throw new SentinelClusterException(ClusterErrorMessages.CLIENT_NOT_READY);
     * }
     * 
     * if (!promise.await(ClusterClientConfigManager.getRequestTimeout())) {
     *     // 感知点：请求超时
     *     throw new SentinelClusterException(ClusterErrorMessages.REQUEST_TIME_OUT);
     * }
     */
    
    /**
     * 3. 不同异常类型的感知方式
     */
    
    /**
     * 3.1 连接异常感知
     * 
     * 特征：
     * - 连接建立失败
     * - 连接断开
     * - 网络不可达
     * 
     * 感知方式：
     * - 连接超时异常
     * - 连接状态变化回调
     * - 重连机制触发
     * 
     * 用户感知：
     * - 请求响应时间变长
     * - 从集群限流降级到本地限流
     * - 监控指标显示连接异常
     */
    public static class ConnectionExceptionAnalysis {
        
        /**
         * 连接异常的具体表现
         * 
         * 在 NettyTransportClient.connect() 方法中：
         * 
         * if (future.cause() != null) {
         *     RecordLog.warn(
         *         String.format("[NettyTransportClient] Could not connect to <%s:%d> after %d times",
         *             host, port, failConnectedTime.get()), future.cause());
         *     failConnectedTime.incrementAndGet();
         *     channel = null;
         * }
         */
        
        private final AtomicInteger failConnectedTime = new AtomicInteger(0);
        private final AtomicLong lastConnectTime = new AtomicLong(0);
        
        public void recordConnectionFailure(String host, int port, Throwable cause) {
            int failCount = failConnectedTime.incrementAndGet();
            long currentTime = System.currentTimeMillis();
            
            System.out.printf("🔴 连接异常感知 - 服务器: %s:%d, 失败次数: %d, 异常: %s\n",
                host, port, failCount, cause.getMessage());
            
            // 用户感知：根据失败次数调整重连策略
            if (failCount >= 3) {
                System.out.println("⚠️  用户感知：连接连续失败，建议检查网络状态");
            }
            
            // 计算重连延迟
            long reconnectDelay = 2000 * (failCount + 1); // 指数退避
            System.out.printf("🔄 重连策略：%d毫秒后重试\n", reconnectDelay);
        }
        
        public void recordConnectionSuccess(String host, int port) {
            if (failConnectedTime.get() > 0) {
                System.out.printf("✅ 连接恢复 - 服务器: %s:%d, 之前失败次数: %d\n",
                    host, port, failConnectedTime.get());
            }
            failConnectedTime.set(0);
            lastConnectTime.set(System.currentTimeMillis());
        }
    }
    
    /**
     * 3.2 请求超时感知
     * 
     * 特征：
     * - 请求发送成功但响应超时
     * - 服务器处理慢
     * - 网络延迟高
     * 
     * 感知方式：
     * - 请求超时异常
     * - 响应时间统计
     * - 超时计数器
     * 
     * 用户感知：
     * - 请求响应时间增加
     * - 超时率上升
     * - 自动降级到本地限流
     */
    public static class RequestTimeoutAnalysis {
        
        private final AtomicInteger timeoutCount = new AtomicInteger(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicInteger totalRequests = new AtomicInteger(0);
        
        public void recordRequestTimeout(String resource, long requestTime) {
            int timeouts = timeoutCount.incrementAndGet();
            int total = totalRequests.incrementAndGet();
            
            System.out.printf("⏰ 请求超时感知 - 资源: %s, 请求时间: %dms, 超时次数: %d\n",
                resource, requestTime, timeouts);
            
            // 用户感知：超时率统计
            double timeoutRate = (double) timeouts / total;
            if (timeoutRate > 0.1) { // 超时率超过10%
                System.out.printf("⚠️  用户感知：超时率过高 %.2f%%, 建议检查服务器性能\n", timeoutRate * 100);
            }
            
            // 降级触发提示
            System.out.println("🔄 降级策略：请求超时，降级到本地限流");
        }
        
        public void recordRequestSuccess(String resource, long responseTime) {
            totalRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            
            long avgResponseTime = totalResponseTime.get() / totalRequests.get();
            if (avgResponseTime > 1000) { // 平均响应时间超过1秒
                System.out.printf("⚠️  用户感知：平均响应时间过长 %dms\n", avgResponseTime);
            }
        }
    }
    
    /**
     * 3.3 服务不可用感知
     * 
     * 特征：
     * - 服务器宕机
     * - 服务进程异常
     * - 负载过高拒绝服务
     * 
     * 感知方式：
     * - 连接拒绝
     * - 服务返回错误状态
     * - 健康检查失败
     * 
     * 用户感知：
     * - 立即降级到本地限流
     * - 错误率急剧上升
     * - 集群限流完全失效
     */
    public static class ServiceUnavailableAnalysis {
        
        private final ConcurrentHashMap<String, ServiceStatus> serviceStatus = new ConcurrentHashMap<>();
        
        public void recordServiceUnavailable(String serverAddress, String reason) {
            ServiceStatus status = serviceStatus.computeIfAbsent(serverAddress, k -> new ServiceStatus());
            status.recordFailure(reason);
            
            System.out.printf("❌ 服务不可用感知 - 服务器: %s, 原因: %s, 连续失败: %d次\n",
                serverAddress, reason, status.consecutiveFailures);
            
            // 用户感知：服务状态变化
            if (status.consecutiveFailures >= 3) {
                System.out.printf("🚨 用户感知：服务器 %s 疑似宕机，已连续失败 %d 次\n",
                    serverAddress, status.consecutiveFailures);
                
                // 触发告警
                triggerAlert(serverAddress, "服务器不可用", status.consecutiveFailures);
            }
            
            // 降级策略
            System.out.println("🔄 降级策略：服务不可用，全部降级到本地限流");
        }
        
        public void recordServiceRecovery(String serverAddress) {
            ServiceStatus status = serviceStatus.get(serverAddress);
            if (status != null && status.consecutiveFailures > 0) {
                System.out.printf("✅ 服务恢复感知 - 服务器: %s, 之前失败次数: %d\n",
                    serverAddress, status.consecutiveFailures);
                status.reset();
            }
        }
        
        private void triggerAlert(String serverAddress, String reason, int failureCount) {
            System.out.printf("🚨 告警触发 - 服务器: %s, 原因: %s, 失败次数: %d\n",
                serverAddress, reason, failureCount);
            
            // 这里可以集成实际的告警系统
            // 例如：发送邮件、短信、钉钉通知等
        }
        
        private static class ServiceStatus {
            volatile int consecutiveFailures = 0;
            volatile long lastFailureTime = 0;
            
            void recordFailure(String reason) {
                consecutiveFailures++;
                lastFailureTime = System.currentTimeMillis();
            }
            
            void reset() {
                consecutiveFailures = 0;
                lastFailureTime = 0;
            }
        }
    }
    
    /**
     * 4. 降级策略分析
     * 
     * 基于 FlowRuleChecker.fallbackToLocalOrPass() 方法：
     * 
     * if (rule.getClusterConfig().isFallbackToLocalWhenFail()) {
     *     return passLocalCheck(rule, context, node, acquireCount, prioritized);
     * } else {
     *     // The rule won't be activated, just pass.
     *     return true;
     * }
     */
    public static class FallbackStrategyAnalysis {
        
        /**
         * 降级策略类型
         */
        public enum FallbackType {
            /**
             * 降级到本地限流
             * 用户感知：限流效果略有变化，但仍有保护
             */
            LOCAL_FALLBACK,
            
            /**
             * 直接放行
             * 用户感知：限流失效，需要依赖其他保护机制
             */
            PASS_THROUGH,
            
            /**
             * 拒绝所有请求
             * 用户感知：服务暂时不可用，影响用户体验
             */
            REJECT_ALL
        }
        
        /**
         * 分析不同降级策略的用户感知
         */
        public void analyzeFallbackImpact(FlowRule rule, FallbackType fallbackType) {
            String resource = rule.getResource();
            double clusterLimit = rule.getCount();
            
            switch (fallbackType) {
                case LOCAL_FALLBACK:
                    System.out.printf("🔄 降级策略分析 - 资源: %s\n", resource);
                    System.out.printf("   集群限制: %.0f QPS → 本地限制: %.0f QPS\n", 
                        clusterLimit, clusterLimit * 0.8); // 通常本地限制会略低
                    System.out.println("   用户感知：限流效果略有变化，但保护机制仍然有效");
                    System.out.println("   影响程度：低 ✅");
                    break;
                    
                case PASS_THROUGH:
                    System.out.printf("🔄 降级策略分析 - 资源: %s\n", resource);
                    System.out.printf("   集群限制: %.0f QPS → 无限制\n", clusterLimit);
                    System.out.println("   用户感知：限流失效，可能出现流量洪峰");
                    System.out.println("   影响程度：高 ⚠️");
                    break;
                    
                case REJECT_ALL:
                    System.out.printf("🔄 降级策略分析 - 资源: %s\n", resource);
                    System.out.println("   集群限制: 拒绝所有请求");
                    System.out.println("   用户感知：服务暂时不可用");
                    System.out.println("   影响程度：严重 🚨");
                    break;
            }
        }
    }
    
    /**
     * 5. Token结果状态分析
     * 
     * 基于 TokenResultStatus 分析用户感知：
     */
    public static class TokenResultAnalysis {
        
        public void analyzeTokenResult(TokenResult result, String resource) {
            switch (result.getStatus()) {
                case TokenResultStatus.OK:
                    System.out.printf("✅ 令牌获取成功 - 资源: %s, 剩余: %d\n", 
                        resource, result.getRemaining());
                    break;
                    
                case TokenResultStatus.BLOCKED:
                    System.out.printf("🚫 令牌获取被阻断 - 资源: %s\n", resource);
                    System.out.println("   用户感知：请求被限流，符合预期");
                    break;
                    
                case TokenResultStatus.SHOULD_WAIT:
                    System.out.printf("⏳ 令牌获取需等待 - 资源: %s, 等待时间: %dms\n", 
                        resource, result.getWaitInMs());
                    System.out.println("   用户感知：请求延迟，优先级机制生效");
                    break;
                    
                case TokenResultStatus.NO_RULE_EXISTS:
                    System.out.printf("❓ 规则不存在 - 资源: %s\n", resource);
                    System.out.println("   用户感知：集群配置可能有误");
                    System.out.println("   降级策略：fallbackToLocalOrPass");
                    break;
                    
                case TokenResultStatus.BAD_REQUEST:
                    System.out.printf("❌ 请求格式错误 - 资源: %s\n", resource);
                    System.out.println("   用户感知：客户端配置错误");
                    System.out.println("   降级策略：fallbackToLocalOrPass");
                    break;
                    
                case TokenResultStatus.FAIL:
                    System.out.printf("💥 令牌获取失败 - 资源: %s\n", resource);
                    System.out.println("   用户感知：服务器内部错误");
                    System.out.println("   降级策略：fallbackToLocalOrPass");
                    break;
                    
                case TokenResultStatus.TOO_MANY_REQUEST:
                    System.out.printf("🔥 请求过多 - 资源: %s\n", resource);
                    System.out.println("   用户感知：服务器过载");
                    System.out.println("   降级策略：fallbackToLocalOrPass");
                    break;
                    
                default:
                    System.out.printf("❓ 未知状态 - 资源: %s, 状态: %d\n", 
                        resource, result.getStatus());
                    break;
            }
        }
    }
    
    /**
     * 6. 用户感知优化建议
     */
    public static class UserExperienceOptimization {
        
        /**
         * 6.1 监控指标建议
         */
        public void suggestMonitoringMetrics() {
            System.out.println("📊 建议监控指标：");
            System.out.println("   1. 集群连接状态：连接成功率、重连次数");
            System.out.println("   2. 请求性能：平均响应时间、超时率");
            System.out.println("   3. 降级情况：降级触发次数、降级持续时间");
            System.out.println("   4. 错误统计：异常类型分布、错误率趋势");
            System.out.println("   5. 流量分析：集群vs本地限流比例");
        }
        
        /**
         * 6.2 告警策略建议
         */
        public void suggestAlertingStrategy() {
            System.out.println("🚨 建议告警策略：");
            System.out.println("   1. 连接异常：连续失败3次以上告警");
            System.out.println("   2. 响应超时：超时率超过10%告警");
            System.out.println("   3. 服务不可用：服务器宕机立即告警");
            System.out.println("   4. 降级触发：降级持续时间超过5分钟告警");
            System.out.println("   5. 错误率异常：错误率超过5%告警");
        }
        
        /**
         * 6.3 用户体验优化建议
         */
        public void suggestUserExperienceImprovements() {
            System.out.println("🔧 用户体验优化建议：");
            System.out.println("   1. 优雅降级：确保本地限流规则合理配置");
            System.out.println("   2. 快速恢复：优化重连策略，减少故障恢复时间");
            System.out.println("   3. 透明感知：提供清晰的监控面板和状态展示");
            System.out.println("   4. 主动通知：关键异常及时通知运维和开发人员");
            System.out.println("   5. 容错设计：多层防护，避免单点故障");
        }
    }
    
    /**
     * 7. 完整的异常处理流程
     */
    public static void demonstrateCompleteExceptionHandling() {
        System.out.println("🔄 完整异常处理流程演示：");
        
        // 1. 正常请求流程
        System.out.println("\n1. 正常请求流程：");
        System.out.println("   用户请求 → 集群限流检查 → 获取Token → 业务处理");
        
        // 2. 异常检测
        System.out.println("\n2. 异常检测：");
        System.out.println("   连接超时 → 记录异常 → 触发重连");
        System.out.println("   请求失败 → 异常统计 → 降级判断");
        
        // 3. 降级决策
        System.out.println("\n3. 降级决策：");
        System.out.println("   检查降级配置 → 选择降级策略 → 执行降级逻辑");
        
        // 4. 用户感知
        System.out.println("\n4. 用户感知：");
        System.out.println("   响应时间变化 → 限流效果变化 → 监控指标异常");
        
        // 5. 恢复流程
        System.out.println("\n5. 恢复流程：");
        System.out.println("   服务恢复 → 连接重建 → 切换回集群模式");
        
        // 6. 持续监控
        System.out.println("\n6. 持续监控：");
        System.out.println("   指标收集 → 趋势分析 → 预警告警");
    }
    
    public static void main(String[] args) {
        System.out.println("=== Sentinel集群限流异常感知机制分析 ===\n");
        
        // 演示各种异常感知机制
        ConnectionExceptionAnalysis connectionAnalysis = new ConnectionExceptionAnalysis();
        connectionAnalysis.recordConnectionFailure("192.168.1.100", 8719, 
            new Exception("Connection timeout"));
        
        RequestTimeoutAnalysis timeoutAnalysis = new RequestTimeoutAnalysis();
        timeoutAnalysis.recordRequestTimeout("api:user:profile", 2500);
        
        ServiceUnavailableAnalysis serviceAnalysis = new ServiceUnavailableAnalysis();
        serviceAnalysis.recordServiceUnavailable("192.168.1.100:8719", "Server not responding");
        
        // 降级策略分析
        FallbackStrategyAnalysis fallbackAnalysis = new FallbackStrategyAnalysis();
        FlowRule rule = new FlowRule();
        rule.setResource("api:user:profile");
        rule.setCount(100);
        fallbackAnalysis.analyzeFallbackImpact(rule, FallbackStrategyAnalysis.FallbackType.LOCAL_FALLBACK);
        
        // 用户体验优化建议
        UserExperienceOptimization optimization = new UserExperienceOptimization();
        optimization.suggestMonitoringMetrics();
        optimization.suggestAlertingStrategy();
        optimization.suggestUserExperienceImprovements();
        
        // 完整流程演示
        demonstrateCompleteExceptionHandling();
    }
} 