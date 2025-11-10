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
package com.hellobikemap.csp.sentinel.command.handler;

import java.util.ArrayList;
import java.util.List;

import com.hellobikemap.csp.sentinel.Constants;
import com.hellobikemap.csp.sentinel.command.CommandHandler;
import com.hellobikemap.csp.sentinel.command.CommandRequest;
import com.hellobikemap.csp.sentinel.command.CommandResponse;
import com.hellobikemap.csp.sentinel.command.annotation.CommandMapping;
import com.hellobikemap.csp.sentinel.node.DefaultNode;
import com.hellobikemap.csp.sentinel.node.Node;
import com.hellobikemap.csp.sentinel.command.vo.NodeVo;

import com.alibaba.fastjson.JSON;

/**
 * @author leyou
 */
@CommandMapping(name = "jsonTree", desc = "get tree node VO start from root node")
public class FetchJsonTreeCommandHandler implements CommandHandler<String> {

    @Override
    public CommandResponse<String> handle(CommandRequest request) {
        List<NodeVo> results = new ArrayList<NodeVo>();
        visit(Constants.ROOT, results, null);
        return CommandResponse.ofSuccess(JSON.toJSONString(results));
    }

    /**
     * Preorder traversal.
     */
    private void visit(DefaultNode node, List<NodeVo> results, String parentId) {
        NodeVo vo = NodeVo.fromDefaultNode(node, parentId);
        results.add(vo);
        String id = vo.getId();
        for (Node n : node.getChildList()) {
            visit((DefaultNode)n, results, id);
        }
    }
}
