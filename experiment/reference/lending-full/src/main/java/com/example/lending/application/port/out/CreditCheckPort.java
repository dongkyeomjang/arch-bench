package com.example.lending.application.port.out;

import com.example.lending.domain.CreditCheck;
import com.example.lending.domain.Member;

public interface CreditCheckPort {

    CreditCheck check(Member member);
}
