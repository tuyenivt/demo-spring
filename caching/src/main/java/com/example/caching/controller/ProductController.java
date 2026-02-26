package com.example.caching.controller;

import com.example.caching.dto.ProductRequest;
import com.example.caching.dto.ProductResponse;
import com.example.caching.mapper.ProductMapper;
import com.example.caching.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Product", description = "Product management API with Redis caching")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @Operation(summary = "Get product by ID", description = "Retrieve a product by its ID. Result is cached in Redis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return productService.findById(id)
                .map(productMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List products", description = "Retrieve all products with pagination.")
    @ApiResponse(responseCode = "200", description = "Paginated list of products")
    @GetMapping
    public Page<ProductResponse> list(@PageableDefault(size = 20, sort = "productId") Pageable pageable) {
        return productService.findAll(pageable).map(productMapper::toResponse);
    }

    @Operation(summary = "Search products by name", description = "Search products by name with pagination, sorted by updated date descending.")
    @ApiResponse(responseCode = "200", description = "Paginated list of products matching the name")
    @GetMapping("/search")
    public Page<ProductResponse> searchByName(
            @RequestParam @NotBlank(message = "Name parameter is required") String name,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return productService.findByProductName(name, pageable).map(productMapper::toResponse);
    }

    @Operation(summary = "Create a new product", description = "Create a new product and update cache")
    @ApiResponse(responseCode = "201", description = "Product created successfully")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        var product = productMapper.toEntity(request);
        var savedProduct = productService.save(product);
        return productMapper.toResponse(savedProduct);
    }

    @Operation(summary = "Update a product", description = "Update an existing product by ID and refresh cache")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.findById(id)
                .map(existingProduct -> {
                    productMapper.updateEntity(existingProduct, request);
                    var updatedProduct = productService.save(existingProduct);
                    return ResponseEntity.ok(productMapper.toResponse(updatedProduct));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a product", description = "Delete an existing product by ID and evict related cache entries.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return productService.findById(id)
                .map(product -> {
                    productService.delete(product);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
