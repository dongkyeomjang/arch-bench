package com.example.lending.hidden;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.UUID;

import static com.example.lending.hidden.Api.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Turn01Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    @Test
    void registering_a_member_returns_201_with_id_and_name() {
        Api.Res r = api.post("/members", obj("name", "Kim Minji"));
        assertEquals(201, r.status());
        assertEquals("Kim Minji", r.text("name"));
        UUID.fromString(r.text("memberId"));
    }

    @Test
    void blank_name_is_rejected_with_400() {
        assertEquals(400, api.post("/members", obj("name", "")).status());
    }

    @Test
    void registered_member_can_be_fetched() {
        String id = api.member("Lee Jun");
        Api.Res r = api.get("/members/" + id);
        assertEquals(200, r.status());
        assertEquals(id, r.text("memberId"));
        assertEquals("Lee Jun", r.text("name"));
    }

    @Test
    void unknown_member_returns_404() {
        assertEquals(200, api.get("/members/" + api.member()).status());
        assertEquals(404, api.get("/members/" + UUID.randomUUID()).status());
    }
}
