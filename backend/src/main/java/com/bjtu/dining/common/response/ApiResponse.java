package com.bjtu.dining.common.response;

import com.bjtu.dining.common.enums.ErrorCode;
import com.bjtu.dining.common.util.TraceIdUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;
    private final String traceId;
    private final OffsetDateTime timestamp;

    private ApiResponse(int code, String message, T data, String traceId, OffsetDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                data,
                TraceIdUtils.currentTraceId(),
                OffsetDateTime.now(ZoneOffset.ofHours(8))
        );
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                message,
                data,
                TraceIdUtils.currentTraceId(),
                OffsetDateTime.now(ZoneOffset.ofHours(8))
        );
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, T data) {
        return new ApiResponse<>(
                errorCode.getCode(),
                errorCode.getMessage(),
                data,
                TraceIdUtils.currentTraceId(),
                OffsetDateTime.now(ZoneOffset.ofHours(8))
        );
    }

    public static <T> ApiResponse<T> failure(int code, String message, T data) {
        return new ApiResponse<>(
                code,
                message,
                data,
                TraceIdUtils.currentTraceId(),
                OffsetDateTime.now(ZoneOffset.ofHours(8))
        );
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}
