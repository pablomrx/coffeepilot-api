package com.coffeepilot.api.common.exception;

public class InvalidTokenException extends RuntimeException {

    public static final String CODE = "INVALID_TOKEN";

    public InvalidTokenException() {
        super("Link inválido ou expirado.");
    }

    public String getCode() {
        return CODE;
    }
}
