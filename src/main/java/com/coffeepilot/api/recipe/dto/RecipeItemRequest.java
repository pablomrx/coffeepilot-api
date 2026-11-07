package com.coffeepilot.api.recipe.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecipeItemRequest(

        @NotNull(message = "O ingrediente é obrigatório.")
        @Positive(message = "O ingrediente informado é inválido.")
        Long ingredientId,

        @NotNull(message = "A quantidade é obrigatória.")
        @DecimalMin(value = "0.000", inclusive = false,
                message = "A quantidade deve ser maior que zero.")
        @Digits(integer = 11, fraction = 3,
                message = "A quantidade deve ter até 11 dígitos inteiros e 3 decimais.")
        BigDecimal quantity

) {
}
