package com.coffeepilot.api.stock.dto;

import com.coffeepilot.api.stock.StockAdjustmentReason;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

public record CreateStockAdjustmentRequest(

        @NotNull(message = "A variação é obrigatória.")
        @Digits(integer = 11, fraction = 3,
                message = "A variação deve ter até 11 dígitos inteiros e 3 decimais.")
        BigDecimal quantityDelta,

        @NotNull(message = "O motivo é obrigatório.")
        StockAdjustmentReason reason,

        @NotBlank(message = "A justificativa é obrigatória.")
        @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres.")
        String justification

) {

    public CreateStockAdjustmentRequest {
        justification = justification == null ? null : justification.trim();
    }

    @JsonIgnore
    @AssertTrue(message = "A variação não pode ser zero.")
    public boolean isQuantityDeltaNotZero() {
        return quantityDelta == null || quantityDelta.signum() != 0;
    }
}
