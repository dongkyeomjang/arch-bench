package com.example.lending.application.port.out;

import com.example.lending.domain.Member;

public interface SaveMemberPort {

    void saveMember(Member member);
}
