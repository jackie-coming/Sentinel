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
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于规则ID的哈希负载均衡策略
 * <p>
 * 特性: 1. 简单的哈希取模算法 2. 同一个规则ID总是路由到同一个服务器 3. 性能开销小
 * <p>
 * 注意: - 当服务器节点数量变化时，大部分规则的路由目标会改变 - 如果需要最小化服务器变化的影响，请使用ConsistentHashLoadBalanceStrategy
 * <p>
 * 适用场景: - 服务器节点相对稳定 - 对性能要求高 - 集群限流（同一规则需要在同一服务器上计数）
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public class RuleIdHashLoadBalanceStrategy implements LoadBalanceStrategy {

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

    // 没有规则ID时，使用时间戳哈希（避免都路由到第一个）
    long hash = System.nanoTime();
    int index = (int) (Math.abs(hash) % enabledNodes.size());
    return enabledNodes.get(index);
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

    // 使用规则ID的哈希值取模
    int index = (int) (Math.abs(ruleId) % enabledNodes.size());
    return enabledNodes.get(index);
  }

  @Override
  public void reset() {
    // 无状态策略，不需要重置
  }
}

