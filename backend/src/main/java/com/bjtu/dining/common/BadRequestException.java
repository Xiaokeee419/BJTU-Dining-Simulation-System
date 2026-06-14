package com.bjtu.dining.common;

public class BadRequestException extends RuntimeException {
    private final String field;
    private final String reason;

    public BadRequestException(String field, String reason) {
        super(reason);
        this.field = field;
        this.reason = reason;
    }

    public String field() {
        return field;
    }

    public String reason() {
        return reason;
    }
}
