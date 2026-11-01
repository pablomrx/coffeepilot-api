package com.coffeepilot.api.stock.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateStockEntryRequest(

        @NotNull(message = "A quantidade é obrigatória.")
        @Positive(message = "A quantidade deve ser maior que zero.")
        @Digits(integer = 11, fraction = 3,
                message = "A quantidade deve ter até 11 dígitos inteiros e 3 decimais.")
        BigDecimal quantity,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String description

) {

    public CreateStockEntryRequest {
        description = description == null || description.isBlank()
                ? null
                : description.trim();
    }
}
