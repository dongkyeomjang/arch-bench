package com.example.lending.adapter.out.credit;

import com.example.lending.application.port.out.CreditCheckPort;
import com.example.lending.domain.CreditCheck;
import com.example.lending.domain.Member;
import com.examplecredit.sdk.CreditCheckClient;
import com.examplecredit.sdk.CreditCheckResponse;
import org.springframework.stereotype.Component;

/**
 * 업체 SDK(com.examplecredit.sdk)를 감싸는 신용조회. SDK 타입은 이 클래스 밖으로 나가지 않는다.
 * 실제 서버로 바꿀 때는 SDK 의 클라이언트만 바뀌고, 업체를 바꿀 때는 같은 포트를 구현하는 다른 어댑터를 둔다.
 */
@Component
public class ExampleCreditAdapter implements CreditCheckPort {

    private final CreditCheckClient client;

    public ExampleCreditAdapter(CreditCheckClient client) {
        this.client = client;
    }

    @Override
    public CreditCheck check(Member member) {
        CreditCheckResponse response = client.check(member.getId().toString(), member.getName());
        return new CreditCheck(response.approved, response.score, response.checkedAt);
    }
}
