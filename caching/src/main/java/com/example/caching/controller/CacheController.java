package com.example.caching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Tag(name = "Cache", description = "Cache management API")
public class CacheController {

    private final CacheManager cacheManager;

    @Operation(summary = "Get all cache names", description = "Retrieve the names of all available caches")
    @ApiResponse(responseCode = "200", description = "List of cache names")
    @GetMapping("/names")
    public Collection<String> getCacheNames() {
        return cacheManager.getCacheNames();
    }

    @Operation(summary = "Get cache statistics", description = "Retrieve cache statistics for Redis caches")
    @ApiResponse(responseCode = "200", description = "Cache statistics")
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        var stats = new LinkedHashMap<String, Object>();
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache instanceof RedisCache redisCache) {
                stats.put(name, redisCache.getStatistics());
            }
        });
        return stats;
    }

    @Operation(summary = "Clear entire cache", description = "Clear all entries from a specific cache")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cache cleared successfully"),
            @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<Void> clearCache(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(summary = "Evict specific cache key", description = "Remove a specific key from a cache")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key evicted successfully"),
            @ApiResponse(responseCode = "404", description = "Cache not found")
    })
    @DeleteMapping("/{cacheName}/{key}")
    public ResponseEntity<Void> evictKey(@PathVariable String cacheName, @PathVariable String key) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
