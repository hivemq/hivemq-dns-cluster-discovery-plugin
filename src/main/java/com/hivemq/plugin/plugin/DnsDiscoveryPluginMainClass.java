/*
 * Copyright 2018 dc-square GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.plugin.plugin;

import com.hivemq.plugin.callbacks.DnsClusterDiscovery;
import com.hivemq.spi.PluginEntryPoint;
import com.hivemq.spi.callback.registry.CallbackRegistry;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public class DnsDiscoveryPluginMainClass extends PluginEntryPoint {

    private final DnsClusterDiscovery dnsClusterDiscovery;


    @Inject
    public DnsDiscoveryPluginMainClass(final DnsClusterDiscovery dnsClusterDiscovery) {
        this.dnsClusterDiscovery = dnsClusterDiscovery;
    }

    /**
     * This method is executed after the instantiation of the whole class. It is used to initialize
     * the implemented callbacks and make them known to the HiveMQ core.
     */
    @SuppressWarnings("unused")
    @PostConstruct
    public void postConstruct() {
        CallbackRegistry callbackRegistry = getCallbackRegistry();
        callbackRegistry.addCallback(dnsClusterDiscovery);
    }

}
