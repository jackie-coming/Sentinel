import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 严格限流版漏桶算法演示
 * 超出QPS立即拒绝，不进行排队
 */
public class StrictLeakyBucketDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 严格限流版漏桶算法演示 ===\n");
        
        // 创建严格限流漏桶：每秒10个请求
        StrictLeakyBucket bucket = new StrictLeakyBucket(10);
        
        // 场景1：正常流量
        System.out.println("场景1：正常流量（8 QPS）");
        simulateNormalTraffic(bucket);
        Thread.sleep(1000);
        
        // 场景2：超出QPS的突发流量
        System.out.println("\n场景2：突发流量（20 QPS）");
        simulateBurstTraffic(bucket);
        Thread.sleep(1000);
        
        // 场景3：持续超载
        System.out.println("\n场景3：持续超载（30 QPS）");
        simulateOverloadTraffic(bucket);
    }
    
    private static void simulateNormalTraffic(StrictLeakyBucket bucket) {
        System.out.println("每125ms发送1个请求");
        int success = 0, total = 8;
        
        for (int i = 0; i < total; i++) {
            boolean accepted = bucket.tryAcquire();
            if (accepted) success++;
            System.out.printf("请求%d: %s\n", i+1, accepted ? "✅ 通过" : "❌ 拒绝");
            
            try {
                Thread.sleep(125);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.printf("结果: %d/%d 通过 (%.1f%%)\n", success, total, success*100.0/total);
    }
    
    private static void simulateBurstTraffic(StrictLeakyBucket bucket) {
        System.out.println("50ms内发送20个请求");
        int success = 0, total = 20;
        
        for (int i = 0; i < total; i++) {
            boolean accepted = bucket.tryAcquire();
            if (accepted) success++;
            if (i % 5 == 0) {
                System.out.printf("请求%d: %s, 当前水位: %.2f\n", 
                    i+1, accepted ? "✅ 通过" : "❌ 拒绝", bucket.getCurrentLevel());
            }
            
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.printf("结果: %d/%d 通过 (%.1f%%) - 超出部分立即拒绝\n", 
            success, total, success*100.0/total);
    }
    
    private static void simulateOverloadTraffic(StrictLeakyBucket bucket) {
        System.out.println("33ms内发送30个请求");
        int success = 0, total = 30;
        
        for (int i = 0; i < total; i++) {
            boolean accepted = bucket.tryAcquire();
            if (accepted) success++;
            
            try {
                Thread.sleep(33);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.printf("结果: %d/%d 通过 (%.1f%%) - 严格按QPS限制\n", 
            success, total, success*100.0/total);
    }
}

/**
 * 严格限流版漏桶实现
 * 基于水位控制，超出立即拒绝
 */
class StrictLeakyBucket {
    private final double capacity;        // 桶容量（秒）
    private final double leakRate;       // 漏出速率（每秒）
    private final AtomicLong lastLeakTime; // 上次漏水时间
    private volatile double currentLevel; // 当前水位
    private final ReentrantLock lock = new ReentrantLock();
    
    public StrictLeakyBucket(double leakRatePerSecond) {
        this.capacity = 1.0; // 1秒的容量
        this.leakRate = leakRatePerSecond;
        this.currentLevel = 0.0;
        this.lastLeakTime = new AtomicLong(System.currentTimeMillis());
    }
    
    /**
     * 尝试获取许可（严格限流，超出立即拒绝）
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }
    
    public boolean tryAcquire(int permits) {
        if (permits <= 0) return true;
        
        lock.lock();
        try {
            // 先漏水（模拟时间流逝）
            leak();
            
            // 计算添加这些请求后的水位
            double requestVolume = permits / leakRate; // 请求占用的时间
            double newLevel = currentLevel + requestVolume;
            
            // 检查是否超出容量
            if (newLevel > capacity) {
                // 超出容量，拒绝请求
                return false;
            }
            
            // 接受请求，更新水位
            currentLevel = newLevel;
            return true;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 模拟漏水过程
     */
    private void leak() {
        long now = System.currentTimeMillis();
        long lastLeak = lastLeakTime.get();
        
        if (now <= lastLeak) {
            return;
        }
        
        // 计算漏水量
        double timeDelta = (now - lastLeak) / 1000.0; // 转换为秒
        double leakAmount = timeDelta; // 每秒漏1秒的水
        
        // 更新水位
        currentLevel = Math.max(0, currentLevel - leakAmount);
        lastLeakTime.set(now);
        
        if (leakAmount > 0 && currentLevel > 0) {
            System.out.printf("💧 漏水: -%.3fs, 当前水位: %.3fs\n", leakAmount, currentLevel);
        }
    }
    
    public double getCurrentLevel() {
        lock.lock();
        try {
            leak();
            return currentLevel;
        } finally {
            lock.unlock();
        }
    }
    
    public double getCapacity() {
        return capacity;
    }
    
    public double getLeakRate() {
        return leakRate;
    }
} 