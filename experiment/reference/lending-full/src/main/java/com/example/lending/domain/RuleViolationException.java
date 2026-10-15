package com.example.lending.domain;

/**
 * 비즈니스 규칙 때문에 요청을 거절할 때 던진다. code 는 API 응답의 error 값이 된다.
 */
public abstract class RuleViolationException extends RuntimeException {

    private final String code;

    protected RuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
