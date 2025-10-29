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

import com.alibaba.csp.sentinel.cluster.ClusterConstants;
import com.alibaba.csp.sentinel.cluster.ClusterErrorMessages;
import com.alibaba.csp.sentinel.cluster.ClusterTransportClient;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenServerDescriptor;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig.ServerNode;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfigManager;
import com.alibaba.csp.sentinel.cluster.client.loadbalance.LoadBalanceStrategy;
import com.alibaba.csp.sentinel.cluster.client.loadbalance.LoadBalanceStrategyFactory;
import com.alibaba.csp.sentinel.cluster.client.loadbalance.LoadBalanceStrategyType;
import com.alibaba.csp.sentinel.cluster.log.ClusterClientStatLogUtil;
import com.alibaba.csp.sentinel.cluster.request.ClusterRequest;
import com.alibaba.csp.sentinel.cluster.request.data.FlowRequestData;
import com.alibaba.csp.sentinel.cluster.request.data.ParamFlowRequestData;
import com.alibaba.csp.sentinel.cluster.response.ClusterResponse;
import com.alibaba.csp.sentinel.cluster.response.data.FlowTokenResponseData;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.util.StringUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 支持多个Token Server的集群客户端实现
 * <p>
 * 特性: 
 * 1. 支持配置多个Token Server 
 * 2. 采用一致性哈希负载均衡策略（支持平滑扩缩容） 
 * 3. 支持自动故障转移 
 * 4. 支持健康检查
 * 5. 支持动态配置更新（兼容 DefaultClusterTokenClient）
 * 6. 支持单服务器到多服务器的平滑切换
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public class MultiServerClusterTokenClient implements ClusterTokenClient {

  /**
   * 维护所有server的transport client映射 key: host:port value: transport client
   */
  private final Map<String, ClusterTransportClient> transportClientMap = new ConcurrentHashMap<>();

  /**
   * 维护所有server的描述信息映射 key: host:port value: server descriptor
   */
  private final Map<String, TokenServerDescriptor> serverDescriptorMap = new ConcurrentHashMap<>();

  /**
   * 服务器健康状态映射 key: host:port value: 是否健康
   */
  private final Map<String, Boolean> serverHealthMap = new ConcurrentHashMap<>();

  /**
   * 服务器连续失败次数映射 key: host:port value: 失败次数
   */
  private final Map<String, Integer> serverFailureCountMap = new ConcurrentHashMap<>();

  private ClusterClientMultiServerConfig config;
  private LoadBalanceStrategy loadBalanceStrategy;

  private final AtomicBoolean shouldStart = new AtomicBoolean(false);
  private final ScheduledExecutorService healthCheckExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "sentinel-cluster-client-health-checker");
        thread.setDaemon(true);
        return thread;
      });

  /**
   * 最大连续失败次数，超过此次数将标记服务器为不健康
   */
  private static final int MAX_FAILURE_COUNT = 3;

  /**
   * 构造函数 - 使用指定配置（默认启用动态配置）
   *
   * @param config 多服务器配置
   */
  public MultiServerClusterTokenClient(ClusterClientMultiServerConfig config) {
    if (config == null || config.getServerNodes() == null || config.getServerNodes().isEmpty()) {
      throw new IllegalArgumentException("Config and server nodes cannot be null or empty");
    }

    this.config = config;
    this.loadBalanceStrategy = LoadBalanceStrategyFactory.getStrategy(
        config.getLoadBalanceStrategy());

    initConnections();
    startHealthCheck();

    // 默认启用动态配置，自动注册监听器（参考 DefaultClusterTokenClient）
    registerConfigListeners();
  }

  /**
   * 构造函数 - 无参构造（用于 SPI 加载）
   * <p>
   * 创建一个空的客户端实例，不初始化任何连接。 通过动态配置监听器等待配置推送后自动初始化。
   * <p>
   * 适用场景： 1. 通过 SPI 自动加载 2. 完全依赖配置中心动态配置 3. 无需预先指定服务器地址
   */
  public MultiServerClusterTokenClient() {
    this.config = new ClusterClientMultiServerConfig();
    this.loadBalanceStrategy = LoadBalanceStrategyFactory.getStrategy(
        LoadBalanceStrategyType.CONSISTENT_HASH);

    // 不初始化连接，等待动态配置
    // 不启动健康检查，等待有服务器节点后再启动

    // 注册动态配置监听器
    registerConfigListeners();

    RecordLog.info("[MultiServerClusterTokenClient] Created empty client instance, "
        + "waiting for dynamic config update");
  }

  /**
   * 注册配置监听器（参考 DefaultClusterTokenClient 的构造函数）
   * <p>
   * 同时支持： 1. 单服务器配置变更（兼容 DefaultClusterTokenClient） 2. 多服务器配置变更（多服务器模式）
   * <p>
   * 用户无需手动配置，系统自动处理
   */
  private void registerConfigListeners() {

    // 监听器 多服务器配置变更（多服务器模式）
    ClusterClientMultiServerConfigManager.addConfigChangeObserver(
        new ClusterClientMultiServerConfigManager.MultiServerConfigChangeObserver() {
          @Override
          public void onMultiServerConfigChange(ClusterClientMultiServerConfig newConfig) {
            handleMultiServerChange(newConfig);
          }
        });

    RecordLog.info("[MultiServerClusterTokenClient] Dynamic config listeners registered, "
        + "supporting both single-server and multi-server configurations");
  }

  /**
   * 处理单服务器配置变更（兼容 DefaultClusterTokenClient 的 changeServer 方法）
   * <p>
   * 当用户使用 DefaultClusterTokenClient 的配置方式时， 自动将单服务器配置转换为多服务器配置
   *
   * @param assignConfig 单服务器分配配置
   */
  private void handleSingleServerChange(ClusterClientAssignConfig assignConfig) {
    if (assignConfig == null) {
      RecordLog.warn("[MultiServerClusterTokenClient] Empty assign config, ignoring");
      return;
    }

    String newHost = assignConfig.getServerHost();
    int newPort = assignConfig.getServerPort();

    if (StringUtil.isBlank(newHost) || newPort <= 0) {
      RecordLog.warn("[MultiServerClusterTokenClient] Invalid server config: {}:{}", newHost,
          newPort);
      return;
    }

    // 检查是否与当前配置相同
    if (isSameAsSingleServer(newHost, newPort)) {
      RecordLog.info("[MultiServerClusterTokenClient] Server config unchanged: {}:{}", newHost,
          newPort);
      return;
    }

    RecordLog.info("[MultiServerClusterTokenClient] Single server config changed to: {}:{}",
        newHost, newPort);

    // 将单服务器配置转换为多服务器配置
    ClusterClientMultiServerConfig newConfig = new ClusterClientMultiServerConfig()
        .addServerNode(newHost, newPort)
        .setLoadBalanceStrategy(config.getLoadBalanceStrategy())
        .setEnableFailover(config.isEnableFailover())
        .setMaxRetries(config.getMaxRetries())
        .setHealthCheckInterval(config.getHealthCheckInterval());

    // 应用新配置
    updateConfig(newConfig);
  }

  /**
   * 处理多服务器配置变更（多服务器配置模式）
   * <p>
   * 支持完整的多服务器配置动态更新，包括： - 多个服务器节点 - 负载均衡策略 - 故障转移配置 - 健康检查配置
   *
   * @param newConfig 新的多服务器配置
   */
  private void handleMultiServerChange(ClusterClientMultiServerConfig newConfig) {
    if (newConfig == null) {
      RecordLog.warn("[MultiServerClusterTokenClient] Empty multi-server config, ignoring");
      return;
    }

    if (!ClusterClientMultiServerConfigManager.isValidConfig(newConfig)) {
      RecordLog.warn("[MultiServerClusterTokenClient] Invalid multi-server config, ignoring: {}",
          newConfig);
      return;
    }

    RecordLog.info("[MultiServerClusterTokenClient] Multi-server config changed, "
        + "new config has {} servers", newConfig.getServerNodes().size());

    // 如果是首次配置（从空配置变为有配置）
    boolean isFirstConfig = config.getServerNodes().isEmpty();

    // 应用新的多服务器配置
    updateConfig(newConfig);

    // 如果是首次配置且客户端应该启动，则启动健康检查
    if (isFirstConfig && shouldStart.get()) {
      startHealthCheck();
      RecordLog.info("[MultiServerClusterTokenClient] First config received, health check started");
    }
  }

  /**
   * 检查是否与当前的单服务器配置相同
   */
  private boolean isSameAsSingleServer(String host, int port) {
    if (config.getServerNodes().size() != 1) {
      return false;
    }
    ServerNode currentNode = config.getServerNodes().get(0);
    return currentNode.getHost().equals(host) && currentNode.getPort() == port;
  }

  /**
   * 初始化到所有服务器的连接
   */
  private void initConnections() {
    for (ServerNode node : config.getServerNodes()) {
      if (node.isEnabled()) {
        String key = getServerKey(node.getHost(), node.getPort());
        try {
          ClusterTransportClient client = new NettyTransportClient(node.getHost(), node.getPort());
          transportClientMap.put(key, client);

          TokenServerDescriptor descriptor = new TokenServerDescriptor(node.getHost(),
              node.getPort());
          serverDescriptorMap.put(key, descriptor);

          serverHealthMap.put(key, true);
          serverFailureCountMap.put(key, 0);

          RecordLog.info("[MultiServerClusterTokenClient] Initialized connection to server: {}",
              key);
        } catch (Exception ex) {
          RecordLog.warn(
              "[MultiServerClusterTokenClient] Failed to initialize connection to server: " + key,
              ex);
          serverHealthMap.put(key, false);
        }
      }
    }
  }

  /**
   * 启动健康检查
   */
  private void startHealthCheck() {
    if (config == null || config.getHealthCheckInterval() <= 0) {
      return;
    }

    // 检查是否已经启动过健康检查
    if (healthCheckExecutor.isShutdown() || healthCheckExecutor.isTerminated()) {
      return;
    }

    try {
      healthCheckExecutor.scheduleAtFixedRate(() -> {
        try {
          performHealthCheck();
        } catch (Exception ex) {
          RecordLog.warn("[MultiServerClusterTokenClient] Health check error", ex);
        }
      }, config.getHealthCheckInterval(), config.getHealthCheckInterval(), TimeUnit.MILLISECONDS);

      RecordLog.info("[MultiServerClusterTokenClient] Health check started with interval: {} ms",
          config.getHealthCheckInterval());
    } catch (Exception ex) {
      RecordLog.warn("[MultiServerClusterTokenClient] Failed to start health check", ex);
    }
  }

  /**
   * 执行健康检查
   */
  private void performHealthCheck() {
    for (Map.Entry<String, ClusterTransportClient> entry : transportClientMap.entrySet()) {
      String serverKey = entry.getKey();
      ClusterTransportClient client = entry.getValue();

      boolean isHealthy = client != null && client.isReady();
      boolean wasHealthy = serverHealthMap.getOrDefault(serverKey, false);

      if (isHealthy != wasHealthy) {
        serverHealthMap.put(serverKey, isHealthy);
        RecordLog.info("[MultiServerClusterTokenClient] Server {} health status changed: {} -> {}",
            serverKey, wasHealthy, isHealthy);
      }

      // 如果服务器不健康，尝试重连
      if (!isHealthy && shouldStart.get()) {
        tryReconnect(serverKey);
      }
    }
  }

  /**
   * 尝试重新连接到服务器
   */
  private void tryReconnect(String serverKey) {
    String[] parts = serverKey.split(":");
    if (parts.length != 2) {
      return;
    }

    String host = parts[0];
    int port;
    try {
      port = Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      return;
    }

    try {
      ClusterTransportClient oldClient = transportClientMap.get(serverKey);
      if (oldClient != null) {
        try {
          oldClient.stop();
        } catch (Exception e) {
          // Ignore
        }
      }

      ClusterTransportClient newClient = new NettyTransportClient(host, port);
      newClient.start();

      transportClientMap.put(serverKey, newClient);
      serverHealthMap.put(serverKey, true);
      serverFailureCountMap.put(serverKey, 0);

      RecordLog.info("[MultiServerClusterTokenClient] Successfully reconnected to server: {}",
          serverKey);
    } catch (Exception ex) {
      RecordLog.warn("[MultiServerClusterTokenClient] Failed to reconnect to server: " + serverKey,
          ex);
    }
  }

  /**
   * 获取服务器key
   */
  private String getServerKey(String host, int port) {
    return host + ":" + port;
  }

  /**
   * 根据负载均衡策略选择一个健康的服务器
   */
  private ServerNode selectHealthyServer() {
    return selectHealthyServer(null);
  }

  /**
   * 根据负载均衡策略和规则ID选择一个健康的服务器
   *
   * @param ruleId 规则ID，用于一致性哈希路由（可选）
   * @return 选中的服务器节点
   */
  private ServerNode selectHealthyServer(Long ruleId) {
    List<ServerNode> enabledNodes = new ArrayList<>();

    for (ServerNode node : config.getServerNodes()) {
      if (node.isEnabled()) {
        String key = getServerKey(node.getHost(), node.getPort());
        Boolean isHealthy = serverHealthMap.get(key);

        // 只选择健康的服务器
        if (Boolean.TRUE.equals(isHealthy)) {
          enabledNodes.add(node);
        }
      }
    }

    if (enabledNodes.isEmpty()) {
      RecordLog.warn("[MultiServerClusterTokenClient] No healthy server available");
      return null;
    }

    // 如果负载均衡策略支持按规则ID路由，则使用规则ID
    if (ruleId != null) {
      if (loadBalanceStrategy instanceof com.alibaba.csp.sentinel.cluster.client.loadbalance.ConsistentHashLoadBalanceStrategy) {
        return ((com.alibaba.csp.sentinel.cluster.client.loadbalance.ConsistentHashLoadBalanceStrategy) loadBalanceStrategy)
            .selectByRuleId(enabledNodes, ruleId);
      }
    }

    return loadBalanceStrategy.select(enabledNodes);
  }

  /**
   * 标记服务器失败
   */
  private void markServerFailure(String serverKey) {
    Integer failureCount = serverFailureCountMap.getOrDefault(serverKey, 0);
    failureCount++;
    serverFailureCountMap.put(serverKey, failureCount);

    if (failureCount >= MAX_FAILURE_COUNT) {
      serverHealthMap.put(serverKey, false);
      RecordLog.warn(
          "[MultiServerClusterTokenClient] Server {} marked as unhealthy after {} failures",
          serverKey, failureCount);
    }
  }

  /**
   * 标记服务器成功
   */
  private void markServerSuccess(String serverKey) {
    serverFailureCountMap.put(serverKey, 0);
    if (!Boolean.TRUE.equals(serverHealthMap.get(serverKey))) {
      serverHealthMap.put(serverKey, true);
      RecordLog.info("[MultiServerClusterTokenClient] Server {} marked as healthy", serverKey);
    }
  }

  @Override
  public TokenResult requestToken(Long flowId, int acquireCount, boolean prioritized) {
    if (notValidRequest(flowId, acquireCount)) {
      return badRequest();
    }

    FlowRequestData data = new FlowRequestData()
        .setCount(acquireCount)
        .setFlowId(flowId)
        .setPriority(prioritized);
    ClusterRequest<FlowRequestData> request = new ClusterRequest<>(ClusterConstants.MSG_TYPE_FLOW,
        data);

    // 使用flowId作为路由键，确保同一规则的请求路由到同一服务器
    return sendRequestWithRetry(request, flowId);
  }

  @Override
  public TokenResult requestParamToken(Long flowId, int acquireCount, Collection<Object> params) {
    if (notValidRequest(flowId, acquireCount) || params == null || params.isEmpty()) {
      return badRequest();
    }

    ParamFlowRequestData data = new ParamFlowRequestData()
        .setCount(acquireCount)
        .setFlowId(flowId)
        .setParams(params);
    ClusterRequest<ParamFlowRequestData> request = new ClusterRequest<>(
        ClusterConstants.MSG_TYPE_PARAM_FLOW, data);

    // 使用flowId作为路由键，确保同一规则的请求路由到同一服务器
    return sendRequestWithRetry(request, flowId);
  }

  /**
   * 发送请求，支持故障转移和重试
   */
  private TokenResult sendRequestWithRetry(ClusterRequest<?> request) {
    return sendRequestWithRetry(request, null);
  }

  /**
   * 发送请求，支持故障转移和重试，并根据规则ID路由
   *
   * @param request 请求对象
   * @param ruleId  规则ID，用于路由选择
   * @return Token结果
   */
  private TokenResult sendRequestWithRetry(ClusterRequest<?> request, Long ruleId) {
    int maxRetries = config.isEnableFailover() ? config.getMaxRetries() : 0;
    Set<String> triedServers = new HashSet<>();

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      // 根据规则ID选择服务器，确保同一规则路由到同一服务器
      ServerNode selectedNode = selectHealthyServer(ruleId);

      if (selectedNode == null) {
        ClusterClientStatLogUtil.log("No healthy server available");
        return clientFail();
      }

      String serverKey = getServerKey(selectedNode.getHost(), selectedNode.getPort());

      // 如果已经尝试过这个服务器，跳过
      if (triedServers.contains(serverKey)) {
        continue;
      }

      triedServers.add(serverKey);

      try {
        ClusterTransportClient client = transportClientMap.get(serverKey);
        if (client == null || !client.isReady()) {
          RecordLog.warn("[MultiServerClusterTokenClient] Client not ready for server: {}",
              serverKey);
          markServerFailure(serverKey);
          continue;
        }

        ClusterResponse response = client.sendRequest(request);
        TokenResult result = new TokenResult(response.getStatus());

        if (response.getData() != null) {
          FlowTokenResponseData responseData = (FlowTokenResponseData) response.getData();
          result.setRemaining(responseData.getRemainingCount())
              .setWaitInMs(responseData.getWaitInMs());
        }

        // 请求成功，标记服务器为健康
        markServerSuccess(serverKey);
        logForResult(result);

        return result;

      } catch (Exception ex) {
        RecordLog.warn("[MultiServerClusterTokenClient] Request failed on server: " + serverKey
            + ", attempt: " + (attempt + 1), ex);
        markServerFailure(serverKey);

        // 如果是最后一次尝试，记录错误并返回失败
        if (attempt == maxRetries) {
          ClusterClientStatLogUtil.log(ex.getMessage());
          return new TokenResult(TokenResultStatus.FAIL);
        }

        // 否则，继续尝试其他服务器
      }
    }

    return new TokenResult(TokenResultStatus.FAIL);
  }

  @Override
  public TokenResult requestConcurrentToken(String clientAddress, Long ruleId, int acquireCount) {
    // 暂不支持
    return null;
  }

  @Override
  public void releaseConcurrentToken(Long tokenId) {
    // 暂不支持
  }

  private void logForResult(TokenResult result) {
    switch (result.getStatus()) {
      case TokenResultStatus.NO_RULE_EXISTS:
        ClusterClientStatLogUtil.log(ClusterErrorMessages.NO_RULES_IN_SERVER);
        break;
      case TokenResultStatus.TOO_MANY_REQUEST:
        ClusterClientStatLogUtil.log(ClusterErrorMessages.TOO_MANY_REQUESTS);
        break;
      default:
    }
  }

  private boolean notValidRequest(Long id, int count) {
    return id == null || id <= 0 || count <= 0;
  }

  private TokenResult badRequest() {
    return new TokenResult(TokenResultStatus.BAD_REQUEST);
  }

  private TokenResult clientFail() {
    return new TokenResult(TokenResultStatus.FAIL);
  }

  @Override
  public void start() throws Exception {
    if (shouldStart.compareAndSet(false, true)) {
      // 如果没有服务器节点，只标记为启动状态，等待配置推送
      if (transportClientMap.isEmpty()) {
        RecordLog.info("[MultiServerClusterTokenClient] Client marked as started, "
            + "waiting for server configuration");
        return;
      }

      // 启动所有已配置的服务器连接
      for (Map.Entry<String, ClusterTransportClient> entry : transportClientMap.entrySet()) {
        try {
          entry.getValue().start();
          RecordLog.info("[MultiServerClusterTokenClient] Started client for server: {}",
              entry.getKey());
        } catch (Exception ex) {
          RecordLog.warn("[MultiServerClusterTokenClient] Failed to start client for server: "
              + entry.getKey(), ex);
          serverHealthMap.put(entry.getKey(), false);
        }
      }

      // 启动健康检查
      startHealthCheck();
    }
  }

  @Override
  public void stop() throws Exception {
    if (shouldStart.compareAndSet(true, false)) {
      healthCheckExecutor.shutdown();

      for (Map.Entry<String, ClusterTransportClient> entry : transportClientMap.entrySet()) {
        try {
          entry.getValue().stop();
          RecordLog.info("[MultiServerClusterTokenClient] Stopped client for server: {}",
              entry.getKey());
        } catch (Exception ex) {
          RecordLog.warn("[MultiServerClusterTokenClient] Failed to stop client for server: "
              + entry.getKey(), ex);
        }
      }

      transportClientMap.clear();
      serverDescriptorMap.clear();
      serverHealthMap.clear();
      serverFailureCountMap.clear();
    }
  }

  @Override
  public int getState() {
    // 如果有任何一个客户端是启动的，就返回启动状态
    for (ClusterTransportClient client : transportClientMap.values()) {
      if (client != null && client.isReady()) {
        return ClientConstants.CLIENT_STATUS_STARTED;
      }
    }
    return ClientConstants.CLIENT_STATUS_OFF;
  }

  @Override
  public TokenServerDescriptor currentServer() {
    ServerNode selectedNode = selectHealthyServer();
    if (selectedNode != null) {
      String key = getServerKey(selectedNode.getHost(), selectedNode.getPort());
      return serverDescriptorMap.get(key);
    }
    return null;
  }

  /**
   * 获取所有服务器描述信息
   */
  public List<TokenServerDescriptor> getAllServers() {
    return new ArrayList<>(serverDescriptorMap.values());
  }

  /**
   * 获取所有健康的服务器
   */
  public List<TokenServerDescriptor> getHealthyServers() {
    List<TokenServerDescriptor> healthyServers = new ArrayList<>();
    for (Map.Entry<String, Boolean> entry : serverHealthMap.entrySet()) {
      if (Boolean.TRUE.equals(entry.getValue())) {
        healthyServers.add(serverDescriptorMap.get(entry.getKey()));
      }
    }
    return healthyServers;
  }

  /**
   * 更新配置（参考 DefaultClusterTokenClient 的 changeServer 方法）
   *
   * 支持以下场景：
   * 1. 添加新服务器节点
   * 2. 移除旧服务器节点
   * 3. 修改服务器配置（权重、启用状态等）
   * 4. 切换负载均衡策略
   * 5. 从空配置到有配置（首次配置）
   *
   * @param newConfig 新的配置
   */
  public synchronized void updateConfig(ClusterClientMultiServerConfig newConfig) {
    if (newConfig == null || newConfig.getServerNodes() == null || newConfig.getServerNodes()
        .isEmpty()) {
      RecordLog.warn("[MultiServerClusterTokenClient] Invalid config, ignoring update");
      return;
    }

    boolean isFirstConfig = (config == null || config.getServerNodes().isEmpty());

    RecordLog.info("[MultiServerClusterTokenClient] Updating config, isFirstConfig: {}, "
            + "old servers: {}, new servers: {}",
        isFirstConfig,
        config != null ? config.getServerNodes().size() : 0,
        newConfig.getServerNodes().size());

    // 更新负载均衡策略
    if (config != null && !newConfig.getLoadBalanceStrategy()
        .equals(config.getLoadBalanceStrategy())) {
      LoadBalanceStrategyType oldStrategy = config.getLoadBalanceStrategy();
      this.loadBalanceStrategy = LoadBalanceStrategyFactory.getStrategy(
          newConfig.getLoadBalanceStrategy());
      RecordLog.info("[MultiServerClusterTokenClient] Load balance strategy changed: {} -> {}",
          oldStrategy, newConfig.getLoadBalanceStrategy());

      // 重置负载均衡器状态
      if (this.loadBalanceStrategy != null) {
        this.loadBalanceStrategy.reset();
      }
    }

    this.config = newConfig;

    // 构建新服务器节点集合
    Set<String> newServerKeys = new HashSet<>();
    for (ServerNode node : newConfig.getServerNodes()) {
      if (node.isEnabled()) {
        newServerKeys.add(getServerKey(node.getHost(), node.getPort()));
      }
    }

    // 移除不再存在的服务器连接（参考 DefaultClusterTokenClient 的 stop 逻辑）
    Set<String> existingKeys = new HashSet<>(transportClientMap.keySet());
    for (String key : existingKeys) {
      if (!newServerKeys.contains(key)) {
        removeServer(key);
      }
    }

    // 添加新的服务器连接（参考 DefaultClusterTokenClient 的 start 逻辑）
    for (ServerNode node : newConfig.getServerNodes()) {
      if (node.isEnabled()) {
        String key = getServerKey(node.getHost(), node.getPort());
        if (!transportClientMap.containsKey(key)) {
          addServer(node);
        }
      }
    }

    RecordLog.info("[MultiServerClusterTokenClient] Config update completed, "
            + "total servers: {}, healthy servers: {}",
        transportClientMap.size(), getHealthyServers().size());
  }

  /**
   * 添加服务器（参考 DefaultClusterTokenClient 的初始化逻辑）
   */
  private void addServer(ServerNode node) {
    String key = getServerKey(node.getHost(), node.getPort());
    try {
      ClusterTransportClient client = new NettyTransportClient(node.getHost(), node.getPort());

      // 如果客户端已启动，自动启动新连接
      if (shouldStart.get()) {
        client.start();
        RecordLog.info("[MultiServerClusterTokenClient] Started new server connection: {}", key);
      }

      transportClientMap.put(key, client);

      TokenServerDescriptor descriptor = new TokenServerDescriptor(node.getHost(), node.getPort());
      serverDescriptorMap.put(key, descriptor);

      serverHealthMap.put(key, true);
      serverFailureCountMap.put(key, 0);

      RecordLog.info("[MultiServerClusterTokenClient] Added new server: {} (weight: {})",
          key, node.getWeight());
    } catch (Exception ex) {
      RecordLog.warn("[MultiServerClusterTokenClient] Failed to add server: " + key, ex);
      serverHealthMap.put(key, false);
    }
  }

  /**
   * 移除服务器（参考 DefaultClusterTokenClient 的 stop 逻辑）
   */
  private void removeServer(String serverKey) {
    ClusterTransportClient client = transportClientMap.remove(serverKey);
    if (client != null) {
      try {
        client.stop();
        RecordLog.info("[MultiServerClusterTokenClient] Stopped and removed server: {}", serverKey);
      } catch (Exception e) {
        RecordLog.warn("[MultiServerClusterTokenClient] Error stopping client: " + serverKey, e);
      }
    }
    serverDescriptorMap.remove(serverKey);
    serverHealthMap.remove(serverKey);
    serverFailureCountMap.remove(serverKey);
  }

  /**
   * 获取当前配置
   */
  public ClusterClientMultiServerConfig getCurrentConfig() {
    return config;
  }
}

