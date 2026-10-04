package com.example.monitor.controller;

import com.example.monitor.entity.Customer;
import com.example.monitor.health.ExternalApiHealthIndicator;
import com.example.monitor.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Random;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RootController.class)
class RootControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerRepository repository;

    @MockitoBean
    private ExternalApiHealthIndicator externalApiHealthIndicator;

    @MockitoBean
    private Random random;

    @Test
    void pingReturnsPong() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void customersReturnsList() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new Customer("1", "Alice"),
                new Customer("2", "Bob")
        ));

        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].name").value("Bob"));

        verify(repository).findAll();
    }

    @Test
    void unreliableReturnsBadRequestForInvalidFailureRate() throws Exception {
        mockMvc.perform(get("/customers/unreliable").param("failureRate", "120"))
                .andExpect(status().isBadRequest());
    }
}