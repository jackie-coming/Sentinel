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

import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientMultiServerConfigManager;
import com.alibaba.csp.sentinel.cluster.client.loadbalance.LoadBalanceStrategyType;

/**
 * MultiServerClusterTokenClient 动态配置使用示例
 * <p>
 * 展示如何使用类似 DefaultClusterTokenClient 的方式进行动态配置
 *
 * @author Example
 * @since 1.4.0
 */
public class MultiServerClusterTokenClientDynamicConfigExample {

  /**
   * 示例1：通过 SPI 自动加载（推荐）
   * <p>
   * 适用场景： - 应用启动时自动通过 SPI 加载 - 完全依赖配置中心动态配置 - 无需预先指定服务器地址
   */
  public static void example1_CompatibleWithDefaultClient() {
    System.out.println("========== 示例1：无参构造 + 动态配置 ==========");

    // 步骤1：创建客户端（无参构造，不需要初始配置）
    MultiServerClusterTokenClient client = new MultiServerClusterTokenClient();

    System.out.println("✅ 客户端已创建（空实例，等待配置推送）");
    System.out.println("   - 当前服务器数: " + client.getAllServers().size());

    // 步骤2：推送配置（模拟从配置中心获取）
    System.out.println("\n🔄 推送多服务器配置...");
    ClusterClientMultiServerConfig config = new ClusterClientMultiServerConfig()
        .addServerNode("192.168.1.100", 18730)
        .addServerNode("192.168.1.101", 18730)
        .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);

    ClusterClientMultiServerConfigManager.applyNewConfig(config);

    try {
      Thread.sleep(100);  // 等待配置生效

      System.out.println("✅ 配置已推送并生效");
      System.out.println("   - 当前服务器数: " + client.getAllServers().size());

      // 步骤3：启动客户端
      client.start();
      System.out.println("✅ 客户端启动成功");
      System.out.println("   - 健康服务器: " + client.getHealthyServers().size());

      // 步骤4：发起请求
      TokenResult result = client.requestToken(1000L, 1, false);
      System.out.println("   - 请求结果: " + result.getStatus());

      // 步骤5：模拟配置更新（添加新服务器）
      System.out.println("\n🔄 推送新配置（添加服务器）...");
      ClusterClientMultiServerConfig newConfig = new ClusterClientMultiServerConfig()
          .addServerNode("192.168.1.100", 18730)
          .addServerNode("192.168.1.101", 18730)
          .addServerNode("192.168.1.102", 18730)  // 新增
          .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH);

      ClusterClientMultiServerConfigManager.applyNewConfig(newConfig);
      Thread.sleep(100);

      // 配置会自动生效，无需重启客户端
      System.out.println("✅ 配置已更新");
      System.out.println("   - 当前服务器数: " + client.getAllServers().size());

      // 步骤6：停止客户端
      client.stop();
      System.out.println("✅ 客户端已停止");

    } catch (Exception e) {
      System.err.println("❌ 错误: " + e.getMessage());
    }
  }

  /**
   * 示例2：手动配置 + 自动动态更新（推荐）
   * <p>
   * 适用场景： - 已有固定的多个 Token Server - 需要支持在线扩容/缩容 - 动态配置自动启用，无需手动配置
   */
  public static void example2_ManualWithDynamicUpdate() {
    System.out.println("\n========== 示例2：手动配置 + 动态更新 ==========");

    // 步骤1：手动创建初始配置（多服务器）
    ClusterClientMultiServerConfig config = new ClusterClientMultiServerConfig()
        .addServerNode("192.168.1.100", 18730, 5)  // 权重 5
        .addServerNode("192.168.1.101", 18730, 3)  // 权重 3
        .addServerNode("192.168.1.102", 18730, 2)  // 权重 2
        .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH)
        .setEnableFailover(true)
        .setMaxRetries(2)
        .setHealthCheckInterval(5000);

    // 步骤2：创建客户端（动态配置自动启用）
    MultiServerClusterTokenClient client = new MultiServerClusterTokenClient(config);

    try {
      // 步骤3：启动客户端
      client.start();
      System.out.println("✅ 客户端启动成功（动态配置已自动启用）");
      System.out.println("   - 所有服务器: " + client.getAllServers().size());
      System.out.println("   - 健康服务器: " + client.getHealthyServers().size());

      // 步骤4：发起请求（使用一致性哈希，相同规则ID路由到同一服务器）
      Long ruleId = 1000L;
      for (int i = 0; i < 3; i++) {
        TokenResult result = client.requestToken(ruleId, 1, false);
        System.out.println("   - 请求 " + (i + 1) + " 结果: " + result.getStatus());
      }

      // 步骤5：模拟单服务器配置更新（兼容模式）
      System.out.println("\n🔄 模拟单服务器配置更新（会自动转换为多服务器配置）...");
      ClusterClientAssignConfig singleServerConfig = new ClusterClientAssignConfig()
          .setServerHost("192.168.1.200")
          .setServerPort(18730);

      ClusterClientConfigManager.applyNewAssignConfig(singleServerConfig);

      System.out.println("✅ 配置已更新（单服务器模式）");
      System.out.println("   - 所有服务器: " + client.getAllServers().size());

      // 步骤6：手动调用 updateConfig 更新为多服务器配置
      System.out.println("\n🔄 手动更新为多服务器配置...");
      ClusterClientMultiServerConfig newConfig = new ClusterClientMultiServerConfig()
          .addServerNode("192.168.1.100", 18730, 4)
          .addServerNode("192.168.1.103", 18730, 3)  // 新增服务器
          .addServerNode("192.168.1.104", 18730, 3)  // 新增服务器
          .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH)
          .setEnableFailover(true)
          .setMaxRetries(3)
          .setHealthCheckInterval(5000);

      client.updateConfig(newConfig);

      System.out.println("✅ 配置已更新（多服务器模式）");
      System.out.println("   - 所有服务器: " + client.getAllServers().size());
      System.out.println("   - 健康服务器: " + client.getHealthyServers().size());

      // 步骤7：停止客户端
      client.stop();
      System.out.println("✅ 客户端已停止");

    } catch (Exception e) {
      System.err.println("❌ 错误: " + e.getMessage());
    }
  }

  /**
   * 示例3：使用不同负载均衡策略
   * <p>
   * 适用场景： - 测试不同的负载均衡策略 - 根据业务特点选择合适的策略
   */
  public static void example3_ManualConfigOnly() {
    System.out.println("\n========== 示例3：手动配置客户端 ==========");

    // 步骤1：创建配置（使用一致性哈希策略）
    ClusterClientMultiServerConfig config = new ClusterClientMultiServerConfig()
        .addServerNode("192.168.1.100", 18730)
        .addServerNode("192.168.1.101", 18730)
        .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH)
        .setEnableFailover(true)
        .setMaxRetries(2)
        .setHealthCheckInterval(5000);

    // 步骤2：创建客户端（动态配置自动启用）
    MultiServerClusterTokenClient client = new MultiServerClusterTokenClient(config);

    try {
      // 步骤3：启动客户端
      client.start();
      System.out.println("✅ 客户端启动成功（动态配置已自动启用）");
      System.out.println("   - 所有服务器: " + client.getAllServers().size());
      System.out.println("   - 当前策略: 一致性哈希");

      // 步骤4：测试请求
      for (int i = 0; i < 5; i++) {
        Long ruleId = 1000L + i;
        TokenResult result = client.requestToken(ruleId, 1, false);
        System.out.println("   - 规则 " + ruleId + " 请求结果: " + result.getStatus());
      }

      // 步骤5：通过配置中心切换负载均衡策略（多服务器配置）
      System.out.println("\n🔄 切换到一致性哈希策略...");
      ClusterClientMultiServerConfig newConfig = new ClusterClientMultiServerConfig()
          .addServerNode("192.168.1.100", 18730)
          .addServerNode("192.168.1.101", 18730)
          .addServerNode("192.168.1.102", 18730)  // 新增服务器
          .setLoadBalanceStrategy(LoadBalanceStrategyType.CONSISTENT_HASH)
          .setEnableFailover(true)
          .setMaxRetries(2)
          .setHealthCheckInterval(5000);

      // 通过配置管理器更新（模拟配置中心推送）
      ClusterClientMultiServerConfigManager.applyNewConfig(newConfig);

      Thread.sleep(100);  // 等待配置生效

      System.out.println("✅ 配置已更新");
      System.out.println("   - 所有服务器: " + client.getAllServers().size());
      System.out.println("   - 当前策略: 一致性哈希");

      // 步骤6：停止客户端
      client.stop();
      System.out.println("✅ 客户端已停止");

    } catch (Exception e) {
      System.err.println("❌ 错误: " + e.getMessage());
    }
  }

  /**
   * 示例4：从 DefaultClusterTokenClient 迁移示例
   */
  public static void example4_MigrationFromDefaultClient() {
    System.out.println("\n========== 示例4：迁移示例 ==========");

    System.out.println("旧代码（使用 DefaultClusterTokenClient）：");
    System.out.println("  ClusterTokenClient client = new DefaultClusterTokenClient();");
    System.out.println("  client.start();");

    System.out.println("\n新代码（使用 MultiServerClusterTokenClient）：");
    System.out.println("  ClusterTokenClient client = new MultiServerClusterTokenClient();");
    System.out.println("  client.start();");

    System.out.println("\n✅ 完全兼容，无需修改其他代码！");
    System.out.println("\n🎯 动态配置特性：");
    System.out.println("  - 默认启用动态配置，无需用户手动配置");
    System.out.println("  - 支持单服务器配置（兼容 DefaultClusterTokenClient）");
    System.out.println("  - 支持多服务器配置（通过 ClusterClientMultiServerConfigManager）");
    System.out.println("  - 配置变更自动生效，无需重启应用");
  }

  /**
   * 主函数 - 运行所有示例
   */
  public static void main(String[] args) {
    try {
      // 运行所有示例
      example1_CompatibleWithDefaultClient();
      example2_ManualWithDynamicUpdate();
      example3_ManualConfigOnly();
      example4_MigrationFromDefaultClient();

      System.out.println("\n========================================");
      System.out.println("✅ 所有示例运行完成！");
      System.out.println("========================================");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

