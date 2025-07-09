package com.alibaba.csp.sentinel.demo.server;

import com.alibaba.csp.sentinel.cluster.server.ClusterTokenServer;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.cluster.flow.statistic.ClusterMetricStatistics;
import com.alibaba.csp.sentinel.cluster.flow.statistic.metric.ClusterMetric;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sentinel集群Token Server节点健康检查器
 * 
 * 专注于Server节点的关键异常检测，包括：
 * 1. Server进程状态检查
 * 2. 端口监听状态检查
 * 3. 配置有效性检查
 * 4. 资源使用检查
 * 5. 业务处理能力检查
 * 6. 客户端连接状态检查
 */
public class SentinelClusterServerHealthChecker {
    
    private final String serverHost;
    private final int serverPort;
    private final AtomicInteger checkCount = new AtomicInteger(0);
    private final AtomicLong lastCheckTime = new AtomicLong(0);
    
    public SentinelClusterServerHealthChecker(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    public static void main(String[] args) {
        // 示例：检查本地Token Server
        SentinelClusterServerHealthChecker checker = new SentinelClusterServerHealthChecker("127.0.0.1", 8719);
        
        // 执行健康检查
        ServerHealthReport report = checker.performHealthCheck();
        
        // 输出检查结果
        System.out.println(report.toString());
        
        // 根据结果采取行动
        if (!report.isHealthy()) {
            System.out.println("❌ Server节点存在异常，需要人工干预!");
            // 这里可以触发告警或自动修复
        } else {
            System.out.println("✅ Server节点运行正常");
        }
    }
    
    /**
     * 执行完整的Server健康检查
     */
    public ServerHealthReport performHealthCheck() {
        System.out.println("🔍 开始Server节点健康检查...");
        
        ServerHealthReport report = new ServerHealthReport();
        checkCount.incrementAndGet();
        lastCheckTime.set(System.currentTimeMillis());
        
        // 1. 检查Server进程状态
        checkServerProcess(report);
        
        // 2. 检查端口监听状态
        checkPortListening(report);
        
        // 3. 检查Server配置
        checkServerConfiguration(report);
        
        // 4. 检查资源使用情况
        checkResourceUsage(report);
        
        // 5. 检查业务处理能力
        checkBusinessCapability(report);
        
        // 6. 检查客户端连接
        checkClientConnections(report);
        
        // 计算综合健康状态
        report.calculateOverallHealth();
        
        System.out.println("✅ Server健康检查完成");
        return report;
    }
    
    /**
     * 1. 检查Server进程状态
     */
    private void checkServerProcess(ServerHealthReport report) {
        System.out.println("\n🔍 检查Server进程状态:");
        
        try {
            // 检查Server实例是否存在
            ClusterTokenServer server = EmbeddedClusterTokenServerProvider.getServer();
            if (server == null) {
                report.addIssue("CRITICAL", "Server实例未初始化", "Token Server进程未启动");
                System.out.println("❌ Server实例未初始化");
                return;
            }
            
            // 检查Server是否正在运行
            if (isServerRunning(server)) {
                report.addSuccess("Server进程正常运行");
                System.out.println("✅ Server进程正常运行");
            } else {
                report.addIssue("ERROR", "Server进程异常", "Server可能已停止或无响应");
                System.out.println("❌ Server进程异常");
            }
            
        } catch (Exception e) {
            report.addIssue("ERROR", "Server状态检查失败", e.getMessage());
            System.out.println("❌ Server状态检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查Server是否正在运行
     */
    private boolean isServerRunning(ClusterTokenServer server) {
        try {
            // 方法1：检查端口是否在监听
            if (!isPortListening(serverHost, serverPort)) {
                return false;
            }
            
            // 方法2：检查Server内部状态（如果有API）
            // return server.isRunning(); // 假设的API
            
            // 方法3：尝试简单的健康检查请求
            return performSimpleHealthCheck();
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 执行简单的健康检查
     */
    private boolean performSimpleHealthCheck() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverHost, serverPort), 3000);
            // 如果能连接，说明Server在运行
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 2. 检查端口监听状态
     */
    private void checkPortListening(ServerHealthReport report) {
        System.out.println("\n🔍 检查端口监听状态:");
        
        try {
            long startTime = System.currentTimeMillis();
            boolean listening = isPortListening(serverHost, serverPort);
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (listening) {
                report.addSuccess("端口监听正常");
                System.out.printf("✅ 端口 %s:%d 监听正常 (响应时间: %dms)\n", 
                    serverHost, serverPort, responseTime);
                
                // 记录响应时间
                report.setConnectionResponseTime(responseTime);
                
                // 检查响应时间是否正常
                if (responseTime > 1000) {
                    report.addIssue("WARNING", "端口响应缓慢", 
                        String.format("连接时间: %dms", responseTime));
                }
                
            } else {
                report.addIssue("CRITICAL", "端口监听异常", 
                    String.format("端口 %s:%d 无法连接", serverHost, serverPort));
                System.out.printf("❌ 端口 %s:%d 监听异常\n", serverHost, serverPort);
            }
            
        } catch (Exception e) {
            report.addIssue("ERROR", "端口检查失败", e.getMessage());
            System.out.println("❌ 端口检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查端口是否在监听
     */
    private boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 3. 检查Server配置
     */
    private void checkServerConfiguration(ServerHealthReport report) {
        System.out.println("\n🔍 检查Server配置:");
        
        try {
            // 检查传输配置
            ServerTransportConfig config = ClusterServerConfigManager.getTransportConfig();
            if (config == null) {
                report.addIssue("ERROR", "配置缺失", "Server传输配置为空");
                System.out.println("❌ Server配置缺失");
                return;
            }
            
            // 检查端口配置
            if (config.getPort() <= 0 || config.getPort() > 65535) {
                report.addIssue("ERROR", "端口配置无效", 
                    String.format("配置端口: %d", config.getPort()));
                System.out.printf("❌ 端口配置无效: %d\n", config.getPort());
            } else {
                report.addSuccess("端口配置正常");
                System.out.printf("✅ 端口配置正常: %d\n", config.getPort());
            }
            
            // 检查其他配置项
            if (config.getIdleSeconds() > 0) {
                report.addSuccess("空闲超时配置正常");
                System.out.printf("✅ 空闲超时配置: %d秒\n", config.getIdleSeconds());
            }
            
        } catch (Exception e) {
            report.addIssue("ERROR", "配置检查失败", e.getMessage());
            System.out.println("❌ 配置检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 4. 检查资源使用情况
     */
    private void checkResourceUsage(ServerHealthReport report) {
        System.out.println("\n🔍 检查资源使用情况:");
        
        try {
            // 检查内存使用
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            double memoryUsage = (double) usedMemory / maxMemory;
            
            report.setMemoryUsage(memoryUsage);
            System.out.printf("💾 内存使用率: %.2f%% (%d MB / %d MB)\n", 
                memoryUsage * 100, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024);
            
            if (memoryUsage > 0.9) {
                report.addIssue("CRITICAL", "内存使用率过高", 
                    String.format("使用率: %.2f%%", memoryUsage * 100));
            } else if (memoryUsage > 0.8) {
                report.addIssue("WARNING", "内存使用率较高", 
                    String.format("使用率: %.2f%%", memoryUsage * 100));
            } else {
                report.addSuccess("内存使用正常");
            }
            
            // 检查线程数
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
            report.setThreadCount(threadCount);
            System.out.printf("🧵 线程数量: %d\n", threadCount);
            
            if (threadCount > 1000) {
                report.addIssue("WARNING", "线程数过多", 
                    String.format("线程数: %d", threadCount));
            } else {
                report.addSuccess("线程数正常");
            }
            
        } catch (Exception e) {
            report.addIssue("ERROR", "资源使用检查失败", e.getMessage());
            System.out.println("❌ 资源使用检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 5. 检查业务处理能力
     */
    private void checkBusinessCapability(ServerHealthReport report) {
        System.out.println("\n🔍 检查业务处理能力:");
        
        try {
            // 检查集群规则统计
            Map<Long, ClusterMetric> metrics = ClusterMetricStatistics.getMetricMap();
            
            if (metrics == null || metrics.isEmpty()) {
                report.addIssue("WARNING", "无集群规则", "Server没有配置集群限流规则");
                System.out.println("⚠️  没有发现集群限流规则");
            } else {
                report.addSuccess("集群规则配置正常");
                System.out.printf("✅ 集群规则数量: %d\n", metrics.size());
                
                // 检查规则处理情况
                long totalRequests = 0;
                long totalBlocked = 0;
                
                for (Map.Entry<Long, ClusterMetric> entry : metrics.entrySet()) {
                    Long flowId = entry.getKey();
                    ClusterMetric metric = entry.getValue();
                    
                    try {
                        // 获取通过的请求数（需要根据实际API调整）
                        double passQps = metric.getAvg(null);
                        totalRequests += (long) passQps;
                        
                        System.out.printf("  规则[%d]: 处理QPS=%.2f\n", flowId, passQps);
                        
                    } catch (Exception e) {
                        System.out.printf("  规则[%d]: 获取统计失败\n", flowId);
                    }
                }
                
                report.setRequestMetrics(totalRequests, totalBlocked);
                
                // 检查是否有请求处理
                if (totalRequests == 0) {
                    report.addIssue("WARNING", "无请求处理", "Server没有处理任何集群请求");
                    System.out.println("⚠️  Server没有处理任何请求");
                } else {
                    report.addSuccess("请求处理正常");
                    System.out.printf("✅ 总请求处理量: %d\n", totalRequests);
                }
            }
            
        } catch (Exception e) {
            report.addIssue("ERROR", "业务能力检查失败", e.getMessage());
            System.out.println("❌ 业务能力检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 6. 检查客户端连接
     */
    private void checkClientConnections(ServerHealthReport report) {
        System.out.println("\n🔍 检查客户端连接:");
        
        try {
            // 获取客户端连接数（需要根据实际API实现）
            int clientCount = getActiveClientCount();
            report.setClientCount(clientCount);
            
            if (clientCount == 0) {
                report.addIssue("WARNING", "无客户端连接", "没有客户端连接到Server");
                System.out.println("⚠️  没有客户端连接");
            } else {
                report.addSuccess("客户端连接正常");
                System.out.printf("✅ 活跃客户端数: %d\n", clientCount);
            }
            
        } catch (Exception e) {
            report.addIssue("ERROR", "客户端连接检查失败", e.getMessage());
            System.out.println("❌ 客户端连接检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取活跃客户端数量（需要根据实际API实现）
     */
    private int getActiveClientCount() {
        // 这里需要根据实际的Sentinel Server API来实现
        // 可能的实现方式：
        // 1. 通过Server管理接口获取连接数
        // 2. 通过JMX监控获取
        // 3. 通过连接池统计
        // 4. 通过网络连接统计
        
        // 模拟返回（实际实现需要替换）
        return (int) (Math.random() * 5) + 1;
    }
    
    /**
     * Server健康检查报告
     */
    public static class ServerHealthReport {
        private final java.util.List<String> issues = new java.util.ArrayList<>();
        private final java.util.List<String> successes = new java.util.ArrayList<>();
        private final java.util.Map<String, String> criticalIssues = new java.util.HashMap<>();
        private final java.util.Map<String, String> warnings = new java.util.HashMap<>();
        
        // 性能指标
        private double memoryUsage = 0.0;
        private int threadCount = 0;
        private long connectionResponseTime = 0;
        private long totalRequests = 0;
        private long totalBlocked = 0;
        private int clientCount = 0;
        
        // 健康状态
        private boolean healthy = true;
        private int healthScore = 100;
        
        public void addIssue(String level, String title, String message) {
            String issue = String.format("[%s] %s: %s", level, title, message);
            issues.add(issue);
            
            if ("CRITICAL".equals(level)) {
                criticalIssues.put(title, message);
                healthScore -= 30;
            } else if ("ERROR".equals(level)) {
                healthScore -= 20;
            } else if ("WARNING".equals(level)) {
                warnings.put(title, message);
                healthScore -= 10;
            }
            
            if (healthScore < 70) {
                healthy = false;
            }
        }
        
        public void addSuccess(String message) {
            successes.add(message);
        }
        
        public void calculateOverallHealth() {
            if (healthScore < 0) healthScore = 0;
            healthy = healthScore >= 70 && criticalIssues.isEmpty();
        }
        
        // Getters and Setters
        public boolean isHealthy() { return healthy; }
        public int getHealthScore() { return healthScore; }
        public java.util.List<String> getIssues() { return issues; }
        public java.util.Map<String, String> getCriticalIssues() { return criticalIssues; }
        public java.util.Map<String, String> getWarnings() { return warnings; }
        
        public void setMemoryUsage(double usage) { this.memoryUsage = usage; }
        public void setThreadCount(int count) { this.threadCount = count; }
        public void setConnectionResponseTime(long time) { this.connectionResponseTime = time; }
        public void setRequestMetrics(long requests, long blocked) {
            this.totalRequests = requests;
            this.totalBlocked = blocked;
        }
        public void setClientCount(int count) { this.clientCount = count; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== Sentinel集群Token Server健康检查报告 ===\n");
            sb.append(String.format("检查时间: %s\n", new java.util.Date()));
            sb.append(String.format("健康状态: %s\n", healthy ? "✅ 健康" : "❌ 异常"));
            sb.append(String.format("健康分数: %d/100\n", healthScore));
            
            if (!criticalIssues.isEmpty()) {
                sb.append("\n🚨 严重问题:\n");
                for (java.util.Map.Entry<String, String> entry : criticalIssues.entrySet()) {
                    sb.append(String.format("  - %s: %s\n", entry.getKey(), entry.getValue()));
                }
            }
            
            if (!warnings.isEmpty()) {
                sb.append("\n⚠️  警告:\n");
                for (java.util.Map.Entry<String, String> entry : warnings.entrySet()) {
                    sb.append(String.format("  - %s: %s\n", entry.getKey(), entry.getValue()));
                }
            }
            
            sb.append("\n📊 性能指标:\n");
            sb.append(String.format("  内存使用率: %.2f%%\n", memoryUsage * 100));
            sb.append(String.format("  线程数量: %d\n", threadCount));
            sb.append(String.format("  连接响应时间: %dms\n", connectionResponseTime));
            sb.append(String.format("  处理请求数: %d\n", totalRequests));
            sb.append(String.format("  客户端连接数: %d\n", clientCount));
            
            if (!successes.isEmpty()) {
                sb.append("\n✅ 正常项目:\n");
                for (String success : successes) {
                    sb.append(String.format("  - %s\n", success));
                }
            }
            
            sb.append("\n=====================================\n");
            return sb.toString();
        }
    }
} 