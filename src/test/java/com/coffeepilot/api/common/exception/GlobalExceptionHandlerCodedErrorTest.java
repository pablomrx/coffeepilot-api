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

    @Test
    void emailQuotaExceededReturns503WithRetryAfterAndStableCode() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/v1/auth/forgot-password");

        ResponseEntity<CodedApiError> response = handler.handleEmailQuotaExceeded(
                new EmailQuotaExceededException(3600), request);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("3600", response.getHeaders().getFirst("Retry-After"));

        CodedApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.status());
        assertEquals("EMAIL_QUOTA_EXCEEDED", body.code());
        assertEquals("/v1/auth/forgot-password", body.path());
        assertEquals(
                "Não foi possível concluir a solicitação agora. Tente novamente mais tarde.",
                body.message());
        assertTrue(body.fieldErrors().isEmpty());
    }

    @Test
    void emailQuotaExceededNeverSendsRetryAfterBelowOneSecond() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/v1/auth/register");

        ResponseEntity<CodedApiError> response = handler.handleEmailQuotaExceeded(
                new EmailQuotaExceededException(0), request);

        assertEquals("1", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void invalidTokenReturns400WithStableCode() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/v1/auth/confirm-email");

        ResponseEntity<CodedApiError> response = handler.handleInvalidToken(
                new InvalidTokenException(), request);

        assertEquals(400, response.getStatusCode().value());

        CodedApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.status());
        assertEquals("INVALID_TOKEN", body.code());
        assertEquals("Link inválido ou expirado.", body.message());
        assertEquals("/v1/auth/confirm-email", body.path());
        assertTrue(body.fieldErrors().isEmpty());
    }
}
