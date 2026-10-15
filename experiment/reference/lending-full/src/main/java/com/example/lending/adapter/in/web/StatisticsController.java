package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.GetMonthlyStatisticsUseCase;
import com.example.lending.application.port.in.GetMonthlyStatisticsUseCase.MonthlyStatisticsQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class StatisticsController {

    private final GetMonthlyStatisticsUseCase getMonthlyStatisticsUseCase;

    public StatisticsController(GetMonthlyStatisticsUseCase getMonthlyStatisticsUseCase) {
        this.getMonthlyStatisticsUseCase = getMonthlyStatisticsUseCase;
    }

    @GetMapping("/monthly")
    public MonthlyStatisticsResponse getMonthlyStatistics(@RequestParam int year, @RequestParam int month) {
        return MonthlyStatisticsResponse.from(
                getMonthlyStatisticsUseCase.getMonthlyStatistics(new MonthlyStatisticsQuery(year, month)));
    }
}
