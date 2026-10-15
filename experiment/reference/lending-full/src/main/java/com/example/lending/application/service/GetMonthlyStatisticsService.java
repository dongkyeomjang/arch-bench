package com.example.lending.application.service;

import com.example.lending.application.port.in.GetMonthlyStatisticsUseCase;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.domain.MonthlyLoanStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
@Transactional(readOnly = true)
public class GetMonthlyStatisticsService implements GetMonthlyStatisticsUseCase {

    private final LoadLoanPort loadLoanPort;

    public GetMonthlyStatisticsService(LoadLoanPort loadLoanPort) {
        this.loadLoanPort = loadLoanPort;
    }

    @Override
    public MonthlyLoanStatistics getMonthlyStatistics(MonthlyStatisticsQuery query) {
        YearMonth month = YearMonth.of(query.year(), query.month());
        return MonthlyLoanStatistics.compute(month,
                loadLoanPort.loadLoansLoanedBetween(month.atDay(1), month.atEndOfMonth()),
                loadLoanPort.loadLoansReturnedBetween(month.atDay(1), month.atEndOfMonth()));
    }
}
