package com.example.openapi.controller;

import com.example.openapi.exception.GlobalExceptionHandler;
import com.example.openapi.petstore.api.PetApi;
import com.example.openapi.petstore.model.Pet;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PetController.class)
@Import(GlobalExceptionHandler.class)
class PetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PetApi petApi;

    @Test
    void getPetByIdReturns200() throws Exception {
        var pet = new Pet().id(1L).name("Buddy").status(Pet.StatusEnum.AVAILABLE);
        when(petApi.getPetById(1L)).thenReturn(pet);

        mockMvc.perform(get("/api/pets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Buddy"))
                .andExpect(jsonPath("$.status").value("available"));
    }

    @Test
    void findPetsByStatusReturns200() throws Exception {
        var pet = new Pet().id(2L).name("Milo").status(Pet.StatusEnum.PENDING);
        when(petApi.findPetsByStatus(List.of("pending"))).thenReturn(List.of(pet));

        mockMvc.perform(get("/api/pets").queryParam("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("Milo"))
                .andExpect(jsonPath("$[0].status").value("pending"));
    }

    @Test
    void createPetReturns201() throws Exception {
        mockMvc.perform(post("/api/pets")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Buddy",
                                  "status": "available"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Buddy"))
                .andExpect(jsonPath("$.status").value("available"));

        verify(petApi).addPet(org.mockito.ArgumentMatchers.any(Pet.class));
    }

    @Test
    void createPetWithInvalidStatusReturns400() throws Exception {
        mockMvc.perform(post("/api/pets")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Buddy",
                                  "status": "broken"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getPetByIdWithNegativeIdReturns400() throws Exception {
        mockMvc.perform(get("/api/pets/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getPetByIdNotFoundReturns404() throws Exception {
        when(petApi.getPetById(99L)).thenThrow(feignException(404, "Not Found"));

        mockMvc.perform(get("/api/pets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findPetsByStatusWithInvalidValueReturns400() throws Exception {
        mockMvc.perform(get("/api/pets").queryParam("status", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createPetWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/pets")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": " ",
                                  "status": "available"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getPetByIdWithUpstream502Returns502() throws Exception {
        when(petApi.getPetById(1L)).thenThrow(feignException(502, "Bad Gateway"));

        mockMvc.perform(get("/api/pets/1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    private FeignException feignException(int status, String reason) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/pet/" + status,
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );

        Response response = Response.builder()
                .status(status)
                .reason(reason)
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        return FeignException.errorStatus("PetApi#getPetById", response);
    }
}
