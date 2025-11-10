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

/**
 * 负载均衡策略接口
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public interface LoadBalanceStrategy {

  /**
   * 从可用的服务器节点列表中选择一个
   *
   * @param nodes 服务器节点列表
   * @return 选中的服务器节点，如果没有可用节点则返回null
   */
  ServerNode select(List<ServerNode> nodes);

  /**
   * 重置策略状态（例如轮询计数器）
   */
  void reset();
}

