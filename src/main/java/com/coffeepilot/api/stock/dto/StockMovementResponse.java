package com.coffeepilot.api.stock.dto;

import com.coffeepilot.api.stock.StockAdjustmentReason;
import com.coffeepilot.api.stock.StockMovement;
import com.coffeepilot.api.stock.StockMovementType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StockMovementResponse(
        Long id,
        Long ingredientId,
        StockMovementType type,
        BigDecimal quantityDelta,
        BigDecimal balanceAfter,
        StockAdjustmentReason reason,
        String description,
        Long createdById,
        String createdByName,
        Long reversesMovementId,
        OffsetDateTime createdAt
) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getIngredient().getId(),
                movement.getType(),
                movement.getQuantityDelta(),
                movement.getBalanceAfter(),
                movement.getReason(),
                movement.getDescription(),
                movement.getCreatedBy().getId(),
                movement.getCreatedBy().getName(),
                movement.getReversesMovement() == null
                        ? null
                        : movement.getReversesMovement().getId(),
                movement.getCreatedAt()
        );
    }
}
