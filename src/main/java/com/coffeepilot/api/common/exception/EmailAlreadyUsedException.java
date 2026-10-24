package com.coffeepilot.api.common.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Este e-mail já está em uso.");
    }
}
