package com.alibaba.csp.sentinel.demo.guide;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.ClusterTokenClient;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.ClusterFlowConfig;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sentinel集群限流Server节点异常感知实用指南
 * 
 * 本指南展示了用户在实际业务中如何感知和处理Sentinel集群限流Server节点异常
 */
public class ClusterServerExceptionDetectionGuide {
    
    // 异常感知统计
    private static final AtomicInteger clusterRequestCount = new AtomicInteger(0);
    private static final AtomicInteger localFallbackCount = new AtomicInteger(0);
    private static final AtomicLong lastClusterSuccessTime = new AtomicLong(System.currentTimeMillis());
    
    public static void main(String[] args) throws Exception {
        // 1. 初始化集群配置
        initClusterConfiguration();
        
        // 2. 启动异常监控
        startExceptionMonitoring();
        
        // 3. 模拟业务请求
        simulateBusinessRequests();
        
        // 4. 演示Server节点监测
        demonstrateServerMonitoring();
        
        // 保持运行
        Thread.sleep(60000);
    }
    
    /**
     * 1. 业务代码中的异常感知方式
     */
    public static class BusinessLevelDetection {
        
        /**
         * 方式1：通过限流结果感知
         */
        public boolean processUserRequest(String userId) {
            String resource = "api:user:query";
            long startTime = System.currentTimeMillis();
            boolean isClusterMode = false;
            
            try {
                Entry entry = SphU.entry(resource);
                try {
                    // 判断当前是否使用集群限流
                    isClusterMode = isUsingClusterMode();
                    
                    if (!isClusterMode) {
                        // 感知点1：发现使用本地限流，说明集群可能异常
                        System.out.println("⚠️  业务感知：当前使用本地限流，集群服务可能异常");
                        recordFallbackUsage();
                    } else {
                        recordClusterUsage();
                    }
                    
                    // 执行业务逻辑
                    return executeBusinessLogic(userId);
                    
                } finally {
                    entry.exit();
                }
            } catch (BlockException e) {
                // 感知点2：通过限流触发频率感知异常
                System.out.printf("🚫 业务感知：请求被限流 - 资源: %s, 模式: %s\n", 
                    resource, isClusterMode ? "集群" : "本地");
                return false;
            }
        }
        
        /**
         * 方式2：通过响应时间感知
         */
        public boolean processOrderRequest(String orderId) {
            String resource = "api:order:create";
            long startTime = System.currentTimeMillis();
            
            try {
                Entry entry = SphU.entry(resource);
                try {
                    // 执行业务逻辑
                    boolean result = executeBusinessLogic(orderId);
                    
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    // 感知点3：响应时间异常
                    if (responseTime > 1000) { // 超过1秒认为异常
                        System.out.printf("⏰ 业务感知：响应时间异常 %dms，可能集群服务延迟\n", responseTime);
                        checkClusterHealth();
                    }
                    
                    return result;
                    
                } finally {
                    entry.exit();
                }
            } catch (BlockException e) {
                return false;
            }
        }
        
        private boolean isUsingClusterMode() {
            ClusterTokenClient client = TokenClientProvider.getClient();
            if (client == null) {
                return false;
            }
            return client.getState() == 1; // CLIENT_STATUS_STARTED
        }
        
        private void recordClusterUsage() {
            clusterRequestCount.incrementAndGet();
            lastClusterSuccessTime.set(System.currentTimeMillis());
        }
        
        private void recordFallbackUsage() {
            localFallbackCount.incrementAndGet();
        }
        
        private boolean executeBusinessLogic(String id) {
            // 模拟业务处理
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
    }
    
    /**
     * 2. 主动健康检查方式
     */
    public static class ActiveHealthCheck {
        
        /**
         * 检查集群连接状态
         */
        public ClusterHealthStatus checkClusterHealth() {
            ClusterHealthStatus status = new ClusterHealthStatus();
            
            // 检查1：客户端状态
            ClusterTokenClient client = TokenClientProvider.getClient();
            if (client == null) {
                status.clientAvailable = false;
                status.addIssue("Token客户端未初始化");
                System.out.println("❌ 健康检查：Token客户端未初始化");
            } else {
                int clientState = client.getState();
                status.clientState = clientState;
                
                switch (clientState) {
                    case 0: // CLIENT_STATUS_OFF
                        status.clientAvailable = false;
                        status.addIssue("客户端已关闭");
                        System.out.println("❌ 健康检查：客户端状态 - 已关闭");
                        break;
                    case 1: // CLIENT_STATUS_STARTED
                        status.clientAvailable = true;
                        System.out.println("✅ 健康检查：客户端状态 - 正常运行");
                        break;
                    default:
                        status.clientAvailable = false;
                        status.addIssue("客户端状态未知: " + clientState);
                        System.out.println("⚠️  健康检查：客户端状态未知");
                        break;
                }
                
                // 检查2：服务器信息
                if (client.currentServer() != null) {
                    status.serverAddress = client.currentServer().toString();
                    System.out.println("📡 健康检查：连接服务器 - " + status.serverAddress);
                } else {
                    status.addIssue("未连接到服务器");
                    System.out.println("❌ 健康检查：未连接到服务器");
                }
            }
            
            // 检查3：集群模式状态
            if (ClusterStateManager.isClient()) {
                status.clusterModeEnabled = true;
                System.out.println("✅ 健康检查：集群模式已启用");
            } else {
                status.clusterModeEnabled = false;
                status.addIssue("集群模式未启用");
                System.out.println("❌ 健康检查：集群模式未启用");
            }
            
            // 检查4：配置状态
            String serverHost = ClusterClientConfigManager.getServerHost();
            int serverPort = ClusterClientConfigManager.getServerPort();
            if (serverHost != null && serverPort > 0) {
                status.configValid = true;
                System.out.printf("✅ 健康检查：配置有效 - %s:%d\n", serverHost, serverPort);
            } else {
                status.configValid = false;
                status.addIssue("服务器配置无效");
                System.out.println("❌ 健康检查：服务器配置无效");
            }
            
            // 检查5：请求统计
            long timeSinceLastSuccess = System.currentTimeMillis() - lastClusterSuccessTime.get();
            if (timeSinceLastSuccess > 30000) { // 30秒内没有集群请求成功
                status.addIssue("30秒内无集群请求成功");
                System.out.printf("⚠️  健康检查：%d秒内无集群请求成功\n", timeSinceLastSuccess / 1000);
            }
            
            // 检查6：降级比例
            int totalRequests = clusterRequestCount.get() + localFallbackCount.get();
            if (totalRequests > 0) {
                double fallbackRate = (double) localFallbackCount.get() / totalRequests;
                status.fallbackRate = fallbackRate;
                if (fallbackRate > 0.5) { // 降级比例超过50%
                    status.addIssue("降级比例过高: " + String.format("%.2f%%", fallbackRate * 100));
                    System.out.printf("⚠️  健康检查：降级比例过高 %.2f%%\n", fallbackRate * 100);
                }
            }
            
            return status;
        }
        
        /**
         * 集群健康状态
         */
        public static class ClusterHealthStatus {
            public boolean clientAvailable = false;
            public boolean clusterModeEnabled = false;
            public boolean configValid = false;
            public int clientState = -1;
            public String serverAddress = null;
            public double fallbackRate = 0.0;
            public List<String> issues = new ArrayList<>();
            
            public void addIssue(String issue) {
                issues.add(issue);
            }
            
            public boolean isHealthy() {
                return clientAvailable && clusterModeEnabled && configValid && issues.isEmpty();
            }
            
            public void printStatus() {
                System.out.println("\n📊 集群健康状态报告:");
                System.out.println("客户端可用: " + (clientAvailable ? "✅" : "❌"));
                System.out.println("集群模式: " + (clusterModeEnabled ? "✅" : "❌"));
                System.out.println("配置有效: " + (configValid ? "✅" : "❌"));
                if (serverAddress != null) {
                    System.out.println("服务器地址: " + serverAddress);
                }
                System.out.printf("降级比例: %.2f%%\n", fallbackRate * 100);
                
                if (!issues.isEmpty()) {
                    System.out.println("发现问题:");
                    for (String issue : issues) {
                        System.out.println("  - " + issue);
                    }
                }
                
                System.out.println("整体状态: " + (isHealthy() ? "✅ 健康" : "❌ 异常"));
                System.out.println();
            }
        }
    }
    
    /**
     * 3. Server节点直接监测
     */
    public static class ServerNodeMonitoring {
        
        /**
         * Server实例状态监测
         */
        public boolean checkServerInstance() {
            try {
                // 检查Server实例是否存在
                Object server = getClusterTokenServer();
                if (server == null) {
                    System.out.println("❌ Server监测：Token Server实例未初始化");
                    sendServerAlert("CRITICAL", "Server实例异常", "Token Server未启动");
                    return false;
                }
                
                System.out.println("✅ Server监测：Token Server实例正常");
                return true;
                
            } catch (Exception e) {
                System.out.println("❌ Server监测：实例检查失败 - " + e.getMessage());
                sendServerAlert("ERROR", "Server检查失败", e.getMessage());
                return false;
            }
        }
        
        /**
         * Server端口监测
         */
        public boolean checkServerPort() {
            try {
                // 获取Server配置
                Object config = getServerTransportConfig();
                if (config == null) {
                    System.out.println("❌ Server监测：无法获取Server配置");
                    return false;
                }
                
                // 检查端口配置和监听状态
                int port = getServerPort(config);
                if (port <= 0) {
                    System.out.println("❌ Server监测：端口配置无效");
                    sendServerAlert("ERROR", "端口配置无效", "端口: " + port);
                    return false;
                }
                
                // 检查端口是否在监听
                boolean listening = isPortListening("127.0.0.1", port);
                if (!listening) {
                    System.out.println("❌ Server监测：端口监听异常 - " + port);
                    sendServerAlert("CRITICAL", "端口监听异常", "端口: " + port);
                    return false;
                }
                
                System.out.println("✅ Server监测：端口监听正常 - " + port);
                return true;
                
            } catch (Exception e) {
                System.out.println("❌ Server监测：端口检查失败 - " + e.getMessage());
                sendServerAlert("ERROR", "端口检查失败", e.getMessage());
                return false;
            }
        }
        
        /**
         * Server资源监测
         */
        public boolean checkServerResources() {
            try {
                // 内存使用检查
                var memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean();
                long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
                long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
                double memoryUsage = (double) usedMemory / maxMemory;
                
                System.out.printf("📊 Server监测：内存使用率 %.2f%%\n", memoryUsage * 100);
                
                if (memoryUsage > 0.9) {
                    System.out.println("❌ Server监测：内存使用率过高");
                    sendServerAlert("CRITICAL", "内存使用率过高", 
                        String.format("%.2f%%", memoryUsage * 100));
                    return false;
                } else if (memoryUsage > 0.8) {
                    System.out.println("⚠️  Server监测：内存使用率较高");
                    sendServerAlert("WARNING", "内存使用率较高", 
                        String.format("%.2f%%", memoryUsage * 100));
                }
                
                // 线程数检查
                int threadCount = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
                System.out.println("📊 Server监测：线程数量 " + threadCount);
                
                if (threadCount > 1000) {
                    System.out.println("⚠️  Server监测：线程数过多");
                    sendServerAlert("WARNING", "线程数过多", "线程数: " + threadCount);
                }
                
                return true;
                
            } catch (Exception e) {
                System.out.println("❌ Server监测：资源检查失败 - " + e.getMessage());
                sendServerAlert("ERROR", "资源检查失败", e.getMessage());
                return false;
            }
        }
        
        /**
         * 综合Server健康检查
         */
        public ServerHealthResult performServerHealthCheck() {
            System.out.println("\n🔍 执行Server节点健康检查...");
            
            ServerHealthResult result = new ServerHealthResult();
            
            // 执行各项检查
            result.instanceCheck = checkServerInstance();
            result.portCheck = checkServerPort();
            result.resourceCheck = checkServerResources();
            
            // 计算综合健康状态
            int healthScore = 0;
            if (result.instanceCheck) healthScore += 34;
            if (result.portCheck) healthScore += 33;
            if (result.resourceCheck) healthScore += 33;
            
            result.healthScore = healthScore;
            result.healthy = healthScore >= 75;
            
            System.out.printf("📊 Server健康检查完成 - 分数: %d/100, 状态: %s\n", 
                healthScore, result.healthy ? "健康" : "异常");
            
            if (!result.healthy) {
                sendServerAlert("ERROR", "Server健康检查失败", 
                    "健康分数: " + healthScore + "/100");
            }
            
            return result;
        }
        
        // 辅助方法
        private Object getClusterTokenServer() {
            try {
                // 使用反射获取Server实例，避免编译依赖
                Class<?> providerClass = Class.forName("com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider");
                java.lang.reflect.Method getServerMethod = providerClass.getMethod("getServer");
                return getServerMethod.invoke(null);
            } catch (Exception e) {
                return null;
            }
        }
        
        private Object getServerTransportConfig() {
            try {
                Class<?> configClass = Class.forName("com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager");
                java.lang.reflect.Method getConfigMethod = configClass.getMethod("getTransportConfig");
                return getConfigMethod.invoke(null);
            } catch (Exception e) {
                return null;
            }
        }
        
        private int getServerPort(Object config) {
            try {
                java.lang.reflect.Method getPortMethod = config.getClass().getMethod("getPort");
                return (Integer) getPortMethod.invoke(config);
            } catch (Exception e) {
                return -1;
            }
        }
        
        private boolean isPortListening(String host, int port) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 3000);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        private void sendServerAlert(String level, String title, String message) {
            String alertMessage = String.format("[%s] Server异常: %s - %s", level, title, message);
            System.out.println("🚨 " + alertMessage);
            
            // 这里可以集成实际的告警系统
            switch (level) {
                case "CRITICAL":
                    System.out.println("📱 发送Server紧急告警");
                    break;
                case "ERROR":
                    System.out.println("📧 发送Server错误告警");
                    break;
                case "WARNING":
                    System.out.println("⚠️  发送Server警告通知");
                    break;
            }
        }
        
        /**
         * Server健康检查结果
         */
        public static class ServerHealthResult {
            public boolean instanceCheck = false;
            public boolean portCheck = false;
            public boolean resourceCheck = false;
            public int healthScore = 0;
            public boolean healthy = false;
            
            public void printReport() {
                System.out.println("\n=== Server健康检查报告 ===");
                System.out.println("实例检查: " + (instanceCheck ? "✅ 通过" : "❌ 失败"));
                System.out.println("端口检查: " + (portCheck ? "✅ 通过" : "❌ 失败"));
                System.out.println("资源检查: " + (resourceCheck ? "✅ 通过" : "❌ 失败"));
                System.out.println("综合评分: " + healthScore + "/100");
                System.out.println("健康状态: " + (healthy ? "✅ 健康" : "❌ 异常"));
                System.out.println("========================\n");
            }
        }
    }
    
    /**
     * 4. 异常告警机制
     */
    public static class ExceptionAlertSystem {
        
        private final ScheduledExecutorService alertScheduler = Executors.newScheduledThreadPool(1);
        private volatile boolean alertEnabled = true;
        
        public void startAlerting() {
            alertScheduler.scheduleWithFixedDelay(() -> {
                try {
                    checkAndAlert();
                } catch (Exception e) {
                    System.err.println("告警检查异常: " + e.getMessage());
                }
            }, 0, 10, TimeUnit.SECONDS);
        }
        
        private void checkAndAlert() {
            if (!alertEnabled) return;
            
            ActiveHealthCheck healthChecker = new ActiveHealthCheck();
            ActiveHealthCheck.ClusterHealthStatus status = healthChecker.checkClusterHealth();
            
            // 告警条件1：客户端不可用
            if (!status.clientAvailable) {
                sendAlert("CRITICAL", "集群客户端不可用", "客户端状态异常，请检查连接");
            }
            
            // 告警条件2：降级比例过高
            if (status.fallbackRate > 0.3) { // 30%
                sendAlert("WARNING", "降级比例过高", 
                    String.format("当前降级比例: %.2f%%", status.fallbackRate * 100));
            }
            
            // 告警条件3：长时间无集群请求成功
            long timeSinceLastSuccess = System.currentTimeMillis() - lastClusterSuccessTime.get();
            if (timeSinceLastSuccess > 60000) { // 1分钟
                sendAlert("ERROR", "集群服务长时间无响应", 
                    String.format("已有%d秒无集群请求成功", timeSinceLastSuccess / 1000));
            }
            
            // 告警条件4：配置异常
            if (!status.configValid) {
                sendAlert("ERROR", "集群配置异常", "服务器配置无效，请检查配置文件");
            }
        }
        
        private void sendAlert(String level, String title, String message) {
            String alertMessage = String.format("[%s] %s: %s", level, title, message);
            System.out.println("🚨 " + alertMessage);
            
            // 这里可以集成实际的告警系统
            // 例如：发送邮件、短信、钉钉、企业微信等
            switch (level) {
                case "CRITICAL":
                    // 发送紧急告警
                    System.out.println("📱 发送紧急告警通知");
                    break;
                case "ERROR":
                    // 发送错误告警
                    System.out.println("📧 发送错误告警邮件");
                    break;
                case "WARNING":
                    // 发送警告通知
                    System.out.println("⚠️  发送警告通知");
                    break;
            }
        }
        
        public void stopAlerting() {
            alertEnabled = false;
            alertScheduler.shutdown();
        }
    }
    
    /**
     * 4. 监控指标收集
     */
    public static class MetricsCollector {
        
        public void collectAndReportMetrics() {
            System.out.println("\n📈 监控指标报告:");
            
            // 请求分布统计
            int totalRequests = clusterRequestCount.get() + localFallbackCount.get();
            if (totalRequests > 0) {
                double clusterRatio = (double) clusterRequestCount.get() / totalRequests;
                double fallbackRatio = (double) localFallbackCount.get() / totalRequests;
                
                System.out.printf("总请求数: %d\n", totalRequests);
                System.out.printf("集群请求: %d (%.2f%%)\n", 
                    clusterRequestCount.get(), clusterRatio * 100);
                System.out.printf("本地降级: %d (%.2f%%)\n", 
                    localFallbackCount.get(), fallbackRatio * 100);
                
                // 异常判断
                if (fallbackRatio > 0.5) {
                    System.out.println("⚠️  指标异常：降级比例过高，建议检查集群服务");
                }
            }
            
            // 时间统计
            long timeSinceLastSuccess = System.currentTimeMillis() - lastClusterSuccessTime.get();
            System.out.printf("距离上次集群成功: %d秒\n", timeSinceLastSuccess / 1000);
            
            if (timeSinceLastSuccess > 30000) {
                System.out.println("⚠️  指标异常：长时间无集群请求成功");
            }
            
            // 连接状态
            ClusterTokenClient client = TokenClientProvider.getClient();
            if (client != null) {
                System.out.printf("客户端状态: %s\n", getClientStateText(client.getState()));
                if (client.currentServer() != null) {
                    System.out.printf("连接服务器: %s\n", client.currentServer());
                }
            }
            
            System.out.println();
        }
        
        private String getClientStateText(int state) {
            switch (state) {
                case 0: return "已关闭 ❌";
                case 1: return "正常运行 ✅";
                default: return "未知状态 ❓";
            }
        }
    }
    
    /**
     * 初始化集群配置
     */
    private static void initClusterConfiguration() {
        // 配置集群规则
        List<FlowRule> rules = new ArrayList<>();
        
        FlowRule rule = new FlowRule();
        rule.setResource("api:user:query");
        rule.setCount(10); // 集群限制10 QPS
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setClusterMode(true);
        
        ClusterFlowConfig clusterConfig = new ClusterFlowConfig();
        clusterConfig.setFlowId(1001L);
        clusterConfig.setFallbackToLocalWhenFail(true); // 启用降级
        rule.setClusterConfig(clusterConfig);
        rules.add(rule);
        
        // 本地兜底规则
        FlowRule localRule = new FlowRule();
        localRule.setResource("api:user:query");
        localRule.setCount(8); // 本地限制8 QPS
        localRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        localRule.setClusterMode(false);
        rules.add(localRule);
        
        FlowRuleManager.loadRules(rules);
        
        // 启用集群模式
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);
        
        System.out.println("✅ 集群限流配置初始化完成");
    }
    
    /**
     * 启动异常监控
     */
    private static void startExceptionMonitoring() {
        ExceptionAlertSystem alertSystem = new ExceptionAlertSystem();
        alertSystem.startAlerting();
        
        MetricsCollector metricsCollector = new MetricsCollector();
        ScheduledExecutorService metricsScheduler = Executors.newScheduledThreadPool(1);
        metricsScheduler.scheduleWithFixedDelay(() -> {
            metricsCollector.collectAndReportMetrics();
        }, 0, 15, TimeUnit.SECONDS);
        
        System.out.println("✅ 异常监控启动完成");
    }
    
    /**
     * 演示Server节点监测
     */
    private static void demonstrateServerMonitoring() {
        System.out.println("\n=== Server节点监测演示 ===");
        
        ServerNodeMonitoring serverMonitoring = new ServerNodeMonitoring();
        
        // 启动定期Server健康检查
        ScheduledExecutorService serverScheduler = Executors.newScheduledThreadPool(1);
        serverScheduler.scheduleWithFixedDelay(() -> {
            try {
                ServerNodeMonitoring.ServerHealthResult result = serverMonitoring.performServerHealthCheck();
                result.printReport();
            } catch (Exception e) {
                System.err.println("Server健康检查异常: " + e.getMessage());
            }
        }, 5, 30, TimeUnit.SECONDS);
        
        System.out.println("✅ Server节点监测已启动");
    }
    
    /**
     * 模拟业务请求
     */
    private static void simulateBusinessRequests() {
        BusinessLevelDetection businessDetection = new BusinessLevelDetection();
        ActiveHealthCheck healthCheck = new ActiveHealthCheck();
        
        ScheduledExecutorService requestSimulator = Executors.newScheduledThreadPool(2);
        
        // 模拟用户请求
        requestSimulator.scheduleWithFixedDelay(() -> {
            businessDetection.processUserRequest("user123");
        }, 1, 2, TimeUnit.SECONDS);
        
        // 定期健康检查
        requestSimulator.scheduleWithFixedDelay(() -> {
            ActiveHealthCheck.ClusterHealthStatus status = healthCheck.checkClusterHealth();
            status.printStatus();
        }, 10, 20, TimeUnit.SECONDS);
        
        System.out.println("✅ 业务请求模拟启动完成");
    }
} 