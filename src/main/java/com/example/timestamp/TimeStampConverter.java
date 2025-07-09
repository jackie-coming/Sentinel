package com.example.timestamp;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class TimeStampConverter {
    
    public static void main(String[] args) {
        // 测试秒级时间戳转换
        long secondsTimestamp = 1749717700L;
        System.out.println("秒级时间戳转换结果：" + getDateFromSeconds(secondsTimestamp));
        
        // 获取当前时间戳（毫秒）
        long timestamp = System.currentTimeMillis();
        
        // 方法1：使用SimpleDateFormat（传统方式）
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateStr1 = sdf.format(new Date(timestamp));
        System.out.println("方法1 - SimpleDateFormat: " + dateStr1);
        
        // 方法2：使用Java 8的DateTimeFormatter（推荐方式）
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.systemDefault()
        );
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String dateStr2 = dateTime.format(formatter);
        System.out.println("方法2 - DateTimeFormatter: " + dateStr2);
        
        // 方法3：使用Instant直接转换
        String dateStr3 = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("方法3 - Instant: " + dateStr3);
        
        // 方法4：自定义格式
        DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒");
        String dateStr4 = dateTime.format(customFormatter);
        System.out.println("方法4 - 自定义格式: " + dateStr4);
    }
    
    /**
     * 将秒级时间戳转换为日期字符串
     * @param seconds 秒级时间戳
     * @return 格式化后的日期字符串
     */
    public static String getDateFromSeconds(long seconds) {
        // 将秒转换为毫秒
        long milliseconds = seconds * 1000;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(milliseconds));
    }
} 