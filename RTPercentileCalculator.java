import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * RT百分位数计算器
 * 演示P99等指标的计算和含义
 */
public class RTPercentileCalculator {
    
    private final Queue<Long> rtData = new ConcurrentLinkedQueue<>();
    private final int maxSamples;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public RTPercentileCalculator(int maxSamples) {
        this.maxSamples = maxSamples;
        
        // 每分钟计算一次百分位数
        scheduler.scheduleAtFixedRate(this::calculateAndPrintPercentiles, 
            1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 记录响应时间
     */
    public void recordRT(long responseTime) {
        rtData.offer(responseTime);
        
        // 保持样本数量在限制内
        while (rtData.size() > maxSamples) {
            rtData.poll();
        }
    }
    
    /**
     * 计算百分位数
     */
    public RTPercentiles calculatePercentiles() {
        if (rtData.isEmpty()) {
            return new RTPercentiles();
        }
        
        List<Long> sortedData = new ArrayList<>(rtData);
        Collections.sort(sortedData);
        
        int size = sortedData.size();
        
        return RTPercentiles.builder()
            .count(size)
            .min(sortedData.get(0))
            .max(sortedData.get(size - 1))
            .avg(calculateAverage(sortedData))
            .p50(calculatePercentile(sortedData, 0.50))
            .p90(calculatePercentile(sortedData, 0.90))
            .p95(calculatePercentile(sortedData, 0.95))
            .p99(calculatePercentile(sortedData, 0.99))
            .p999(calculatePercentile(sortedData, 0.999))
            .build();
    }
    
    /**
     * 计算指定百分位数
     */
    private long calculatePercentile(List<Long> sortedData, double percentile) {
        if (sortedData.isEmpty()) return 0;
        
        int index = (int) Math.ceil(sortedData.size() * percentile) - 1;
        index = Math.max(0, Math.min(index, sortedData.size() - 1));
        
        return sortedData.get(index);
    }
    
    /**
     * 计算平均值
     */
    private long calculateAverage(List<Long> data) {
        return (long) data.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    /**
     * 计算并打印百分位数
     */
    private void calculateAndPrintPercentiles() {
        RTPercentiles percentiles = calculatePercentiles();
        
        System.out.println("=== RT 百分位数统计 ===");
        System.out.println("样本数量: " + percentiles.getCount());
        System.out.println("最小值: " + percentiles.getMin() + "ms");
        System.out.println("最大值: " + percentiles.getMax() + "ms");
        System.out.println("平均值: " + percentiles.getAvg() + "ms");
        System.out.println("P50: " + percentiles.getP50() + "ms");
        System.out.println("P90: " + percentiles.getP90() + "ms");
        System.out.println("P95: " + percentiles.getP95() + "ms");
        System.out.println("P99: " + percentiles.getP99() + "ms");
        System.out.println("P99.9: " + percentiles.getP999() + "ms");
        System.out.println("========================");
        
        // P99含义解释
        explainP99(percentiles);
    }
    
    /**
     * 解释P99的含义
     */
    private void explainP99(RTPercentiles percentiles) {
        if (percentiles.getCount() == 0) return;
        
        long p99 = percentiles.getP99();
        int totalRequests = percentiles.getCount();
        int requestsUnderP99 = (int) (totalRequests * 0.99);
        int requestsOverP99 = totalRequests - requestsUnderP99;
        
        System.out.println("📊 P99 = " + p99 + "ms 的含义：");
        System.out.println("   • " + requestsUnderP99 + " 个请求（99%）的RT ≤ " + p99 + "ms");
        System.out.println("   • " + requestsOverP99 + " 个请求（1%）的RT > " + p99 + "ms");
        System.out.println();
    }
    
    // 数据模型
    public static class RTPercentiles {
        private int count;
        private long min, max, avg;
        private long p50, p90, p95, p99, p999;
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public int getCount() { return count; }
        public long getMin() { return min; }
        public long getMax() { return max; }
        public long getAvg() { return avg; }
        public long getP50() { return p50; }
        public long getP90() { return p90; }
        public long getP95() { return p95; }
        public long getP99() { return p99; }
        public long getP999() { return p999; }
        
        public static class Builder {
            private RTPercentiles percentiles = new RTPercentiles();
            
            public Builder count(int count) { percentiles.count = count; return this; }
            public Builder min(long min) { percentiles.min = min; return this; }
            public Builder max(long max) { percentiles.max = max; return this; }
            public Builder avg(long avg) { percentiles.avg = avg; return this; }
            public Builder p50(long p50) { percentiles.p50 = p50; return this; }
            public Builder p90(long p90) { percentiles.p90 = p90; return this; }
            public Builder p95(long p95) { percentiles.p95 = p95; return this; }
            public Builder p99(long p99) { percentiles.p99 = p99; return this; }
            public Builder p999(long p999) { percentiles.p999 = p999; return this; }
            
            public RTPercentiles build() { return percentiles; }
        }
    }
    
    // 测试用例
    public static void main(String[] args) throws InterruptedException {
        RTPercentileCalculator calculator = new RTPercentileCalculator(10000);
        
        // 模拟不同的请求响应时间
        Random random = new Random();
        
        System.out.println("开始模拟请求...");
        
        // 模拟1000个请求
        for (int i = 0; i < 1000; i++) {
            long rt;
            
            if (i < 900) {
                // 90%的请求：10-100ms之间
                rt = 10 + random.nextInt(90);
            } else if (i < 990) {
                // 9%的请求：100-500ms之间
                rt = 100 + random.nextInt(400);
            } else {
                // 1%的请求：500-2000ms之间（长尾请求）
                rt = 500 + random.nextInt(1500);
            }
            
            calculator.recordRT(rt);
        }
        
        // 立即计算一次
        calculator.calculateAndPrintPercentiles();
        
        // 等待定时任务执行
        Thread.sleep(2000);
        
        calculator.scheduler.shutdown();
    }
} 