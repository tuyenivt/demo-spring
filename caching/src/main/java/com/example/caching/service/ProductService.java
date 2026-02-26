package com.example.caching.service;

import com.example.caching.entity.Product;
import com.example.caching.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "product", unless = "#result == null", key = "#id")
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "product_list", unless = "#result == null", key = "#productName")
    public List<Product> findByProductNameOrderByUpdatedAtDesc(String productName) {
        return productRepository.findByProductName(productName, Sort.by("updatedAt").descending());
    }

    @Transactional(readOnly = true)
    public Page<Product> findByProductName(String productName, Pageable pageable) {
        return productRepository.findByProductNameContainingIgnoreCase(productName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional
    @Caching(
            put = @CachePut(cacheNames = "product", condition = "#result.inStock gt 0", key = "#result.productId"),
            evict = {
                    @CacheEvict(cacheNames = "product_list", allEntries = true),
                    @CacheEvict(cacheNames = "product", condition = "#result.inStock eq 0", key = "#result.productId")
            }
    )
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "product", key = "#product.productId", beforeInvocation = true),
            @CacheEvict(cacheNames = "product_list", allEntries = true, beforeInvocation = true)
    })
    public void delete(Product product) {
        productRepository.delete(product);
    }
}
