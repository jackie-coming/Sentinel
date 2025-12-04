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
package com.hellobikemap.csp.sentinel;

import com.hellobikemap.csp.sentinel.cluster.ClusterMetadataKeys;
import com.hellobikemap.csp.sentinel.context.Context;
import com.hellobikemap.csp.sentinel.util.StringUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for extracting cluster flow control metadata from {@link Entry}.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * Entry entry = null;
 * try {
 *     entry = SphU.entry("myResource");
 *
 *     // Get cluster metadata
 *     String server = ClusterMetadataHelper.getRoutedServer(entry);
 *     Long rt = ClusterMetadataHelper.getResponseTime(entry);
 *     String ruleName = ClusterMetadataHelper.getRuleName(entry);
 *
 *     System.out.println("Routed to: " + server + ", RT: " + rt + "ms");
 *
 *     // ... your business logic
 * } catch (BlockException e) {
 *     // handle block exception
 * } finally {
 *     if (entry != null) {
 *         entry.exit();
 *     }
 * }
 * }</pre>
 *
 * @author Eric Zhao
 * @see ClusterMetadataKeys
 * @since 1.8.x
 */
public final class ClusterMetadataHelper {

    private ClusterMetadataHelper() {
    }

    /**
     * Get cluster metadata from the entry's context.
     *
     * @param entry the entry
     * @return unmodifiable map of cluster metadata, or empty map if no metadata is available
     */
    private static Map<String, String> getMetadata(Entry entry) {
        if (entry == null) {
            return Collections.emptyMap();
        }

        Context context = getContext(entry);
        if (context == null) {
            return Collections.emptyMap();
        }

        return context.getClusterMetadata();
    }

    /**
     * Extract context from entry (works with CtEntry).
     */
    private static Context getContext(Entry entry) {
        if (entry == null) {
            return null;
        }

        // CtEntry has a context field, use reflection to access it
        try {
            java.lang.reflect.Field contextField = entry.getClass().getDeclaredField("context");
            contextField.setAccessible(true);
            return (Context) contextField.get(entry);
        } catch (Exception e) {
            // If reflection fails, return null
            return null;
        }
    }

    /**
     * Get the routed Token Server address.
     *
     * @param entry the entry
     * @return server address (e.g., "192.168.1.100:8720"), or null if not available
     */
    public static String getRoutedServer(Entry entry) {
        return getMetadata(entry).get(ClusterMetadataKeys.KEY_ROUTED_SERVER);
    }

    /**
     * Get the rule ID.
     *
     * @param entry the entry
     * @return rule ID, or null if not available
     */
    public static Long getRuleId(Entry entry) {
        String ruleIdStr = getMetadata(entry).get(ClusterMetadataKeys.KEY_RULE_ID);
        return StringUtil.isNotBlank(ruleIdStr) ? Long.parseLong(ruleIdStr) : null;
    }

    /**
     * Get the serverCostTime.
     *
     * @param entry the entry
     * @return serverCostTime, or null if not available
     */
    public static Long getServerCostTime(Entry entry) {
        String serverCostTime = getMetadata(entry).get(ClusterMetadataKeys.SERVER_COST_TIME);
        return StringUtil.isNotBlank(serverCostTime) ? Long.parseLong(serverCostTime) : null;
    }

    /**
     * Get the rule name.
     *
     * @param entry the entry
     * @return rule name, or null if not available
     */
    public static String getRuleName(Entry entry) {
        return entry.getResourceWrapper().getName();
    }

    /**
     * Get the response time (single request RT) in milliseconds.
     *
     * @param entry the entry
     * @return response time in ms, or -1 if not available
     */
    public static Long getResponseTime(Entry entry) {
        String rtStr = getMetadata(entry).get(ClusterMetadataKeys.KEY_RESPONSE_TIME);
        return StringUtil.isNotBlank(rtStr) ? Long.parseLong(rtStr) : -1L;
    }

    /**
     * Get the total time (including retries) in milliseconds.
     *
     * @param entry the entry
     * @return total time in ms, or -1 if not available
     */
    public static Long getTotalTime(Entry entry) {
        String totalTimeStr = getMetadata(entry).get(ClusterMetadataKeys.KEY_TOTAL_TIME);
        return StringUtil.isNotBlank(totalTimeStr) ? Long.parseLong(totalTimeStr) : -1L;
    }

    /**
     * Get the retry count.
     *
     * @param entry the entry
     * @return retry count, or 0 if not available
     */
    public static Integer getRetryCount(Entry entry) {
        String retryCountStr = getMetadata(entry).get(ClusterMetadataKeys.KEY_RETRY_COUNT);
        return StringUtil.isNotBlank(retryCountStr) ? Integer.parseInt(retryCountStr) : 0;
    }

    /**
     * Get the request timestamp.
     *
     * @param entry the entry
     * @return request timestamp in milliseconds, or -1 if not available
     */
    public static Long getRequestTimestamp(Entry entry) {
        String timestampStr = getMetadata(entry).get(ClusterMetadataKeys.KEY_REQUEST_TIMESTAMP);
        return StringUtil.isNotBlank(timestampStr) ? Long.parseLong(timestampStr) : -1L;
    }

    /**
     * Get the request status.
     *
     * @param entry the entry
     * @return request status ("success" or "failure"), or null if not available
     */
    public static String getRequestStatus(Entry entry) {
        return getMetadata(entry).get(ClusterMetadataKeys.KEY_REQUEST_STATUS);
    }

    /**
     * Check if the cluster request was successful.
     *
     * @param entry the entry
     * @return true if request was successful, false otherwise
     */
    public static boolean isSuccess(Entry entry) {
        return "success".equals(getRequestStatus(entry));
    }

    /**
     * Get the exception type (when request failed).
     *
     * @param entry the entry
     * @return exception type (full class name), or null if not available
     */
    public static String getExceptionType(Entry entry) {
        return getMetadata(entry).get(ClusterMetadataKeys.KEY_EXCEPTION_TYPE);
    }

    /**
     * Get the exception message (when request failed).
     *
     * @param entry the entry
     * @return exception message, or null if not available
     */
    public static String getExceptionMessage(Entry entry) {
        return getMetadata(entry).get(ClusterMetadataKeys.KEY_EXCEPTION_MESSAGE);
    }

    /**
     * Get all cluster metadata.
     *
     * @param entry the entry
     * @return a copy of all metadata, or empty map if not available
     */
    public static Map<String, String> getAll(Entry entry) {
        return new HashMap<>(getMetadata(entry));
    }

    /**
     * Get a formatted summary of cluster metadata.
     *
     * @param entry the entry
     * @return formatted summary string
     */
    public static String getSummary(Entry entry) {
        if (entry == null) {
            return "Entry is null";
        }

        Map<String, String> metadata = getMetadata(entry);
        if (metadata.isEmpty()) {
            return "No cluster metadata available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ClusterMetadata{");
        sb.append("server=").append(getRoutedServer(entry));
        sb.append(", ruleId=").append(getRuleId(entry));
        sb.append(", ruleName=").append(getRuleName(entry));
        sb.append(", rt=").append(getResponseTime(entry)).append("ms");
        sb.append(", totalTime=").append(getTotalTime(entry)).append("ms");
        sb.append(", retries=").append(getRetryCount(entry));
        sb.append(", status=").append(getRequestStatus(entry));

        if (!isSuccess(entry)) {
            sb.append(", exceptionType=").append(getExceptionType(entry));
            sb.append(", exceptionMessage=").append(getExceptionMessage(entry));
        }

        sb.append("}");
        return sb.toString();
    }
}


