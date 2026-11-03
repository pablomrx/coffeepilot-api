package com.coffeepilot.api.product.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(

        @NotNull(message = "O status é obrigatório.")
        Boolean active

) {
}
