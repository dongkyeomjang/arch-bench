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
class Turn08Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    // 후원자 ID 는 테스트마다 다른 것을 쓴다(같은 애플리케이션 컨텍스트에서 같은 ID 를 두 번 등록하지 않게)

    private String registerWithId(String memberId, String name) {
        Api.Res r = api.post("/members", obj("memberId", memberId, "name", name));
        assertEquals(201, r.status(), "POST /members with memberId " + r.body());
        assertEquals(memberId, r.text("memberId"));
        return memberId;
    }

    private void borrowFive(String member) {
        for (int i = 0; i < 5; i++) {
            api.loanOk(api.book(), member, "2026-02-02");
        }
    }

    @Test
    void sponsor_registered_with_given_id_can_borrow_a_sixth_book() {
        String sponsor = registerWithId("11111111-1111-1111-1111-111111111111", "후원자 하나");
        Api.Res got = api.get("/members/" + sponsor);
        assertEquals(200, got.status());
        assertEquals("후원자 하나", got.text("name"));
        borrowFive(sponsor);
        assertEquals(201, api.loan(api.book(), sponsor, "2026-02-02").status());
    }

    @Test
    void another_sponsor_is_not_limited_either() {
        String sponsor = registerWithId("22222222-2222-2222-2222-222222222222", "후원자 둘");
        borrowFive(sponsor);
        assertEquals(201, api.loan(api.book(), sponsor, "2026-02-02").status());
        assertEquals(201, api.loan(api.book(), sponsor, "2026-02-02").status());
    }

    @Test
    void third_sponsor_is_not_limited_either() {
        String sponsor = registerWithId("33333333-3333-3333-3333-333333333333", "후원자 셋");
        borrowFive(sponsor);
        assertEquals(201, api.loan(api.book(), sponsor, "2026-02-02").status());
    }

    @Test
    void member_registered_with_some_other_id_keeps_the_five_book_limit() {
        String member = registerWithId(UUID.randomUUID().toString(), Api.unique("Member"));
        borrowFive(member);
        Api.Res r = api.loan(api.book(), member, "2026-02-02");
        assertEquals(409, r.status());
        assertEquals("LOAN_LIMIT_EXCEEDED", r.error());
    }
}
