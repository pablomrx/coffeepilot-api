package com.coffeepilot.api.common.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {

    public ApiError {
        fieldErrors = fieldErrors == null
                ? List.of()
                : List.copyOf(fieldErrors);
    }

    public record FieldViolation(
            String field,
            String message
    ) {
    }
}
