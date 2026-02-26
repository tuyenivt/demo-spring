package com.example.caching.repository;

import com.example.caching.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByProductName(String productName, Sort sort);

    Page<Product> findByProductNameContainingIgnoreCase(String productName, Pageable pageable);
}
