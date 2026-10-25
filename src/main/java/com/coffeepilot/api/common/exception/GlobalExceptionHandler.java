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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        if (isEmailUniqueViolation(ex)) {
            return handleEmailAlreadyUsed(
                    new EmailAlreadyUsedException(),
                    request
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

    private boolean isEmailUniqueViolation(
            DataIntegrityViolationException ex
    ) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }

            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(EMAIL_UNIQUE_CONSTRAINT)) {
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