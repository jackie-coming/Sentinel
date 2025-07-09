/**
 * 两种漏桶算法对比演示
 */
public class LeakyBucketComparison {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 两种漏桶算法对比演示 ===\n");
        
        // 创建两种漏桶：相同的QPS限制
        LeakyBucket queueBucket = new LeakyBucket(20, 10);  // 队列型：容量20，10 QPS
        StrictLeakyBucket strictBucket = new StrictLeakyBucket(10); // 严格型：10 QPS
        
        queueBucket.start();
        
        System.out.println("📊 突发流量测试：瞬间发送30个请求\n");
        
        // 测试突发流量
        testBurstTraffic(queueBucket, strictBucket);
        
        Thread.sleep(3000); // 等待队列型漏桶处理完积压
        queueBucket.stop();
        
        // 总结对比
        printComparison();
    }
    
    private static void testBurstTraffic(LeakyBucket queueBucket, StrictLeakyBucket strictBucket) {
        System.out.println("🪣 队列型漏桶结果:");
        int queueAccepted = 0;
        for (int i = 0; i < 30; i++) {
            boolean accepted = queueBucket.addRequest("突发请求-" + i);
            if (accepted) queueAccepted++;
            if (i % 10 == 9) {
                System.out.printf("  请求1-%d: %d个接受, 队列长度: %d\n", 
                    i+1, queueAccepted, queueBucket.getQueueSize());
            }
        }
        System.out.printf("  最终结果: %d/30 接受 (%.1f%%), 队列中等待: %d个\n\n", 
            queueAccepted, queueAccepted*100.0/30, queueBucket.getQueueSize());
        
        System.out.println("⚡ 严格型漏桶结果:");
        int strictAccepted = 0;
        for (int i = 0; i < 30; i++) {
            boolean accepted = strictBucket.tryAcquire();
            if (accepted) strictAccepted++;
            if (i % 10 == 9) {
                System.out.printf("  请求1-%d: %d个接受, 当前水位: %.3f\n", 
                    i+1, strictAccepted, strictBucket.getCurrentLevel());
            }
        }
        System.out.printf("  最终结果: %d/30 接受 (%.1f%%), 超出部分立即拒绝\n\n", 
            strictAccepted, strictAccepted*100.0/30);
    }
    
    private static void printComparison() {
        System.out.println("=".repeat(80));
        System.out.println("📋 两种漏桶算法对比总结");
        System.out.println("=".repeat(80));
        
        System.out.println("\n🔍 核心差异:");
        System.out.printf("%-20s %-30s %-30s\n", "特性", "队列型漏桶", "严格型漏桶");
        System.out.println("-".repeat(80));
        System.out.printf("%-20s %-30s %-30s\n", "超出QPS行为", "进入队列等待", "立即拒绝");
        System.out.printf("%-20s %-30s %-30s\n", "请求丢失", "仅队列满时丢失", "超出QPS立即丢失");
        System.out.printf("%-20s %-30s %-30s\n", "响应延迟", "可能有排队延迟", "无延迟（通过/拒绝）");
        System.out.printf("%-20s %-30s %-30s\n", "内存占用", "需要队列空间", "只需计数器");
        System.out.printf("%-20s %-30s %-30s\n", "突发处理", "缓冲后平滑处理", "严格按QPS限制");
        System.out.printf("%-20s %-30s %-30s\n", "资源利用", "充分利用（处理积压）", "可能浪费（拒绝请求）");
        
        System.out.println("\n🎯 使用场景:");
        
        System.out.println("\n📦 队列型漏桶适用于:");
        System.out.println("  ✓ 可以接受一定延迟的场景");
        System.out.println("  ✓ 希望充分利用处理能力");
        System.out.println("  ✓ 突发流量需要缓冲处理");
        System.out.println("  ✓ 下游系统处理能力稳定");
        System.out.println("  ✓ 消息队列、批处理系统");
        
        System.out.println("\n⚡ 严格型漏桶适用于:");
        System.out.println("  ✓ 对响应时间敏感的场景");
        System.out.println("  ✓ 需要严格QPS控制");
        System.out.println("  ✓ 不允许请求积压");
        System.out.println("  ✓ 实时系统、在线服务");
        System.out.println("  ✓ API网关、限流中间件");
        
        System.out.println("\n💡 实际应用:");
        System.out.println("  • Nginx rate limiting: 严格型漏桶");
        System.out.println("  • 消息队列消费: 队列型漏桶");
        System.out.println("  • API网关: 通常使用严格型");
        System.out.println("  • 批处理系统: 通常使用队列型");
        
        System.out.println("\n🏆 选择建议:");
        System.out.println("  • 用户体验优先 → 严格型漏桶");
        System.out.println("  • 资源利用优先 → 队列型漏桶");
        System.out.println("  • 实时性要求高 → 严格型漏桶");
        System.out.println("  • 可接受延迟 → 队列型漏桶");
    }
} 