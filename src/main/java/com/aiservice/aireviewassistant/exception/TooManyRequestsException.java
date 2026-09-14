package com.aiservice.aireviewassistant.exception;

// 限流异常：触发 API 限流时抛出
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
