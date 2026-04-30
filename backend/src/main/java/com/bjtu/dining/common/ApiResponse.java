package com.bjtu.dining.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId,
        String timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, newTraceId(), now());
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, newTraceId(), now());
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String now() {
        return OffsetDateTime.now(ZoneOffset.ofHours(8))
                .withNano(0)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
