package com.example.keyframevideo.exception;

public class BusinessException extends RuntimeException {

    // 业务错误码，最终由 GlobalExceptionHandler 转为统一 R.fail。
    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(String message, Throwable cause) {
        this(400, message, cause);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
