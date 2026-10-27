package com.coffeepilot.api.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    private static final String BEARER = "Bearer";

    public LoginResponse {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("O token de acesso é obrigatório.");
        }

        if (expiresIn <= 0) {
            throw new IllegalArgumentException("O tempo de validade deve ser maior que zero.");
        }
    }

    public static LoginResponse bearer(
            String accessToken,
            long expiresInSeconds
    ) {
        return new LoginResponse(
                accessToken,
                BEARER,
                expiresInSeconds
        );
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=***, tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + "]";
    }
}