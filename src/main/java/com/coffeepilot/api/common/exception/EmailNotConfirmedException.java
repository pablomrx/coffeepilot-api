package com.coffeepilot.api.common.exception;

public class EmailNotConfirmedException extends RuntimeException {

    public static final String CODE = "EMAIL_NOT_CONFIRMED";

    public EmailNotConfirmedException() {
        super("Confirme seu e-mail para acessar o sistema.");
    }

    public String getCode() {
        return CODE;
    }
}
