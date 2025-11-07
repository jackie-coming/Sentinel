/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.cluster.client;

import com.alibaba.csp.sentinel.cluster.ClusterMetadataKeys;
import com.alibaba.csp.sentinel.cluster.TokenResult;

/**
 * TokenResult 辅助工具类
 * <p>
 * 提供便捷方法从 TokenResult 的 attachments 中获取集群流控的元数据信息
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * TokenResult result = clusterClient.requestToken(ruleId, 1, false);
 *
 * // 获取路由信息
 * String server = TokenResultHelper.getRoutedServer(result);
 * System.out.println("Routed to: " + server);
 *
 * // 获取性能信息
 * long rt = TokenResultHelper.getResponseTime(result);
 * System.out.println("Response time: " + rt + "ms");
 *
 * // 获取异常信息（如果请求失败）
 * String error = TokenResultHelper.getExceptionMessage(result);
 * if (error != null) {
 *     System.err.println("Error: " + error);
 * }
 * }</pre>
 *
 * @author guojiaxiong
 * @see ClusterMetadataKeys
 * @since 1.8.9
 */
public class TokenResultHelper {

    /**
     * 获取实际路由的 Token Server 地址
     *
     * @param result TokenResult 对象
     * @return 服务器地址（格式：host:port），如果不存在则返回 null
     */
    public static String getRoutedServer(TokenResult result) {
        return getAttachment(result, ClusterMetadataKeys.KEY_ROUTED_SERVER);
    }

    /**
     * 获取规则 ID
     *
     * @param result TokenResult 对象
     * @return 规则 ID，如果不存在则返回 null
     */
    public static String getRuleId(TokenResult result) {
        return getAttachment(result, ClusterMetadataKeys.KEY_RULE_ID);
    }

    /**
     * 获取规则 ID（Long 类型）
     *
     * @param result TokenResult 对象
     * @return 规则 ID，如果不存在或解析失败则返回 null
     */
    public static Long getRuleIdAsLong(TokenResult result) {
        String ruleId = getRuleId(result);
        if (ruleId == null) {
            return null;
        }
        try {
            return Long.parseLong(ruleId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取规则名称
     *
     * @param result TokenResult 对象
     * @return 规则名称，如果不存在则返回 null
     */
    public static String getRuleName(TokenResult result) {
        return getAttachment(result, ClusterMetadataKeys.KEY_RULE_NAME);
    }

    /**
     * 获取响应时间（单次请求耗时）
     *
     * @param result TokenResult 对象
     * @return 响应时间（毫秒），如果不存在或解析失败则返回 -1
     */
    public static long getResponseTime(TokenResult result) {
        return getLongAttachment(result, ClusterMetadataKeys.KEY_RESPONSE_TIME, -1L);
    }

    /**
     * 获取总耗时（包含所有重试）
     *
     * @param result TokenResult 对象
     * @return 总耗时（毫秒），如果不存在或解析失败则返回 -1
     */
    public static long getTotalTime(TokenResult result) {
        return getLongAttachment(result, ClusterMetadataKeys.KEY_TOTAL_TIME, -1L);
    }

    /**
     * 获取重试次数
     *
     * @param result TokenResult 对象
     * @return 重试次数，如果不存在或解析失败则返回 0
     */
    public static int getRetryCount(TokenResult result) {
        return getIntAttachment(result, ClusterMetadataKeys.KEY_RETRY_COUNT, 0);
    }

    /**
     * 获取请求状态
     *
     * @param result TokenResult 对象
     * @return 请求状态（"success" 或 "failure"），如果不存在则返回 null
     */
    public static String getRequestStatus(TokenResult result) {
        return getAttachment(result, ClusterMetadataKeys.KEY_REQUEST_STATUS);
    }

    /**
     * 判断请求是否成功
     *
     * @param result TokenResult 对象
     * @return true 表示成功，false 表示失败或未知
     */
    public static boolean isSuccess(TokenResult result) {
        return "success".equals(getRequestStatus(result));
    }

    /**
     * 获取异常类型
     *
     * @param result TokenResult 对象
     * @return 异常类型的完整类名，如果不存在则返回 null
     */
    public static String getExceptionType(TokenResult result) {
        return getAttachment(result, ClusterMetadataKeys.KEY_EXCEPTION_TYPE);
    }

    /**
     * 获取异常消息
     *
     * @param result TokenResult 对象
     * @return 异常消息，如果不存在则返回 null
     */
    public static String getExceptionMessage(TokenResult result) {
        return getAttachment(result, ClusterMetadataKeys.KEY_EXCEPTION_MESSAGE);
    }

    /**
     * 获取请求时间戳
     *
     * @param result TokenResult 对象
     * @return 时间戳（毫秒），如果不存在或解析失败则返回 -1
     */
    public static long getRequestTimestamp(TokenResult result) {
        return getLongAttachment(result, ClusterMetadataKeys.KEY_REQUEST_TIMESTAMP, -1L);
    }

    /**
     * 判断是否有异常信息
     *
     * @param result TokenResult 对象
     * @return true 表示有异常信息
     */
    public static boolean hasException(TokenResult result) {
        return getExceptionMessage(result) != null;
    }

    /**
     * 获取完整的元数据信息摘要
     *
     * @param result TokenResult 对象
     * @return 格式化的元数据信息字符串
     */
    public static String getSummary(TokenResult result) {
        if (result == null) {
            return "TokenResult is null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("TokenResult Summary:\n");
        sb.append("  Status: ").append(result.getStatus()).append("\n");
        sb.append("  Server: ").append(getRoutedServer(result)).append("\n");
        sb.append("  Rule ID: ").append(getRuleId(result)).append("\n");
        sb.append("  Rule Name: ").append(getRuleName(result)).append("\n");
        sb.append("  Response Time: ").append(getResponseTime(result)).append("ms\n");
        sb.append("  Total Time: ").append(getTotalTime(result)).append("ms\n");
        sb.append("  Retry Count: ").append(getRetryCount(result)).append("\n");
        sb.append("  Request Status: ").append(getRequestStatus(result)).append("\n");

        if (hasException(result)) {
            sb.append("  Exception Type: ").append(getExceptionType(result)).append("\n");
            sb.append("  Exception Message: ").append(getExceptionMessage(result)).append("\n");
        }

        return sb.toString();
    }

    /**
     * 从 attachments 中获取字符串值
     */
    private static String getAttachment(TokenResult result, String key) {
        if (result == null || result.getAttachments() == null) {
            return null;
        }
        return result.getAttachments().get(key);
    }

    /**
     * 从 attachments 中获取 long 值
     */
    private static long getLongAttachment(TokenResult result, String key, long defaultValue) {
        String value = getAttachment(result, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 attachments 中获取 int 值
     */
    private static int getIntAttachment(TokenResult result, String key, int defaultValue) {
        String value = getAttachment(result, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 私有构造函数，防止实例化
     */
    private TokenResultHelper() {
        throw new AssertionError("No instances for you!");
    }
}

