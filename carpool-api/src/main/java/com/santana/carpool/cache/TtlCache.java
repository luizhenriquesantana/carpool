package com.santana.carpool.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic in-memory cache with per-entry TTL expiration.
 * Thread-safe via ConcurrentHashMap and atomic counters.
 *
 * @param <K> cache key type
 * @param <V> cache value type
 */
public class TtlCache<K, V> {
    private final long ttlSeconds;
    private final Map<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public TtlCache(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public V get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }

        if (entry.expiresAtEpochMillis < System.currentTimeMillis()) {
            store.remove(key);
            missCount.incrementAndGet();
            return null;
        }

        hitCount.incrementAndGet();
        return entry.value;
    }

    public void put(K key, V value) {
        long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
        store.put(key, new CacheEntry<>(value, expiresAt));
    }

    public Map<String, Long> stats() {
        return Map.of(
                "hits", hitCount.get(),
                "misses", missCount.get(),
                "size", (long) store.size()
        );
    }

    private record CacheEntry<T>(T value, long expiresAtEpochMillis) {
    }
}
