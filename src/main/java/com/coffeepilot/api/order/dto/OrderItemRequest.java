package com.coffeepilot.api.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(

        @NotNull(message = "O produto é obrigatório.")
        @Positive(message = "O produto informado é inválido.")
        Long productId,

        @NotNull(message = "A quantidade é obrigatória.")
        @Min(value = 1,
                message = "A quantidade de cada item deve estar entre 1 e 999.")
        @Max(value = 999,
                message = "A quantidade de cada item deve estar entre 1 e 999.")
        Integer quantity

) {
}
