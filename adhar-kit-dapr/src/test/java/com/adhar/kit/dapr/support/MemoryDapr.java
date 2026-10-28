package com.adhar.kit.dapr.support;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprFacade;

import java.util.Map;

/**
 * Builds facades in memory mode for tests, independent of the machine's environment.
 */
public final class MemoryDapr {

    private MemoryDapr() {
    }

    public static DaprConnectionDetails memoryConnection(String appId) {
        Map<String, String> values = Map.of("adhar.dapr.mode", "memory", "adhar.dapr.app-id", appId);
        return DaprConnectionDetails.resolve(values::get);
    }

    public static DaprFacade facade() {
        return DaprFacade.builder().connection(memoryConnection("test-app")).build();
    }
}
