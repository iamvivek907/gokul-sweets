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
import java.util.LinkedHashMap;
import java.util.Map;
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
            for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
                var usage = pool.getUsage();
                if (usage != null) {
                    log.info(
                            "JVM pool sample: name={}, usedMiB={}, committedMiB={}, maxMiB={}",
                            pool.getName(),
                            mib(usage.getUsed()),
                            mib(usage.getCommitted()),
                            usage.getMax() < 0 ? -1 : mib(usage.getMax()));
                }
            }
            // Values are cumulative or bytes exactly as the kernel reports them, not JVM heap.
            for (var entry :
                    Map.of(
                                    "memory.stat",
                                            new String[] {"anon", "file", "kernel", "slab", "sock"},
                                    "memory.events",
                                            new String[] {"high", "max", "oom", "oom_kill"},
                                    "cpu.stat",
                                            new String[] {
                                                "usage_usec",
                                                "nr_periods",
                                                "nr_throttled",
                                                "throttled_usec"
                                            })
                            .entrySet()) {
                var counters =
                        readCgroupCounters(
                                Path.of("/sys/fs/cgroup", entry.getKey()), entry.getValue());
                if (!counters.isEmpty()) {
                    log.info("Cgroup sample: source={}, counters={}", entry.getKey(), counters);
                }
            }
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
     * Reads selected cgroup v2 counters without assuming unavailable counters are zero.
     *
     * @param path the kernel statistics file or a fixture
     * @param names the permitted numeric counter names
     * @return available counters, or an empty map if the file cannot be read
     */
    static Map<String, Long> readCgroupCounters(Path path, String... names) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MemoryDiagnostics.class, "readCgroupCounters(Path,String...)");
        try {
            Map<String, Long> counters = new LinkedHashMap<>();
            try {
                for (String line : Files.readAllLines(path)) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length != 2) continue;
                    for (String name : names) {
                        if (name.equals(parts[0])) {
                            try {
                                long value = Long.parseLong(parts[1]);
                                if (value >= 0) counters.put(name, value);
                            } catch (NumberFormatException malformed) {
                                // Leave malformed or unsupported values absent.
                            }
                            break;
                        }
                    }
                }
            } catch (Exception unavailable) {
                // Other cgroup layouts and non-Linux development remain supported.
            }
            return counters;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MemoryDiagnostics.class,
                    "readCgroupCounters(Path,String...)");
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
