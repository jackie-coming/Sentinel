/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
package com.hellobikemap.csp.sentinel.adapter.apache.httpclient.config;

import com.hellobikemap.csp.sentinel.adapter.apache.httpclient.extractor.ApacheHttpClientResourceExtractor;
import com.hellobikemap.csp.sentinel.adapter.apache.httpclient.extractor.DefaultApacheHttpClientResourceExtractor;
import com.hellobikemap.csp.sentinel.adapter.apache.httpclient.fallback.ApacheHttpClientFallback;
import com.hellobikemap.csp.sentinel.adapter.apache.httpclient.fallback.DefaultApacheHttpClientFallback;
import com.hellobikemap.csp.sentinel.util.AssertUtil;

/**
 * @author zhaoyuguang
 */
public class SentinelApacheHttpClientConfig {

    private String prefix = "httpclient:";
    private ApacheHttpClientResourceExtractor extractor = new DefaultApacheHttpClientResourceExtractor();
    private ApacheHttpClientFallback fallback = new DefaultApacheHttpClientFallback();

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        AssertUtil.notNull(prefix, "prefix cannot be null");
        this.prefix = prefix;
    }

    public ApacheHttpClientResourceExtractor getExtractor() {
        return extractor;
    }

    public void setExtractor(ApacheHttpClientResourceExtractor extractor) {
        AssertUtil.notNull(extractor, "extractor cannot be null");
        this.extractor = extractor;
    }

    public ApacheHttpClientFallback getFallback() {
        return fallback;
    }

    public void setFallback(ApacheHttpClientFallback fallback) {
        AssertUtil.notNull(fallback, "fallback cannot be null");
        this.fallback = fallback;
    }
}
