package com.example.fixtures.v7.common;

import java.time.LocalDate;

// 위반(R7): domain/application/adapter 밖의 최상위 패키지(common)
public final class DateUtils {

    private DateUtils() {
    }

    public static LocalDate dueDate(LocalDate loanedAt, int days) {
        return loanedAt.plusDays(days);
    }
}
