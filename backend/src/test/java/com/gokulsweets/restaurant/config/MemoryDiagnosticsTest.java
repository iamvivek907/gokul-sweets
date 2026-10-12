package com.gokulsweets.restaurant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class MemoryDiagnosticsTest {
    @TempDir Path folder;

    @Test
    void countersSelectKnownFieldsAndDoNotTurnUnavailableValuesIntoZero() throws Exception {
        Path stats = folder.resolve("memory.stat");
        Files.writeString(
                stats, "anon 1048576\nfile\t2097152\nsecret 123\nkernel invalid\nslab -1\n");
        assertThat(MemoryDiagnostics.readCgroupCounters(stats, "anon", "file", "kernel", "slab"))
                .containsOnlyKeys("anon", "file")
                .containsEntry("anon", 1048576L)
                .containsEntry("file", 2097152L);
        assertThat(MemoryDiagnostics.readCgroupCounters(folder.resolve("missing"), "anon"))
                .isEmpty();
    }
}
