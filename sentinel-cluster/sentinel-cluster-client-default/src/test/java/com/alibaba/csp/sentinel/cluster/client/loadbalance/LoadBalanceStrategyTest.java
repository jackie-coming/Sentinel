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
package com.alibaba.csp.sentinel.cluster.client.loadbalance;

import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig.ServerNode;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * 负载均衡策略测试类
 * <p>
 * 只测试支持规则ID路由的策略
 *
 * @author Test
 * @since 1.4.0
 */
public class LoadBalanceStrategyTest {

  private List<ServerNode> nodes;

  @Before
  public void setUp() {
    nodes = Arrays.asList(
        new ServerNode("192.168.1.10", 18730).setWeight(5),
        new ServerNode("192.168.1.11", 18730).setWeight(3),
        new ServerNode("192.168.1.12", 18730).setWeight(2)
    );
  }

  // ==================== 一致性哈希策略测试 ====================

  @Test
  public void testConsistentHashStrategy_Basic() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    ServerNode node = strategy.select(nodes);
    assertNotNull("一致性哈希应该返回节点", node);
  }

  @Test
  public void testConsistentHashStrategy_RuleIdConsistency() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    Long testRuleId = 1000L;

    // 同一规则ID应该路由到同一服务器
    ServerNode node1 = strategy.selectByRuleId(nodes, testRuleId);
    ServerNode node2 = strategy.selectByRuleId(nodes, testRuleId);
    ServerNode node3 = strategy.selectByRuleId(nodes, testRuleId);

    assertNotNull("第1次选择不应为null", node1);
    assertNotNull("第2次选择不应为null", node2);
    assertNotNull("第3次选择不应为null", node3);

    assertEquals("相同规则ID应该路由到同一服务器", node1.getHost(), node2.getHost());
    assertEquals("相同规则ID应该路由到同一服务器", node1.getPort(), node2.getPort());
    assertEquals("相同规则ID应该路由到同一服务器", node1.getHost(), node3.getHost());
    assertEquals("相同规则ID应该路由到同一服务器", node1.getPort(), node3.getPort());
  }

  @Test
  public void testConsistentHashStrategy_DifferentRuleIds() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    Set<String> selectedServers = new HashSet<>();

    // 100个不同的规则ID
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = strategy.selectByRuleId(nodes, ruleId);
      selectedServers.add(node.getHost() + ":" + node.getPort());
    }

    // 应该使用到所有服务器
    assertTrue("不同规则ID应该分布到多个服务器", selectedServers.size() > 1);
  }

  @Test
  public void testConsistentHashStrategy_NodeScaling() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    // 记录100个规则的初始路由
    Map<Long, String> originalRoutes = new HashMap<>();
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = strategy.selectByRuleId(nodes, ruleId);
      originalRoutes.put(ruleId, node.getHost() + ":" + node.getPort());
    }

    // 添加第4个节点
    List<ServerNode> expandedNodes = new ArrayList<>(nodes);
    expandedNodes.add(new ServerNode("192.168.1.13", 18730).setWeight(2));

    // 统计变化的路由数量
    int changedCount = 0;
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = strategy.selectByRuleId(expandedNodes, ruleId);
      String newRoute = node.getHost() + ":" + node.getPort();
      if (!originalRoutes.get(ruleId).equals(newRoute)) {
        changedCount++;
      }
    }

    // 一致性哈希应该最小化影响，变化应该少于50%
    assertTrue("节点扩容后变化应该少于50%", changedCount < 50);
  }

  @Test
  public void testConsistentHashStrategy_HashRingSize() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    // 触发哈希环构建
    strategy.select(nodes);

    // 验证哈希环大小
    int ringSize = strategy.getHashRingSize();
    // 3个节点 × 160个虚拟节点 = 480
    assertEquals("哈希环大小应该正确", 480, ringSize);
  }

  @Test
  public void testConsistentHashStrategy_Reset() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    strategy.select(nodes);
    assertTrue("构建后哈希环应该有节点", strategy.getHashRingSize() > 0);

    strategy.reset();
    assertEquals("重置后哈希环应该为空", 0, strategy.getHashRingSize());
  }

  @Test
  public void testConsistentHashStrategy_NullRuleId() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    ServerNode node = strategy.selectByRuleId(nodes, null);
    assertNull("规则ID为null应该返回null", node);
  }

  @Test
  public void testConsistentHashStrategy_NullNodes() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    ServerNode node = strategy.select(null);
    assertNull("空节点列表应该返回null", node);
  }

  @Test
  public void testConsistentHashStrategy_EmptyNodes() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    ServerNode node = strategy.select(new ArrayList<>());
    assertNull("空节点列表应该返回null", node);
  }

  @Test
  public void testConsistentHashStrategy_DisabledNodes() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    List<ServerNode> nodesWithDisabled = Arrays.asList(
        new ServerNode("192.168.1.10", 18730).setEnabled(true),
        new ServerNode("192.168.1.11", 18730).setEnabled(false), // 禁用
        new ServerNode("192.168.1.12", 18730).setEnabled(true)
    );

    Set<String> selectedServers = new HashSet<>();
    for (int i = 0; i < 10; i++) {
      ServerNode node = strategy.select(nodesWithDisabled);
      if (node != null) {
        selectedServers.add(node.getHost() + ":" + node.getPort());
      }
    }

    // 应该只选择启用的节点
    assertFalse("不应该选择禁用的节点", selectedServers.contains("192.168.1.11:18730"));
  }

  // ==================== 规则ID哈希策略测试 ====================

  @Test
  public void testRuleIdHashStrategy_Basic() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    ServerNode node = strategy.select(nodes);
    assertNotNull("规则ID哈希应该返回节点", node);
  }

  @Test
  public void testRuleIdHashStrategy_RuleIdConsistency() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    Long testRuleId = 1000L;

    // 同一规则ID应该路由到同一服务器
    ServerNode node1 = strategy.selectByRuleId(nodes, testRuleId);
    ServerNode node2 = strategy.selectByRuleId(nodes, testRuleId);
    ServerNode node3 = strategy.selectByRuleId(nodes, testRuleId);

    assertNotNull("第1次选择不应为null", node1);
    assertNotNull("第2次选择不应为null", node2);
    assertNotNull("第3次选择不应为null", node3);

    assertEquals("相同规则ID应该路由到同一服务器", node1.getHost(), node2.getHost());
    assertEquals("相同规则ID应该路由到同一服务器", node1.getPort(), node2.getPort());
    assertEquals("相同规则ID应该路由到同一服务器", node1.getHost(), node3.getHost());
    assertEquals("相同规则ID应该路由到同一服务器", node1.getPort(), node3.getPort());
  }

  @Test
  public void testRuleIdHashStrategy_Distribution() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    Map<String, Integer> distribution = new HashMap<>();

    // 100个不同的规则ID
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = strategy.selectByRuleId(nodes, ruleId);
      String key = node.getHost() + ":" + node.getPort();
      distribution.put(key, distribution.getOrDefault(key, 0) + 1);
    }

    // 应该分布到所有服务器
    assertEquals("应该使用到所有服务器", 3, distribution.size());

    // 每个服务器应该有请求
    for (Integer count : distribution.values()) {
      assertTrue("每个服务器都应该有请求", count > 0);
    }
  }

  @Test
  public void testRuleIdHashStrategy_NodeScaling() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    // 记录100个规则的初始路由
    Map<Long, String> originalRoutes = new HashMap<>();
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = strategy.selectByRuleId(nodes, ruleId);
      originalRoutes.put(ruleId, node.getHost() + ":" + node.getPort());
    }

    // 添加第4个节点
    List<ServerNode> expandedNodes = new ArrayList<>(nodes);
    expandedNodes.add(new ServerNode("192.168.1.13", 18730));

    // 统计变化的路由数量
    int changedCount = 0;
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = strategy.selectByRuleId(expandedNodes, ruleId);
      String newRoute = node.getHost() + ":" + node.getPort();
      if (!originalRoutes.get(ruleId).equals(newRoute)) {
        changedCount++;
      }
    }

    // 简单哈希会导致大部分路由变化，应该大于50%
    assertTrue("节点扩容后大部分路由应该改变", changedCount > 50);
  }

  @Test
  public void testRuleIdHashStrategy_NullNodes() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    ServerNode node = strategy.select(null);
    assertNull("空节点列表应该返回null", node);
  }

  @Test
  public void testRuleIdHashStrategy_EmptyNodes() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    ServerNode node = strategy.select(new ArrayList<>());
    assertNull("空节点列表应该返回null", node);
  }

  @Test
  public void testRuleIdHashStrategy_NullRuleId() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    ServerNode node = strategy.selectByRuleId(nodes, null);
    assertNull("规则ID为null应该返回null", node);
  }

  @Test
  public void testRuleIdHashStrategy_Reset() {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    strategy.select(nodes);
    strategy.reset();

    // 无状态策略，reset不应该影响功能
    ServerNode node = strategy.select(nodes);
    assertNotNull("重置后选择应该正常", node);
  }

  // ==================== 策略工厂测试 ====================

  @Test
  public void testLoadBalanceStrategyFactory_GetAllStrategies() {
    // 使用枚举类型获取策略（只有2个支持规则ID路由的策略）
    assertNotNull("应该能获取CONSISTENT_HASH策略",
        LoadBalanceStrategyFactory.getStrategy(LoadBalanceStrategyType.CONSISTENT_HASH));
    assertNotNull("应该能获取RULE_ID_HASH策略",
        LoadBalanceStrategyFactory.getStrategy(LoadBalanceStrategyType.RULE_ID_HASH));
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testLoadBalanceStrategyFactory_LegacyStringMode() {
    // 测试向后兼容的字符串模式（旧策略自动映射到一致性哈希）
    LoadBalanceStrategy strategy1 = LoadBalanceStrategyFactory.getStrategy("ROUND_ROBIN");
    assertNotNull("旧的ROUND_ROBIN策略应该映射到一致性哈希", strategy1);
    assertTrue("应该映射到一致性哈希", strategy1 instanceof ConsistentHashLoadBalanceStrategy);

    LoadBalanceStrategy strategy2 = LoadBalanceStrategyFactory.getStrategy("RANDOM");
    assertNotNull("旧的RANDOM策略应该映射到一致性哈希", strategy2);
    assertTrue("应该映射到一致性哈希", strategy2 instanceof ConsistentHashLoadBalanceStrategy);

    LoadBalanceStrategy strategy3 = LoadBalanceStrategyFactory.getStrategy("WEIGHTED");
    assertNotNull("旧的WEIGHTED策略应该映射到一致性哈希", strategy3);
    assertTrue("应该映射到一致性哈希", strategy3 instanceof ConsistentHashLoadBalanceStrategy);
  }

  @Test
  public void testLoadBalanceStrategyFactory_NullStrategy() {
    LoadBalanceStrategy strategy = LoadBalanceStrategyFactory.getStrategy((LoadBalanceStrategyType) null);
    assertNotNull("null策略应该返回默认策略", strategy);
    assertTrue("null策略应该返回一致性哈希策略",
        strategy instanceof ConsistentHashLoadBalanceStrategy);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testLoadBalanceStrategyFactory_UnknownStringStrategy() {
    LoadBalanceStrategy strategy = LoadBalanceStrategyFactory.getStrategy("UNKNOWN");
    assertNotNull("未知策略应该返回默认策略", strategy);
    assertTrue("未知策略应该返回一致性哈希策略",
        strategy instanceof ConsistentHashLoadBalanceStrategy);
  }

  @Test
  public void testLoadBalanceStrategyType_FromString() {
    // 测试支持的策略
    assertEquals("应该能从字符串转换为枚举", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("CONSISTENT_HASH"));
    assertEquals("应该能从字符串转换为枚举", LoadBalanceStrategyType.RULE_ID_HASH,
        LoadBalanceStrategyType.fromString("RULE_ID_HASH"));

    // 测试大小写不敏感
    assertEquals("应该不区分大小写", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("consistent_hash"));

    // 测试未知值返回默认值（一致性哈希）
    assertEquals("未知值应该返回默认值", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("UNKNOWN"));
    assertEquals("null应该返回默认值", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString(null));

    // 测试旧策略名称映射到一致性哈希
    assertEquals("旧的ROUND_ROBIN应该映射到CONSISTENT_HASH", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("ROUND_ROBIN"));
    assertEquals("旧的RANDOM应该映射到CONSISTENT_HASH", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("RANDOM"));
    assertEquals("旧的WEIGHT应该映射到CONSISTENT_HASH", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("WEIGHT"));
  }

  @Test
  public void testLoadBalanceStrategyType_SupportsRuleIdRouting() {
    // 所有策略都支持规则ID路由
    assertTrue("一致性哈希应该支持规则ID路由",
        LoadBalanceStrategyType.CONSISTENT_HASH.supportsRuleIdRouting());
    assertTrue("规则ID哈希应该支持规则ID路由",
        LoadBalanceStrategyType.RULE_ID_HASH.supportsRuleIdRouting());
  }

  @Test
  public void testLoadBalanceStrategyType_GetDescription() {
    assertEquals("一致性哈希描述应该正确", "一致性哈希",
        LoadBalanceStrategyType.CONSISTENT_HASH.getDescription());
    assertEquals("规则ID哈希描述应该正确", "规则ID哈希",
        LoadBalanceStrategyType.RULE_ID_HASH.getDescription());
  }

  @Test
  public void testLoadBalanceStrategyType_AllValues() {
    // 验证只有2个枚举值
    LoadBalanceStrategyType[] values = LoadBalanceStrategyType.values();
    assertEquals("应该只有2个策略", 2, values.length);

    // 验证都支持规则ID路由
    for (LoadBalanceStrategyType type : values) {
      assertTrue("所有策略都应该支持规则ID路由", type.supportsRuleIdRouting());
    }
  }

  // ==================== 并发测试 ====================

  @Test
  public void testConcurrentAccess_ConsistentHash() throws InterruptedException {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++) {
      final long baseRuleId = i * 100L;
      threads[i] = new Thread(() -> {
        for (long ruleId = baseRuleId; ruleId < baseRuleId + 100; ruleId++) {
          ServerNode node = strategy.selectByRuleId(nodes, ruleId);
          assertNotNull("并发选择不应返回null", node);
        }
      });
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }
  }

  @Test
  public void testConcurrentAccess_RuleIdHash() throws InterruptedException {
    RuleIdHashLoadBalanceStrategy strategy = new RuleIdHashLoadBalanceStrategy();

    int threadCount = 10;
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++) {
      final long baseRuleId = i * 100L;
      threads[i] = new Thread(() -> {
        for (long ruleId = baseRuleId; ruleId < baseRuleId + 100; ruleId++) {
          ServerNode node = strategy.selectByRuleId(nodes, ruleId);
          assertNotNull("并发选择不应返回null", node);
        }
      });
      threads[i].start();
    }

    for (Thread thread : threads) {
      thread.join();
    }
  }

  // ==================== 策略对比测试 ====================

  @Test
  public void testCompareStrategies_ScalingImpact() {
    ConsistentHashLoadBalanceStrategy consistentHash = new ConsistentHashLoadBalanceStrategy();
    RuleIdHashLoadBalanceStrategy ruleIdHash = new RuleIdHashLoadBalanceStrategy();

    // 记录100个规则的初始路由
    Map<Long, String> consistentHashRoutes = new HashMap<>();
    Map<Long, String> ruleIdHashRoutes = new HashMap<>();

    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node1 = consistentHash.selectByRuleId(nodes, ruleId);
      consistentHashRoutes.put(ruleId, node1.getHost() + ":" + node1.getPort());

      ServerNode node2 = ruleIdHash.selectByRuleId(nodes, ruleId);
      ruleIdHashRoutes.put(ruleId, node2.getHost() + ":" + node2.getPort());
    }

    // 添加第4个节点
    List<ServerNode> expandedNodes = new ArrayList<>(nodes);
    expandedNodes.add(new ServerNode("192.168.1.13", 18730));

    // 统计一致性哈希的变化
    int consistentHashChanges = 0;
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = consistentHash.selectByRuleId(expandedNodes, ruleId);
      String newRoute = node.getHost() + ":" + node.getPort();
      if (!consistentHashRoutes.get(ruleId).equals(newRoute)) {
        consistentHashChanges++;
      }
    }

    // 统计规则ID哈希的变化
    int ruleIdHashChanges = 0;
    for (long ruleId = 1L; ruleId <= 100; ruleId++) {
      ServerNode node = ruleIdHash.selectByRuleId(expandedNodes, ruleId);
      String newRoute = node.getHost() + ":" + node.getPort();
      if (!ruleIdHashRoutes.get(ruleId).equals(newRoute)) {
        ruleIdHashChanges++;
      }
    }

    // 一致性哈希的变化应该远小于规则ID哈希
    assertTrue("一致性哈希的影响应该小于规则ID哈希",
        consistentHashChanges < ruleIdHashChanges);
    assertTrue("一致性哈希的变化应该少于50%", consistentHashChanges < 50);
    assertTrue("规则ID哈希的变化应该大于50%", ruleIdHashChanges > 50);
  }
}
