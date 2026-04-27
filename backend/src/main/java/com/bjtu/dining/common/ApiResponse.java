package com.bjtu.dining.common;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, newTraceId(), OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, newTraceId(), OffsetDateTime.now());
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
