package com.coffeepilot.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";
    private static final String INGREDIENT_NAME_UNIQUE_CONSTRAINT =
            "uq_ingredients_coffee_shop_name";
    private static final String REVERSAL_UNIQUE_CONSTRAINT =
            "uq_stock_movements_reverses_movement";
    private static final String INITIAL_UNIQUE_CONSTRAINT =
            "uq_stock_movements_initial_per_ingredient";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyUsed(
            EmailAlreadyUsedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request,
                List.of(
                        new ApiError.FieldViolation("email", ex.getMessage())
                )
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            BusinessConflictException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(
            BusinessRuleException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        if (hasUniqueViolation(ex, EMAIL_UNIQUE_CONSTRAINT)) {
            return handleEmailAlreadyUsed(
                    new EmailAlreadyUsedException(),
                    request
            );
        }

        if (hasUniqueViolation(ex, INGREDIENT_NAME_UNIQUE_CONSTRAINT)) {
            return build(
                    HttpStatus.CONFLICT,
                    "Já existe um ingrediente com este nome.",
                    request,
                    List.of(
                            new ApiError.FieldViolation(
                                    "name",
                                    "Já existe um ingrediente com este nome."
                            )
                    )
            );
        }

        if (hasUniqueViolation(ex, REVERSAL_UNIQUE_CONSTRAINT)) {
            return build(
                    HttpStatus.CONFLICT,
                    "Esta movimentação já foi estornada.",
                    request,
                    List.of()
            );
        }

        if (hasUniqueViolation(ex, INITIAL_UNIQUE_CONSTRAINT)) {
            return build(
                    HttpStatus.CONFLICT,
                    "O ingrediente já possui movimentações de estoque.",
                    request,
                    List.of()
            );
        }

        log.error(
                "Violação de integridade do banco em {}",
                request.getRequestURI(),
                ex
        );

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno do servidor.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ApiError.FieldViolation> violations =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error -> new ApiError.FieldViolation(
                                normalizeField(error.getField()),
                                error.getDefaultMessage() == null
                                        ? "Valor inválido."
                                        : error.getDefaultMessage()
                        ))
                        .toList();

        return build(
                HttpStatus.BAD_REQUEST,
                "Dados inválidos.",
                request,
                violations
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido ou ilegível.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "Credenciais inválidas.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.FORBIDDEN,
                "Acesso negado.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status =
                    HttpStatus.resolve(errorResponse.getStatusCode().value());

            if (status != null && status.is4xxClientError()) {
                return build(
                        status,
                        status.getReasonPhrase(),
                        request,
                        List.of()
                );
            }
        }

        log.error(
                "Erro inesperado em {}",
                request.getRequestURI(),
                ex
        );

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno do servidor.",
                request,
                List.of()
        );
    }

    private boolean hasUniqueViolation(
            DataIntegrityViolationException ex,
            String constraintName
    ) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }

            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(constraintName)) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private String normalizeField(String field) {
        if ("passwordWithinBcryptLimit".equals(field)) {
            return "password";
        }

        return field;
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<ApiError.FieldViolation> fieldErrors
    ) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(status).body(body);
    }
}
