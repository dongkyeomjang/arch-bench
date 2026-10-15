package com.example.lending.application.port.out;

import com.example.lending.domain.Member;

import java.util.Optional;
import java.util.UUID;

public interface LoadMemberPort {

    Optional<Member> loadMember(UUID memberId);
}
