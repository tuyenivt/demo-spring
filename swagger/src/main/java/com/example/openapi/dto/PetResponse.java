package com.example.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pet response")
public record PetResponse(
        @Schema(description = "Pet ID", example = "1")
        Long id,
        @Schema(description = "Pet name", example = "Buddy")
        String name,
        @Schema(description = "Current pet status", example = "available", allowableValues = {"available", "pending", "sold"})
        String status
) {
}
