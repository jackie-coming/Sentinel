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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenServerDescriptor;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig.ServerNode;
import com.alibaba.csp.sentinel.cluster.client.loadbalance.LoadBalanceStrategyType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 多服务器集群客户端测试类
 *
 * @author Test
 * @since 1.4.0
 */
public class MultiServerClusterTokenClientTest {

  private ClusterClientMultiServerConfig config;
  private MultiServerClusterTokenClient client;

  @Before
  public void setUp() {
    // 基础配置
    config = new ClusterClientMultiServerConfig()
        .addServerNode("127.0.0.1", 18730)
        .addServerNode("127.0.0.1", 18731)
        .addServerNode("127.0.0.1", 18732)
        .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH)
        .setEnableFailover(true)
        .setMaxRetries(2)
        .setHealthCheckInterval(5000);
  }

  @After
  public void tearDown() throws Exception {
    if (client != null) {
      client.stop();
      client = null;
    }
  }

  /**
   * 测试1: 基本初始化
   */
  @Test
  public void testBasicInitialization() {
    client = new MultiServerClusterTokenClient(config);
    assertNotNull("客户端应该成功创建", client);

    List<TokenServerDescriptor> allServers = client.getAllServers();
    assertEquals("应该有3个服务器", 3, allServers.size());
  }

  /**
   * 测试2: 配置验证
   */
  @Test(expected = IllegalArgumentException.class)
  public void testNullConfig() {
    new MultiServerClusterTokenClient(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyServerNodes() {
    ClusterClientMultiServerConfig emptyConfig = new ClusterClientMultiServerConfig();
    new MultiServerClusterTokenClient(emptyConfig);
  }

  /**
   * 测试3: 客户端启动和停止
   */
  @Test
  public void testStartAndStop() throws Exception {
    client = new MultiServerClusterTokenClient(config);

    // 启动前状态
    int initialState = client.getState();
    assertEquals("初始状态应该是OFF", ClientConstants.CLIENT_STATUS_OFF, initialState);

    // 启动
    client.start();
    Thread.sleep(500); // 等待启动完成

    // 注意: 由于没有真实的服务器，状态可能仍然是OFF
    // 这里只测试方法不抛异常

    // 停止
    client.stop();
    int finalState = client.getState();
    assertEquals("停止后状态应该是OFF", ClientConstants.CLIENT_STATUS_OFF, finalState);
  }

  /**
   * 测试4: 获取所有服务器
   */
  @Test
  public void testGetAllServers() {
    client = new MultiServerClusterTokenClient(config);

    List<TokenServerDescriptor> allServers = client.getAllServers();
    assertNotNull("服务器列表不应为null", allServers);
    assertEquals("应该有3个服务器", 3, allServers.size());

    // 验证服务器信息
    boolean hasServer1 = allServers.stream()
        .anyMatch(s -> s.getHost().equals("127.0.0.1") && s.getPort() == 18730);
    boolean hasServer2 = allServers.stream()
        .anyMatch(s -> s.getHost().equals("127.0.0.1") && s.getPort() == 18731);
    boolean hasServer3 = allServers.stream()
        .anyMatch(s -> s.getHost().equals("127.0.0.1") && s.getPort() == 18732);

    assertTrue("应该包含服务器1", hasServer1);
    assertTrue("应该包含服务器2", hasServer2);
    assertTrue("应该包含服务器3", hasServer3);
  }

  /**
   * 测试5: 获取健康的服务器
   */
  @Test
  public void testGetHealthyServers() throws Exception {
    client = new MultiServerClusterTokenClient(config);
    client.start();
    Thread.sleep(500);

    List<TokenServerDescriptor> healthyServers = client.getHealthyServers();
    assertNotNull("健康服务器列表不应为null", healthyServers);
    // 注意: 由于没有真实服务器，可能所有服务器都是不健康的
  }


  /**
   * 测试8: 一致性哈希负载均衡策略
   */
  @Test
  public void testConsistentHashLoadBalance() {
    config.setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);
    client = new MultiServerClusterTokenClient(config);

    assertNotNull("使用一致性哈希策略应该成功创建客户端", client);
  }

  /**
   * 测试9: 规则ID哈希负载均衡策略
   */
  @Test
  public void testRuleIdHashLoadBalance() {
    config.setLoadBalanceStrategy(LoadBalanceStrategyType.RULE_ID_HASH);
    client = new MultiServerClusterTokenClient(config);

    assertNotNull("使用规则ID哈希策略应该成功创建客户端", client);
  }

  /**
   * 测试10: 策略枚举值验证
   */
  @Test
  public void testLoadBalanceStrategyEnum() {
    // 测试所有枚举值（只有两个支持规则ID路由的策略）
    for (LoadBalanceStrategyType type : LoadBalanceStrategyType.values()) {
      ClusterClientMultiServerConfig testConfig = new ClusterClientMultiServerConfig()
          .addServerNode("127.0.0.1", 18730)
          .setLoadBalanceStrategy(type);

      MultiServerClusterTokenClient testClient = new MultiServerClusterTokenClient(testConfig);
      assertNotNull("策略 " + type + " 应该成功创建客户端", testClient);

      try {
        testClient.stop();
      } catch (Exception e) {
        // ignore
      }
    }
  }

  /**
   * 测试11: 策略类型描述
   */
  @Test
  public void testLoadBalanceStrategyDescription() {
    assertEquals("一致性哈希策略描述应该正确", "一致性哈希", LoadBalanceStrategyType.CONSISTENT_HASH.getDescription());
    assertEquals("规则ID哈希策略描述应该正确", "规则ID哈希", LoadBalanceStrategyType.RULE_ID_HASH.getDescription());
  }

  /**
   * 测试12: 所有策略都支持规则ID路由
   */
  @Test
  public void testSupportsRuleIdRouting() {
    // 所有策略都支持规则ID路由
    assertTrue("一致性哈希应该支持规则ID路由", LoadBalanceStrategyType.CONSISTENT_HASH.supportsRuleIdRouting());
    assertTrue("规则ID哈希应该支持规则ID路由", LoadBalanceStrategyType.RULE_ID_HASH.supportsRuleIdRouting());
  }

  /**
   * 测试13: 兼容旧策略名称（自动映射到一致性哈希）
   */
  @Test
  @SuppressWarnings("deprecation")
  public void testLegacyStringStrategy() {
    // 旧的策略名称会自动映射到一致性哈希
    config.setLoadBalanceStrategy("ROUND_ROBIN");
    client = new MultiServerClusterTokenClient(config);
    assertNotNull("字符串方式设置策略应该仍然有效", client);

    // 旧策略会自动转换为一致性哈希
    assertEquals("旧策略应该转换为一致性哈希", LoadBalanceStrategyType.CONSISTENT_HASH, config.getLoadBalanceStrategy());
  }

  /**
   * 测试14: 按规则ID路由的一致性
   */
  @Test
  public void testRuleIdRoutingConsistency() {
    config.setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);
    client = new MultiServerClusterTokenClient(config);

    Long testRuleId = 1000L;

    // 多次请求同一规则ID
    // 注意: 由于没有真实服务器连接，这里只测试不抛异常
    for (int i = 0; i < 5; i++) {
      TokenResult result = client.requestToken(testRuleId, 1, false);
      assertNotNull("结果不应为null", result);
    }
  }

  /**
   * 测试15: 动态配置更新 - 添加服务器
   */
  @Test
  public void testDynamicConfigUpdate_AddServers() throws Exception {
    client = new MultiServerClusterTokenClient(config);
    client.start();

    assertEquals("初始应该有3个服务器", 3, client.getAllServers().size());

    // 添加新服务器
    ClusterClientMultiServerConfig newConfig = new ClusterClientMultiServerConfig()
        .addServerNode("127.0.0.1", 18730)
        .addServerNode("127.0.0.1", 18731)
        .addServerNode("127.0.0.1", 18732)
        .addServerNode("127.0.0.1", 18733) // 新增
        .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);

    client.updateConfig(newConfig);

    assertEquals("更新后应该有4个服务器", 4, client.getAllServers().size());
  }

  /**
   * 测试16: 动态配置更新 - 移除服务器
   */
  @Test
  public void testDynamicConfigUpdate_RemoveServers() throws Exception {
    client = new MultiServerClusterTokenClient(config);
    client.start();

    assertEquals("初始应该有3个服务器", 3, client.getAllServers().size());

    // 移除一个服务器
    ClusterClientMultiServerConfig newConfig = new ClusterClientMultiServerConfig()
        .addServerNode("127.0.0.1", 18730)
        .addServerNode("127.0.0.1", 18731)
        .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);

    client.updateConfig(newConfig);

    assertEquals("更新后应该有2个服务器", 2, client.getAllServers().size());
  }

  /**
   * 测试17: 动态配置更新 - 切换负载均衡策略
   */
  @Test
  public void testDynamicConfigUpdate_ChangeStrategy() throws Exception {
    config.setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);
    client = new MultiServerClusterTokenClient(config);
    client.start();

    // 切换到规则ID哈希
    ClusterClientMultiServerConfig newConfig = new ClusterClientMultiServerConfig()
        .addServerNode("127.0.0.1", 18730)
        .addServerNode("127.0.0.1", 18731)
        .addServerNode("127.0.0.1", 18732)
        .setLoadBalanceStrategy(LoadBalanceStrategyType.RULE_ID_HASH);

    client.updateConfig(newConfig);

    // 验证不抛异常
    TokenResult result = client.requestToken(1000L, 1, false);
    assertNotNull("切换策略后请求应该正常", result);
  }

  /**
   * 测试18: 动态配置更新 - 无效配置
   */
  @Test
  public void testDynamicConfigUpdate_InvalidConfig() throws Exception {
    client = new MultiServerClusterTokenClient(config);
    client.start();

    int originalSize = client.getAllServers().size();

    // 尝试更新为null配置
    client.updateConfig(null);

    // 应该保持原有配置
    assertEquals("无效配置更新不应改变服务器数量",
        originalSize, client.getAllServers().size());
  }

  /**
   * 测试19: 故障转移配置
   */
  @Test
  public void testFailoverConfig() {
    config.setEnableFailover(true);
    config.setMaxRetries(3);

    client = new MultiServerClusterTokenClient(config);
    assertNotNull("启用故障转移应该成功创建客户端", client);

    // 验证配置已应用
    // 注意: 配置是私有的，这里只验证客户端创建成功
  }

  /**
   * 测试20: 禁用故障转移
   */
  @Test
  public void testFailoverDisabled() {
    config.setEnableFailover(false);
    client = new MultiServerClusterTokenClient(config);

    assertNotNull("禁用故障转移应该成功创建客户端", client);
  }

  /**
   * 测试21: 健康检查间隔配置
   */
  @Test
  public void testHealthCheckInterval() {
    // 设置不同的健康检查间隔
    config.setHealthCheckInterval(3000);
    client = new MultiServerClusterTokenClient(config);
    assertNotNull("设置健康检查间隔应该成功", client);

    // 禁用健康检查（间隔为0）
    ClusterClientMultiServerConfig configNoHealth = new ClusterClientMultiServerConfig()
        .addServerNode("127.0.0.1", 18730)
        .setHealthCheckInterval(0);

    MultiServerClusterTokenClient client2 = new MultiServerClusterTokenClient(configNoHealth);
    assertNotNull("禁用健康检查应该成功", client2);

    try {
      client2.stop();
    } catch (Exception e) {
      // ignore
    }
  }

  /**
   * 测试22: 服务器节点启用/禁用
   */
  @Test
  public void testServerNodeEnabled() {
    ServerNode disabledNode = new ServerNode("127.0.0.1", 18733)
        .setEnabled(false);

    ClusterClientMultiServerConfig configWithDisabled = new ClusterClientMultiServerConfig()
        .addServerNode("127.0.0.1", 18730)
        .addServerNode("127.0.0.1", 18731)
        .addServerNode(disabledNode);

    client = new MultiServerClusterTokenClient(configWithDisabled);

    List<TokenServerDescriptor> allServers = client.getAllServers();
    // 只有启用的服务器会被初始化
    assertEquals("只应该初始化启用的服务器", 2, allServers.size());
  }

  /**
   * 测试23: 当前服务器获取
   */
  @Test
  public void testCurrentServer() {
    client = new MultiServerClusterTokenClient(config);

    // 获取当前服务器（根据负载均衡策略选择）
    TokenServerDescriptor current = client.currentServer();
    // 注意: 如果所有服务器都不健康，可能返回null
    // 这里只验证方法不抛异常
  }

  /**
   * 测试24: 并发请求测试
   */
  @Test
  public void testConcurrentRequests() throws InterruptedException {
    config.setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);
    client = new MultiServerClusterTokenClient(config);

    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++) {
      final long ruleId = 1000L + i;
      threads[i] = new Thread(() -> {
        for (int j = 0; j < 10; j++) {
          TokenResult result = client.requestToken(ruleId, 1, false);
          assertNotNull("并发请求结果不应为null", result);
        }
      });
      threads[i].start();
    }

    // 等待所有线程完成
    for (Thread thread : threads) {
      thread.join();
    }
  }

  /**
   * 测试25: 不同规则ID的路由分布
   */
  @Test
  public void testRuleIdDistribution() {
    config.setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);
    client = new MultiServerClusterTokenClient(config);

    Map<String, Integer> distribution = new HashMap<>();

    // 发送多个不同规则ID的请求
    for (long ruleId = 1L; ruleId <= 30; ruleId++) {
      client.requestToken(ruleId, 1, false);
      TokenServerDescriptor server = client.currentServer();
      if (server != null) {
        String key = server.getHost() + ":" + server.getPort();
        distribution.put(key, distribution.getOrDefault(key, 0) + 1);
      }
    }

    // 验证有分布（不是所有请求都到同一个服务器）
    // 注意: 由于没有真实服务器，currentServer可能返回null
    // 这里只验证不抛异常
  }

  /**
   * 测试26: 重复启动和停止
   */
  @Test
  public void testMultipleStartStop() throws Exception {
    client = new MultiServerClusterTokenClient(config);

    // 多次启动
    client.start();
    client.start(); // 第二次启动应该被忽略

    // 多次停止
    client.stop();
    client.stop(); // 第二次停止应该被忽略

    // 不应该抛异常
  }

  /**
   * 测试27: 配置toString
   */
  @Test
  public void testConfigToString() {
    String configStr = config.toString();
    assertNotNull("配置toString不应为null", configStr);
    assertTrue("配置toString应该包含服务器信息", configStr.contains("serverNodes"));
  }

  /**
   * 测试28: ServerNode toString
   */
  @Test
  public void testServerNodeToString() {
    ServerNode node = new ServerNode("127.0.0.1", 18730, 5);
    String nodeStr = node.toString();
    assertNotNull("ServerNode toString不应为null", nodeStr);
    assertTrue("应该包含host信息", nodeStr.contains("127.0.0.1"));
    assertTrue("应该包含port信息", nodeStr.contains("18730"));
  }

  /**
   * 测试29: 请求优先级标记
   */
  @Test
  public void testPrioritizedRequest() {
    client = new MultiServerClusterTokenClient(config);

    // 测试优先级请求
    TokenResult result = client.requestToken(1000L, 1, true);
    assertNotNull("优先级请求结果不应为null", result);
  }

  /**
   * 测试30: 大量规则ID测试
   */
  @Test
  public void testLargeNumberOfRules() {
    config.setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);
    client = new MultiServerClusterTokenClient(config);

    // 测试1000个不同的规则ID
    for (long ruleId = 1L; ruleId <= 1000; ruleId++) {
      TokenResult result = client.requestToken(ruleId, 1, false);
      assertNotNull("规则ID " + ruleId + " 的结果不应为null", result);
    }
  }

  /**
   * 测试31: 参数流控请求
   */
  @Test
  public void testParamFlowRequest() {
    client = new MultiServerClusterTokenClient(config);

    List<Object> params = Arrays.asList("param1", "param2", 123);
    TokenResult result = client.requestParamToken(1000L, 1, params);

    assertNotNull("参数流控请求结果不应为null", result);
  }
}

