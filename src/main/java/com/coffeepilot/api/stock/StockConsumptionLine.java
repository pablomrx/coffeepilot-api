package com.coffeepilot.api.stock;

import java.math.BigDecimal;

public record StockConsumptionLine(
        Long movementId,
        Long ingredientId,
        BigDecimal quantityDelta
) {
}
