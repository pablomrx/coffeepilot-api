package com.coffeepilot.api.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseMovementRequest(

        @NotBlank(message = "A justificativa do estorno é obrigatória.")
        @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres.")
        String description

) {

    public ReverseMovementRequest {
        description = description == null ? null : description.trim();
    }
}
