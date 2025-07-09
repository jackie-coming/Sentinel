import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 令牌桶算法演示
 */
public class TokenBucketDemo {
    
    public static void main(String[] args) throws InterruptedException {
        // 创建令牌桶：容量50，每秒产生10个令牌
        TokenBucket bucket = new TokenBucket(50, 10);
        
        // 场景1：平稳流量
        System.out.println("=== 场景1：平稳流量 ===");
        simulateStableTraffic(bucket);
        Thread.sleep(1000);
        
        // 场景2：突发流量（令牌桶优势）
        System.out.println("\n=== 场景2：突发流量 ===");
        simulateBurstTraffic(bucket);
        Thread.sleep(2000);
        
        // 场景3：持续超载
        System.out.println("\n=== 场景3：持续超载 ===");
        simulateOverloadTraffic(bucket);
        Thread.sleep(1000);
        
        // 场景4：突发后恢复
        System.out.println("\n=== 场景4：突发后恢复 ===");
        simulateBurstRecovery(bucket);
    }
    
    /**
     * 平稳流量：每秒8个请求
     */
    private static void simulateStableTraffic(TokenBucket bucket) {
        System.out.println("每125ms发送1个请求（8 QPS）");
        for (int i = 0; i < 10; i++) {
            boolean acquired = bucket.acquire(1);
            System.out.printf("请求%d: %s, 剩余令牌: %d\n", 
                i, acquired ? "通过" : "拒绝", bucket.getAvailableTokens());
            
            try {
                Thread.sleep(125);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 突发流量：利用累积的令牌处理突发
     */
    private static void simulateBurstTraffic(TokenBucket bucket) {
        System.out.println("等待令牌累积...");
        try {
            Thread.sleep(3000); // 等待3秒，累积30个令牌
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.printf("累积令牌数: %d\n", bucket.getAvailableTokens());
        System.out.println("瞬间发送25个请求（突发）");
        
        for (int i = 0; i < 25; i++) {
            boolean acquired = bucket.acquire(1);
            if (i % 5 == 0) { // 每5个请求打印状态
                System.out.printf("请求%d: %s, 剩余令牌: %d\n", 
                    i, acquired ? "通过" : "拒绝", bucket.getAvailableTokens());
            }
        }
        
        System.out.println("突发处理完成，剩余令牌: " + bucket.getAvailableTokens());
    }
    
    /**
     * 持续超载：超过令牌产生速率
     */
    private static void simulateOverloadTraffic(TokenBucket bucket) {
        System.out.println("每50ms发送1个请求（20 QPS，超过产生速率）");
        int successCount = 0;
        
        for (int i = 0; i < 20; i++) {
            boolean acquired = bucket.acquire(1);
            if (acquired) successCount++;
            
            System.out.printf("请求%d: %s, 剩余令牌: %d\n", 
                i, acquired ? "通过" : "拒绝", bucket.getAvailableTokens());
            
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.printf("超载测试结果: %d/%d 请求通过\n", successCount, 20);
    }
    
    /**
     * 突发后恢复：展示令牌桶的恢复能力
     */
    private static void simulateBurstRecovery(TokenBucket bucket) {
        System.out.println("先耗尽令牌...");
        while (bucket.acquire(1)) {
            // 耗尽所有令牌
        }
        System.out.println("令牌已耗尽，剩余: " + bucket.getAvailableTokens());
        
        System.out.println("等待令牌恢复...");
        try {
            Thread.sleep(2000); // 等待2秒
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.printf("恢复后令牌数: %d\n", bucket.getAvailableTokens());
        System.out.println("验证恢复能力：");
        
        for (int i = 0; i < 5; i++) {
            boolean acquired = bucket.acquire(1);
            System.out.printf("恢复请求%d: %s, 剩余令牌: %d\n", 
                i, acquired ? "通过" : "拒绝", bucket.getAvailableTokens());
        }
    }
}

/**
 * 令牌桶算法实现
 */
class TokenBucket {
    private final long capacity;          // 桶容量
    private final long refillRate;       // 令牌产生速率（每秒）
    private final AtomicLong tokens;     // 当前令牌数
    private final AtomicLong lastRefillTime; // 上次补充令牌时间
    private final ReentrantLock lock = new ReentrantLock();
    
    public TokenBucket(long capacity, long refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = new AtomicLong(capacity); // 初始时桶是满的
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
    }
    
    /**
     * 尝试获取指定数量的令牌
     */
    public boolean acquire(long tokensRequested) {
        refillTokens(); // 先补充令牌
        
        lock.lock();
        try {
            long currentTokens = tokens.get();
            if (currentTokens >= tokensRequested) {
                tokens.addAndGet(-tokensRequested);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 根据时间间隔补充令牌
     */
    private void refillTokens() {
        long now = System.currentTimeMillis();
        long lastRefill = lastRefillTime.get();
        
        if (now <= lastRefill) {
            return; // 时间没有推进
        }
        
        // 计算应该补充的令牌数
        long timeDelta = now - lastRefill;
        long tokensToAdd = (timeDelta * refillRate) / 1000; // 每秒refillRate个令牌
        
        if (tokensToAdd > 0) {
            lock.lock();
            try {
                // 双重检查，防止并发问题
                if (lastRefillTime.compareAndSet(lastRefill, now)) {
                    long currentTokens = tokens.get();
                    long newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                    tokens.set(newTokens);
                    
                    if (tokensToAdd > 0) {
                        System.out.printf("🪙 补充令牌: +%d, 当前: %d/%d\n", 
                            tokensToAdd, newTokens, capacity);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
    
    /**
     * 获取当前可用令牌数
     */
    public long getAvailableTokens() {
        refillTokens();
        return tokens.get();
    }
    
    /**
     * 获取桶容量
     */
    public long getCapacity() {
        return capacity;
    }
    
    /**
     * 获取令牌产生速率
     */
    public long getRefillRate() {
        return refillRate;
    }
} 