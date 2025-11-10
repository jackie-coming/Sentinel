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
package com.hellobikemap.csp.sentinel.cluster.client.loadbalance;

import com.hellobikemap.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig.ServerNode;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 一致性哈希负载均衡策略
 * <p>
 * 特性: 1. 同一个规则ID总是路由到同一个服务器 2. 服务器节点变化时，影响范围最小 3. 支持虚拟节点，提高负载均衡效果
 * <p>
 * 适用场景: - 集群限流（同一规则需要在同一服务器上计数） - 需要会话保持的场景 - 缓存亲和性要求高的场景
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public class ConsistentHashLoadBalanceStrategy implements LoadBalanceStrategy {

  /**
   * 虚拟节点数量，每个真实节点对应的虚拟节点数 虚拟节点越多，负载越均衡，但内存占用也越大
   */
  private static final int VIRTUAL_NODE_COUNT = 160;

  /**
   * 一致性哈希环 key: 哈希值 value: 服务器节点
   */
  private final TreeMap<Long, ServerNode> hashRing = new TreeMap<>();

  /**
   * 当前节点列表的哈希值，用于判断节点列表是否变化
   */
  private volatile int nodesHashCode = 0;

  @Override
  public ServerNode select(List<ServerNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }

    // 过滤出启用的节点
    List<ServerNode> enabledNodes = nodes.stream()
        .filter(ServerNode::isEnabled)
        .collect(Collectors.toList());

    if (enabledNodes.isEmpty()) {
      return null;
    }

    // 如果节点列表发生变化，重建哈希环
    int currentHashCode = enabledNodes.hashCode();
    if (currentHashCode != nodesHashCode) {
      synchronized (this) {
        if (currentHashCode != nodesHashCode) {
          rebuildHashRing(enabledNodes);
          nodesHashCode = currentHashCode;
        }
      }
    }

    // 没有路由键时，使用随机值（避免所有请求都路由到第一个节点）
    long hash = System.nanoTime();
    return selectByHash(hash);
  }

  /**
   * 根据规则ID选择服务器
   *
   * @param nodes  服务器节点列表
   * @param ruleId 规则ID
   * @return 选中的服务器节点
   */
  public ServerNode selectByRuleId(List<ServerNode> nodes, Long ruleId) {
    if (nodes == null || nodes.isEmpty() || ruleId == null) {
      return null;
    }

    // 过滤出启用的节点
    List<ServerNode> enabledNodes = nodes.stream()
        .filter(ServerNode::isEnabled)
        .collect(Collectors.toList());

    if (enabledNodes.isEmpty()) {
      return null;
    }

    // 如果节点列表发生变化，重建哈希环
    int currentHashCode = enabledNodes.hashCode();
    if (currentHashCode != nodesHashCode) {
      synchronized (this) {
        if (currentHashCode != nodesHashCode) {
          rebuildHashRing(enabledNodes);
          nodesHashCode = currentHashCode;
        }
      }
    }

    // 根据规则ID计算哈希值
    long hash = hash(ruleId.toString());
    return selectByHash(hash);
  }

  /**
   * 根据哈希值选择服务器
   */
  private ServerNode selectByHash(long hash) {
    // 在哈希环上查找第一个大于等于该哈希值的节点
    SortedMap<Long, ServerNode> tailMap = hashRing.tailMap(hash);

    // 如果找到了，返回第一个
    if (!tailMap.isEmpty()) {
      return tailMap.get(tailMap.firstKey());
    }

    // 如果没找到（说明hash值比环上所有值都大），返回环上的第一个节点（形成环状）
    return hashRing.firstEntry().getValue();
  }

  /**
   * 重建哈希环
   */
  private void rebuildHashRing(List<ServerNode> nodes) {
    hashRing.clear();

    for (ServerNode node : nodes) {
      // 为每个真实节点创建多个虚拟节点
      for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
        String virtualNodeKey = node.getHost() + ":" + node.getPort() + "#" + i;
        long hash = hash(virtualNodeKey);
        hashRing.put(hash, node);
      }
    }
  }

  /**
   * 计算字符串的哈希值 使用FNV1_32_HASH算法
   */
  private long hash(String key) {
    final int p = 16777619;
    long hash = 2166136261L;
    for (int i = 0; i < key.length(); i++) {
      hash = (hash ^ key.charAt(i)) * p;
    }
    hash += hash << 13;
    hash ^= hash >> 7;
    hash += hash << 3;
    hash ^= hash >> 17;
    hash += hash << 5;

    // 如果算出来的值为负数，取其绝对值
    if (hash < 0) {
      hash = Math.abs(hash);
    }
    return hash;
  }

  @Override
  public void reset() {
    synchronized (this) {
      hashRing.clear();
      nodesHashCode = 0;
    }
  }

  /**
   * 获取哈希环的大小（包含虚拟节点）
   */
  public int getHashRingSize() {
    return hashRing.size();
  }
}

