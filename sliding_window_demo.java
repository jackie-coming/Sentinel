/**
 * 滑动窗口算法演示
 */
public class SlidingWindowDemo {
    
    public static void main(String[] args) {
        // 模拟Sentinel滑动窗口
        SlidingWindow window = new SlidingWindow(10, 1000); // 10个桶，1秒窗口
        
        // 场景1：平稳流量
        System.out.println("=== 场景1：平稳流量 ===");
        simulateStableTraffic(window);
        
        // 场景2：突发流量
        System.out.println("\n=== 场景2：突发流量 ===");
        simulateBurstTraffic(window);
        
        // 场景3：边界流量
        System.out.println("\n=== 场景3：边界流量 ===");
        simulateBoundaryTraffic(window);
    }
    
    /**
     * 平稳流量测试：每100ms发送10个请求
     */
    private static void simulateStableTraffic(SlidingWindow window) {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10; i++) {
            long currentTime = startTime + i * 100;
            window.addRequest(currentTime, 10);
            
            double qps = window.getQPS(currentTime);
            System.out.printf("时间: %dms, 当前QPS: %.1f, 状态: %s\n", 
                i * 100, qps, qps <= 100 ? "通过" : "限流");
        }
    }
    
    /**
     * 突发流量测试：前500ms无请求，后500ms大量请求
     */
    private static void simulateBurstTraffic(SlidingWindow window) {
        long startTime = System.currentTimeMillis();
        
        // 前500ms无请求
        for (int i = 0; i < 5; i++) {
            long currentTime = startTime + i * 100;
            double qps = window.getQPS(currentTime);
            System.out.printf("时间: %dms, 当前QPS: %.1f, 状态: 静默期\n", i * 100, qps);
        }
        
        // 后500ms突发大量请求
        for (int i = 5; i < 10; i++) {
            long currentTime = startTime + i * 100;
            window.addRequest(currentTime, 50); // 突发50个请求
            
            double qps = window.getQPS(currentTime);
            System.out.printf("时间: %dms, 当前QPS: %.1f, 状态: %s\n", 
                i * 100, qps, qps <= 100 ? "通过" : "限流");
        }
    }
    
    /**
     * 边界流量测试：窗口边界的流量处理
     */
    private static void simulateBoundaryTraffic(SlidingWindow window) {
        long startTime = System.currentTimeMillis();
        
        // 在窗口边界发送请求
        window.addRequest(startTime + 950, 60);  // 第一个窗口末尾
        window.addRequest(startTime + 1050, 60); // 第二个窗口开始
        
        double qps1 = window.getQPS(startTime + 1000);
        double qps2 = window.getQPS(startTime + 1100);
        
        System.out.printf("窗口1末尾(1000ms): QPS=%.1f\n", qps1);
        System.out.printf("窗口2开始(1100ms): QPS=%.1f\n", qps2);
        System.out.println("优势: 避免了固定窗口的边界突发问题");
    }
}

/**
 * 简化的滑动窗口实现
 */
class SlidingWindow {
    private final int bucketCount;
    private final int windowSizeMs;
    private final int bucketSizeMs;
    private final long[] buckets;
    private final long[] bucketTimes;
    
    public SlidingWindow(int bucketCount, int windowSizeMs) {
        this.bucketCount = bucketCount;
        this.windowSizeMs = windowSizeMs;
        this.bucketSizeMs = windowSizeMs / bucketCount;
        this.buckets = new long[bucketCount];
        this.bucketTimes = new long[bucketCount];
    }
    
    public void addRequest(long timestamp, int count) {
        int index = (int) ((timestamp / bucketSizeMs) % bucketCount);
        long bucketStart = timestamp - (timestamp % bucketSizeMs);
        
        if (bucketTimes[index] != bucketStart) {
            // 新的时间桶，重置
            buckets[index] = 0;
            bucketTimes[index] = bucketStart;
        }
        
        buckets[index] += count;
    }
    
    public double getQPS(long timestamp) {
        long windowStart = timestamp - windowSizeMs;
        long totalRequests = 0;
        
        for (int i = 0; i < bucketCount; i++) {
            if (bucketTimes[i] > windowStart && bucketTimes[i] <= timestamp) {
                totalRequests += buckets[i];
            }
        }
        
        return totalRequests / (windowSizeMs / 1000.0);
    }
} 