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
package com.hivemq.plugin.configuration;

import com.hivemq.spi.config.SystemInformation;
import com.hivemq.spi.services.PluginExecutorService;
import com.hivemq.spi.services.configuration.ValueChangedCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Properties;

/**
 * This reads a property file and provides some utility methods for working with {@link Properties}
 *
 * @author Christoph Schäbel
 * @author Simon Baier
 */
@Singleton
public class DnsDiscoveryConfiguration extends ReloadingPropertiesReader {

    private static final Logger log = LoggerFactory.getLogger(DnsDiscoveryConfiguration.class);

    private static final String DISCOVERY_ADDRESS_PROPERTY = "discoveryAddress";
    private static final String RESOLUTION_TIMEOUT_PROPERTY = "resolutionTimeout";
    private static final String DISCOVERY_ADDRESS_ENV = "HIVEMQ_DNS_DISCOVERY_ADDRESS";
    private static final String DISCOVERY_TIMEOUT_ENV = "HIVEMQ_DNS_DISCOVERY_TIMEOUT";

    /* How long we wait before failing the dns resolution */
    private static final int DEFAULT_DISCOVERY_TIMEOUT = 30;

    private RestartListener listener;

    @Inject
    public DnsDiscoveryConfiguration(final PluginExecutorService pluginExecutorService,
                                     final SystemInformation systemInformation) {
        super(pluginExecutorService, systemInformation);

        final ValueChangedCallback<String> callback = newValue -> {
            if (listener != null) {
                listener.restart();
            }
        };

        addCallback(DISCOVERY_ADDRESS_PROPERTY, callback);
        addCallback(RESOLUTION_TIMEOUT_PROPERTY, callback);
    }

    @Override
    @PostConstruct
    public void postConstruct() {
        super.postConstruct();
    }

    public String discoveryAddress() {
        final String discoveryAddressConf = getProperty();
        if (!isPropertiesEnabled() || discoveryAddressConf == null || discoveryAddressConf.isEmpty()) {
            final String discoveryAddressEnv = System.getenv(DISCOVERY_ADDRESS_ENV);
            if (discoveryAddressEnv == null || discoveryAddressEnv.isEmpty()) {
                log.error("No discovery address was set in the configuration file or environment variable");
                return null;
            } else {
                return discoveryAddressEnv;
            }
        }
        return discoveryAddressConf;
    }

    public int resolutionTimeout() {
        String resolveTimeout = properties.getProperty("resolutionTimeout");
        if (!isPropertiesEnabled() || resolveTimeout == null || resolveTimeout.isEmpty()) {
            resolveTimeout = System.getenv(DISCOVERY_TIMEOUT_ENV);
            if (resolveTimeout == null || resolveTimeout.isEmpty()) {
                log.warn("No DNS resolution timeout configured in configuration file or environment variable, using default: {}", DEFAULT_DISCOVERY_TIMEOUT);
                return DEFAULT_DISCOVERY_TIMEOUT;
            }
        }
        try {
            return Integer.parseInt(resolveTimeout);
        } catch (NumberFormatException e) {
            log.error("Invalid format {} for DNS discovery property resolutionTimeout, using default: 30", resolveTimeout);
            return 30;
        }
    }

    @Override
    public String getFilename() {
        return "dnsdiscovery.properties";
    }

    @SuppressWarnings("unused")
    public void setRestartListener(final RestartListener listener) {
        this.listener = listener;
    }

    public interface RestartListener {
        void restart();
    }
}