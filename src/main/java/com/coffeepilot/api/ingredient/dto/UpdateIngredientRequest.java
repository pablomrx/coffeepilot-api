package com.coffeepilot.api.ingredient.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateIngredientRequest(

        @Size(min = 1, max = 150,
                message = "O nome não pode ficar em branco e deve ter no máximo 150 caracteres.")
        String name,

        @PositiveOrZero(message = "O estoque mínimo não pode ser negativo.")
        @Digits(integer = 11, fraction = 3,
                message = "O estoque mínimo deve ter até 11 dígitos inteiros e 3 decimais.")
        BigDecimal minimumQuantity

) {

    public UpdateIngredientRequest {
        name = name == null ? null : name.trim();
    }
}
