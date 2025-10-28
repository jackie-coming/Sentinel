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
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 随机负载均衡策略
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public class RandomLoadBalanceStrategy implements LoadBalanceStrategy {

  private final Random random = new Random();

  @Override
  public ServerNode select(List<ServerNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }

    // 只选择启用的节点
    List<ServerNode> enabledNodes = nodes.stream()
        .filter(ServerNode::isEnabled)
        .collect(Collectors.toList());

    if (enabledNodes.isEmpty()) {
      return null;
    }

    int index = random.nextInt(enabledNodes.size());
    return enabledNodes.get(index);
  }

  @Override
  public void reset() {
    // Random策略不需要重置
  }
}

