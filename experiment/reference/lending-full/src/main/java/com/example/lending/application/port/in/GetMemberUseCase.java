package com.example.lending.application.port.in;

import com.example.lending.domain.Member;

import java.util.UUID;

public interface GetMemberUseCase {

    Member getMember(UUID memberId);
}
