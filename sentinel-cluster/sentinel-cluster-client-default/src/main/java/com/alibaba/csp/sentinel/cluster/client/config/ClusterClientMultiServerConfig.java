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
package com.alibaba.csp.sentinel.cluster.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持多个Token Server的配置类
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public class ClusterClientMultiServerConfig {

  private List<ServerNode> serverNodes = new ArrayList<>();

  /**
   * 负载均衡策略: ROUND_ROBIN(轮询), RANDOM(随机), WEIGHT(权重), FAILOVER(故障转移)
   */
  private String loadBalanceStrategy = "ROUND_ROBIN";

  /**
   * 故障检测间隔(毫秒)
   */
  private int healthCheckInterval = 5000;

  /**
   * 连接失败重试次数
   */
  private int maxRetries = 2;

  /**
   * 是否启用自动故障转移
   */
  private boolean enableFailover = true;

  public static class ServerNode {

    private String host;
    private int port;
    private int weight = 1; // 权重，默认为1
    private boolean enabled = true; // 是否启用

    public ServerNode() {
    }

    public ServerNode(String host, int port) {
      this.host = host;
      this.port = port;
    }

    public ServerNode(String host, int port, int weight) {
      this.host = host;
      this.port = port;
      this.weight = weight;
    }

    public String getHost() {
      return host;
    }

    public ServerNode setHost(String host) {
      this.host = host;
      return this;
    }

    public int getPort() {
      return port;
    }

    public ServerNode setPort(int port) {
      this.port = port;
      return this;
    }

    public int getWeight() {
      return weight;
    }

    public ServerNode setWeight(int weight) {
      this.weight = weight;
      return this;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public ServerNode setEnabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    @Override
    public String toString() {
      return "ServerNode{" +
          "host='" + host + '\'' +
          ", port=" + port +
          ", weight=" + weight +
          ", enabled=" + enabled +
          '}';
    }
  }

  public List<ServerNode> getServerNodes() {
    return serverNodes;
  }

  public ClusterClientMultiServerConfig setServerNodes(List<ServerNode> serverNodes) {
    this.serverNodes = serverNodes;
    return this;
  }

  public ClusterClientMultiServerConfig addServerNode(ServerNode node) {
    this.serverNodes.add(node);
    return this;
  }

  public ClusterClientMultiServerConfig addServerNode(String host, int port) {
    this.serverNodes.add(new ServerNode(host, port));
    return this;
  }

  public ClusterClientMultiServerConfig addServerNode(String host, int port, int weight) {
    this.serverNodes.add(new ServerNode(host, port, weight));
    return this;
  }

  public String getLoadBalanceStrategy() {
    return loadBalanceStrategy;
  }

  public ClusterClientMultiServerConfig setLoadBalanceStrategy(String loadBalanceStrategy) {
    this.loadBalanceStrategy = loadBalanceStrategy;
    return this;
  }

  public int getHealthCheckInterval() {
    return healthCheckInterval;
  }

  public ClusterClientMultiServerConfig setHealthCheckInterval(int healthCheckInterval) {
    this.healthCheckInterval = healthCheckInterval;
    return this;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public ClusterClientMultiServerConfig setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }

  public boolean isEnableFailover() {
    return enableFailover;
  }

  public ClusterClientMultiServerConfig setEnableFailover(boolean enableFailover) {
    this.enableFailover = enableFailover;
    return this;
  }

  @Override
  public String toString() {
    return "ClusterClientMultiServerConfig{" +
        "serverNodes=" + serverNodes +
        ", loadBalanceStrategy='" + loadBalanceStrategy + '\'' +
        ", healthCheckInterval=" + healthCheckInterval +
        ", maxRetries=" + maxRetries +
        ", enableFailover=" + enableFailover +
        '}';
  }
}

