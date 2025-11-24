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
   * 优化：使用双重哈希提升虚拟节点分布的随机性
   */
  private void rebuildHashRing(List<ServerNode> nodes) {
    hashRing.clear();

    for (ServerNode node : nodes) {
      String nodeKey = node.getHost() + ":" + node.getPort();

      // 为每个真实节点创建多个虚拟节点
      for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
        // 优化1：使用双重哈希增强随机性
        // 第一次哈希：nodeKey + 序号
        String key1 = nodeKey + "#" + i;
        long hash1 = hash(key1);

        // 第二次哈希：基于第一次结果，进一步打散
        String key2 = nodeKey + "#" + i + "#" + hash1;
        long hash2 = hash(key2);

        hashRing.put(hash2, node);
      }
    }
  }

  /**
   * 计算字符串的哈希值
   * 使用优化的MurmurHash3算法，具有：
   * 1. 极好的雪崩效应（输入微小变化导致输出完全不同）
   * 2. 均匀的分布特性
   * 3. 高性能（比FNV1更快）
   * 4. 低碰撞率
   */
  private long hash(String key) {
    byte[] data = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return murmurHash3(data);
  }

  /**
   * MurmurHash3 64位实现 基于Austin Appleby的MurmurHash3算法
   */
  private long murmurHash3(byte[] data) {
    final long seed = 0x1234ABCD; // 固定种子，保证确定性
    final long m = 0xc6a4a7935bd1e995L;
    final int r = 47;

    long h = seed ^ (data.length * m);

    int length8 = data.length / 8;

    // Process 8 bytes at a time
    for (int i = 0; i < length8; i++) {
      final int i8 = i * 8;
      long k = ((long) data[i8] & 0xff)
          + (((long) data[i8 + 1] & 0xff) << 8)
          + (((long) data[i8 + 2] & 0xff) << 16)
          + (((long) data[i8 + 3] & 0xff) << 24)
          + (((long) data[i8 + 4] & 0xff) << 32)
          + (((long) data[i8 + 5] & 0xff) << 40)
          + (((long) data[i8 + 6] & 0xff) << 48)
          + (((long) data[i8 + 7] & 0xff) << 56);

      k *= m;
      k ^= k >>> r;
      k *= m;

      h ^= k;
      h *= m;
    }

    // Handle remaining bytes
    switch (data.length % 8) {
      case 7:
        h ^= (long) (data[(length8 * 8) + 6] & 0xff) << 48;
      case 6:
        h ^= (long) (data[(length8 * 8) + 5] & 0xff) << 40;
      case 5:
        h ^= (long) (data[(length8 * 8) + 4] & 0xff) << 32;
      case 4:
        h ^= (long) (data[(length8 * 8) + 3] & 0xff) << 24;
      case 3:
        h ^= (long) (data[(length8 * 8) + 2] & 0xff) << 16;
      case 2:
        h ^= (long) (data[(length8 * 8) + 1] & 0xff) << 8;
      case 1:
        h ^= (long) (data[length8 * 8] & 0xff);
        h *= m;
    }

    // Final mix
    h ^= h >>> r;
    h *= m;
    h ^= h >>> r;

    // Return positive value
    return h & Long.MAX_VALUE;
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

