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
class Turn10Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    @Test
    void credit_rejected_member_cannot_borrow_and_the_book_stays_available() {
        String book = api.book();
        String blocked = api.member(Api.unique("BLOCKED"));
        Api.Res r = api.loan(book, blocked, "2026-03-02");
        assertEquals(409, r.status());
        assertEquals("CREDIT_REJECTED", r.error());
        assertEquals(201, api.loan(book, api.member(), "2026-03-02").status());
    }

    @Test
    void loan_details_keep_the_credit_check_result() {
        String book = api.book();
        String member = api.member();
        String loanId = api.loanOk(book, member, "2026-03-02");
        Api.Res r = api.get("/loans/" + loanId);
        assertEquals(200, r.status());
        assertEquals(loanId, r.text("loanId"));
        assertEquals(book, r.text("bookId"));
        assertEquals(member, r.text("memberId"));
        assertEquals("2026-03-02", r.text("loanedAt"));
        assertEquals("2026-03-16", r.text("dueAt"));
        JsonNode credit = r.body().get("creditCheck");
        assertNotNull(credit, "creditCheck");
        assertTrue(credit.get("approved").asBoolean());
        assertTrue(credit.get("score").isNumber());
        assertNotNull(credit.get("checkedAt"));
        assertTrue(!credit.get("checkedAt").isNull());
    }

    @Test
    void unknown_loan_returns_404() {
        assertEquals(200, api.get("/loans/" + api.loanOk(api.book(), api.member(), "2026-03-02")).status());
        assertEquals(404, api.get("/loans/" + UUID.randomUUID()).status());
    }
}
