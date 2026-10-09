package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.observability.MethodTiming;

import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Internal aggregate measurements only; no public diagnostic endpoint. */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gokul.memory.diagnostics-enabled", havingValue = "true")
public class MemoryDiagnostics {

    private final MeterRegistry metrics;

    /** Returns sample information for memory diagnostics. */
    @Scheduled(fixedDelayString = "${gokul.memory.diagnostics-delay-ms:30000}")
    public void sample() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MemoryDiagnostics.class, "sample()");
        try {
            var memory = ManagementFactory.getMemoryMXBean();
            long buffers =
                    ManagementFactory.getPlatformMXBeans(
                                    java.lang.management.BufferPoolMXBean.class)
                            .stream()
                            .mapToLong(java.lang.management.BufferPoolMXBean::getMemoryUsed)
                            .sum();
            log.info(
                    "Memory sample: heapUsedMiB={}, heapCommittedMiB={}, nonHeapUsedMiB={},"
                            + " bufferMiB={}, threads={}, containerMiB={}",
                    mib(memory.getHeapMemoryUsage().getUsed()),
                    mib(memory.getHeapMemoryUsage().getCommitted()),
                    mib(memory.getNonHeapMemoryUsage().getUsed()),
                    mib(buffers),
                    ManagementFactory.getThreadMXBean().getThreadCount(),
                    containerMiB());
            for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                log.info(
                        "GC sample: collector={}, count={}, elapsedMs={}",
                        gc.getName(),
                        gc.getCollectionCount(),
                        gc.getCollectionTime());
            }
            for (String name :
                    new String[] {
                        "hikaricp.connections.active",
                        "hikaricp.connections.pending",
                        "hikaricp.connections.max"
                    }) {
                metrics.find(name)
                        .gauges()
                        .forEach(
                                gauge ->
                                        log.info(
                                                "Pool sample: metric={}, value={}",
                                                name,
                                                gauge.value()));
            }
            for (String name :
                    new String[] {"http.server.requests", "hikaricp.connections.acquire"}) {
                metrics.find(name)
                        .timers()
                        .forEach(
                                timer -> {
                                    var snapshot = timer.takeSnapshot();
                                    for (var percentile : snapshot.percentileValues()) {
                                        log.info(
                                                "Latency sample: metric={}, tags={}, count={},"
                                                        + " percentile={}, milliseconds={}",
                                                name,
                                                timer.getId().getTags(),
                                                snapshot.count(),
                                                percentile.percentile(),
                                                percentile.value(TimeUnit.MILLISECONDS));
                                    }
                                });
            }
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MemoryDiagnostics.class, "sample()");
        }
    }

    /**
     * Returns mib information for memory diagnostics.
     *
     * @param bytes the bytes supplied to this method
     * @return the value of {@code bytes / (1024 * 1024)}
     */
    private static long mib(long bytes) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MemoryDiagnostics.class, "mib(long)");
        try {
            return bytes / (1024 * 1024);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MemoryDiagnostics.class, "mib(long)");
        }
    }

    /**
     * Containers mi b.
     *
     * @return the container mi b result
     */
    private static long containerMiB() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MemoryDiagnostics.class, "containerMiB()");
        try {
            for (String file :
                    new String[] {
                        "/sys/fs/cgroup/memory.current",
                        "/sys/fs/cgroup/memory/memory.usage_in_bytes"
                    }) {
                try {
                    return mib(Long.parseLong(Files.readString(Path.of(file)).trim()));
                } catch (Exception unavailable) {
                    /* Non-Linux or different cgroup layout. */
                }
            }
            return -1;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MemoryDiagnostics.class, "containerMiB()");
        }
    }
}
