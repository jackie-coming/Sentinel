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
package com.hellobikemap.csp.sentinel.cluster.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.hellobikemap.csp.sentinel.cluster.ClusterMetadataKeys;
import com.hellobikemap.csp.sentinel.cluster.TokenResult;
import com.hellobikemap.csp.sentinel.cluster.TokenResultStatus;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * TokenResultHelper 测试类
 *
 * @author guojiaxiong
 */
public class TokenResultHelperTest {

  /**
   * 测试获取路由服务器地址
   */
  @Test
  public void testGetRoutedServer() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.100:8720");
    result.setAttachments(attachments);

    String server = TokenResultHelper.getRoutedServer(result);
    assertEquals("应该能正确获取路由服务器地址", "192.168.1.100:8720", server);
  }

  /**
   * 测试获取规则ID
   */
  @Test
  public void testGetRuleId() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_RULE_ID, "1000");
    result.setAttachments(attachments);

    String ruleId = TokenResultHelper.getRuleId(result);
    assertEquals("应该能正确获取规则ID", "1000", ruleId);

    Long ruleIdLong = TokenResultHelper.getRuleIdAsLong(result);
    assertEquals("应该能正确获取Long类型的规则ID", Long.valueOf(1000), ruleIdLong);
  }

  /**
   * 测试获取响应时间
   */
  @Test
  public void testGetResponseTime() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "50");
    result.setAttachments(attachments);

    long rt = TokenResultHelper.getResponseTime(result);
    assertEquals("应该能正确获取响应时间", 50L, rt);
  }

  /**
   * 测试获取总耗时
   */
  @Test
  public void testGetTotalTime() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_TOTAL_TIME, "150");
    result.setAttachments(attachments);

    long totalTime = TokenResultHelper.getTotalTime(result);
    assertEquals("应该能正确获取总耗时", 150L, totalTime);
  }

  /**
   * 测试获取重试次数
   */
  @Test
  public void testGetRetryCount() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_RETRY_COUNT, "3");
    result.setAttachments(attachments);

    int retryCount = TokenResultHelper.getRetryCount(result);
    assertEquals("应该能正确获取重试次数", 3, retryCount);
  }

  /**
   * 测试获取请求状态
   */
  @Test
  public void testGetRequestStatus() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_REQUEST_STATUS, "success");
    result.setAttachments(attachments);

    String status = TokenResultHelper.getRequestStatus(result);
    assertEquals("应该能正确获取请求状态", "success", status);

    assertTrue("应该能判断请求成功", TokenResultHelper.isSuccess(result));
  }

  /**
   * 测试获取异常信息
   */
  @Test
  public void testGetException() {
    TokenResult result = new TokenResult(TokenResultStatus.FAIL);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_EXCEPTION_TYPE, "java.net.ConnectException");
    attachments.put(ClusterMetadataKeys.KEY_EXCEPTION_MESSAGE, "Connection refused");
    result.setAttachments(attachments);

    String exceptionType = TokenResultHelper.getExceptionType(result);
    assertEquals("应该能正确获取异常类型",
        "java.net.ConnectException", exceptionType);

    String exceptionMessage = TokenResultHelper.getExceptionMessage(result);
    assertEquals("应该能正确获取异常消息",
        "Connection refused", exceptionMessage);

    assertTrue("应该能判断有异常", TokenResultHelper.hasException(result));
  }

  /**
   * 测试空值处理
   */
  @Test
  public void testNullHandling() {
    // 测试null result
    assertNull("null result应返回null", TokenResultHelper.getRoutedServer(null));
    assertEquals("null result的RT应返回-1", -1L, TokenResultHelper.getResponseTime(null));
    assertEquals("null result的重试次数应返回0", 0, TokenResultHelper.getRetryCount(null));

    // 测试没有attachments的result
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    assertNull("没有attachments应返回null", TokenResultHelper.getRoutedServer(result));
    assertEquals("没有attachments的RT应返回-1", -1L, TokenResultHelper.getResponseTime(result));
  }

  /**
   * 测试完整的成功场景
   */
  @Test
  public void testCompleteSuccessScenario() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();

    // 服务器信息
    attachments.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.100:8720");
    // 规则信息
    attachments.put(ClusterMetadataKeys.KEY_RULE_ID, "1000");
    attachments.put(ClusterMetadataKeys.KEY_RULE_NAME, "test_rule");
    // 性能信息
    attachments.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "45");
    attachments.put(ClusterMetadataKeys.KEY_TOTAL_TIME, "50");
    attachments.put(ClusterMetadataKeys.KEY_RETRY_COUNT, "0");
    attachments.put(ClusterMetadataKeys.KEY_REQUEST_TIMESTAMP, "1699876543210");
    // 状态信息
    attachments.put(ClusterMetadataKeys.KEY_REQUEST_STATUS, "success");

    result.setAttachments(attachments);

    // 验证所有字段
    assertEquals("192.168.1.100:8720", TokenResultHelper.getRoutedServer(result));
    assertEquals("1000", TokenResultHelper.getRuleId(result));
    assertEquals("test_rule", TokenResultHelper.getRuleName(result));
    assertEquals(45L, TokenResultHelper.getResponseTime(result));
    assertEquals(50L, TokenResultHelper.getTotalTime(result));
    assertEquals(0, TokenResultHelper.getRetryCount(result));
    assertEquals(1699876543210L, TokenResultHelper.getRequestTimestamp(result));
    assertTrue(TokenResultHelper.isSuccess(result));
    assertFalse(TokenResultHelper.hasException(result));
  }

  /**
   * 测试完整的失败场景
   */
  @Test
  public void testCompleteFailureScenario() {
    TokenResult result = new TokenResult(TokenResultStatus.FAIL);
    Map<String, String> attachments = new HashMap<>();

    // 服务器信息
    attachments.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.101:8720");
    // 规则信息
    attachments.put(ClusterMetadataKeys.KEY_RULE_ID, "1001");
    // 性能信息
    attachments.put(ClusterMetadataKeys.KEY_TOTAL_TIME, "500");
    attachments.put(ClusterMetadataKeys.KEY_RETRY_COUNT, "3");
    attachments.put(ClusterMetadataKeys.KEY_REQUEST_TIMESTAMP, "1699876543210");
    // 状态和异常信息
    attachments.put(ClusterMetadataKeys.KEY_REQUEST_STATUS, "failure");
    attachments.put(ClusterMetadataKeys.KEY_EXCEPTION_TYPE, "java.net.ConnectException");
    attachments.put(ClusterMetadataKeys.KEY_EXCEPTION_MESSAGE, "Connection refused");

    result.setAttachments(attachments);

    // 验证所有字段
    assertEquals("192.168.1.101:8720", TokenResultHelper.getRoutedServer(result));
    assertEquals("1001", TokenResultHelper.getRuleId(result));
    assertEquals(500L, TokenResultHelper.getTotalTime(result));
    assertEquals(3, TokenResultHelper.getRetryCount(result));
    assertFalse(TokenResultHelper.isSuccess(result));
    assertTrue(TokenResultHelper.hasException(result));
    assertEquals("java.net.ConnectException", TokenResultHelper.getExceptionType(result));
    assertEquals("Connection refused", TokenResultHelper.getExceptionMessage(result));
  }

  /**
   * 测试getSummary方法
   */
  @Test
  public void testGetSummary() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_ROUTED_SERVER, "192.168.1.100:8720");
    attachments.put(ClusterMetadataKeys.KEY_RULE_ID, "1000");
    attachments.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "50");
    attachments.put(ClusterMetadataKeys.KEY_REQUEST_STATUS, "success");
    result.setAttachments(attachments);

    String summary = TokenResultHelper.getSummary(result);
    assertNotNull("summary不应为null", summary);
    assertTrue("summary应包含服务器信息", summary.contains("192.168.1.100:8720"));
    assertTrue("summary应包含规则ID", summary.contains("1000"));
    assertTrue("summary应包含响应时间", summary.contains("50"));
  }

  /**
   * 测试无效数字格式处理
   */
  @Test
  public void testInvalidNumberFormat() {
    TokenResult result = new TokenResult(TokenResultStatus.OK);
    Map<String, String> attachments = new HashMap<>();
    attachments.put(ClusterMetadataKeys.KEY_RESPONSE_TIME, "invalid");
    attachments.put(ClusterMetadataKeys.KEY_RETRY_COUNT, "abc");
    attachments.put(ClusterMetadataKeys.KEY_RULE_ID, "not_a_number");
    result.setAttachments(attachments);

    // 应该返回默认值而不是抛异常
    assertEquals("无效的RT应返回-1", -1L, TokenResultHelper.getResponseTime(result));
    assertEquals("无效的重试次数应返回0", 0, TokenResultHelper.getRetryCount(result));
    assertNull("无效的规则ID(Long)应返回null", TokenResultHelper.getRuleIdAsLong(result));
  }

  /**
   * 测试常量定义
   */
  @Test
  public void testConstants() {
    assertEquals("routed_server", ClusterMetadataKeys.KEY_ROUTED_SERVER);
    assertEquals("rule_id", ClusterMetadataKeys.KEY_RULE_ID);
    assertEquals("rule_name", ClusterMetadataKeys.KEY_RULE_NAME);
    assertEquals("response_time", ClusterMetadataKeys.KEY_RESPONSE_TIME);
    assertEquals("total_time", ClusterMetadataKeys.KEY_TOTAL_TIME);
    assertEquals("retry_count", ClusterMetadataKeys.KEY_RETRY_COUNT);
    assertEquals("request_status", ClusterMetadataKeys.KEY_REQUEST_STATUS);
    assertEquals("exception_type", ClusterMetadataKeys.KEY_EXCEPTION_TYPE);
    assertEquals("exception_message", ClusterMetadataKeys.KEY_EXCEPTION_MESSAGE);
    assertEquals("request_timestamp", ClusterMetadataKeys.KEY_REQUEST_TIMESTAMP);
  }
}

