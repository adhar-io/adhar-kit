package com.adhar.kit.test.toxic;

import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.ToxicList;
import eu.rekawek.toxiproxy.model.toxic.Latency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NetworkToxics}. The Toxiproxy client is fully mocked, so the toxic-application
 * logic (directions, jitter handling, argument validation, checked-exception wrapping) is exercised
 * without any running Toxiproxy container - no Docker required.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("NetworkToxics Tests")
class NetworkToxicsTest {

    @Test
    @DisplayName("addLatency should create a downstream latency toxic without jitter")
    void testAddLatencyNoJitter() throws IOException {
        ContainerProxy proxy = mock(ContainerProxy.class);
        ToxicList toxics = mock(ToxicList.class);
        Latency latency = mock(Latency.class);
        when(proxy.toxics()).thenReturn(toxics);
        when(toxics.latency("l", ToxicDirection.DOWNSTREAM, 500L)).thenReturn(latency);

        NetworkToxics.addLatency(proxy, "l", 500L, 0L);

        verify(toxics).latency("l", ToxicDirection.DOWNSTREAM, 500L);
        verify(latency, org.mockito.Mockito.never()).setJitter(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("addLatency should set jitter when requested")
    void testAddLatencyWithJitter() throws IOException {
        ContainerProxy proxy = mock(ContainerProxy.class);
        ToxicList toxics = mock(ToxicList.class);
        Latency latency = mock(Latency.class);
        when(proxy.toxics()).thenReturn(toxics);
        when(toxics.latency(eq("l"), eq(ToxicDirection.DOWNSTREAM), eq(500L))).thenReturn(latency);

        NetworkToxics.addLatency(proxy, "l", 500L, 100L);

        verify(latency).setJitter(100L);
    }

    @Test
    @DisplayName("addLatency should reject negative arguments")
    void testAddLatencyValidation() {
        ContainerProxy proxy = mock(ContainerProxy.class);
        assertThrows(IllegalArgumentException.class, () -> NetworkToxics.addLatency(proxy, "l", -1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> NetworkToxics.addLatency(proxy, "l", 1L, -1L));
    }

    @Test
    @DisplayName("addLatency should wrap IOException as UncheckedIOException")
    void testAddLatencyWrapsIoException() throws IOException {
        ContainerProxy proxy = mock(ContainerProxy.class);
        ToxicList toxics = mock(ToxicList.class);
        when(proxy.toxics()).thenReturn(toxics);
        when(toxics.latency(eq("l"), eq(ToxicDirection.DOWNSTREAM), eq(1L))).thenThrow(new IOException("boom"));

        assertThrows(UncheckedIOException.class, () -> NetworkToxics.addLatency(proxy, "l", 1L, 0L));
    }

    @Test
    @DisplayName("addBandwidth should create a downstream bandwidth toxic")
    void testAddBandwidth() throws IOException {
        ContainerProxy proxy = mock(ContainerProxy.class);
        ToxicList toxics = mock(ToxicList.class);
        when(proxy.toxics()).thenReturn(toxics);

        NetworkToxics.addBandwidth(proxy, "b", 64L);

        verify(toxics).bandwidth("b", ToxicDirection.DOWNSTREAM, 64L);
    }

    @Test
    @DisplayName("addBandwidth should reject a negative rate and wrap IOException")
    void testAddBandwidthValidationAndWrap() throws IOException {
        ContainerProxy proxy = mock(ContainerProxy.class);
        assertThrows(IllegalArgumentException.class, () -> NetworkToxics.addBandwidth(proxy, "b", -1L));

        ToxicList toxics = mock(ToxicList.class);
        when(proxy.toxics()).thenReturn(toxics);
        when(toxics.bandwidth(eq("b"), eq(ToxicDirection.DOWNSTREAM), eq(1L))).thenThrow(new IOException("boom"));
        assertThrows(UncheckedIOException.class, () -> NetworkToxics.addBandwidth(proxy, "b", 1L));
    }

    @Test
    @DisplayName("removeToxic should look up and remove the named toxic")
    void testRemoveToxic() throws IOException {
        ContainerProxy proxy = mock(ContainerProxy.class);
        ToxicList toxics = mock(ToxicList.class);
        Toxic toxic = mock(Toxic.class);
        when(proxy.toxics()).thenReturn(toxics);
        when(toxics.get("l")).thenReturn(toxic);

        NetworkToxics.removeToxic(proxy, "l");

        verify(toxic).remove();
    }

    @Test
    @DisplayName("removeToxic should wrap IOException")
    void testRemoveToxicWraps() throws IOException {
        ContainerProxy proxy = mock(ContainerProxy.class);
        ToxicList toxics = mock(ToxicList.class);
        when(proxy.toxics()).thenReturn(toxics);
        when(toxics.get("l")).thenThrow(new IOException("boom"));

        assertThrows(UncheckedIOException.class, () -> NetworkToxics.removeToxic(proxy, "l"));
    }

    @Test
    @DisplayName("takeDown and bringUp should toggle the connection cut")
    void testTakeDownBringUp() {
        ContainerProxy proxy = mock(ContainerProxy.class);

        NetworkToxics.takeDown(proxy);
        NetworkToxics.bringUp(proxy);

        verify(proxy).setConnectionCut(true);
        verify(proxy).setConnectionCut(false);
    }
}
