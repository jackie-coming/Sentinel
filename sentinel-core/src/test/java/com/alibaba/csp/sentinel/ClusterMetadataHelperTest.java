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
package com.alibaba.csp.sentinel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.csp.sentinel.cluster.ClusterMetadataKeys;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slotchain.StringResourceWrapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for {@link ClusterMetadataHelper}
 *
 * @author Eric Zhao
 * @since 1.8.x
 */
public class ClusterMetadataHelperTest {

    @Before
    public void setUp() {
        // 确保清理旧的 Context
        if (ContextUtil.getContext() != null) {
            ContextUtil.exit();
        }
        ContextUtil.enter("test-context");
    }

    @After
    public void tearDown() {
        // 清理 cluster metadata
        Context context = ContextUtil.getContext();
        if (context != null) {
            context.setClusterMetadata(null);
        }
        ContextUtil.exit();
    }

    @Test
    public void testGetMetadataFromEntry() {
        Context context = ContextUtil.getContext();
        StringResourceWrapper resourceWrapper = new StringResourceWrapper("test-resource",
            EntryType.IN);
        CtEntry entry = new CtEntry(resourceWrapper, null, context);

        // Prepare cluster metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.100:8720");
        metadata.put(ClusterMetadataKeys.KEY_RULE_ID, "1000");
        metadata.put(ClusterMetadataKeys.KEY_RULE_NAME, "test_rule");
        metadata.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "50");
        metadata.put(ClusterMetadataKeys.KEY_TOTAL_TIME, "100");
        metadata.put(ClusterMetadataKeys.KEY_RETRY_COUNT, "2");
        metadata.put(ClusterMetadataKeys.KEY_REQUEST_TIMESTAMP, "1699876543210");
        metadata.put(ClusterMetadataKeys.KEY_REQUEST_STATUS, "success");

        context.setClusterMetadata(metadata);

        // Test getters
        assertEquals("192.168.1.100:8720", ClusterMetadataHelper.getRoutedServer(entry));
        assertEquals(Long.valueOf(1000L), ClusterMetadataHelper.getRuleId(entry));
        assertEquals("test_rule", ClusterMetadataHelper.getRuleName(entry));
        assertEquals(Long.valueOf(50L), ClusterMetadataHelper.getResponseTime(entry));
        assertEquals(Long.valueOf(100L), ClusterMetadataHelper.getTotalTime(entry));
        assertEquals(Integer.valueOf(2), ClusterMetadataHelper.getRetryCount(entry));
        assertEquals(Long.valueOf(1699876543210L),
            ClusterMetadataHelper.getRequestTimestamp(entry));
        assertEquals("success", ClusterMetadataHelper.getRequestStatus(entry));
        assertTrue(ClusterMetadataHelper.isSuccess(entry));
    }

    @Test
    public void testGetMetadataFromEntryWithFailure() {
        Context context = ContextUtil.getContext();
        StringResourceWrapper resourceWrapper = new StringResourceWrapper("test-resource",
            EntryType.IN);
        CtEntry entry = new CtEntry(resourceWrapper, null, context);

        // Prepare failure metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.101:8720");
        metadata.put(ClusterMetadataKeys.KEY_RULE_ID, "1001");
        metadata.put(ClusterMetadataKeys.KEY_RULE_NAME, "failed_rule");
        metadata.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "80");
        metadata.put(ClusterMetadataKeys.KEY_TOTAL_TIME, "200");
        metadata.put(ClusterMetadataKeys.KEY_RETRY_COUNT, "3");
        metadata.put(ClusterMetadataKeys.KEY_REQUEST_STATUS, "failure");
        metadata.put(ClusterMetadataKeys.KEY_EXCEPTION_TYPE, "java.net.ConnectException");
        metadata.put(ClusterMetadataKeys.KEY_EXCEPTION_MESSAGE, "Connection refused");

        context.setClusterMetadata(metadata);

        // Test failure status
        assertEquals("failure", ClusterMetadataHelper.getRequestStatus(entry));
        assertFalse(ClusterMetadataHelper.isSuccess(entry));
        assertEquals("java.net.ConnectException", ClusterMetadataHelper.getExceptionType(entry));
        assertEquals("Connection refused", ClusterMetadataHelper.getExceptionMessage(entry));
        assertEquals("failed_rule", ClusterMetadataHelper.getRuleName(entry));
    }

    @Test
    public void testGetAllMetadata() {
        Context context = ContextUtil.getContext();
        StringResourceWrapper resourceWrapper = new StringResourceWrapper("test-resource",
            EntryType.IN);
        CtEntry entry = new CtEntry(resourceWrapper, null, context);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.100:8720");
        metadata.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "50");
        context.setClusterMetadata(metadata);

        Map<String, String> allMetadata = ClusterMetadataHelper.getAll(entry);
        assertEquals(2, allMetadata.size());
        assertTrue(allMetadata.containsKey(ClusterMetadataKeys.KEY_ROUTED_SERVER));
        assertTrue(allMetadata.containsKey(ClusterMetadataKeys.KEY_RESPONSE_TIME));
    }

    @Test
    public void testGetSummary() {
        Context context = ContextUtil.getContext();
        StringResourceWrapper resourceWrapper = new StringResourceWrapper("test-resource",
            EntryType.IN);
        CtEntry entry = new CtEntry(resourceWrapper, null, context);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.100:8720");
        metadata.put(ClusterMetadataKeys.KEY_RULE_ID, "1000");
        metadata.put(ClusterMetadataKeys.KEY_RULE_NAME, "test_rule");
        metadata.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "50");
        metadata.put(ClusterMetadataKeys.KEY_TOTAL_TIME, "100");
        metadata.put(ClusterMetadataKeys.KEY_RETRY_COUNT, "2");
        metadata.put(ClusterMetadataKeys.KEY_REQUEST_STATUS, "success");
        context.setClusterMetadata(metadata);

        String summary = ClusterMetadataHelper.getSummary(entry);
        assertNotNull(summary);
        assertTrue(summary.contains("192.168.1.100:8720"));
        assertTrue(summary.contains("1000"));
        assertTrue(summary.contains("test_rule"));
        assertTrue(summary.contains("50ms"));
        assertTrue(summary.contains("success"));
    }

    @Test
    public void testGetMetadataFromNullEntry() {
        assertNull(ClusterMetadataHelper.getRoutedServer(null));
        assertNull(ClusterMetadataHelper.getRuleId(null));
        assertNull(ClusterMetadataHelper.getRuleName(null));
        assertEquals(-1L, ClusterMetadataHelper.getResponseTime(null).longValue());
        assertEquals(-1L, ClusterMetadataHelper.getTotalTime(null).longValue());
        assertEquals(0, ClusterMetadataHelper.getRetryCount(null).intValue());
        assertEquals(-1L, ClusterMetadataHelper.getRequestTimestamp(null).longValue());
        assertNull(ClusterMetadataHelper.getRequestStatus(null));
        assertFalse(ClusterMetadataHelper.isSuccess(null));
    }

    @Test
    public void testGetMetadataFromEntryWithNoMetadata() {
        Context context = ContextUtil.getContext();
        StringResourceWrapper resourceWrapper = new StringResourceWrapper("test-resource",
            EntryType.IN);
        CtEntry entry = new CtEntry(resourceWrapper, null, context);

        // No metadata set
        assertNull(ClusterMetadataHelper.getRoutedServer(entry));
        assertNull(ClusterMetadataHelper.getRuleId(entry));
        assertNull(ClusterMetadataHelper.getRuleName(entry));
        assertEquals(-1L, ClusterMetadataHelper.getResponseTime(entry).longValue());
        assertEquals(-1L, ClusterMetadataHelper.getTotalTime(entry).longValue());
        assertEquals(0, ClusterMetadataHelper.getRetryCount(entry).intValue());
        assertFalse(ClusterMetadataHelper.isSuccess(entry));

        Map<String, String> allMetadata = ClusterMetadataHelper.getAll(entry);
        assertTrue(allMetadata.isEmpty());
    }
}


