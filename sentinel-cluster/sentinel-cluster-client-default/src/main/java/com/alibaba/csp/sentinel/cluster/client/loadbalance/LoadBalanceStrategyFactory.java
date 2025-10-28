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

  private static final Map<String, LoadBalanceStrategy> STRATEGY_MAP = new ConcurrentHashMap<>();

  static {
    STRATEGY_MAP.put("ROUND_ROBIN", new RoundRobinLoadBalanceStrategy());
    STRATEGY_MAP.put("RANDOM", new RandomLoadBalanceStrategy());
    STRATEGY_MAP.put("WEIGHT", new WeightedLoadBalanceStrategy());
    STRATEGY_MAP.put("WEIGHTED", new WeightedLoadBalanceStrategy());
    STRATEGY_MAP.put("CONSISTENT_HASH", new ConsistentHashLoadBalanceStrategy());
    STRATEGY_MAP.put("RULE_ID_HASH", new RuleIdHashLoadBalanceStrategy());
  }

  /**
   * 获取负载均衡策略
   *
   * @param strategyName 策略名称
   * @return 负载均衡策略实例
   */
  public static LoadBalanceStrategy getStrategy(String strategyName) {
    if (strategyName == null || strategyName.isEmpty()) {
      return STRATEGY_MAP.get("ROUND_ROBIN");
    }

    LoadBalanceStrategy strategy = STRATEGY_MAP.get(strategyName.toUpperCase());
    return strategy != null ? strategy : STRATEGY_MAP.get("ROUND_ROBIN");
  }

  /**
   * 注册自定义负载均衡策略
   *
   * @param name     策略名称
   * @param strategy 策略实例
   */
  public static void registerStrategy(String name, LoadBalanceStrategy strategy) {
    if (name != null && strategy != null) {
      STRATEGY_MAP.put(name.toUpperCase(), strategy);
    }
  }

  private LoadBalanceStrategyFactory() {
  }
}

