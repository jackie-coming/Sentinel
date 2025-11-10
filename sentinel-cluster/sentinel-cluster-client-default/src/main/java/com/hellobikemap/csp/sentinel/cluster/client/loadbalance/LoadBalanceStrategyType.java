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

/**
 * 负载均衡策略类型枚举
 * <p>
 * 仅支持一致性哈希策略，确保集群限流的准确性和平滑扩缩容
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public enum LoadBalanceStrategyType {

  /**
   * 一致性哈希策略 - 按规则ID路由，支持平滑扩缩容
   * <p>
   * 特性：
   * <ul>
   *   <li>同一规则ID总是路由到同一服务器，确保限流准确性</li>
   *   <li>服务器节点变化时，影响范围最小化（理论上约 K/N，K为节点数）</li>
   *   <li>支持虚拟节点，负载分布更加均匀</li>
   *   <li>支持平滑扩缩容，不影响现有规则路由</li>
   * </ul>
   * <p>
   * 适用于集群限流场景，特别是需要频繁扩缩容的生产环境
   */
  CONSISTENT_HASH("一致性哈希");

  private final String description;

  LoadBalanceStrategyType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  /**
   * 从字符串获取枚举值（兼容旧版本）
   *
   * @param name 策略名称
   * @return 始终返回一致性哈希策略
   */
  public static LoadBalanceStrategyType fromString(String name) {
    // 所有输入都返回一致性哈希策略
    return CONSISTENT_HASH;
  }

  /**
   * 是否支持按规则ID路由
   *
   * @return 始终返回true
   */
  public boolean supportsRuleIdRouting() {
    return true;
  }

  @Override
  public String toString() {
    return name() + "(" + description + ")";
  }
}

