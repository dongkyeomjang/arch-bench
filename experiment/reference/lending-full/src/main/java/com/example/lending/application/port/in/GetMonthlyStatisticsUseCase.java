package com.example.lending.application.port.in;

import com.example.lending.domain.MonthlyLoanStatistics;

public interface GetMonthlyStatisticsUseCase {

    MonthlyLoanStatistics getMonthlyStatistics(MonthlyStatisticsQuery query);

    record MonthlyStatisticsQuery(int year, int month) {
        public MonthlyStatisticsQuery {
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("month must be between 1 and 12");
            }
        }
    }
}
