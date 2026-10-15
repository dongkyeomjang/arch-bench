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
class Turn02Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    @Test
    void sixth_concurrent_loan_is_rejected_but_other_members_are_unaffected() {
        String member = api.member();
        for (int i = 0; i < 5; i++) {
            api.loanOk(api.book(), member, null);
        }
        String sixth = api.book();
        Api.Res r = api.loan(sixth, member);
        assertEquals(409, r.status());
        assertEquals("LOAN_LIMIT_EXCEEDED", r.error());
        assertEquals(201, api.loan(sixth, api.member()).status());
    }

    @Test
    void unregistered_member_cannot_borrow() {
        assertEquals(404, api.loan(api.book(), UUID.randomUUID().toString()).status());
    }
}
