package com.example.lending.application.port.in;

import com.example.lending.domain.Member;

import java.util.UUID;

public interface RegisterMemberUseCase {

    Member registerMember(RegisterMemberCommand command);

    /**
     * @param memberId 선택. null 이면 새 ID
     */
    record RegisterMemberCommand(UUID memberId, String name) {
    }
}
