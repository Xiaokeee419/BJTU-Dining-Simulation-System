package com.bjtu.dining.common.exception;

import com.bjtu.dining.common.enums.ErrorCode;
import com.bjtu.dining.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex) {
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.failure(ex.getErrorCode().getCode(), ex.getMessage(), ex.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, buildFieldErrorData(
                        fieldError == null ? null : fieldError.getField(),
                        fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage()
                )));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, buildFieldErrorData(
                        fieldError == null ? null : fieldError.getField(),
                        fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage()
                )));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        ConstraintViolation<?> violation = ex.getConstraintViolations().stream().findFirst().orElse(null);
        String field = violation == null ? null : violation.getPropertyPath().toString();
        String reason = violation == null ? "请求参数不合法" : violation.getMessage();
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, buildFieldErrorData(field, reason)));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleBadRequestException(Exception ex) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, buildFieldErrorData(null, ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception ex) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, buildFieldErrorData(null, ex.getMessage())));
    }

    private Map<String, Object> buildFieldErrorData(String field, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field", field);
        data.put("reason", reason);
        return data;
    }
}
