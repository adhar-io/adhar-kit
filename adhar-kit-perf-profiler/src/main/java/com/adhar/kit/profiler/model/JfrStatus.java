package com.adhar.kit.profiler.model;

import java.time.Duration;
import java.util.List;

/**
 * Point-in-time status of the continuous JFR recording managed by the profiler.
 */
public record JfrStatus(
        boolean available,
        boolean running,
        String settings,
        long maxSizeMb,
        Duration maxAge,
        String dumpDirectory,
        List<String> dumpFiles
) {}
