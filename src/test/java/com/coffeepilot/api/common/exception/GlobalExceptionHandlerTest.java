package com.coffeepilot.api.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/employees");

    private DataIntegrityViolationException violation(String constraint) {
        var sql = new SQLException("duplicate key " + constraint, "23505");
        var cause = new ConstraintViolationException("violação", sql, constraint);
        return new DataIntegrityViolationException("erro", cause);
    }

    @Test
    void constraintDeEmailVira409() {
        var response = handler.handleDataIntegrity(violation("uq_users_email"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void outraConstraintVira500() {
        var response = handler.handleDataIntegrity(violation("fk_users_coffee_shop"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}