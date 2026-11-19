package com.fangjian.clinic.common;

/**
 * 业务异常。message 直接面向用户展示。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(404, what + "不存在");
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }
}
