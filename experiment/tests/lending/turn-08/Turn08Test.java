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
class Turn08Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    // 2026-01-03(토) 대출 → 기한 2026-01-17(토). 2026-01-04(일) 대출 → 기한 2026-01-18(일). 2026-01-19 는 월요일

    private Api.Res loanAndReturn(String loanedAt, String returnedAt) {
        String loanId = api.loanOk(api.book(), api.member(), loanedAt);
        return api.returnLoan(loanId, returnedAt);
    }

    @Test
    void due_on_saturday_and_returned_on_monday_costs_nothing() {
        Api.Res r = loanAndReturn("2026-01-03", "2026-01-19");
        assertEquals(200, r.status());
        assertEquals(0, r.body().get("lateFee").asInt());
    }

    @Test
    void due_on_saturday_and_returned_on_tuesday_costs_one_day() {
        Api.Res r = loanAndReturn("2026-01-03", "2026-01-20");
        assertEquals(200, r.status());
        assertEquals(100, r.body().get("lateFee").asInt());
    }

    @Test
    void due_on_sunday_and_returned_on_monday_costs_nothing() {
        Api.Res r = loanAndReturn("2026-01-04", "2026-01-19");
        assertEquals(200, r.status());
        assertEquals(0, r.body().get("lateFee").asInt());
    }

    @Test
    void due_date_in_responses_is_not_moved() {
        String member = api.member();
        Api.Res loan = api.loan(api.book(), member, "2026-01-03");
        assertEquals(201, loan.status());
        assertEquals("2026-01-17", loan.text("dueAt"));
        Api.Res list = api.get("/members/" + member + "/loans?page=0&size=5");
        assertEquals(200, list.status());
        assertEquals("2026-01-17", list.body().get("items").get(0).get("dueAt").asText());
    }
}
