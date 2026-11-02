package com.coffeepilot.api.ingredient.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateIngredientStatusRequest(

        @NotNull(message = "O status é obrigatório.")
        Boolean active

) {
}
