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
class Turn15Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    // 2024년 2월·2023년 7월은 이 클래스만 쓴다(다른 턴 테스트는 2026년 날짜). 기한은 모두 평일이라 턴 8 규칙과 무관

    private int field(Api.Res r, String name) {
        return r.body().get(name).asInt();
    }

    @Test
    void monthly_stats_count_loans_returns_and_late_fees() {
        String member = api.member();
        // 1/10(수) 대출 → 기한 1/24(수), 2/1 반납: 8일 800원 (2월 반납·연체)
        String january = api.loanOk(api.book(), member, "2024-01-10");
        assertEquals(200, api.returnLoan(january, "2024-02-01").status());
        // 2/1(목) 대출 → 기한 2/15(목), 2/20 반납: 5일 500원
        String a = api.loanOk(api.book(), member, "2024-02-01");
        assertEquals(200, api.returnLoan(a, "2024-02-20").status());
        // 2/2 대출, 2/10 반납: 0원
        String b = api.loanOk(api.book(), member, "2024-02-02");
        assertEquals(200, api.returnLoan(b, "2024-02-10").status());
        // 2/5 대출, 미반납
        api.loanOk(api.book(), member, "2024-02-05");

        Api.Res r = api.get("/stats/monthly?year=2024&month=2");
        assertEquals(200, r.status());
        assertEquals(2024, field(r, "year"));
        assertEquals(2, field(r, "month"));
        assertEquals(3, field(r, "loanCount"));
        assertEquals(3, field(r, "returnCount"));
        assertEquals(2, field(r, "overdueCount"));
        assertEquals(1300, field(r, "lateFeeTotal"));
    }

    @Test
    void a_month_without_activity_has_zeros() {
        Api.Res r = api.get("/stats/monthly?year=2023&month=7");
        assertEquals(200, r.status());
        assertEquals(0, field(r, "loanCount"));
        assertEquals(0, field(r, "returnCount"));
        assertEquals(0, field(r, "overdueCount"));
        assertEquals(0, field(r, "lateFeeTotal"));
    }
}
