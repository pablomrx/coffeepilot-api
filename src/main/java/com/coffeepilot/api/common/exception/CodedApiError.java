package com.coffeepilot.api.common.exception;

import java.time.Instant;
import java.util.List;

public record CodedApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ApiError.FieldViolation> fieldErrors,
        String code
) {

    public CodedApiError {
        fieldErrors = fieldErrors == null
                ? List.of()
                : List.copyOf(fieldErrors);
    }
}
