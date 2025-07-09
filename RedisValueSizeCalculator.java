import java.nio.charset.StandardCharsets;

/**
 * Redis 值大小计算工具
 */
public class RedisValueSizeCalculator {
    
    public static void main(String[] args) {
        // 你提供的Redis值
        String redisValue = "{\"bikeNo\":\"5310587124\",\"logTime\":\"2025-06-20 12:14:41.159\",\"trackStateInfo\":\"{\\\"bikeNo\\\": \\\"5310587124\\\", \\\"lastConfidenceInfo\\\": \\\"{\\\\\\\"lastConfidenceLng\\\\\\\": 114.32674835848334, \\\\\\\"lastConfidenceLat\\\\\\\": 38.09755131505201, \\\\\\\"lastConfidenceTime\\\\\\\": 1750392880993, \\\\\\\"lastConfidencePosType\\\\\\\": 0, \\\\\\\"lastConfidenceCell\\\\\\\": \\\\\\\"[\\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\", \\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\"]\\\\\\\"}\\\", \\\"lastGpsInfo\\\": \\\"{\\\\\\\"lastGpsLng\\\\\\\": 114.32674835848334, \\\\\\\"lastGpsLat\\\\\\\": 38.09755131505201, \\\\\\\"lastGpsTime\\\\\\\": 1750392880993, \\\\\\\"lastGpsCell\\\\\\\": \\\\\\\"[\\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\", \\\\\\\\\\\\\\\"0\\\\\\\\\\\\\\\"]\\\\\\\", \\\\\\\"cov\\\\\\\": [[1.8731128236653216e-10, -5.04608102421184e-12], [-5.046081024211834e-12, 1.0616875924875262e-10]]}\\\", \\\"illegalMovement\\\": 0, \\\"drift\\\": 0, \\\"cov\\\": [[1.8731128236653216e-10, -5.04608102421184e-12], [-5.046081024211834e-12, 1.0616875924875262e-10]]}\"}";
        
        System.out.println("=== Redis 值大小计算 ===\n");
        
        // 计算不同编码下的大小
        calculateSize(redisValue);
        
        // 分析内容结构
        analyzeContent(redisValue);
        
        // 优化建议
        optimizationSuggestions(redisValue);
    }
    
    /**
     * 计算不同编码格式下的大小
     */
    private static void calculateSize(String value) {
        System.out.println("📏 大小计算结果:");
        
        // UTF-8 编码（Redis默认）
        byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
        System.out.printf("UTF-8 编码: %d 字节 (%.2f KB)\n", utf8Bytes.length, utf8Bytes.length / 1024.0);
        
        // ASCII 编码（如果适用）
        byte[] asciiBytes = value.getBytes(StandardCharsets.US_ASCII);
        System.out.printf("ASCII 编码: %d 字节 (%.2f KB)\n", asciiBytes.length, asciiBytes.length / 1024.0);
        
        // 字符串长度
        System.out.printf("字符串长度: %d 个字符\n", value.length());
        
        // Redis 内存开销估算（包括key、过期时间等元数据）
        int redisOverhead = 64; // Redis对象头、过期时间等开销
        int totalRedisMemory = utf8Bytes.length + redisOverhead;
        System.out.printf("Redis 总内存占用: ~%d 字节 (%.2f KB)\n", totalRedisMemory, totalRedisMemory / 1024.0);
        
        System.out.println();
    }
    
    /**
     * 分析内容结构
     */
    private static void analyzeContent(String value) {
        System.out.println("🔍 内容结构分析:");
        
        // 统计转义字符
        int escapeCount = countOccurrences(value, "\\");
        System.out.printf("转义字符数量: %d 个\n", escapeCount);
        
        // 统计引号
        int quoteCount = countOccurrences(value, "\"");
        System.out.printf("引号数量: %d 个\n", quoteCount);
        
        // 统计嵌套层级（大致估算）
        int maxNesting = calculateNestingLevel(value);
        System.out.printf("最大嵌套层级: ~%d 层\n", maxNesting);
        
        // 主要字段识别
        System.out.println("\n主要字段:");
        if (value.contains("bikeNo")) System.out.println("  ✓ bikeNo (自行车编号)");
        if (value.contains("logTime")) System.out.println("  ✓ logTime (日志时间)");
        if (value.contains("trackStateInfo")) System.out.println("  ✓ trackStateInfo (轨迹状态信息)");
        if (value.contains("lastConfidenceInfo")) System.out.println("  ✓ lastConfidenceInfo (最后置信信息)");
        if (value.contains("lastGpsInfo")) System.out.println("  ✓ lastGpsInfo (最后GPS信息)");
        
        System.out.println();
    }
    
    /**
     * 优化建议
     */
    private static void optimizationSuggestions(String value) {
        System.out.println("💡 优化建议:");
        
        int originalSize = value.getBytes(StandardCharsets.UTF_8).length;
        
        // 1. 压缩建议
        System.out.println("\n1. 数据压缩:");
        System.out.println("   • 使用 Redis 压缩 (如 LZ4, Snappy)");
        System.out.println("   • 预估压缩后大小: ~" + (originalSize * 0.3) + " 字节 (70%压缩率)");
        
        // 2. 结构优化
        System.out.println("\n2. 数据结构优化:");
        System.out.println("   • 减少JSON嵌套层级");
        System.out.println("   • 使用更短的字段名");
        System.out.println("   • 移除不必要的转义字符");
        
        // 3. 存储策略
        System.out.println("\n3. 存储策略:");
        System.out.println("   • 考虑使用 Redis Hash 替代单个大字符串");
        System.out.println("   • 将嵌套对象拆分为独立的 key");
        System.out.println("   • 使用二进制格式 (如 MessagePack, Protobuf)");
        
        // 4. 内存影响评估
        System.out.println("\n4. 内存影响评估:");
        System.out.printf("   • 1万条记录: %.2f MB\n", (originalSize * 10000) / (1024.0 * 1024.0));
        System.out.printf("   • 10万条记录: %.2f MB\n", (originalSize * 100000) / (1024.0 * 1024.0));
        System.out.printf("   • 100万条记录: %.2f MB\n", (originalSize * 1000000) / (1024.0 * 1024.0));
    }
    
    /**
     * 统计字符串中指定子串的出现次数
     */
    private static int countOccurrences(String str, String substring) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
    
    /**
     * 计算JSON嵌套层级（简单估算）
     */
    private static int calculateNestingLevel(String json) {
        int maxLevel = 0;
        int currentLevel = 0;
        
        for (char c : json.toCharArray()) {
            if (c == '{' || c == '[') {
                currentLevel++;
                maxLevel = Math.max(maxLevel, currentLevel);
            } else if (c == '}' || c == ']') {
                currentLevel--;
            }
        }
        
        return maxLevel;
    }
} 