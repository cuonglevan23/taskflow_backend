package com.example.taskmanagement_backend.services.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Professional Cache Metrics Service
 * 
 * Responsibilities:
 * - Track cache hit/miss ratios
 * - Monitor cache performance
 * - Provide detailed metrics for monitoring
 * - Support cache optimization decisions
 * 
 * @author Task Management Team
 * @version 1.0
 */
@Slf4j
@Service
public class CacheMetricsService {

    // Metrics storage
    private final Map<String, AtomicLong> cacheHits = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> cacheMisses = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> cacheWrites = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> cacheEvictions = new ConcurrentHashMap<>();
    private final AtomicLong bulkEvictions = new AtomicLong(0);
    
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * Record cache hit for specific cache type
     */
    public void recordCacheHit(String cacheType) {
        cacheHits.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
        log.trace("📈 Cache HIT recorded for: {}", cacheType);
    }

    /**
     * Record cache miss for specific cache type
     */
    public void recordCacheMiss(String cacheType) {
        cacheMisses.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
        log.trace("📉 Cache MISS recorded for: {}", cacheType);
    }

    /**
     * Record cache write operation
     */
    public void recordCacheWrite(String cacheType) {
        cacheWrites.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
        log.trace("✍️ Cache WRITE recorded for: {}", cacheType);
    }

    /**
     * Record cache eviction
     */
    public void recordCacheEviction(String cacheType) {
        cacheEvictions.computeIfAbsent(cacheType, k -> new AtomicLong(0)).incrementAndGet();
        log.trace("🗑️ Cache EVICTION recorded for: {}", cacheType);
    }

    /**
     * Record bulk eviction operation
     */
    public void recordBulkEviction(long count) {
        bulkEvictions.addAndGet(count);
        log.debug("🔄 Bulk eviction recorded: {} keys", count);
    }

    /**
     * Get comprehensive cache metrics
     */
    public CacheMetrics getMetrics() {
        CacheMetrics.Builder builder = CacheMetrics.builder()
                .startTime(startTime)
                .bulkEvictions(bulkEvictions.get());

        // Calculate metrics for each cache type
        for (String cacheType : getAllCacheTypes()) {
            long hits = cacheHits.getOrDefault(cacheType, new AtomicLong(0)).get();
            long misses = cacheMisses.getOrDefault(cacheType, new AtomicLong(0)).get();
            long writes = cacheWrites.getOrDefault(cacheType, new AtomicLong(0)).get();
            long evictions = cacheEvictions.getOrDefault(cacheType, new AtomicLong(0)).get();
            
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total * 100 : 0.0;

            CacheTypeMetrics typeMetrics = CacheTypeMetrics.builder()
                    .cacheType(cacheType)
                    .hits(hits)
                    .misses(misses)
                    .writes(writes)
                    .evictions(evictions)
                    .totalRequests(total)
                    .hitRate(hitRate)
                    .build();

            builder.addCacheTypeMetrics(cacheType, typeMetrics);
        }

        return builder.build();
    }

    /**
     * Get metrics for specific cache type
     */
    public CacheTypeMetrics getMetricsForCacheType(String cacheType) {
        long hits = cacheHits.getOrDefault(cacheType, new AtomicLong(0)).get();
        long misses = cacheMisses.getOrDefault(cacheType, new AtomicLong(0)).get();
        long writes = cacheWrites.getOrDefault(cacheType, new AtomicLong(0)).get();
        long evictions = cacheEvictions.getOrDefault(cacheType, new AtomicLong(0)).get();
        
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0.0;

        return CacheTypeMetrics.builder()
                .cacheType(cacheType)
                .hits(hits)
                .misses(misses)
                .writes(writes)
                .evictions(evictions)
                .totalRequests(total)
                .hitRate(hitRate)
                .build();
    }

    /**
     * Reset all metrics
     */
    public void resetMetrics() {
        cacheHits.clear();
        cacheMisses.clear();
        cacheWrites.clear();
        cacheEvictions.clear();
        bulkEvictions.set(0);
        log.info("🔄 Cache metrics reset");
    }

    /**
     * Get summary statistics
     */
    public Map<String, Object> getSummaryStats() {
        CacheMetrics metrics = getMetrics();
        
        long totalHits = metrics.getCacheTypeMetrics().values().stream()
                .mapToLong(CacheTypeMetrics::getHits)
                .sum();
        
        long totalMisses = metrics.getCacheTypeMetrics().values().stream()
                .mapToLong(CacheTypeMetrics::getMisses)
                .sum();
        
        long totalWrites = metrics.getCacheTypeMetrics().values().stream()
                .mapToLong(CacheTypeMetrics::getWrites)
                .sum();
        
        long totalEvictions = metrics.getCacheTypeMetrics().values().stream()
                .mapToLong(CacheTypeMetrics::getEvictions)
                .sum();
        
        long totalRequests = totalHits + totalMisses;
        double overallHitRate = totalRequests > 0 ? (double) totalHits / totalRequests * 100 : 0.0;

        return Map.of(
                "totalHits", totalHits,
                "totalMisses", totalMisses,
                "totalWrites", totalWrites,
                "totalEvictions", totalEvictions,
                "totalRequests", totalRequests,
                "overallHitRate", String.format("%.2f%%", overallHitRate),
                "bulkEvictions", metrics.getBulkEvictions(),
                "startTime", metrics.getStartTime(),
                "cacheTypes", metrics.getCacheTypeMetrics().keySet()
        );
    }

    /**
     * Get all known cache types
     */
    private java.util.Set<String> getAllCacheTypes() {
        java.util.Set<String> allTypes = new java.util.HashSet<>();
        allTypes.addAll(cacheHits.keySet());
        allTypes.addAll(cacheMisses.keySet());
        allTypes.addAll(cacheWrites.keySet());
        allTypes.addAll(cacheEvictions.keySet());
        return allTypes;
    }

    /**
     * Cache metrics data class
     */
    public static class CacheMetrics {
        private final LocalDateTime startTime;
        private final long bulkEvictions;
        private final Map<String, CacheTypeMetrics> cacheTypeMetrics;

        private CacheMetrics(Builder builder) {
            this.startTime = builder.startTime;
            this.bulkEvictions = builder.bulkEvictions;
            this.cacheTypeMetrics = Map.copyOf(builder.cacheTypeMetrics);
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public LocalDateTime getStartTime() { return startTime; }
        public long getBulkEvictions() { return bulkEvictions; }
        public Map<String, CacheTypeMetrics> getCacheTypeMetrics() { return cacheTypeMetrics; }

        public static class Builder {
            private LocalDateTime startTime;
            private long bulkEvictions;
            private final Map<String, CacheTypeMetrics> cacheTypeMetrics = new ConcurrentHashMap<>();

            public Builder startTime(LocalDateTime startTime) {
                this.startTime = startTime;
                return this;
            }

            public Builder bulkEvictions(long bulkEvictions) {
                this.bulkEvictions = bulkEvictions;
                return this;
            }

            public Builder addCacheTypeMetrics(String cacheType, CacheTypeMetrics metrics) {
                this.cacheTypeMetrics.put(cacheType, metrics);
                return this;
            }

            public CacheMetrics build() {
                return new CacheMetrics(this);
            }
        }
    }

    /**
     * Cache type specific metrics
     */
    public static class CacheTypeMetrics {
        private final String cacheType;
        private final long hits;
        private final long misses;
        private final long writes;
        private final long evictions;
        private final long totalRequests;
        private final double hitRate;

        private CacheTypeMetrics(Builder builder) {
            this.cacheType = builder.cacheType;
            this.hits = builder.hits;
            this.misses = builder.misses;
            this.writes = builder.writes;
            this.evictions = builder.evictions;
            this.totalRequests = builder.totalRequests;
            this.hitRate = builder.hitRate;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getCacheType() { return cacheType; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getWrites() { return writes; }
        public long getEvictions() { return evictions; }
        public long getTotalRequests() { return totalRequests; }
        public double getHitRate() { return hitRate; }

        public static class Builder {
            private String cacheType;
            private long hits;
            private long misses;
            private long writes;
            private long evictions;
            private long totalRequests;
            private double hitRate;

            public Builder cacheType(String cacheType) {
                this.cacheType = cacheType;
                return this;
            }

            public Builder hits(long hits) {
                this.hits = hits;
                return this;
            }

            public Builder misses(long misses) {
                this.misses = misses;
                return this;
            }

            public Builder writes(long writes) {
                this.writes = writes;
                return this;
            }

            public Builder evictions(long evictions) {
                this.evictions = evictions;
                return this;
            }

            public Builder totalRequests(long totalRequests) {
                this.totalRequests = totalRequests;
                return this;
            }

            public Builder hitRate(double hitRate) {
                this.hitRate = hitRate;
                return this;
            }

            public CacheTypeMetrics build() {
                return new CacheTypeMetrics(this);
            }
        }
    }
}