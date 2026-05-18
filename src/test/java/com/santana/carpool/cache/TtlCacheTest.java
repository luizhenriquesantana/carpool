package com.santana.carpool.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TtlCache Tests")
class TtlCacheTest {

    @Test
    @DisplayName("Should store and retrieve values")
    void testPutAndGet() {
        TtlCache<String, String> cache = new TtlCache<>(3600);

        cache.put("key1", "value1");

        assertThat(cache.get("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("Should return null for missing keys")
    void testGetMissing() {
        TtlCache<String, String> cache = new TtlCache<>(3600);

        assertThat(cache.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("Should expire entries when TTL is negative")
    void testExpiry() {
        TtlCache<String, String> cache = new TtlCache<>(-1);

        cache.put("key1", "value1");

        assertThat(cache.get("key1")).isNull();
    }

    @Test
    @DisplayName("Should track hit and miss counts")
    void testStats() {
        TtlCache<String, String> cache = new TtlCache<>(3600);

        cache.get("miss1");
        cache.put("key1", "value1");
        cache.get("key1");
        cache.get("miss2");

        Map<String, Long> stats = cache.stats();
        assertThat(stats).containsEntry("hits", 1L);
        assertThat(stats).containsEntry("misses", 2L);
        assertThat(stats).containsEntry("size", 1L);
    }

    @Test
    @DisplayName("Should count expired entry retrieval as a miss")
    void testExpiredEntryCountsAsMiss() {
        TtlCache<String, String> cache = new TtlCache<>(-1);

        cache.put("key1", "value1");
        cache.get("key1");

        Map<String, Long> stats = cache.stats();
        assertThat(stats).containsEntry("misses", 1L);
        assertThat(stats).containsEntry("hits", 0L);
    }

    @Test
    @DisplayName("Should initialize stats as zero")
    void testInitialStats() {
        TtlCache<String, String> cache = new TtlCache<>(3600);

        Map<String, Long> stats = cache.stats();
        assertThat(stats)
                .containsEntry("hits", 0L)
                .containsEntry("misses", 0L)
                .containsEntry("size", 0L);
    }

    @Test
    @DisplayName("Should overwrite existing entries")
    void testOverwrite() {
        TtlCache<String, String> cache = new TtlCache<>(3600);

        cache.put("key1", "original");
        cache.put("key1", "updated");

        assertThat(cache.get("key1")).isEqualTo("updated");
    }
}
