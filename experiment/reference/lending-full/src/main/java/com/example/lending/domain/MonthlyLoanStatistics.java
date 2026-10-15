package com.example.lending.domain;

import java.time.YearMonth;
import java.util.List;

/**
 * 한 달의 대출 통계. 대출 건수는 대출일 기준, 반납·연체는 반납일 기준. 연체 = 연체료가 0보다 큰 반납.
 */
public record MonthlyLoanStatistics(int year, int month, long loanCount, long returnCount, long overdueCount,
                                    long lateFeeTotal) {

    public static MonthlyLoanStatistics compute(YearMonth yearMonth, List<Loan> loanedInMonth,
                                                List<Loan> returnedInMonth) {
        if (loanedInMonth.stream().anyMatch(l -> !YearMonth.from(l.getLoanedAt()).equals(yearMonth))
                || returnedInMonth.stream().anyMatch(l -> !l.isReturned()
                || !YearMonth.from(l.getReturnedAt()).equals(yearMonth))) {
            throw new IllegalArgumentException("loans outside " + yearMonth);
        }
        long overdue = returnedInMonth.stream().filter(l -> l.lateFee() > 0).count();
        long feeTotal = returnedInMonth.stream().mapToLong(Loan::lateFee).sum();
        return new MonthlyLoanStatistics(yearMonth.getYear(), yearMonth.getMonthValue(), loanedInMonth.size(),
                returnedInMonth.size(), overdue, feeTotal);
    }
}
