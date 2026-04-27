package com.bjtu.dining.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final int code;
    private final HttpStatus status;
    private final Object data;

    public ApiException(int code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public ApiException(int code, String message, HttpStatus status, Object data) {
        super(message);
        this.code = code;
        this.status = status;
        this.data = data;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Object data() {
        return data;
    }
}
