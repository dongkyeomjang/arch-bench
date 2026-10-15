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
class Turn03Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    @Test
    void returning_a_loan_makes_the_book_available_again() {
        String book = api.book();
        String loanId = api.loanOk(book, api.member(), null);
        Api.Res r = api.returnLoan(loanId);
        assertEquals(200, r.status());
        assertEquals(loanId, r.text("loanId"));
        assertNotNull(r.text("returnedAt"));
        assertEquals(201, api.loan(book, api.member()).status());
    }

    @Test
    void returning_the_same_loan_twice_is_rejected() {
        String loanId = api.loanOk(api.book(), api.member(), null);
        assertEquals(200, api.returnLoan(loanId).status());
        Api.Res again = api.returnLoan(loanId);
        assertEquals(409, again.status());
        assertEquals("ALREADY_RETURNED", again.error());
    }

    @Test
    void returned_loans_no_longer_count_toward_the_limit() {
        String member = api.member();
        String first = api.loanOk(api.book(), member, null);
        for (int i = 0; i < 4; i++) {
            api.loanOk(api.book(), member, null);
        }
        assertEquals(200, api.returnLoan(first).status());
        assertEquals(201, api.loan(api.book(), member).status());
    }

    @Test
    void returning_an_unknown_loan_returns_404() {
        assertEquals(200, api.returnLoan(api.loanOk(api.book(), api.member(), null)).status());
        assertEquals(404, api.returnLoan(UUID.randomUUID().toString()).status());
    }
}
