package com.example.caching.service;

import com.example.caching.entity.Product;
import com.example.caching.enums.Category;
import com.example.caching.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProductCachingIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("demodb")
            .withUsername("root")
            .withPassword("root");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4-alpine")
            .withExposedPorts(6379);

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private ProductRepository productRepository;

    private Product savedProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        for (var cacheName : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.invalidate();
            }
        }

        savedProduct = productRepository.save(Product.builder()
                .productName("Cached Product")
                .category(Category.PRODUCT)
                .price(new BigDecimal("19.99"))
                .inStock(5L)
                .dateOfManufacture(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .vendor("Vendor A")
                .build());

        clearInvocations(productRepository);
    }

    @Test
    void secondCallReturnsCachedResult() {
        var firstCall = productService.findById(savedProduct.getProductId());
        var secondCall = productService.findById(savedProduct.getProductId());

        assertThat(firstCall).isPresent();
        assertThat(secondCall).get()
                .isInstanceOf(Product.class)
                .extracting(Product::getProductName)
                .isEqualTo("Cached Product");
        verify(productRepository, times(1)).findById(savedProduct.getProductId());
    }

    @Test
    void secondListCallReturnsCachedProducts() {
        var firstCall = productService.findByProductNameOrderByUpdatedAtDesc("Cached Product");
        var secondCall = productService.findByProductNameOrderByUpdatedAtDesc("Cached Product");

        assertThat(firstCall).hasSize(1);
        assertThat(secondCall).singleElement()
                .isInstanceOf(Product.class)
                .extracting(Product::getProductId)
                .isEqualTo(savedProduct.getProductId());
        verify(productRepository, times(1)).findByProductName(eq("Cached Product"), any());
    }
}
