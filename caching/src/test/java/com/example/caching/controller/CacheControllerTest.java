package com.example.caching.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CacheController.class)
class CacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private Cache cache;

    @MockitoBean
    private RedisCache redisCache;

    @Test
    void getCacheNames_shouldReturnCacheNames() throws Exception {
        // Given
        when(cacheManager.getCacheNames()).thenReturn(List.of("product", "product_list"));

        // When / Then
        mockMvc.perform(get("/api/cache/names"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("product"))
                .andExpect(jsonPath("$[1]").value("product_list"));

        verify(cacheManager, times(1)).getCacheNames();
    }

    @Test
    void clearCache_shouldClearCache_whenCacheExists() throws Exception {
        // Given
        when(cacheManager.getCache("product")).thenReturn(cache);

        // When / Then
        mockMvc.perform(delete("/api/cache/product"))
                .andExpect(status().isOk());

        verify(cacheManager, times(1)).getCache("product");
        verify(cache, times(1)).clear();
    }

    @Test
    void clearCache_shouldReturn404_whenCacheNotFound() throws Exception {
        // Given
        when(cacheManager.getCache("nonexistent")).thenReturn(null);

        // When / Then
        mockMvc.perform(delete("/api/cache/nonexistent"))
                .andExpect(status().isNotFound());

        verify(cacheManager, times(1)).getCache("nonexistent");
        verify(cache, never()).clear();
    }

    @Test
    void evictKey_shouldEvictKey_whenCacheExists() throws Exception {
        // Given
        when(cacheManager.getCache("product")).thenReturn(cache);

        // When / Then
        mockMvc.perform(delete("/api/cache/product/123"))
                .andExpect(status().isOk());

        verify(cacheManager, times(1)).getCache("product");
        verify(cache, times(1)).evict("123");
    }

    @Test
    void evictKey_shouldReturn404_whenCacheNotFound() throws Exception {
        // Given
        when(cacheManager.getCache("nonexistent")).thenReturn(null);

        // When / Then
        mockMvc.perform(delete("/api/cache/nonexistent/123"))
                .andExpect(status().isNotFound());

        verify(cacheManager, times(1)).getCache("nonexistent");
        verify(cache, never()).evict(anyString());
    }

    @Test
    void getStats_shouldReturnRedisCacheStats() throws Exception {
        // Given
        when(cacheManager.getCacheNames()).thenReturn(List.of("product"));
        when(cacheManager.getCache("product")).thenReturn(redisCache);

        // When / Then
        mockMvc.perform(get("/api/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value(nullValue()));

        verify(cacheManager, times(1)).getCacheNames();
        verify(cacheManager, times(1)).getCache("product");
    }
}
