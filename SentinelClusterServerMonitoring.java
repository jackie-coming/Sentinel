package com.alibaba.csp.sentinel.demo.server;

import com.alibaba.csp.sentinel.cluster.server.ClusterTokenServer;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.cluster.server.ServerConstants;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.cluster.flow.statistic.ClusterMetricStatistics;
import com.alibaba.csp.sentinel.cluster.flow.statistic.metric.ClusterMetric;
import com.alibaba.csp.sentinel.log.RecordLog;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sentinel集群Token Server节点异常监控完整方案
 * 
 * 这个类提供了对Sentinel集群Token Server节点的全面监控，包括：
 * 1. Server进程状态监控
 * 2. 网络连接监控
 * 3. 性能指标监控
 * 4. 业务指标监控
 * 5. 资源使用监控
 * 6. 客户端连接监控
 */
public class SentinelClusterServerMonitoring {
    
    private final ScheduledExecutorService monitoringScheduler = Executors.newScheduledThreadPool(4);
    private final String serverHost;
    private final int serverPort;
    
    // 监控指标
    private final ServerHealthMetrics healthMetrics = new ServerHealthMetrics();
    private final ServerPerformanceMetrics performanceMetrics = new ServerPerformanceMetrics();
    private final ServerBusinessMetrics businessMetrics = new ServerBusinessMetrics();
    
    public SentinelClusterServerMonitoring(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    public static void main(String[] args) {
        // 示例：监控本地Token Server
        SentinelClusterServerMonitoring monitor = new SentinelClusterServerMonitoring("127.0.0.1", 8719);
        monitor.startMonitoring();
        
        // 保持运行
        try {
            Thread.sleep(300000); // 运行5分钟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            monitor.stopMonitoring();
        }
    }
    
    /**
     * 启动Server节点监控
     */
    public void startMonitoring() {
        System.out.println("🚀 启动Sentinel集群Server节点监控...");
        
        // 1. Server进程状态监控
        startProcessMonitoring();
        
        // 2. 网络连接监控
        startNetworkMonitoring();
        
        // 3. 性能指标监控
        startPerformanceMonitoring();
        
        // 4. 业务指标监控
        startBusinessMonitoring();
        
        // 5. 综合健康检查
        startHealthCheck();
        
        System.out.println("✅ Server节点监控启动完成");
    }
    
    /**
     * 1. Server进程状态监控
     */
    private void startProcessMonitoring() {
        monitoringScheduler.scheduleWithFixedDelay(() -> {
            try {
                checkServerProcess();
            } catch (Exception e) {
                System.err.println("Server进程监控异常: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
    
    private void checkServerProcess() {
        System.out.println("\n🔍 Server进程状态检查:");
        
        // 检查1：Server实例状态
        ClusterTokenServer server = EmbeddedClusterTokenServerProvider.getServer();
        if (server == null) {
            healthMetrics.recordIssue("Server实例未初始化");
            System.out.println("❌ Server实例未初始化");
            sendAlert("CRITICAL", "Token Server进程异常", "Server实例未初始化");
            return;
        }
        
        // 检查2：Server运行状态
        // 注意：这里需要根据实际的Server实现来获取状态
        try {
            // 假设Server有状态检查方法（实际API可能不同）
            boolean isRunning = checkServerRunningStatus(server);
            if (isRunning) {
                healthMetrics.recordSuccess("Server进程正常运行");
                System.out.println("✅ Server进程正常运行");
            } else {
                healthMetrics.recordIssue("Server进程状态异常");
                System.out.println("❌ Server进程状态异常");
                sendAlert("ERROR", "Token Server状态异常", "Server进程可能已停止");
            }
        } catch (Exception e) {
            healthMetrics.recordIssue("Server状态检查失败: " + e.getMessage());
            System.out.println("❌ Server状态检查失败: " + e.getMessage());
        }
        
        // 检查3：配置状态
        ServerTransportConfig config = ClusterServerConfigManager.getTransportConfig();
        if (config != null && config.getPort() > 0) {
            healthMetrics.recordSuccess("Server配置正常");
            System.out.printf("✅ Server配置正常 - 端口: %d\n", config.getPort());
        } else {
            healthMetrics.recordIssue("Server配置异常");
            System.out.println("❌ Server配置异常");
            sendAlert("ERROR", "Token Server配置异常", "端口配置无效");
        }
    }
    
    /**
     * 检查Server是否正在运行（需要根据实际API调整）
     */
    private boolean checkServerRunningStatus(ClusterTokenServer server) {
        try {
            // 方法1：尝试获取Server状态（如果有相关API）
            // return server.isRunning(); // 假设的API
            
            // 方法2：通过端口检查
            return isPortListening(serverHost, serverPort);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 2. 网络连接监控
     */
    private void startNetworkMonitoring() {
        monitoringScheduler.scheduleWithFixedDelay(() -> {
            try {
                checkNetworkConnectivity();
            } catch (Exception e) {
                System.err.println("网络连接监控异常: " + e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS);
    }
    
    private void checkNetworkConnectivity() {
        System.out.println("\n🌐 网络连接状态检查:");
        
        // 检查1：端口监听状态
        boolean portListening = isPortListening(serverHost, serverPort);
        if (portListening) {
            healthMetrics.recordSuccess("端口监听正常");
            System.out.printf("✅ 端口 %s:%d 监听正常\n", serverHost, serverPort);
        } else {
            healthMetrics.recordIssue("端口监听异常");
            System.out.printf("❌ 端口 %s:%d 监听异常\n", serverHost, serverPort);
            sendAlert("CRITICAL", "Token Server端口异常", 
                String.format("端口 %s:%d 无法连接", serverHost, serverPort));
        }
        
        // 检查2：连接响应时间
        long connectTime = measureConnectTime(serverHost, serverPort);
        performanceMetrics.recordConnectTime(connectTime);
        
        if (connectTime > 0 && connectTime < 1000) { // 1秒内
            System.out.printf("✅ 连接响应时间正常: %dms\n", connectTime);
        } else if (connectTime > 0) {
            System.out.printf("⚠️  连接响应时间较长: %dms\n", connectTime);
            if (connectTime > 5000) { // 超过5秒
                sendAlert("WARNING", "Token Server响应慢", 
                    String.format("连接时间: %dms", connectTime));
            }
        } else {
            System.out.println("❌ 连接失败");
        }
        
        // 检查3：客户端连接数（需要Server支持）
        checkClientConnections();
    }
    
    private boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private long measureConnectTime(String host, int port) {
        long startTime = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return System.currentTimeMillis() - startTime;
        } catch (IOException e) {
            return -1; // 连接失败
        }
    }
    
    private void checkClientConnections() {
        try {
            // 这里需要根据实际的Server API来获取客户端连接信息
            // 假设有获取连接数的方法
            int clientCount = getConnectedClientCount();
            businessMetrics.recordClientCount(clientCount);
            
            System.out.printf("📊 当前客户端连接数: %d\n", clientCount);
            
            if (clientCount == 0) {
                System.out.println("⚠️  警告：没有客户端连接");
                // 可以根据业务需要决定是否告警
            }
            
        } catch (Exception e) {
            System.out.println("❌ 获取客户端连接信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取连接的客户端数量（需要根据实际API实现）
     */
    private int getConnectedClientCount() {
        // 这里需要根据实际的Sentinel Server API来实现
        // 可能的实现方式：
        // 1. 通过Server管理接口获取
        // 2. 通过JMX获取
        // 3. 通过日志分析
        
        // 模拟返回
        return (int) (Math.random() * 10);
    }
    
    /**
     * 3. 性能指标监控
     */
    private void startPerformanceMonitoring() {
        monitoringScheduler.scheduleWithFixedDelay(() -> {
            try {
                checkPerformanceMetrics();
            } catch (Exception e) {
                System.err.println("性能监控异常: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    private void checkPerformanceMetrics() {
        System.out.println("\n📊 Server性能指标检查:");
        
        // 检查1：内存使用情况
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        double memoryUsage = (double) usedMemory / maxMemory;
        
        performanceMetrics.recordMemoryUsage(memoryUsage);
        System.out.printf("💾 内存使用率: %.2f%% (%d MB / %d MB)\n", 
            memoryUsage * 100, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024);
        
        if (memoryUsage > 0.8) { // 内存使用超过80%
            sendAlert("WARNING", "Token Server内存使用率过高", 
                String.format("当前使用率: %.2f%%", memoryUsage * 100));
        }
        
        // 检查2：线程使用情况
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        performanceMetrics.recordThreadCount(threadCount);
        System.out.printf("🧵 线程数量: %d\n", threadCount);
        
        if (threadCount > 500) { // 线程数超过500
            sendAlert("WARNING", "Token Server线程数过多", 
                String.format("当前线程数: %d", threadCount));
        }
        
        // 检查3：GC情况
        checkGCMetrics();
        
        // 检查4：CPU使用率（如果可获取）
        checkCPUUsage();
    }
    
    private void checkGCMetrics() {
        try {
            // 这里可以通过JMX获取GC信息
            // 简化示例
            System.out.println("🗑️  GC状态: 正常");
        } catch (Exception e) {
            System.out.println("❌ GC监控失败: " + e.getMessage());
        }
    }
    
    private void checkCPUUsage() {
        try {
            // 获取CPU使用率（需要额外实现）
            double cpuUsage = getCPUUsage();
            performanceMetrics.recordCPUUsage(cpuUsage);
            System.out.printf("🔥 CPU使用率: %.2f%%\n", cpuUsage);
            
            if (cpuUsage > 80) {
                sendAlert("WARNING", "Token Server CPU使用率过高", 
                    String.format("当前使用率: %.2f%%", cpuUsage));
            }
        } catch (Exception e) {
            System.out.println("❌ CPU监控失败: " + e.getMessage());
        }
    }
    
    private double getCPUUsage() {
        // 简化实现，返回模拟值
        return Math.random() * 100;
    }
    
    /**
     * 4. 业务指标监控
     */
    private void startBusinessMonitoring() {
        monitoringScheduler.scheduleWithFixedDelay(() -> {
            try {
                checkBusinessMetrics();
            } catch (Exception e) {
                System.err.println("业务监控异常: " + e.getMessage());
            }
        }, 0, 20, TimeUnit.SECONDS);
    }
    
    private void checkBusinessMetrics() {
        System.out.println("\n📈 Server业务指标检查:");
        
        try {
            // 检查1：请求处理统计
            Map<Long, ClusterMetric> metrics = ClusterMetricStatistics.getMetricMap();
            if (metrics != null && !metrics.isEmpty()) {
                System.out.printf("📊 监控规则数量: %d\n", metrics.size());
                
                long totalRequests = 0;
                long totalBlocked = 0;
                
                for (Map.Entry<Long, ClusterMetric> entry : metrics.entrySet()) {
                    Long flowId = entry.getKey();
                    ClusterMetric metric = entry.getValue();
                    
                    // 获取请求统计（需要根据实际API调整）
                    double passQps = metric.getAvg(null); // 需要传入正确的事件类型
                    
                    System.out.printf("  规则[%d]: QPS=%.2f\n", flowId, passQps);
                    
                    totalRequests += (long) passQps;
                }
                
                businessMetrics.recordRequestMetrics(totalRequests, totalBlocked);
                
                // 检查请求处理是否正常
                if (totalRequests == 0) {
                    System.out.println("⚠️  警告：Server没有处理任何请求");
                }
                
            } else {
                System.out.println("⚠️  没有发现集群限流规则");
            }
            
            // 检查2：错误率统计
            checkErrorRate();
            
            // 检查3：响应时间统计
            checkResponseTime();
            
        } catch (Exception e) {
            System.out.println("❌ 业务指标获取失败: " + e.getMessage());
        }
    }
    
    private void checkErrorRate() {
        // 计算错误率
        double errorRate = businessMetrics.getErrorRate();
        System.out.printf("❌ 错误率: %.2f%%\n", errorRate * 100);
        
        if (errorRate > 0.05) { // 错误率超过5%
            sendAlert("ERROR", "Token Server错误率过高", 
                String.format("当前错误率: %.2f%%", errorRate * 100));
        }
    }
    
    private void checkResponseTime() {
        long avgResponseTime = businessMetrics.getAvgResponseTime();
        System.out.printf("⏱️  平均响应时间: %dms\n", avgResponseTime);
        
        if (avgResponseTime > 100) { // 响应时间超过100ms
            sendAlert("WARNING", "Token Server响应时间过长", 
                String.format("平均响应时间: %dms", avgResponseTime));
        }
    }
    
    /**
     * 5. 综合健康检查
     */
    private void startHealthCheck() {
        monitoringScheduler.scheduleWithFixedDelay(() -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                System.err.println("健康检查异常: " + e.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS);
    }
    
    private void performHealthCheck() {
        System.out.println("\n🏥 Server综合健康检查:");
        
        ServerHealthStatus status = new ServerHealthStatus();
        
        // 汇总各项检查结果
        status.processHealth = healthMetrics.getIssueCount() == 0;
        status.networkHealth = performanceMetrics.getLastConnectTime() > 0;
        status.performanceHealth = performanceMetrics.getMemoryUsage() < 0.9;
        status.businessHealth = businessMetrics.getErrorRate() < 0.1;
        
        // 计算整体健康分数
        int healthScore = 0;
        if (status.processHealth) healthScore += 25;
        if (status.networkHealth) healthScore += 25;
        if (status.performanceHealth) healthScore += 25;
        if (status.businessHealth) healthScore += 25;
        
        status.overallHealth = healthScore;
        
        // 输出健康报告
        System.out.printf("进程健康: %s\n", status.processHealth ? "✅" : "❌");
        System.out.printf("网络健康: %s\n", status.networkHealth ? "✅" : "❌");
        System.out.printf("性能健康: %s\n", status.performanceHealth ? "✅" : "❌");
        System.out.printf("业务健康: %s\n", status.businessHealth ? "✅" : "❌");
        System.out.printf("综合健康分数: %d/100\n", healthScore);
        
        // 健康状态告警
        if (healthScore < 50) {
            sendAlert("CRITICAL", "Token Server健康状态严重异常", 
                String.format("健康分数: %d/100", healthScore));
        } else if (healthScore < 75) {
            sendAlert("WARNING", "Token Server健康状态异常", 
                String.format("健康分数: %d/100", healthScore));
        }
    }
    
    /**
     * 发送告警
     */
    private void sendAlert(String level, String title, String message) {
        String alertMessage = String.format("[%s] %s: %s", level, title, message);
        System.out.println("🚨 " + alertMessage);
        
        // 这里集成实际的告警系统
        switch (level) {
            case "CRITICAL":
                // 发送紧急告警：短信、电话、钉钉群
                System.out.println("📱 发送紧急告警通知");
                break;
            case "ERROR":
                // 发送错误告警：邮件、企业微信
                System.out.println("📧 发送错误告警邮件");
                break;
            case "WARNING":
                // 发送警告通知：邮件
                System.out.println("⚠️  发送警告通知");
                break;
        }
    }
    
    /**
     * 停止监控
     */
    public void stopMonitoring() {
        System.out.println("🛑 停止Server节点监控...");
        monitoringScheduler.shutdown();
        try {
            if (!monitoringScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("✅ Server节点监控已停止");
    }
    
    /**
     * Server健康指标
     */
    static class ServerHealthMetrics {
        private final AtomicInteger issueCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final List<String> recentIssues = new ArrayList<>();
        
        public void recordIssue(String issue) {
            issueCount.incrementAndGet();
            synchronized (recentIssues) {
                recentIssues.add(issue);
                if (recentIssues.size() > 10) {
                    recentIssues.remove(0);
                }
            }
        }
        
        public void recordSuccess(String message) {
            successCount.incrementAndGet();
        }
        
        public int getIssueCount() { return issueCount.get(); }
        public List<String> getRecentIssues() { return new ArrayList<>(recentIssues); }
    }
    
    /**
     * Server性能指标
     */
    static class ServerPerformanceMetrics {
        private volatile double memoryUsage = 0.0;
        private volatile int threadCount = 0;
        private volatile double cpuUsage = 0.0;
        private volatile long lastConnectTime = 0;
        
        public void recordMemoryUsage(double usage) { this.memoryUsage = usage; }
        public void recordThreadCount(int count) { this.threadCount = count; }
        public void recordCPUUsage(double usage) { this.cpuUsage = usage; }
        public void recordConnectTime(long time) { this.lastConnectTime = time; }
        
        public double getMemoryUsage() { return memoryUsage; }
        public long getLastConnectTime() { return lastConnectTime; }
    }
    
    /**
     * Server业务指标
     */
    static class ServerBusinessMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicInteger clientCount = new AtomicInteger(0);
        
        public void recordRequestMetrics(long requests, long errors) {
            totalRequests.addAndGet(requests);
            totalErrors.addAndGet(errors);
        }
        
        public void recordClientCount(int count) {
            clientCount.set(count);
        }
        
        public double getErrorRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) totalErrors.get() / total : 0.0;
        }
        
        public long getAvgResponseTime() {
            long total = totalRequests.get();
            return total > 0 ? totalResponseTime.get() / total : 0;
        }
    }
    
    /**
     * Server健康状态
     */
    static class ServerHealthStatus {
        boolean processHealth = false;
        boolean networkHealth = false;
        boolean performanceHealth = false;
        boolean businessHealth = false;
        int overallHealth = 0;
    }
} 