package com.bjtu.dining.common.enums;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SUCCESS(0, "ok", HttpStatus.OK),
    BAD_REQUEST(40000, "请求格式错误", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40101, "Token 过期", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40102, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "无权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    RATING_TARGET_NOT_FOUND(40401, "评分目标不存在", HttpStatus.NOT_FOUND),
    CONFLICT(40900, "资源状态冲突", HttpStatus.CONFLICT),
    INSUFFICIENT_BALANCE(40901, "余额不足", HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(40902, "菜品库存不足", HttpStatus.CONFLICT),
    USERNAME_EXISTS(40903, "用户名已存在", HttpStatus.CONFLICT),
    DISH_WINDOW_MISMATCH(40904, "菜品与窗口信息不匹配", HttpStatus.CONFLICT),
    INTERNAL_ERROR(50000, "服务端异常", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(50300, "仿真或推荐服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
