package com.bjtu.dining.common.util;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIdUtils {
    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceIdUtils() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return normalizeTraceId(traceId);
    }

    public static String normalizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return newTraceId();
        }
        return traceId.trim();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
