import java.util.*;
import java.util.stream.Collectors;

/**
 * Comparator.comparing() 详细示例
 * 演示 roughSortPointDTOList.sort(Comparator.comparing(RoughSortPointDTO::getNearProb).reversed())
 */
public class ComparatorSortExample {
    
    // 模拟的数据传输对象
    static class RoughSortPointDTO {
        private String pointId;
        private String pointName;
        private double nearProb;  // 接近概率
        private double distance;  // 距离
        private int priority;     // 优先级
        
        public RoughSortPointDTO(String pointId, String pointName, double nearProb, double distance, int priority) {
            this.pointId = pointId;
            this.pointName = pointName;
            this.nearProb = nearProb;
            this.distance = distance;
            this.priority = priority;
        }
        
        // Getter方法
        public String getPointId() { return pointId; }
        public String getPointName() { return pointName; }
        public double getNearProb() { return nearProb; }
        public double getDistance() { return distance; }
        public int getPriority() { return priority; }
        
        @Override
        public String toString() {
            return String.format("Point{id='%s', name='%s', nearProb=%.2f, distance=%.1f, priority=%d}", 
                pointId, pointName, nearProb, distance, priority);
        }
    }
    
    public static void main(String[] args) {
        // 创建测试数据
        List<RoughSortPointDTO> roughSortPointDTOList = createTestData();
        
        System.out.println("=== 原始数据 ===");
        printList(roughSortPointDTOList);
        
        // 1. 基本排序：按nearProb降序
        demonstrateBasicSort(new ArrayList<>(roughSortPointDTOList));
        
        // 2. 对比不同的排序方式
        demonstrateDifferentSortMethods(new ArrayList<>(roughSortPointDTOList));
        
        // 3. 复合排序
        demonstrateComplexSort(new ArrayList<>(roughSortPointDTOList));
        
        // 4. 性能对比
        demonstratePerformanceComparison();
    }
    
    /**
     * 创建测试数据
     */
    private static List<RoughSortPointDTO> createTestData() {
        return Arrays.asList(
            new RoughSortPointDTO("P001", "地铁站A", 0.85, 120.5, 1),
            new RoughSortPointDTO("P002", "公交站B", 0.92, 80.3, 2),
            new RoughSortPointDTO("P003", "商场C", 0.78, 200.1, 3),
            new RoughSortPointDTO("P004", "学校D", 0.95, 150.0, 1),
            new RoughSortPointDTO("P005", "医院E", 0.88, 95.7, 2),
            new RoughSortPointDTO("P006", "公园F", 0.72, 300.2, 3),
            new RoughSortPointDTO("P007", "银行G", 0.90, 110.8, 1)
        );
    }
    
    /**
     * 演示基本排序：按nearProb降序
     */
    private static void demonstrateBasicSort(List<RoughSortPointDTO> list) {
        System.out.println("\n=== 1. 基本排序：按nearProb降序 ===");
        
        // 原始代码的等价实现
        list.sort(Comparator.comparing(RoughSortPointDTO::getNearProb).reversed());
        
        System.out.println("排序后（nearProb从高到低）：");
        printList(list);
        
        // 解释每个步骤
        System.out.println("\n📝 代码解析：");
        System.out.println("1. Comparator.comparing(RoughSortPointDTO::getNearProb)");
        System.out.println("   - 创建一个比较器，按getNearProb()方法的返回值升序排序");
        System.out.println("2. .reversed()");
        System.out.println("   - 反转排序顺序，变成降序");
        System.out.println("3. list.sort(comparator)");
        System.out.println("   - 使用比较器对列表进行原地排序");
    }
    
    /**
     * 演示不同的排序方式对比
     */
    private static void demonstrateDifferentSortMethods(List<RoughSortPointDTO> originalList) {
        System.out.println("\n=== 2. 不同排序方式对比 ===");
        
        // 方式1：Lambda表达式
        List<RoughSortPointDTO> list1 = new ArrayList<>(originalList);
        list1.sort((a, b) -> Double.compare(b.getNearProb(), a.getNearProb()));
        System.out.println("方式1 - Lambda表达式：");
        printList(list1);
        
        // 方式2：方法引用 + reversed()
        List<RoughSortPointDTO> list2 = new ArrayList<>(originalList);
        list2.sort(Comparator.comparing(RoughSortPointDTO::getNearProb).reversed());
        System.out.println("\n方式2 - 方法引用 + reversed()：");
        printList(list2);
        
        // 方式3：传统匿名内部类
        List<RoughSortPointDTO> list3 = new ArrayList<>(originalList);
        list3.sort(new Comparator<RoughSortPointDTO>() {
            @Override
            public int compare(RoughSortPointDTO o1, RoughSortPointDTO o2) {
                return Double.compare(o2.getNearProb(), o1.getNearProb());
            }
        });
        System.out.println("\n方式3 - 传统匿名内部类：");
        printList(list3);
        
        // 方式4：Collections.sort()
        List<RoughSortPointDTO> list4 = new ArrayList<>(originalList);
        Collections.sort(list4, Comparator.comparing(RoughSortPointDTO::getNearProb).reversed());
        System.out.println("\n方式4 - Collections.sort()：");
        printList(list4);
        
        // 验证结果一致性
        boolean allEqual = list1.equals(list2) && list2.equals(list3) && list3.equals(list4);
        System.out.println("\n✅ 所有排序方式结果一致：" + allEqual);
    }
    
    /**
     * 演示复合排序
     */
    private static void demonstrateComplexSort(List<RoughSortPointDTO> list) {
        System.out.println("\n=== 3. 复合排序示例 ===");
        
        // 复合排序1：先按nearProb降序，再按distance升序
        List<RoughSortPointDTO> list1 = new ArrayList<>(list);
        list1.sort(Comparator.comparing(RoughSortPointDTO::getNearProb).reversed()
                  .thenComparing(RoughSortPointDTO::getDistance));
        
        System.out.println("复合排序1 - 先按nearProb降序，再按distance升序：");
        printList(list1);
        
        // 复合排序2：先按priority升序，再按nearProb降序，最后按distance升序
        List<RoughSortPointDTO> list2 = new ArrayList<>(list);
        list2.sort(Comparator.comparing(RoughSortPointDTO::getPriority)
                  .thenComparing(RoughSortPointDTO::getNearProb, Comparator.reverseOrder())
                  .thenComparing(RoughSortPointDTO::getDistance));
        
        System.out.println("\n复合排序2 - 先按priority升序，再按nearProb降序，最后按distance升序：");
        printList(list2);
        
        // 自定义排序逻辑
        List<RoughSortPointDTO> list3 = new ArrayList<>(list);
        list3.sort(Comparator.comparing((RoughSortPointDTO dto) -> dto.getNearProb() * 0.7 + (1000 - dto.getDistance()) * 0.3).reversed());
        
        System.out.println("\n自定义排序 - 按综合得分降序（nearProb*0.7 + (1000-distance)*0.3）：");
        list3.forEach(dto -> {
            double score = dto.getNearProb() * 0.7 + (1000 - dto.getDistance()) * 0.3;
            System.out.printf("  %s, 综合得分=%.2f%n", dto, score);
        });
    }
    
    /**
     * 性能对比
     */
    private static void demonstratePerformanceComparison() {
        System.out.println("\n=== 4. 性能对比 ===");
        
        // 创建大量测试数据
        List<RoughSortPointDTO> bigList = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 100000; i++) {
            bigList.add(new RoughSortPointDTO(
                "P" + i, 
                "Point" + i, 
                random.nextDouble(), 
                random.nextDouble() * 1000, 
                random.nextInt(5) + 1
            ));
        }
        
        // 测试不同排序方式的性能
        testSortPerformance("Lambda表达式", bigList, 
            list -> list.sort((a, b) -> Double.compare(b.getNearProb(), a.getNearProb())));
        
        testSortPerformance("方法引用+reversed", bigList, 
            list -> list.sort(Comparator.comparing(RoughSortPointDTO::getNearProb).reversed()));
        
        testSortPerformance("传统匿名类", bigList, 
            list -> list.sort(new Comparator<RoughSortPointDTO>() {
                @Override
                public int compare(RoughSortPointDTO o1, RoughSortPointDTO o2) {
                    return Double.compare(o2.getNearProb(), o1.getNearProb());
                }
            }));
    }
    
    /**
     * 测试排序性能
     */
    private static void testSortPerformance(String methodName, List<RoughSortPointDTO> originalList, 
                                          java.util.function.Consumer<List<RoughSortPointDTO>> sortMethod) {
        List<RoughSortPointDTO> testList = new ArrayList<>(originalList);
        
        long startTime = System.nanoTime();
        sortMethod.accept(testList);
        long endTime = System.nanoTime();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        System.out.printf("%-15s: %.2f ms%n", methodName, durationMs);
    }
    
    /**
     * 打印列表内容
     */
    private static void printList(List<RoughSortPointDTO> list) {
        for (int i = 0; i < Math.min(list.size(), 5); i++) {
            System.out.printf("  %d. %s%n", i + 1, list.get(i));
        }
        if (list.size() > 5) {
            System.out.println("  ... (共" + list.size() + "条记录)");
        }
    }
}

/**
 * 实际应用场景示例
 */
class RealWorldExample {
    
    // 模拟推荐上车点的业务场景
    public static void demonstratePickupPointRecommendation() {
        System.out.println("\n=== 🚗 实际业务场景：推荐上车点排序 ===");
        
        List<ComparatorSortExample.RoughSortPointDTO> pickupPoints = Arrays.asList(
            new ComparatorSortExample.RoughSortPointDTO("P001", "万达广场门口", 0.95, 50.0, 1),
            new ComparatorSortExample.RoughSortPointDTO("P002", "地铁2号线出口", 0.88, 120.0, 2),
            new ComparatorSortExample.RoughSortPointDTO("P003", "公交站台", 0.82, 80.0, 3),
            new ComparatorSortExample.RoughSortPointDTO("P004", "停车场入口", 0.78, 200.0, 2),
            new ComparatorSortExample.RoughSortPointDTO("P005", "小区门口", 0.92, 30.0, 1)
        );
        
        System.out.println("原始上车点列表：");
        pickupPoints.forEach(point -> System.out.println("  " + point));
        
        // 核心排序逻辑：按接近概率降序排序
        pickupPoints.sort(Comparator.comparing(ComparatorSortExample.RoughSortPointDTO::getNearProb).reversed());
        
        System.out.println("\n按接近概率排序后的推荐列表：");
        for (int i = 0; i < pickupPoints.size(); i++) {
            ComparatorSortExample.RoughSortPointDTO point = pickupPoints.get(i);
            System.out.printf("  推荐%d: %s (概率: %.0f%%)%n", 
                i + 1, point.getPointName(), point.getNearProb() * 100);
        }
        
        System.out.println("\n💡 业务含义：");
        System.out.println("- nearProb 表示用户选择该上车点的概率");
        System.out.println("- 概率越高，越容易被用户接受");
        System.out.println("- 通过降序排序，优先推荐高概率的上车点");
        System.out.println("- 提升用户体验和接单成功率");
    }
    
    public static void main(String[] args) {
        demonstratePickupPointRecommendation();
    }
} 