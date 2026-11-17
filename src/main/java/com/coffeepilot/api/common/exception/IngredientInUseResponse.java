package com.coffeepilot.api.common.exception;

import com.coffeepilot.api.common.exception.IngredientInUseException.AffectedProduct;

import java.time.Instant;
import java.util.List;

public record IngredientInUseResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ApiError.FieldViolation> fieldErrors,
        List<AffectedProduct> affectedProducts
) {

    public IngredientInUseResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        affectedProducts = affectedProducts == null
                ? List.of()
                : List.copyOf(affectedProducts);
    }
}
