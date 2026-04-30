package com.bjtu.dining.common;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleBadRequest(BadRequestException ex) {
        return ApiResponse.error(
                40001,
                "参数校验失败",
                Map.of("field", ex.field(), "reason", ex.reason())
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ApiResponse.error(40401, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Map<String, String>> handleException(Exception ex) {
        return ApiResponse.error(50000, "服务端异常", Map.of("reason", ex.getMessage()));
    }
}
