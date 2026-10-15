package com.example.lending.adapter.in.web;

import com.example.lending.domain.BookNotFoundException;
import com.example.lending.domain.LoanNotFoundException;
import com.example.lending.domain.MemberNotFoundException;
import com.example.lending.domain.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(RuleViolationException.class)
    public ProblemDetail handleRuleViolation(RuleViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setProperty("error", e.getCode());
        return problem;
    }

    @ExceptionHandler({BookNotFoundException.class, MemberNotFoundException.class, LoanNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidInput(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
