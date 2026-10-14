package com.example.fixtures.v7.domain;

import com.example.fixtures.v7.common.DateUtils;

import java.time.LocalDate;

// domain -> base.common 의존. R1 블랙리스트에 없으므로 R1에는 걸리지 않는다(의도된 범위). 위반은 DateUtils의 위치(R7)로만 잡힌다.
public class Book {

    private final String title;

    public Book(String title) {
        this.title = title;
    }

    public LocalDate dueDateFrom(LocalDate loanedAt) {
        return DateUtils.dueDate(loanedAt, 14);
    }
}
