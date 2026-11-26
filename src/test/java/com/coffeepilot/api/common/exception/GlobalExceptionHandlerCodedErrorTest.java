package com.coffeepilot.api.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerCodedErrorTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void emailNotConfirmedReturns403WithStableCode() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/v1/auth/login");

        ResponseEntity<CodedApiError> response = handler.handleEmailNotConfirmed(
                new EmailNotConfirmedException(), request);

        assertEquals(403, response.getStatusCode().value());

        CodedApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(403, body.status());
        assertEquals("EMAIL_NOT_CONFIRMED", body.code());
        assertEquals("/v1/auth/login", body.path());
        assertEquals("Confirme seu e-mail para acessar o sistema.", body.message());
        assertTrue(body.fieldErrors().isEmpty());
        assertNotNull(body.timestamp());
    }
}
