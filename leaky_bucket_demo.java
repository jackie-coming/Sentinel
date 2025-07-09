import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 漏桶算法演示
 */
public class LeakyBucketDemo {
    
    public static void main(String[] args) throws InterruptedException {
        // 创建漏桶：容量100，每秒处理10个请求
        LeakyBucket bucket = new LeakyBucket(100, 10);
        
        // 启动漏桶处理线程
        bucket.start();
        
        // 场景1：平稳流量
        System.out.println("=== 场景1：平稳流量 ===");
        simulateStableTraffic(bucket);
        Thread.sleep(2000);
        
        // 场景2：突发流量
        System.out.println("\n=== 场景2：突发流量 ===");
        simulateBurstTraffic(bucket);
        Thread.sleep(3000);
        
        // 场景3：超载流量
        System.out.println("\n=== 场景3：超载流量 ===");
        simulateOverloadTraffic(bucket);
        Thread.sleep(2000);
        
        bucket.stop();
    }
    
    /**
     * 平稳流量：每秒8个请求
     */
    private static void simulateStableTraffic(LeakyBucket bucket) {
        System.out.println("每125ms发送1个请求（8 QPS）");
        for (int i = 0; i < 10; i++) {
            boolean accepted = bucket.addRequest("稳定请求-" + i);
            System.out.printf("请求%d: %s, 桶内请求数: %d\n", 
                i, accepted ? "接受" : "拒绝", bucket.getQueueSize());
            
            try {
                Thread.sleep(125); // 125ms间隔
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 突发流量：短时间内大量请求
     */
    private static void simulateBurstTraffic(LeakyBucket bucket) {
        System.out.println("100ms内发送50个请求（突发）");
        for (int i = 0; i < 50; i++) {
            boolean accepted = bucket.addRequest("突发请求-" + i);
            if (i % 10 == 0) { // 每10个请求打印一次状态
                System.out.printf("请求%d: %s, 桶内请求数: %d\n", 
                    i, accepted ? "接受" : "拒绝", bucket.getQueueSize());
            }
            
            try {
                Thread.sleep(2); // 2ms间隔，模拟突发
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 超载流量：持续高并发
     */
    private static void simulateOverloadTraffic(LeakyBucket bucket) {
        System.out.println("持续高频请求（超过处理能力）");
        for (int i = 0; i < 30; i++) {
            boolean accepted = bucket.addRequest("超载请求-" + i);
            System.out.printf("请求%d: %s, 桶内请求数: %d\n", 
                i, accepted ? "接受" : "拒绝", bucket.getQueueSize());
            
            try {
                Thread.sleep(50); // 50ms间隔，20 QPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

/**
 * 漏桶算法实现
 */
class LeakyBucket {
    private final LinkedBlockingQueue<String> bucket;
    private final int capacity;           // 桶容量
    private final int leakRate;          // 漏出速率（每秒处理的请求数）
    private volatile boolean running;
    private Thread processingThread;
    
    public LeakyBucket(int capacity, int leakRate) {
        this.capacity = capacity;
        this.leakRate = leakRate;
        this.bucket = new LinkedBlockingQueue<>(capacity);
        this.running = false;
    }
    
    /**
     * 添加请求到桶中
     */
    public boolean addRequest(String request) {
        boolean offered = bucket.offer(request);
        if (!offered) {
            System.out.println("❌ 桶已满，请求被丢弃: " + request);
        }
        return offered;
    }
    
    /**
     * 启动漏桶处理
     */
    public void start() {
        if (running) return;
        
        running = true;
        processingThread = new Thread(this::processRequests, "LeakyBucket-Processor");
        processingThread.start();
        System.out.println("🚀 漏桶开始工作，处理速率: " + leakRate + " req/s");
    }
    
    /**
     * 停止漏桶处理
     */
    public void stop() {
        running = false;
        if (processingThread != null) {
            processingThread.interrupt();
        }
        System.out.println("⏹️ 漏桶停止工作");
    }
    
    /**
     * 恒定速率处理请求
     */
    private void processRequests() {
        long intervalMs = 1000 / leakRate; // 每个请求的处理间隔
        
        while (running) {
            try {
                String request = bucket.poll(intervalMs, TimeUnit.MILLISECONDS);
                if (request != null) {
                    // 模拟处理请求
                    System.out.println("✅ 处理请求: " + request + 
                        " (桶内剩余: " + bucket.size() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public int getQueueSize() {
        return bucket.size();
    }
    
    public int getCapacity() {
        return capacity;
    }
    
    public int getLeakRate() {
        return leakRate;
    }
} 