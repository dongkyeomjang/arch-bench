package com.example.lending.adapter.out.credit;

import com.examplecredit.sdk.CreditCheckClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 업체 SDK 클라이언트를 빈으로 등록한다. SDK 는 com.example.lending 밖에 있어 컴포넌트 스캔에 걸리지 않는다.
 */
@Configuration
class CreditClientConfiguration {

    @Bean
    CreditCheckClient creditCheckClient() {
        return new CreditCheckClient();
    }
}
