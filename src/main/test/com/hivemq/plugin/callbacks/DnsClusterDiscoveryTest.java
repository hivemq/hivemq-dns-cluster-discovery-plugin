package com.hivemq.plugin.callbacks;

import com.google.common.util.concurrent.ListenableFuture;
import com.hivemq.plugin.configuration.DnsDiscoveryConfiguration;
import com.hivemq.spi.callback.cluster.ClusterNodeAddress;
import com.hivemq.spi.services.PluginExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class DnsClusterDiscoveryTest {

    DnsClusterDiscovery dnsClusterDiscovery;

    @Mock
    PluginExecutorService pluginExecutorService;

    @Mock
    DnsDiscoveryConfiguration configuration;

    @Before
    public void setUp() {
        initMocks(this);

        dnsClusterDiscovery = new DnsClusterDiscovery(pluginExecutorService, configuration);

        // need to init so dns resolver works in the tests
        dnsClusterDiscovery.init(null, new ClusterNodeAddress("127.0.0.1", 12345));
    }

    @Test
    public void testResolveSuccessSingleNode() throws Exception {
        when(configuration.discoveryAddress()).thenReturn("www.dc-square.de");
        when(configuration.resolutionTimeout()).thenReturn(30);

        final AtomicReference<List<ClusterNodeAddress>> result = new AtomicReference<>();
        final CountDownLatch countDown = new CountDownLatch(1);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            result.set(((Callable<List<ClusterNodeAddress>>) invocation.getArguments()[0]).call());
            countDown.countDown();
            return null;
        }).when(pluginExecutorService).submit(any(Callable.class));

        final ListenableFuture<List<ClusterNodeAddress>> nodeAddresses = dnsClusterDiscovery.getNodeAddresses();

        countDown.await(30, TimeUnit.SECONDS);

        assertEquals(1, result.get().size());
        final ClusterNodeAddress address = result.get().get(0);
        // A record for dc-square.de
        assertEquals("212.72.72.12", address.getHost());
    }

    @Test
    public void testResolveFailed() throws Exception {
        when(configuration.discoveryAddress()).thenReturn("www.dc-square-this-is-not-resolved.de");
        when(configuration.resolutionTimeout()).thenReturn(30);

        final AtomicReference<List<ClusterNodeAddress>> result = new AtomicReference<>();
        final CountDownLatch countDown = new CountDownLatch(1);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            result.set(((Callable<List<ClusterNodeAddress>>) invocation.getArguments()[0]).call());
            countDown.countDown();
            return null;
        }).when(pluginExecutorService).submit(any(Callable.class));

        final ListenableFuture<List<ClusterNodeAddress>> nodeAddresses = dnsClusterDiscovery.getNodeAddresses();

        countDown.await(30, TimeUnit.SECONDS);

        assertEquals(0, result.get().size());
    }
}
