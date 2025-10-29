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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig.ServerNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/**
 * 负载均衡策略测试类
 * <p>
 * 仅测试一致性哈希策略
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

  @Test
  public void testConsistentHashStrategy_LoadDistribution() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

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

  // ==================== 策略工厂测试 ====================

  @Test
  public void testLoadBalanceStrategyFactory_GetStrategy() {
    // 使用枚举类型获取策略
    assertNotNull("应该能获取CONSISTENT_HASH策略",
        LoadBalanceStrategyFactory.getStrategy(LoadBalanceStrategyType.CONSISTENT_HASH));

    assertTrue("应该返回一致性哈希策略",
        LoadBalanceStrategyFactory.getStrategy(LoadBalanceStrategyType.CONSISTENT_HASH)
            instanceof ConsistentHashLoadBalanceStrategy);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testLoadBalanceStrategyFactory_LegacyStringMode() {
    // 测试向后兼容的字符串模式（所有策略都返回一致性哈希）
    LoadBalanceStrategy strategy1 = LoadBalanceStrategyFactory.getStrategy("ROUND_ROBIN");
    assertNotNull("旧的ROUND_ROBIN策略应该返回一致性哈希", strategy1);
    assertTrue("应该返回一致性哈希", strategy1 instanceof ConsistentHashLoadBalanceStrategy);

    LoadBalanceStrategy strategy2 = LoadBalanceStrategyFactory.getStrategy("RANDOM");
    assertNotNull("旧的RANDOM策略应该返回一致性哈希", strategy2);
    assertTrue("应该返回一致性哈希", strategy2 instanceof ConsistentHashLoadBalanceStrategy);

    LoadBalanceStrategy strategy3 = LoadBalanceStrategyFactory.getStrategy("WEIGHTED");
    assertNotNull("旧的WEIGHTED策略应该返回一致性哈希", strategy3);
    assertTrue("应该返回一致性哈希", strategy3 instanceof ConsistentHashLoadBalanceStrategy);
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
    // 测试所有输入都返回一致性哈希
    assertEquals("应该返回一致性哈希", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("CONSISTENT_HASH"));

    assertEquals("未知值应该返回一致性哈希", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString("UNKNOWN"));

    assertEquals("null应该返回一致性哈希", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString(null));

    assertEquals("空字符串应该返回一致性哈希", LoadBalanceStrategyType.CONSISTENT_HASH,
        LoadBalanceStrategyType.fromString(""));
  }

  @Test
  public void testLoadBalanceStrategyType_SupportsRuleIdRouting() {
    // 一致性哈希支持规则ID路由
    assertTrue("一致性哈希应该支持规则ID路由",
        LoadBalanceStrategyType.CONSISTENT_HASH.supportsRuleIdRouting());
  }

  @Test
  public void testLoadBalanceStrategyType_GetDescription() {
    assertEquals("一致性哈希描述应该正确", "一致性哈希",
        LoadBalanceStrategyType.CONSISTENT_HASH.getDescription());
  }

  @Test
  public void testLoadBalanceStrategyType_AllValues() {
    // 验证只有1个枚举值
    LoadBalanceStrategyType[] values = LoadBalanceStrategyType.values();
    assertEquals("应该只有1个策略", 1, values.length);

    // 验证支持规则ID路由
    for (LoadBalanceStrategyType type : values) {
      assertTrue("策略应该支持规则ID路由", type.supportsRuleIdRouting());
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

  // ==================== 扩缩容影响测试 ====================

  @Test
  public void testConsistentHashStrategy_MinimalScalingImpact() {
    ConsistentHashLoadBalanceStrategy strategy = new ConsistentHashLoadBalanceStrategy();

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

    // 一致性哈希应该最小化影响
    assertTrue("节点扩容后变化应该少于50%", changedCount < 50);
    System.out.println("扩容影响: " + changedCount + "/100 规则发生路由变化");
  }
}
