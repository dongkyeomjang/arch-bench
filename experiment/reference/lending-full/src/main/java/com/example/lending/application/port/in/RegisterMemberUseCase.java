package com.example.lending.application.port.in;

import com.example.lending.domain.Member;

public interface RegisterMemberUseCase {

    Member registerMember(RegisterMemberCommand command);

    record RegisterMemberCommand(String name) {
    }
}
