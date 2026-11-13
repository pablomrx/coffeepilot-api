package com.coffeepilot.api.order.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

        @Size(max = 500,
                message = "A justificativa deve ter no máximo 500 caracteres.")
        String reason

) {

    public CancelOrderRequest {
        reason = reason == null ? null : reason.trim();
    }
}
