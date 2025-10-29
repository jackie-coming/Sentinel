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

/**
 * 负载均衡策略工厂
 * <p>
 * 仅支持一致性哈希策略
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public class LoadBalanceStrategyFactory {

  private static final LoadBalanceStrategy CONSISTENT_HASH_STRATEGY = new ConsistentHashLoadBalanceStrategy();

  /**
   * 获取负载均衡策略
   * <p>
   * 始终返回一致性哈希策略
   *
   * @param strategyType 策略类型枚举（忽略）
   * @return 一致性哈希策略实例
   */
  public static LoadBalanceStrategy getStrategy(LoadBalanceStrategyType strategyType) {
    return CONSISTENT_HASH_STRATEGY;
  }

  /**
   * 获取负载均衡策略（兼容旧版本）
   * <p>
   * 始终返回一致性哈希策略
   *
   * @param strategyName 策略名称字符串（忽略）
   * @return 一致性哈希策略实例
   * @deprecated 推荐使用 {@link #getStrategy(LoadBalanceStrategyType)}
   */
  @Deprecated
  public static LoadBalanceStrategy getStrategy(String strategyName) {
    return CONSISTENT_HASH_STRATEGY;
  }

  private LoadBalanceStrategyFactory() {
  }
}

