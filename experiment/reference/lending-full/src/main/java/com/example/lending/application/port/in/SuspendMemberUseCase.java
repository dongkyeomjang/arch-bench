package com.example.lending.application.port.in;

import com.example.lending.domain.Member;

import java.util.UUID;

public interface SuspendMemberUseCase {

    Member suspendMember(UUID memberId);

    Member unsuspendMember(UUID memberId);
}
