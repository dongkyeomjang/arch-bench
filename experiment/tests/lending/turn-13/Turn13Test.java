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
class Turn13Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    private boolean suspended(String member) {
        Api.Res r = api.get("/members/" + member);
        assertEquals(200, r.status());
        return r.body().get("suspended").asBoolean();
    }

    @Test
    void suspended_member_cannot_borrow_until_unsuspended() {
        String member = api.member();
        assertTrue(!suspended(member), "new member is not suspended");
        assertEquals(200, api.post("/members/" + member + "/suspend", null).status());
        assertTrue(suspended(member));

        Api.Res r = api.loan(api.book(), member, "2026-04-01");
        assertEquals(409, r.status());
        assertEquals("MEMBER_SUSPENDED", r.error());

        assertEquals(200, api.post("/members/" + member + "/unsuspend", null).status());
        assertTrue(!suspended(member));
        assertEquals(201, api.loan(api.book(), member, "2026-04-01").status());
    }

    @Test
    void suspending_an_unknown_member_returns_404() {
        assertEquals(200, api.post("/members/" + api.member() + "/suspend", null).status());
        assertEquals(404, api.post("/members/" + UUID.randomUUID() + "/suspend", null).status());
    }
}
