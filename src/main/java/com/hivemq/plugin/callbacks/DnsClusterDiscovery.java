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

package com.hivemq.plugin.callbacks;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.hivemq.plugin.configuration.DnsDiscoveryConfiguration;
import com.hivemq.spi.callback.cluster.ClusterDiscoveryCallback;
import com.hivemq.spi.callback.cluster.ClusterNodeAddress;
import com.hivemq.spi.services.PluginExecutorService;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Cluster discovery using DNS resolution of round-robin A records.
 * Uses non-blocking netty API for DNS resolution, reads discovery parameters as environment variables.
 *
 * @author Simon Baier
 */
public class DnsClusterDiscovery implements ClusterDiscoveryCallback {
    private static final Logger log = LoggerFactory.getLogger(DnsClusterDiscovery.class);
    public static final ArrayList<ClusterNodeAddress> EMPTY_LIST = Lists.newArrayList();


    private final PluginExecutorService pluginExecutorService;
    private final DnsDiscoveryConfiguration discoveryConfiguration;
    private final NioEventLoopGroup eventLoopGroup;
    private final InetAddressValidator addressValidator;
    private ClusterNodeAddress ownAddress;

    @Inject
    public DnsClusterDiscovery(PluginExecutorService pluginExecutorService,
                               DnsDiscoveryConfiguration discoveryConfiguration) {
        this.pluginExecutorService = pluginExecutorService;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.addressValidator = InetAddressValidator.getInstance();
        this.discoveryConfiguration = discoveryConfiguration;
    }

    @Override
    public void init(String clusterId, ClusterNodeAddress ownAddress) {
        this.ownAddress = ownAddress;
    }

    @Override
    public ListenableFuture<List<ClusterNodeAddress>> getNodeAddresses() {
        return pluginExecutorService.submit(() -> {
            final String discoveryAddress = discoveryConfiguration.discoveryAddress();
            if (discoveryAddress == null) {
                return Lists.newArrayList();
            }
            final int discoveryTimeout = discoveryConfiguration.resolutionTimeout();

            // initialize netty DNS resolver
            try (DnsNameResolver resolver = new DnsNameResolverBuilder(eventLoopGroup.next())
                    .channelType(NioDatagramChannel.class).build()) {
                final Future<List<InetAddress>> addresses = resolver.resolveAll(discoveryAddress);
                final List<ClusterNodeAddress> clusterNodeAddresses = addresses.get(discoveryTimeout, TimeUnit.SECONDS)
                        .stream()
                        // Skip any possibly unresolved elements
                        .filter(Objects::nonNull)
                        // Check if the discoveryAddress address we got from the DNS is a valid IP address
                        .filter((address) -> addressValidator.isValid(address.getHostAddress()))
                        .map((address) -> new ClusterNodeAddress(address.getHostAddress(), ownAddress.getPort()))
                        .collect(Collectors.toList());
                if (log.isTraceEnabled()) {
                    clusterNodeAddresses.forEach((address) -> log.trace("Found address: '{}'", address.getHost()));
                }
                return clusterNodeAddresses;
            } catch (ExecutionException ex) {
                log.warn("Failed to resolve DNS record for address '{}', error: '{}'", discoveryAddress, ex.getMessage());
                if (log.isTraceEnabled()) {
                    log.trace("Stacktrace: '{}'", ExceptionUtils.getStackTrace(ex));
                }
            }
            return EMPTY_LIST;
        });
    }

    @Override
    public void destroy() {
        eventLoopGroup.shutdownGracefully();
    }
}
