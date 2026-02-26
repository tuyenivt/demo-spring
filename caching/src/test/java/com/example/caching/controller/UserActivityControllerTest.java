package com.example.caching.controller;

import com.example.caching.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserActivityController.class)
class UserActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getAll_shouldReturnActivities() throws Exception {
        when(userService.getUserActivities("user123")).thenReturn(List.of("Logged in", "Viewed profile"));

        mockMvc.perform(get("/api/users/user123/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Logged in"))
                .andExpect(jsonPath("$[1]").value("Viewed profile"));

        verify(userService, times(1)).getUserActivities("user123");
    }
}
