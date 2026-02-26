package com.example.caching.controller;

import com.example.caching.dto.ProductRequest;
import com.example.caching.dto.ProductResponse;
import com.example.caching.entity.Product;
import com.example.caching.enums.Category;
import com.example.caching.mapper.ProductMapper;
import com.example.caching.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductMapper productMapper;

    private Product testProduct;
    private ProductResponse testProductResponse;
    private ProductRequest testProductRequest;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .productId(1L)
                .productName("Test Product")
                .category(Category.PRODUCT)
                .price(new BigDecimal("29.99"))
                .inStock(100L)
                .dateOfManufacture(LocalDateTime.of(2026, 1, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .vendor("Test Vendor")
                .build();

        testProductResponse = ProductResponse.builder()
                .productId(1L)
                .productName("Test Product")
                .category(Category.PRODUCT)
                .price(new BigDecimal("29.99"))
                .inStock(100L)
                .dateOfManufacture(LocalDateTime.of(2026, 1, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .vendor("Test Vendor")
                .build();

        testProductRequest = ProductRequest.builder()
                .productName("Test Product")
                .category(Category.PRODUCT)
                .price(new BigDecimal("29.99"))
                .inStock(100L)
                .dateOfManufacture(LocalDateTime.of(2026, 1, 1, 10, 0))
                .vendor("Test Vendor")
                .build();
    }

    @Test
    void getById_shouldReturnProduct_whenProductExists() throws Exception {
        // Given
        when(productService.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productMapper.toResponse(testProduct)).thenReturn(testProductResponse);

        // When / Then
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.productName").value("Test Product"))
                .andExpect(jsonPath("$.price").value(29.99));

        verify(productService, times(1)).findById(1L);
    }

    @Test
    void getById_shouldReturn404_whenProductNotFound() throws Exception {
        // Given
        when(productService.findById(999L)).thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());

        verify(productService, times(1)).findById(999L);
    }

    @Test
    void searchByName_shouldReturnProducts() throws Exception {
        // Given
        when(productService.findByProductName(eq("Test"), any()))
                .thenReturn(new PageImpl<>(List.of(testProduct), PageRequest.of(0, 20), 1));
        when(productMapper.toResponse(testProduct)).thenReturn(testProductResponse);

        // When / Then
        mockMvc.perform(get("/api/products/search")
                        .param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("Test Product"));

        verify(productService, times(1)).findByProductName(eq("Test"), any());
    }

    @Test
    void list_shouldReturnProducts() throws Exception {
        // Given
        when(productService.findAll(any()))
                .thenReturn(new PageImpl<>(List.of(testProduct), PageRequest.of(0, 20), 1));
        when(productMapper.toResponse(testProduct)).thenReturn(testProductResponse);

        // When / Then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("Test Product"));

        verify(productService, times(1)).findAll(any());
    }

    @Test
    void create_shouldCreateProduct() throws Exception {
        // Given
        when(productMapper.toEntity(any(ProductRequest.class))).thenReturn(testProduct);
        when(productService.save(any(Product.class))).thenReturn(testProduct);
        when(productMapper.toResponse(testProduct)).thenReturn(testProductResponse);

        // When / Then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProductRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Test Product"));

        verify(productService, times(1)).save(any(Product.class));
    }

    @Test
    void create_shouldReturn400_whenProductNameIsBlank() throws Exception {
        // Given
        testProductRequest.setProductName("");

        // When / Then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProductRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn400_whenPriceIsNegative() throws Exception {
        // Given
        testProductRequest.setPrice(new BigDecimal("-10.00"));

        // When / Then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProductRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn400_whenInStockIsNegative() throws Exception {
        // Given
        testProductRequest.setInStock(-1L);

        // When / Then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProductRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_shouldUpdateProduct_whenProductExists() throws Exception {
        // Given
        when(productService.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productService.save(testProduct)).thenReturn(testProduct);
        when(productMapper.toResponse(testProduct)).thenReturn(testProductResponse);

        // When / Then
        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProductRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Test Product"));

        verify(productService, times(1)).findById(1L);
        verify(productMapper, times(1)).updateEntity(testProduct, testProductRequest);
        verify(productService, times(1)).save(testProduct);
    }

    @Test
    void update_shouldReturn404_whenProductNotFound() throws Exception {
        // Given
        when(productService.findById(999L)).thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(put("/api/products/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProductRequest)))
                .andExpect(status().isNotFound());

        verify(productService, times(1)).findById(999L);
        verify(productService, never()).save(any());
    }

    @Test
    void update_shouldReturn400_whenValidationFails() throws Exception {
        // Given
        testProductRequest.setProductName("");

        // When / Then
        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProductRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_shouldReturn204_whenProductExists() throws Exception {
        // Given
        when(productService.findById(1L)).thenReturn(Optional.of(testProduct));

        // When / Then
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService, times(1)).findById(1L);
        verify(productService, times(1)).delete(testProduct);
    }

    @Test
    void delete_shouldReturn404_whenProductNotFound() throws Exception {
        // Given
        when(productService.findById(anyLong())).thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(delete("/api/products/999"))
                .andExpect(status().isNotFound());

        verify(productService, times(1)).findById(999L);
        verify(productService, never()).delete(any());
    }
}
