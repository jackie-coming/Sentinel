import java.nio.charset.StandardCharsets;

/**
 * Redis HSET 单个成员大小计算工具
 */
public class RedisHsetSizeCalculator {
    
    public static void main(String[] args) {
        // Redis HSET 的 field 和 value
        String field = "trackData"; // 假设的field名
        String value = "{\"bikeNo\":\"5310587124\",\"logTime\":\"2025-06-20 12:14:41.159\",\"trackStateInfo\":\"{\\\"bikeNo\\\": \\\"5310587124\\\", \\\"lastConfidenceInfo\\\": \\\"{\\\\\\\"lastConfidenceLng\\\\\\\": 114.32674835848334, \\\\\\\"lastConfidenceLat\\\\\\\": 38.09755131505201, \\\\\\\"lastConfidenceTime\\\\\\\": 1750392880993, \\\\\\\"lastConfidencePosType\\\\\\\": 0, \\\\\\\"lastConfidenceCell\\\\\\\": \\\\\\\"[\\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\", \\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\"]\\\\\\\"}\\\", \\\"lastGpsInfo\\\": \\\"{\\\\\\\"lastGpsLng\\\\\\\": 114.32674835848334, \\\\\\\"lastGpsLat\\\\\\\": 38.09755131505201, \\\\\\\"lastGpsTime\\\\\\\": 1750392880993, \\\\\\\"lastGpsCell\\\\\\\": \\\\\\\"[\\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\", \\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\"]\\\\\\\", \\\\\\\"cov\\\\\\\": [[1.8731128236653216e-10, -5.04608102421184e-12], [-5.046081024211834e-12, 1.0616875924875262e-10]]}\\\", \\\"illegalMovement\\\": 0, \\\"drift\\\": 0, \\\"cov\\\": [[1.8731128236653216e-10, -5.04608102421184e-12], [-5.046081024211834e-12, 1.0616875924875262e-10]]}\"";
        
        System.out.println("=== Redis HSET 单个成员大小计算 ===\n");
        
        // 计算HSET成员大小
        calculateHsetMemberSize(field, value);
        
        // 分析内存结构
        analyzeHsetMemoryStructure(field, value);
        
        // 批量数据影响分析
        batchDataAnalysis(field, value);
        
        // 优化建议
        hsetOptimizationSuggestions(field, value);
    }
    
    /**
     * 计算HSET单个成员的大小
     */
    private static void calculateHsetMemberSize(String field, String value) {
        System.out.println("📏 HSET 成员大小计算:");
        
        // Field 大小
        byte[] fieldBytes = field.getBytes(StandardCharsets.UTF_8);
        System.out.printf("Field (\"%s\"): %d 字节\n", field, fieldBytes.length);
        
        // Value 大小
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        System.out.printf("Value: %d 字节 (%.2f KB)\n", valueBytes.length, valueBytes.length / 1024.0);
        
        // 单个成员总大小（field + value）
        int memberSize = fieldBytes.length + valueBytes.length;
        System.out.printf("成员总大小 (field + value): %d 字节 (%.2f KB)\n", memberSize, memberSize / 1024.0);
        
        // Redis Hash 内部开销
        int hashOverhead = calculateHashOverhead(field, value);
        System.out.printf("Redis Hash 内部开销: ~%d 字节\n", hashOverhead);
        
        // 实际内存占用
        int totalMemory = memberSize + hashOverhead;
        System.out.printf("实际内存占用: ~%d 字节 (%.2f KB)\n", totalMemory, totalMemory / 1024.0);
        
        System.out.println();
    }
    
    /**
     * 分析Hash内存结构
     */
    private static void analyzeHsetMemoryStructure(String field, String value) {
        System.out.println("🔍 Redis Hash 内存结构分析:");
        
        // Hash表结构开销
        System.out.println("Hash 表结构组成:");
        System.out.println("  • Hash 对象头: ~16 字节");
        System.out.println("  • Hash 表指针: ~8 字节");
        System.out.println("  • Field 存储: " + field.getBytes(StandardCharsets.UTF_8).length + " 字节");
        System.out.println("  • Value 存储: " + value.getBytes(StandardCharsets.UTF_8).length + " 字节");
        System.out.println("  • Hash 节点开销: ~24 字节 (指针 + 元数据)");
        
        // 编码方式分析
        int valueSize = value.getBytes(StandardCharsets.UTF_8).length;
        String encoding = valueSize > 512 ? "hashtable" : "ziplist";
        System.out.printf("预期编码方式: %s\n", encoding);
        
        if ("ziplist".equals(encoding)) {
            System.out.println("  • ziplist 更节省内存，但查找性能较低");
            System.out.println("  • 适合小数据量的Hash");
        } else {
            System.out.println("  • hashtable 查找性能好，但内存开销较大");
            System.out.println("  • 适合大数据量或频繁访问的Hash");
        }
        
        System.out.println();
    }
    
    /**
     * 批量数据影响分析
     */
    private static void batchDataAnalysis(String field, String value) {
        System.out.println("📊 批量数据内存影响分析:");
        
        int singleMemberSize = calculateTotalMemberSize(field, value);
        
        // 不同数量级的内存占用
        int[] quantities = {100, 1000, 10000, 100000, 1000000};
        
        System.out.println("数据量\t\t内存占用\t\t平均每条");
        System.out.println("----------------------------------------");
        
        for (int qty : quantities) {
            double totalMB = (singleMemberSize * qty) / (1024.0 * 1024.0);
            double avgKB = singleMemberSize / 1024.0;
            
            if (totalMB < 1) {
                System.out.printf("%d条\t\t%.2f KB\t\t%.2f KB\n", qty, totalMB * 1024, avgKB);
            } else if (totalMB < 1024) {
                System.out.printf("%d条\t\t%.2f MB\t\t%.2f KB\n", qty, totalMB, avgKB);
            } else {
                System.out.printf("%d条\t\t%.2f GB\t\t%.2f KB\n", qty, totalMB / 1024, avgKB);
            }
        }
        
        System.out.println();
    }
    
    /**
     * HSET优化建议
     */
    private static void hsetOptimizationSuggestions(String field, String value) {
        System.out.println("💡 Redis HSET 优化建议:");
        
        int valueSize = value.getBytes(StandardCharsets.UTF_8).length;
        
        System.out.println("\n1. 数据结构优化:");
        if (valueSize > 1024) {
            System.out.println("   ⚠️ Value较大，建议考虑以下优化:");
            System.out.println("   • 将大JSON拆分为多个小的Hash field");
            System.out.println("   • 使用数据压缩 (如JSON压缩、gzip)");
            System.out.println("   • 考虑使用二进制序列化 (MessagePack, Protobuf)");
        }
        
        System.out.println("\n2. Hash配置优化:");
        System.out.println("   • hash-max-ziplist-entries: 控制ziplist转hashtable的entry数量");
        System.out.println("   • hash-max-ziplist-value: 控制ziplist转hashtable的value大小");
        System.out.printf("   • 当前value大小 %d 字节，建议设置 hash-max-ziplist-value >= %d\n", 
                         valueSize, valueSize);
        
        System.out.println("\n3. 存储策略建议:");
        System.out.println("   • 如果是时序数据，考虑使用 Redis Streams");
        System.out.println("   • 如果需要范围查询，考虑使用 Sorted Set");
        System.out.println("   • 如果是缓存场景，设置合适的TTL");
        
        System.out.println("\n4. 性能优化:");
        System.out.println("   • 使用 HMGET 批量获取多个field");
        System.out.println("   • 避免使用 HGETALL 获取大Hash的所有数据");
        System.out.println("   • 考虑使用 HSCAN 遍历大Hash");
        
        // 压缩效果预估
        double compressedSize = valueSize * 0.3; // 假设70%压缩率
        System.out.printf("\n5. 压缩效果预估:\n");
        System.out.printf("   • 原始大小: %d 字节\n", valueSize);
        System.out.printf("   • 压缩后: ~%.0f 字节 (节省 %.1f%%)\n", 
                         compressedSize, (1 - compressedSize / valueSize) * 100);
    }
    
    /**
     * 计算Hash内部开销
     */
    private static int calculateHashOverhead(String field, String value) {
        // Redis Hash 内部结构开销估算
        int objectHeader = 16;      // Redis对象头
        int hashTablePointer = 8;   // Hash表指针
        int hashNode = 24;          // Hash节点开销 (指针 + 元数据)
        int keyOverhead = 4;        // Key长度前缀等
        int valueOverhead = 4;      // Value长度前缀等
        
        return objectHeader + hashTablePointer + hashNode + keyOverhead + valueOverhead;
    }
    
    /**
     * 计算单个成员的总大小（包含开销）
     */
    private static int calculateTotalMemberSize(String field, String value) {
        int fieldSize = field.getBytes(StandardCharsets.UTF_8).length;
        int valueSize = value.getBytes(StandardCharsets.UTF_8).length;
        int overhead = calculateHashOverhead(field, value);
        
        return fieldSize + valueSize + overhead;
    }
} 