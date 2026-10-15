package com.example.lending.hidden;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.lending.hidden.Api.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Turn04Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    // 2026-01-01(목) 대출 → 기한 2026-01-15(목). 기한이 주말이 아니라 턴 8 규칙과 무관하다

    private Api.Res loanAndReturn(String returnedAt) {
        String loanId = api.loanOk(api.book(), api.member(), "2026-01-01");
        return api.returnLoan(loanId, returnedAt);
    }

    @Test
    void loan_date_can_be_given_and_due_date_is_14_days_later() {
        Api.Res r = api.loan(api.book(), api.member(), "2026-01-01");
        assertEquals(201, r.status());
        assertEquals("2026-01-01", r.text("loanedAt"));
        assertEquals("2026-01-15", r.text("dueAt"));
    }

    @Test
    void returning_on_the_due_date_costs_nothing() {
        Api.Res r = loanAndReturn("2026-01-15");
        assertEquals(200, r.status());
        assertEquals("2026-01-15", r.text("returnedAt"));
        assertEquals(0, r.body().get("overdueDays").asInt());
        assertEquals(0, r.body().get("lateFee").asInt());
    }

    @Test
    void one_day_late_costs_100() {
        Api.Res r = loanAndReturn("2026-01-16");
        assertEquals(200, r.status());
        assertEquals(1, r.body().get("overdueDays").asInt());
        assertEquals(100, r.body().get("lateFee").asInt());
    }

    @Test
    void five_days_late_costs_500() {
        Api.Res r = loanAndReturn("2026-01-20");
        assertEquals(200, r.status());
        assertEquals(5, r.body().get("overdueDays").asInt());
        assertEquals(500, r.body().get("lateFee").asInt());
    }
}
