package com.coffeepilot.api.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @Size(max = 50,
                message = "A mesa ou comanda deve ter no máximo 50 caracteres.")
        String tableReference,

        @Size(max = 500,
                message = "As observações devem ter no máximo 500 caracteres.")
        String notes,

        @NotNull(message = "A lista de itens é obrigatória.")
        @Size(min = 1, max = 50,
                message = "O pedido deve ter de 1 a 50 itens.")
        List<@NotNull(message = "Item do pedido inválido.") @Valid OrderItemRequest> items

) {

    public CreateOrderRequest {
        tableReference = tableReference == null ? null : tableReference.trim();
        notes = notes == null ? null : notes.trim();
    }
}
