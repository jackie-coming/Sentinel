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

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.PropertyListener;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.util.AssertUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * 多服务器集群客户端配置管理器
 * <p>
 * 提供与 {@link ClusterClientConfigManager} 类似的配置管理能力， 用于管理多服务器配置的动态更新
 *
 * @author Modified for multi-server support
 * @since 1.4.0
 */
public final class ClusterClientMultiServerConfigManager {

  /**
   * 多服务器配置属性
   */
  private static SentinelProperty<ClusterClientMultiServerConfig> multiServerConfigProperty
      = new DynamicSentinelProperty<>();

  /**
   * 配置变更监听器
   */
  private static final PropertyListener<ClusterClientMultiServerConfig> CONFIG_PROPERTY_LISTENER
      = new MultiServerConfigPropertyListener();

  /**
   * 配置变更观察者列表
   */
  private static final List<MultiServerConfigChangeObserver> CONFIG_CHANGE_OBSERVERS = new ArrayList<>();

  static {
    multiServerConfigProperty.addListener(CONFIG_PROPERTY_LISTENER);
  }

  /**
   * 注册多服务器配置属性（用于动态配置源，如 Nacos、Apollo 等）
   *
   * @param property 配置属性
   */
  public static void registerMultiServerConfigProperty(
      SentinelProperty<ClusterClientMultiServerConfig> property) {
    AssertUtil.notNull(property, "property cannot be null");
    synchronized (CONFIG_PROPERTY_LISTENER) {
      RecordLog.info(
          "[ClusterClientMultiServerConfigManager] Registering multi-server config property");
      multiServerConfigProperty.removeListener(CONFIG_PROPERTY_LISTENER);
      property.addListener(CONFIG_PROPERTY_LISTENER);
      multiServerConfigProperty = property;
    }
  }

  /**
   * 添加配置变更观察者
   *
   * @param observer 观察者
   */
  public static void addConfigChangeObserver(MultiServerConfigChangeObserver observer) {
    AssertUtil.notNull(observer, "observer cannot be null");
    synchronized (CONFIG_CHANGE_OBSERVERS) {
      CONFIG_CHANGE_OBSERVERS.add(observer);
    }
  }

  /**
   * 应用新的多服务器配置
   *
   * @param config 新配置
   */
  public static void applyNewConfig(ClusterClientMultiServerConfig config) {
    multiServerConfigProperty.updateValue(config);
  }

  /**
   * 验证配置有效性
   *
   * @param config 配置
   * @return 是否有效
   */
  public static boolean isValidConfig(ClusterClientMultiServerConfig config) {
    return config != null
        && config.getServerNodes() != null
        && !config.getServerNodes().isEmpty();
  }

  /**
   * 配置属性监听器实现
   */
  private static class MultiServerConfigPropertyListener
      implements PropertyListener<ClusterClientMultiServerConfig> {

    @Override
    public void configLoad(ClusterClientMultiServerConfig config) {
      if (config == null) {
        RecordLog.warn("[ClusterClientMultiServerConfigManager] Empty initial multi-server config");
        return;
      }
      applyConfig(config);
    }

    @Override
    public void configUpdate(ClusterClientMultiServerConfig config) {
      applyConfig(config);
    }

    private synchronized void applyConfig(ClusterClientMultiServerConfig config) {
      if (!isValidConfig(config)) {
        RecordLog.warn("[ClusterClientMultiServerConfigManager] Invalid config, ignoring: {}",
            config);
        return;
      }

      RecordLog.info("[ClusterClientMultiServerConfigManager] Applying new multi-server config: {}",
          config);

      // 通知所有观察者
      List<MultiServerConfigChangeObserver> observers;
      synchronized (CONFIG_CHANGE_OBSERVERS) {
        observers = new ArrayList<>(CONFIG_CHANGE_OBSERVERS);
      }

      for (MultiServerConfigChangeObserver observer : observers) {
        try {
          observer.onMultiServerConfigChange(config);
        } catch (Exception ex) {
          RecordLog.warn("[ClusterClientMultiServerConfigManager] Error notifying observer", ex);
        }
      }
    }
  }

  /**
   * 配置变更观察者接口
   */
  public interface MultiServerConfigChangeObserver {

    /**
     * 多服务器配置变更回调
     *
     * @param config 新的配置
     */
    void onMultiServerConfigChange(ClusterClientMultiServerConfig config);
  }

  private ClusterClientMultiServerConfigManager() {
  }
}

