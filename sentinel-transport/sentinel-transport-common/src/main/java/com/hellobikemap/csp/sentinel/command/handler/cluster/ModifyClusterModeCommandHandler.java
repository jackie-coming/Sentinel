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
package com.hellobikemap.csp.sentinel.command.handler.cluster;

import com.hellobikemap.csp.sentinel.cluster.ClusterStateManager;
import com.hellobikemap.csp.sentinel.cluster.client.TokenClientProvider;
import com.hellobikemap.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.hellobikemap.csp.sentinel.command.CommandHandler;
import com.hellobikemap.csp.sentinel.command.CommandRequest;
import com.hellobikemap.csp.sentinel.command.CommandResponse;
import com.hellobikemap.csp.sentinel.command.annotation.CommandMapping;
import com.hellobikemap.csp.sentinel.log.RecordLog;

/**
 * @author Eric Zhao
 * @since 1.4.0
 */
@CommandMapping(name = "setClusterMode", desc = "set cluster mode, accept param: mode={0|1} 0:client mode 1:server mode")
public class ModifyClusterModeCommandHandler implements CommandHandler<String> {

    @Override
    public CommandResponse<String> handle(CommandRequest request) {
        try {
            int mode = Integer.valueOf(request.getParam("mode"));
            if (mode == ClusterStateManager.CLUSTER_CLIENT && !TokenClientProvider.isClientSpiAvailable()) {
                return CommandResponse.ofFailure(new IllegalStateException("token client mode not available: no SPI found"));
            }
            if (mode == ClusterStateManager.CLUSTER_SERVER && !isClusterServerSpiAvailable()) {
                return CommandResponse.ofFailure(new IllegalStateException("token server mode not available: no SPI found"));
            }
            RecordLog.info("[ModifyClusterModeCommandHandler] Modifying cluster mode to: {}", mode);

            ClusterStateManager.applyState(mode);
            return CommandResponse.ofSuccess("success");
        } catch (NumberFormatException ex) {
            return CommandResponse.ofFailure(new IllegalArgumentException("invalid parameter"));
        } catch (Exception ex) {
            return CommandResponse.ofFailure(ex);
        }
    }

    private boolean isClusterServerSpiAvailable() {
        return EmbeddedClusterTokenServerProvider.isServerSpiAvailable();
    }
}
