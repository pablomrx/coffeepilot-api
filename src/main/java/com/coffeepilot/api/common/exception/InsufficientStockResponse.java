package com.coffeepilot.api.common.exception;

import com.coffeepilot.api.common.exception.InsufficientStockException.StockIssue;

import java.time.Instant;
import java.util.List;

public record InsufficientStockResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ApiError.FieldViolation> fieldErrors,
        List<StockIssue> stockIssues
) {

    public InsufficientStockResponse {
        fieldErrors = fieldErrors == null
                ? List.of()
                : List.copyOf(fieldErrors);
        stockIssues = stockIssues == null
                ? List.of()
                : List.copyOf(stockIssues);
    }
}
