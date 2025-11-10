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
package com.hellobikemap.csp.sentinel.cluster;

/**
 * Constants for cluster metadata keys used in {@link TokenResult#getAttachments()}.
 *
 * <p>These keys are used to store and retrieve cluster flow control metadata,
 * including routing information, performance metrics, and error details.</p>
 *
 * @author Eric Zhao
 * @since 1.8.x
 */
public final class ClusterMetadataKeys {

    /**
     * Key for the routed Token Server address (e.g., "192.168.1.100:8720")
     */
    public static final String KEY_ROUTED_SERVER = "routed_server";

    /**
     * Key for the rule ID
     */
    public static final String KEY_RULE_ID = "rule_id";

    /**
     * Key for the rule name
     */
    public static final String KEY_RULE_NAME = "rule_name";

    /**
     * Key for the response time of a single request (in milliseconds)
     */
    public static final String KEY_RESPONSE_TIME = "response_time";

    /**
     * Key for the total time including all retries (in milliseconds)
     */
    public static final String KEY_TOTAL_TIME = "total_time";

    /**
     * Key for the retry count
     */
    public static final String KEY_RETRY_COUNT = "retry_count";

    /**
     * Key for the request timestamp
     */
    public static final String KEY_REQUEST_TIMESTAMP = "request_timestamp";

    /**
     * Key for the request status ("success" or "failure")
     */
    public static final String KEY_REQUEST_STATUS = "request_status";

    /**
     * Key for the exception type (full class name, when request fails)
     */
    public static final String KEY_EXCEPTION_TYPE = "exception_type";

    /**
     * Key for the exception message (when request fails)
     */
    public static final String KEY_EXCEPTION_MESSAGE = "exception_message";

    private ClusterMetadataKeys() {
        throw new AssertionError("No instances for you!");
    }
}

