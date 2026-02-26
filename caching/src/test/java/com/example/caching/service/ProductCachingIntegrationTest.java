package com.example.caching.service;

import com.example.caching.entity.Product;
import com.example.caching.enums.Category;
import com.example.caching.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProductCachingIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("demodb")
            .withUsername("root")
            .withPassword("root");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private ProductRepository productRepository;

    private Product savedProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        var productCache = cacheManager.getCache("product");
        if (productCache != null) {
            productCache.clear();
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
        assertThat(secondCall).isPresent();
        verify(productRepository, times(1)).findById(savedProduct.getProductId());
    }
}
