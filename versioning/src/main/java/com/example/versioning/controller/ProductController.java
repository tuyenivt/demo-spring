package com.example.versioning.controller;

import com.example.versioning.dto.ProductV1;
import com.example.versioning.dto.ProductV2;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates media type versioning: the version is a parameter of a vendor media type.
 * Request with: Accept: application/vnd.company+json;v=1 (or v=2)
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Operation(summary = "Get product (v1)", deprecated = true)
    @GetMapping(version = "1")
    public ProductV1 getProductV1() {
        return new ProductV1("Widget", 29.99);
    }

    @Operation(summary = "Get product (v2)")
    @GetMapping(version = "2")
    public ProductV2 getProductV2() {
        return new ProductV2("Widget", 29.99, "Premium quality widget", "WIDGET-001");
    }
}
