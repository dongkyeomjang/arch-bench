package com.example.lending.adapter.out.credit;

import com.example.lending.application.port.out.CreditCheckPort;
import com.example.lending.domain.CreditCheck;
import com.example.lending.domain.Member;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 실제 서버가 생기기 전까지 쓰는 가짜 신용조회. 이름이 BLOCKED 로 시작하는 회원만 거절한다.
 * 실제 구현으로 바꿀 때는 같은 포트를 구현하는 HTTP 어댑터를 만들어 CreditCheckResponse 를 받아 같은 방식으로 바꾸면 된다.
 */
@Component
public class FakeCreditCheckAdapter implements CreditCheckPort {

    @Override
    public CreditCheck check(Member member, Instant requestedAt) {
        CreditCheckResponse response = call(member, requestedAt);
        return new CreditCheck(response.approved(), response.score(), response.checkedAt());
    }

    private CreditCheckResponse call(Member member, Instant requestedAt) {
        boolean approved = !member.getName().startsWith("BLOCKED");
        return new CreditCheckResponse(approved, approved ? 742 : 312, List.of(approved ? "OK" : "BLOCKED"),
                requestedAt.plusSeconds(1), new CreditCheckResponse.Provider("ExampleCredit", "2.3"));
    }
}
