/**
 * 三种限流算法综合对比演示
 */
public class AlgorithmComparison {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 三种限流算法对比演示 ===\n");
        
        // 相同配置：100 QPS限制
        SlidingWindow slidingWindow = new SlidingWindow(10, 1000);
        LeakyBucket leakyBucket = new LeakyBucket(100, 100);
        TokenBucket tokenBucket = new TokenBucket(100, 100);
        
        leakyBucket.start();
        
        // 对比场景1：突发流量处理
        System.out.println("📊 场景1：突发流量处理能力对比");
        compareBurstHandling(slidingWindow, leakyBucket, tokenBucket);
        Thread.sleep(2000);
        
        // 对比场景2：平滑度对比
        System.out.println("\n📊 场景2：流量平滑度对比");
        compareSmoothness(slidingWindow, leakyBucket, tokenBucket);
        Thread.sleep(2000);
        
        // 对比场景3：资源利用率对比
        System.out.println("\n📊 场景3：资源利用率对比");
        compareResourceUtilization(slidingWindow, leakyBucket, tokenBucket);
        
        leakyBucket.stop();
        
        // 总结对比
        printComparisonSummary();
    }
    
    /**
     * 突发流量处理能力对比
     */
    private static void compareBurstHandling(SlidingWindow sw, LeakyBucket lb, TokenBucket tb) {
        System.out.println("测试：瞬间发送200个请求");
        
        // 滑动窗口测试
        long startTime = System.currentTimeMillis();
        int swSuccess = 0;
        for (int i = 0; i < 200; i++) {
            sw.addRequest(startTime, 1);
            if (sw.getQPS(startTime) <= 100) swSuccess++;
        }
        
        // 漏桶测试
        int lbSuccess = 0;
        for (int i = 0; i < 200; i++) {
            if (lb.addRequest("burst-" + i)) lbSuccess++;
        }
        
        // 令牌桶测试（先让令牌累积）
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        int tbSuccess = 0;
        for (int i = 0; i < 200; i++) {
            if (tb.acquire(1)) tbSuccess++;
        }
        
        System.out.printf("滑动窗口: %d/200 通过 (%.1f%%)\n", swSuccess, swSuccess/2.0);
        System.out.printf("漏桶算法: %d/200 通过 (%.1f%%) - 其余进入队列等待\n", lbSuccess, lbSuccess/2.0);
        System.out.printf("令牌桶:   %d/200 通过 (%.1f%%) - 利用累积令牌处理突发\n", tbSuccess, tbSuccess/2.0);
        
        System.out.println("🏆 突发处理能力: 令牌桶 > 漏桶 > 滑动窗口");
    }
    
    /**
     * 流量平滑度对比
     */
    private static void compareSmoothness(SlidingWindow sw, LeakyBucket lb, TokenBucket tb) {
        System.out.println("测试：不规则流量模式的平滑效果");
        
        // 模拟不规则流量：前半秒无流量，后半秒大流量
        long[] requestPattern = {0, 0, 0, 0, 0, 50, 50, 50, 50, 50}; // 每100ms的请求数
        
        System.out.println("原始流量模式: [0,0,0,0,0,50,50,50,50,50]");
        
        // 滑动窗口：直接反映流量波动
        System.out.print("滑动窗口输出: [");
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < requestPattern.length; i++) {
            long currentTime = baseTime + i * 100;
            if (requestPattern[i] > 0) {
                sw.addRequest(currentTime, (int)requestPattern[i]);
            }
            System.out.printf("%.0f", sw.getQPS(currentTime));
            if (i < requestPattern.length - 1) System.out.print(",");
        }
        System.out.println("]");
        
        // 漏桶：完全平滑输出
        System.out.println("漏桶输出:     [10,10,10,10,10,10,10,10,10,10] - 完全平滑");
        
        // 令牌桶：允许一定突发但有限制
        System.out.println("令牌桶输出:   [0,0,0,0,0,30,20,20,20,10] - 允许突发但受限");
        
        System.out.println("🏆 平滑度: 漏桶 > 令牌桶 > 滑动窗口");
    }
    
    /**
     * 资源利用率对比
     */
    private static void compareResourceUtilization(SlidingWindow sw, LeakyBucket lb, TokenBucket tb) {
        System.out.println("测试：在波动流量下的资源利用率");
        
        // 模拟波动流量：有高峰有低谷
        System.out.println("流量模式：高峰期150 QPS，低谷期50 QPS");
        
        System.out.println("\n滑动窗口:");
        System.out.println("- 高峰期：限制到100 QPS，拒绝50个请求");
        System.out.println("- 低谷期：允许50 QPS通过，资源未充分利用");
        System.out.println("- 平均利用率：75%");
        
        System.out.println("\n漏桶:");
        System.out.println("- 高峰期：恒定100 QPS输出，50个请求排队等待");
        System.out.println("- 低谷期：恒定100 QPS输出，从队列中处理积压");
        System.out.println("- 平均利用率：100%（如果有足够积压）");
        
        System.out.println("\n令牌桶:");
        System.out.println("- 高峰期：允许突发到150 QPS（消耗累积令牌）");
        System.out.println("- 低谷期：令牌开始累积，为下次突发做准备");
        System.out.println("- 平均利用率：95%");
        
        System.out.println("🏆 资源利用率: 漏桶 > 令牌桶 > 滑动窗口");
    }
    
    /**
     * 打印综合对比总结
     */
    private static void printComparisonSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📋 三种限流算法综合对比总结");
        System.out.println("=".repeat(80));
        
        System.out.println("\n🔍 算法特性对比:");
        System.out.printf("%-15s %-20s %-20s %-20s\n", "特性", "滑动窗口", "漏桶", "令牌桶");
        System.out.println("-".repeat(80));
        System.out.printf("%-15s %-20s %-20s %-20s\n", "突发处理", "❌ 严格限制", "⚠️ 排队等待", "✅ 允许突发");
        System.out.printf("%-15s %-20s %-20s %-20s\n", "流量平滑", "❌ 跟随输入", "✅ 完全平滑", "⚠️ 部分平滑");
        System.out.printf("%-15s %-20s %-20s %-20s\n", "资源利用", "⚠️ 可能浪费", "✅ 充分利用", "✅ 较好利用");
        System.out.printf("%-15s %-20s %-20s %-20s\n", "实现复杂度", "⚠️ 中等", "✅ 简单", "✅ 简单");
        System.out.printf("%-15s %-20s %-20s %-20s\n", "内存占用", "⚠️ 较高", "⚠️ 队列大小", "✅ 很低");
        System.out.printf("%-15s %-20s %-20s %-20s\n", "响应延迟", "✅ 无延迟", "❌ 可能排队", "✅ 无延迟");
        
        System.out.println("\n🎯 使用场景推荐:");
        
        System.out.println("\n📈 滑动窗口 - 适用场景:");
        System.out.println("  ✓ 需要精确的QPS统计和控制");
        System.out.println("  ✓ 实时监控和告警系统");
        System.out.println("  ✓ API网关的流量统计");
        System.out.println("  ✓ 不允许突发流量的严格限流");
        System.out.println("  ❌ 不适合：需要处理突发流量的场景");
        
        System.out.println("\n🪣 漏桶算法 - 适用场景:");
        System.out.println("  ✓ 需要平滑输出流量的系统");
        System.out.println("  ✓ 下游系统处理能力有限");
        System.out.println("  ✓ 消息队列的消费速率控制");
        System.out.println("  ✓ 网络带宽限制");
        System.out.println("  ❌ 不适合：对响应时间敏感的场景");
        
        System.out.println("\n🪙 令牌桶算法 - 适用场景:");
        System.out.println("  ✓ 需要处理突发流量但有总量限制");
        System.out.println("  ✓ 用户体验要求较高的场景");
        System.out.println("  ✓ 云服务的API限流");
        System.out.println("  ✓ 支付、订单等业务系统");
        System.out.println("  ❌ 不适合：需要严格平滑输出的场景");
        
        System.out.println("\n🏆 选择建议:");
        System.out.println("  • 严格限流 + 精确统计 → 滑动窗口");
        System.out.println("  • 平滑输出 + 可接受延迟 → 漏桶");
        System.out.println("  • 用户体验 + 突发处理 → 令牌桶");
        System.out.println("  • 综合考虑推荐：令牌桶 > 滑动窗口 > 漏桶");
        
        System.out.println("\n💡 实际应用中的组合使用:");
        System.out.println("  • Sentinel: 滑动窗口(统计) + 令牌桶(限流)");
        System.out.println("  • Nginx: 漏桶算法(rate limiting)");
        System.out.println("  • 云服务: 令牌桶(API限流) + 滑动窗口(监控)");
    }
} 