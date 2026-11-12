package com.coffeepilot.api.common.exception;

import java.math.BigDecimal;
import java.util.List;

public class InsufficientStockException extends RuntimeException {

    public enum Reason {
        INSUFFICIENT_STOCK,
        INACTIVE_INGREDIENT
    }

    public record StockIssue(
            Long ingredientId,
            String ingredientName,
            String unit,
            BigDecimal required,
            BigDecimal available,
            Reason reason
    ) {
    }

    private final List<StockIssue> issues;

    public InsufficientStockException(List<StockIssue> issues) {
        super("Estoque insuficiente para registrar o pedido.");
        this.issues = List.copyOf(issues);
    }

    public List<StockIssue> getIssues() {
        return issues;
    }
}
