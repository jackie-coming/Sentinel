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
 * 负载均衡策略类型枚举
 * <p>
 * 仅支持按规则ID路由的策略，确保集群限流的准确性
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public enum LoadBalanceStrategyType {

  /**
   * 一致性哈希策略 - 按规则ID路由，支持平滑扩缩容（推荐）
   * <p>
   * 特性：
   * <ul>
   *   <li>同一规则ID总是路由到同一服务器</li>
   *   <li>服务器节点变化时，影响范围最小（约25%）</li>
   *   <li>支持虚拟节点，负载均衡效果好</li>
   * </ul>
   * <p>
   * 推荐用于集群限流场景，特别是需要频繁扩缩容的场景
   */
  CONSISTENT_HASH("一致性哈希"),

  /**
   * 规则ID哈希策略 - 简单的按规则ID哈希取模
   * <p>
   * 特性：
   * <ul>
   *   <li>同一规则ID总是路由到同一服务器</li>
   *   <li>实现简单，性能开销小</li>
   *   <li>节点变化时，大部分路由会改变（约75%）</li>
   * </ul>
   * <p>
   * 适合节点相对稳定的集群限流场景
   */
  RULE_ID_HASH("规则ID哈希");

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
   * @return 对应的枚举值，如果不存在则返回默认的一致性哈希策略
   */
  public static LoadBalanceStrategyType fromString(String name) {
    if (name == null || name.isEmpty()) {
      return CONSISTENT_HASH;
    }

    String upperName = name.toUpperCase().trim();

    try {
      return valueOf(upperName);
    } catch (IllegalArgumentException e) {
      return CONSISTENT_HASH;
    }
  }

  /**
   * 是否支持按规则ID路由
   * <p>
   * 注意：所有策略都支持按规则ID路由
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

