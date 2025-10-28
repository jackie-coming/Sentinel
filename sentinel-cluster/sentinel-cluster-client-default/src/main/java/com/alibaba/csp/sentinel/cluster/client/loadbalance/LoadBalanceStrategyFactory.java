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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负载均衡策略工厂
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public class LoadBalanceStrategyFactory {

  private static final Map<LoadBalanceStrategyType, LoadBalanceStrategy> STRATEGY_MAP = new ConcurrentHashMap<>();
  private static final Map<String, LoadBalanceStrategy> LEGACY_STRATEGY_MAP = new ConcurrentHashMap<>();

  static {
    // 使用枚举类型的映射 - 只支持按规则ID路由的策略
    STRATEGY_MAP.put(LoadBalanceStrategyType.CONSISTENT_HASH,
        new ConsistentHashLoadBalanceStrategy());
    STRATEGY_MAP.put(LoadBalanceStrategyType.RULE_ID_HASH, new RuleIdHashLoadBalanceStrategy());

    // 兼容旧版本的字符串映射
    LEGACY_STRATEGY_MAP.put("CONSISTENT_HASH",
        STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH));
    LEGACY_STRATEGY_MAP.put("RULE_ID_HASH", STRATEGY_MAP.get(LoadBalanceStrategyType.RULE_ID_HASH));

    // 旧策略映射到一致性哈希（向后兼容）
    LEGACY_STRATEGY_MAP.put("ROUND_ROBIN",
        STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH));
    LEGACY_STRATEGY_MAP.put("RANDOM", STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH));
    LEGACY_STRATEGY_MAP.put("WEIGHT", STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH));
    LEGACY_STRATEGY_MAP.put("WEIGHTED", STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH));
  }

  /**
   * 获取负载均衡策略（推荐使用）
   *
   * @param strategyType 策略类型枚举
   * @return 负载均衡策略实例
   */
  public static LoadBalanceStrategy getStrategy(LoadBalanceStrategyType strategyType) {
    if (strategyType == null) {
      return STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH);
    }
    return STRATEGY_MAP.getOrDefault(strategyType,
        STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH));
  }

  /**
   * 获取负载均衡策略（兼容旧版本）
   * <p>
   * 注意：不支持的策略会自动映射到一致性哈希策略
   *
   * @param strategyName 策略名称字符串
   * @return 负载均衡策略实例
   * @deprecated 推荐使用 {@link #getStrategy(LoadBalanceStrategyType)}
   */
  @Deprecated
  public static LoadBalanceStrategy getStrategy(String strategyName) {
    if (strategyName == null || strategyName.isEmpty()) {
      return STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH);
    }

    LoadBalanceStrategy strategy = LEGACY_STRATEGY_MAP.get(strategyName.toUpperCase());
    return strategy != null ? strategy : STRATEGY_MAP.get(LoadBalanceStrategyType.CONSISTENT_HASH);
  }

  /**
   * 注册自定义负载均衡策略（推荐使用）
   *
   * @param type     策略类型
   * @param strategy 策略实例
   */
  public static void registerStrategy(LoadBalanceStrategyType type, LoadBalanceStrategy strategy) {
    if (type != null && strategy != null) {
      STRATEGY_MAP.put(type, strategy);
    }
  }

  /**
   * 注册自定义负载均衡策略（兼容旧版本）
   *
   * @param name     策略名称
   * @param strategy 策略实例
   * @deprecated 推荐使用 {@link #registerStrategy(LoadBalanceStrategyType, LoadBalanceStrategy)}
   */
  @Deprecated
  public static void registerStrategy(String name, LoadBalanceStrategy strategy) {
    if (name != null && strategy != null) {
      LEGACY_STRATEGY_MAP.put(name.toUpperCase(), strategy);
    }
  }

  private LoadBalanceStrategyFactory() {
  }
}

