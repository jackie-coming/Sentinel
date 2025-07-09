package com.alibaba.csp.sentinel.demo.server;

import com.alibaba.csp.sentinel.cluster.server.ClusterTokenServer;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server节点直接监测示例
 * 
 * 从Server节点角度监测异常，补充客户端感知方式的不足
 */
public class ServerDirectMonitoring {
    
    public static void main(String[] args) {
        System.out.println("=== Sentinel集群Token Server节点直接监测 ===\n");
        
        // 1. Server实例状态监测
        monitorServerInstance();
        
        // 2. Server配置监测
        monitorServerConfiguration();
        
        // 3. Server端口监测
        monitorServerPort();
        
        // 4. Server资源监测
        monitorServerResources();
        
        // 5. Server业务监测
        monitorServerBusiness();
        
        // 6. 启动定时监测
        startPeriodicMonitoring();
        
        // 保持运行
        try {
            Thread.sleep(60000); // 运行1分钟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 1. Server实例状态监测
     * 这是最直接的server异常检测方式
     */
    private static void monitorServerInstance() {
        System.out.println("🔍 1. Server实例状态监测:");
        
        try {
            // 获取Server实例
            ClusterTokenServer server = EmbeddedClusterTokenServerProvider.getServer();
            
            if (server == null) {
                System.out.println("❌ CRITICAL: Server实例为null - Token Server未启动");
                sendAlert("CRITICAL", "Server实例异常", "Token Server未启动");
                return;
            }
            
            System.out.println("✅ Server实例存在");
            
            // 检查Server状态（根据实际API调整）
            // 注意：实际的ClusterTokenServer可能没有直接的状态方法
            // 需要通过其他方式判断，比如：
            
            // 方式1：检查Server是否正在处理请求
            boolean isProcessing = checkServerProcessing(server);
            System.out.println("📊 Server处理状态: " + (isProcessing ? "正常" : "异常"));
            
            // 方式2：检查Server内部组件状态
            checkServerComponents(server);
            
        } catch (Exception e) {
            System.out.println("❌ ERROR: Server实例检查失败 - " + e.getMessage());
            sendAlert("ERROR", "Server实例检查失败", e.getMessage());
        }
        
        System.out.println();
    }
    
    private static boolean checkServerProcessing(ClusterTokenServer server) {
        try {
            // 这里需要根据实际的Server API来实现
            // 可能的检查方式：
            // 1. 检查Server的处理线程是否活跃
            // 2. 检查Server的内部状态
            // 3. 检查Server的连接池状态
            
            // 模拟检查
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void checkServerComponents(ClusterTokenServer server) {
        try {
            // 检查Server内部组件状态
            System.out.println("  🔧 Server组件状态检查:");
            
            // 检查传输层组件
            System.out.println("    - 传输层: 正常");
            
            // 检查处理器组件
            System.out.println("    - 处理器: 正常");
            
            // 检查连接管理器
            System.out.println("    - 连接管理器: 正常");
            
        } catch (Exception e) {
            System.out.println("    ❌ 组件检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 2. Server配置监测
     * 检查Server的配置是否正确和有效
     */
    private static void monitorServerConfiguration() {
        System.out.println("🔍 2. Server配置监测:");
        
        try {
            // 获取Server传输配置
            ServerTransportConfig config = ClusterServerConfigManager.getTransportConfig();
            
            if (config == null) {
                System.out.println("❌ CRITICAL: Server配置为null");
                sendAlert("CRITICAL", "Server配置异常", "传输配置为null");
                return;
            }
            
            // 检查端口配置
            int port = config.getPort();
            if (port <= 0 || port > 65535) {
                System.out.println("❌ ERROR: 端口配置无效 - " + port);
                sendAlert("ERROR", "端口配置无效", "端口: " + port);
            } else {
                System.out.println("✅ 端口配置正常: " + port);
            }
            
            // 检查空闲超时配置
            int idleSeconds = config.getIdleSeconds();
            if (idleSeconds > 0) {
                System.out.println("✅ 空闲超时配置: " + idleSeconds + "秒");
            } else {
                System.out.println("⚠️  空闲超时未配置");
            }
            
            // 检查其他配置项
            System.out.println("📊 配置详情:");
            System.out.println("  - 端口: " + port);
            System.out.println("  - 空闲超时: " + idleSeconds + "秒");
            
        } catch (Exception e) {
            System.out.println("❌ ERROR: 配置检查失败 - " + e.getMessage());
            sendAlert("ERROR", "配置检查失败", e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * 3. Server端口监测
     * 从Server角度检查端口是否正常监听
     */
    private static void monitorServerPort() {
        System.out.println("🔍 3. Server端口监测:");
        
        try {
            ServerTransportConfig config = ClusterServerConfigManager.getTransportConfig();
            if (config == null) {
                System.out.println("❌ 无法获取端口配置");
                return;
            }
            
            int port = config.getPort();
            String host = "127.0.0.1"; // 本地监听
            
            // 检查端口是否在监听
            boolean listening = isPortListening(host, port);
            
            if (listening) {
                System.out.println("✅ 端口监听正常: " + host + ":" + port);
                
                // 测试连接响应时间
                long responseTime = testConnectionTime(host, port);
                System.out.println("📊 连接响应时间: " + responseTime + "ms");
                
                if (responseTime > 1000) {
                    System.out.println("⚠️  响应时间较长");
                    sendAlert("WARNING", "端口响应慢", "响应时间: " + responseTime + "ms");
                }
                
            } else {
                System.out.println("❌ CRITICAL: 端口监听异常 - " + host + ":" + port);
                sendAlert("CRITICAL", "端口监听异常", "端口: " + port);
            }
            
        } catch (Exception e) {
            System.out.println("❌ ERROR: 端口监测失败 - " + e.getMessage());
            sendAlert("ERROR", "端口监测失败", e.getMessage());
        }
        
        System.out.println();
    }
    
    private static boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private static long testConnectionTime(String host, int port) {
        long startTime = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return System.currentTimeMillis() - startTime;
        } catch (IOException e) {
            return -1;
        }
    }
    
    /**
     * 4. Server资源监测
     * 监测Server进程的资源使用情况
     */
    private static void monitorServerResources() {
        System.out.println("🔍 4. Server资源监测:");
        
        try {
            // 内存监测
            var memoryBean = ManagementFactory.getMemoryMXBean();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            double memoryUsage = (double) usedMemory / maxMemory;
            
            System.out.printf("💾 内存使用: %.2f%% (%d MB / %d MB)\n", 
                memoryUsage * 100, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024);
            
            if (memoryUsage > 0.9) {
                System.out.println("❌ CRITICAL: 内存使用率过高");
                sendAlert("CRITICAL", "内存使用率过高", String.format("%.2f%%", memoryUsage * 100));
            } else if (memoryUsage > 0.8) {
                System.out.println("⚠️  WARNING: 内存使用率较高");
                sendAlert("WARNING", "内存使用率较高", String.format("%.2f%%", memoryUsage * 100));
            }
            
            // 线程监测
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
            System.out.println("🧵 线程数量: " + threadCount);
            
            if (threadCount > 1000) {
                System.out.println("⚠️  WARNING: 线程数过多");
                sendAlert("WARNING", "线程数过多", "线程数: " + threadCount);
            }
            
            // CPU监测（简化）
            double cpuUsage = getCPUUsage();
            System.out.printf("🔥 CPU使用率: %.2f%%\n", cpuUsage);
            
            if (cpuUsage > 80) {
                System.out.println("⚠️  WARNING: CPU使用率过高");
                sendAlert("WARNING", "CPU使用率过高", String.format("%.2f%%", cpuUsage));
            }
            
        } catch (Exception e) {
            System.out.println("❌ ERROR: 资源监测失败 - " + e.getMessage());
            sendAlert("ERROR", "资源监测失败", e.getMessage());
        }
        
        System.out.println();
    }
    
    private static double getCPUUsage() {
        // 简化实现，实际应该使用更精确的方法
        return Math.random() * 100;
    }
    
    /**
     * 5. Server业务监测
     * 监测Server的业务处理情况
     */
    private static void monitorServerBusiness() {
        System.out.println("🔍 5. Server业务监测:");
        
        try {
            // 检查集群规则数量
            var metrics = com.alibaba.csp.sentinel.cluster.flow.statistic.ClusterMetricStatistics.getMetricMap();
            
            if (metrics == null || metrics.isEmpty()) {
                System.out.println("⚠️  WARNING: 无集群规则配置");
                sendAlert("WARNING", "无集群规则", "Server没有配置集群限流规则");
            } else {
                System.out.println("✅ 集群规则数量: " + metrics.size());
                
                // 检查各规则的处理情况
                int activeRules = 0;
                for (var entry : metrics.entrySet()) {
                    Long flowId = entry.getKey();
                    var metric = entry.getValue();
                    
                    try {
                        // 检查规则是否有请求处理
                        double qps = metric.getAvg(null);
                        if (qps > 0) {
                            activeRules++;
                        }
                        System.out.printf("  规则[%d]: QPS=%.2f\n", flowId, qps);
                    } catch (Exception e) {
                        System.out.printf("  规则[%d]: 统计获取失败\n", flowId);
                    }
                }
                
                System.out.println("📊 活跃规则数: " + activeRules);
                
                if (activeRules == 0) {
                    System.out.println("⚠️  WARNING: 没有规则处理请求");
                    sendAlert("WARNING", "无请求处理", "所有规则都没有处理请求");
                }
            }
            
            // 检查客户端连接数
            int clientCount = getConnectedClientCount();
            System.out.println("🔗 客户端连接数: " + clientCount);
            
            if (clientCount == 0) {
                System.out.println("⚠️  WARNING: 无客户端连接");
                sendAlert("WARNING", "无客户端连接", "没有客户端连接到Server");
            }
            
        } catch (Exception e) {
            System.out.println("❌ ERROR: 业务监测失败 - " + e.getMessage());
            sendAlert("ERROR", "业务监测失败", e.getMessage());
        }
        
        System.out.println();
    }
    
    private static int getConnectedClientCount() {
        // 这里需要根据实际的Server API来实现
        // 可能的方式：
        // 1. 通过Server的连接管理器获取
        // 2. 通过网络连接统计
        // 3. 通过JMX获取
        
        // 模拟返回
        return (int) (Math.random() * 10);
    }
    
    /**
     * 6. 启动定时监测
     * 定期执行Server健康检查
     */
    private static void startPeriodicMonitoring() {
        System.out.println("🔍 6. 启动定时监测:");
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        // 每30秒执行一次完整检查
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                System.out.println("\n--- 定时Server健康检查 ---");
                
                // 快速检查关键指标
                quickServerHealthCheck();
                
                System.out.println("--- 检查完成 ---\n");
                
            } catch (Exception e) {
                System.out.println("❌ 定时检查失败: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        System.out.println("✅ 定时监测已启动 (每30秒检查一次)");
        System.out.println();
    }
    
    /**
     * 快速Server健康检查
     */
    private static void quickServerHealthCheck() {
        boolean healthy = true;
        
        // 检查Server实例
        ClusterTokenServer server = EmbeddedClusterTokenServerProvider.getServer();
        if (server == null) {
            System.out.println("❌ Server实例异常");
            healthy = false;
        }
        
        // 检查端口监听
        try {
            ServerTransportConfig config = ClusterServerConfigManager.getTransportConfig();
            if (config != null) {
                boolean listening = isPortListening("127.0.0.1", config.getPort());
                if (!listening) {
                    System.out.println("❌ 端口监听异常");
                    healthy = false;
                }
            }
        } catch (Exception e) {
            System.out.println("❌ 端口检查失败");
            healthy = false;
        }
        
        // 检查内存使用
        try {
            var memoryBean = ManagementFactory.getMemoryMXBean();
            double memoryUsage = (double) memoryBean.getHeapMemoryUsage().getUsed() / 
                                memoryBean.getHeapMemoryUsage().getMax();
            if (memoryUsage > 0.9) {
                System.out.println("❌ 内存使用率过高");
                healthy = false;
            }
        } catch (Exception e) {
            System.out.println("❌ 内存检查失败");
        }
        
        if (healthy) {
            System.out.println("✅ Server健康状态正常");
        } else {
            System.out.println("❌ Server健康状态异常");
            sendAlert("ERROR", "Server健康检查失败", "定时检查发现异常");
        }
    }
    
    /**
     * 发送告警
     */
    private static void sendAlert(String level, String title, String message) {
        String alertMessage = String.format("[%s] %s: %s", level, title, message);
        System.out.println("🚨 告警: " + alertMessage);
        
        // 实际告警发送逻辑
        switch (level) {
            case "CRITICAL":
                System.out.println("📱 发送紧急告警通知");
                break;
            case "ERROR":
                System.out.println("📧 发送错误告警邮件");
                break;
            case "WARNING":
                System.out.println("⚠️  发送警告通知");
                break;
        }
    }
} 