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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.hivemq.spi.annotations.NotNull;
import com.hivemq.spi.config.SystemInformation;
import com.hivemq.spi.services.PluginExecutorService;
import com.hivemq.spi.services.configuration.ValueChangedCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author Christoph Schäbel
 */
abstract class ReloadingPropertiesReader {

    private static final Logger log = LoggerFactory.getLogger(ReloadingPropertiesReader.class);

    private final PluginExecutorService pluginExecutorService;
    private final SystemInformation systemInformation;
    private File file;
    private boolean propertiesEnabled = true;
    Properties properties;
    private Map<String, List<ValueChangedCallback<String>>> callbacks = Maps.newHashMap();

    ReloadingPropertiesReader(final PluginExecutorService pluginExecutorService,
                              final SystemInformation systemInformation) {
        this.pluginExecutorService = pluginExecutorService;
        this.systemInformation = systemInformation;
    }

    public void postConstruct() {
        file = new File(systemInformation.getConfigFolder() + "/" + getFilename());

        try {
            properties = new Properties();
            properties.load(new FileReader(file));
        } catch (IOException e) {
            log.warn("Not able to load configuration file {}, disabling (assuming environment variable used)", file.getAbsolutePath());
            propertiesEnabled = false;
        }

        if (propertiesEnabled) {
            pluginExecutorService.scheduleAtFixedRate(this::reload, 10, 3, TimeUnit.SECONDS);
        }
    }

    String getProperty() {
        if (properties == null) {
            return null;
        }

        return properties.getProperty("discoveryAddress");
    }

    @NotNull
    public abstract String getFilename();

    /**
     * Reloads the specified .properties file
     */
    @VisibleForTesting
    void reload() {

        Map<String, String> oldValues = getCurrentValues();

        try {
            final Properties props = new Properties();
            props.load(new FileReader(file));
            properties = props;

            Map<String, String> newValues = getCurrentValues();

            logChanges(oldValues, newValues);

        } catch (IOException e) {
            log.debug("Not able to reload configuration file {}", this.file.getAbsolutePath());
        }
    }

    void addCallback(final String propertyName, final ValueChangedCallback<String> changedCallback) {

        if (!callbacks.containsKey(propertyName)) {
            callbacks.put(propertyName, Lists.newArrayList());
        }

        callbacks.get(propertyName).add(changedCallback);
    }

    protected boolean isPropertiesEnabled() {
        return propertiesEnabled;
    }

    private Map<String, String> getCurrentValues() {
        Map<String, String> values = Maps.newHashMap();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }
        return values;
    }

    private void logChanges(final Map<String, String> oldValues, final Map<String, String> newValues) {
        final MapDifference<String, String> difference = Maps.difference(oldValues, newValues);

        for (Map.Entry<String, MapDifference.ValueDifference<String>> stringValueDifferenceEntry : difference.entriesDiffering().entrySet()) {
            log.debug("Plugin configuration {} changed from {} to {}",
                    stringValueDifferenceEntry.getKey(), stringValueDifferenceEntry.getValue().leftValue(),
                    stringValueDifferenceEntry.getValue().rightValue());

            if (callbacks.containsKey(stringValueDifferenceEntry.getKey())) {
                for (ValueChangedCallback<String> callback : callbacks.get(stringValueDifferenceEntry.getKey())) {
                    callback.valueChanged(stringValueDifferenceEntry.getValue().rightValue());
                }
            }
        }

        for (Map.Entry<String, String> stringStringEntry : difference.entriesOnlyOnLeft().entrySet()) {
            log.debug("Plugin configuration {} removed", stringStringEntry.getKey(), stringStringEntry.getValue());
            if (callbacks.containsKey(stringStringEntry.getKey())) {
                for (ValueChangedCallback<String> callback : callbacks.get(stringStringEntry.getKey())) {
                    callback.valueChanged(properties.getProperty(stringStringEntry.getValue()));
                }
            }
        }

        for (Map.Entry<String, String> stringStringEntry : difference.entriesOnlyOnRight().entrySet()) {
            log.debug("Plugin configuration {} added: {}", stringStringEntry.getValue(), stringStringEntry.getValue());
            if (callbacks.containsKey(stringStringEntry.getKey())) {
                for (ValueChangedCallback<String> callback : callbacks.get(stringStringEntry.getKey())) {
                    callback.valueChanged(stringStringEntry.getValue());
                }
            }
        }
    }

    @NotNull
    public Properties getProperties() {
        return properties;
    }


}