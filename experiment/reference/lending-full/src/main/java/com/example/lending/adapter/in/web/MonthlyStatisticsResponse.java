package com.example.lending.adapter.in.web;

import com.example.lending.domain.MonthlyLoanStatistics;

public record MonthlyStatisticsResponse(int year, int month, long loanCount, long returnCount, long overdueCount,
                                        long lateFeeTotal) {

    static MonthlyStatisticsResponse from(MonthlyLoanStatistics s) {
        return new MonthlyStatisticsResponse(s.year(), s.month(), s.loanCount(), s.returnCount(), s.overdueCount(),
                s.lateFeeTotal());
    }
}
