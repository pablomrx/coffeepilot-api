package com.coffeepilot.api.ingredient.dto;

import com.coffeepilot.api.ingredient.IngredientUnit;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateIngredientRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        @NotNull(message = "A unidade é obrigatória.")
        IngredientUnit unit,

        @PositiveOrZero(message = "O estoque mínimo não pode ser negativo.")
        @Digits(integer = 11, fraction = 3,
                message = "O estoque mínimo deve ter até 11 dígitos inteiros e 3 decimais.")
        BigDecimal minimumQuantity,

        @PositiveOrZero(message = "O saldo inicial não pode ser negativo.")
        @Digits(integer = 11, fraction = 3,
                message = "O saldo inicial deve ter até 11 dígitos inteiros e 3 decimais.")
        BigDecimal initialQuantity

) {

    public CreateIngredientRequest {
        name = name == null ? null : name.trim();
    }
}
