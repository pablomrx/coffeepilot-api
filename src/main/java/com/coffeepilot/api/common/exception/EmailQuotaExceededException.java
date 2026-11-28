package com.coffeepilot.api.common.exception;

public class EmailQuotaExceededException extends RuntimeException {

    public static final String CODE = "EMAIL_QUOTA_EXCEEDED";

    private final long retryAfterSeconds;

    public EmailQuotaExceededException(long retryAfterSeconds) {
        super("Não foi possível concluir a solicitação agora. Tente novamente mais tarde.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return CODE;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}