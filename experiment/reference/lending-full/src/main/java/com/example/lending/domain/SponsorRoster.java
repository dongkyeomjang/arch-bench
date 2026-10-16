package com.example.lending.domain;

import java.util.Set;
import java.util.UUID;

/**
 * 후원자로 지정된 회원 ID 목록. 등록 시 회원의 대출 등급을 정한다.
 */
public record SponsorRoster(Set<UUID> memberIds) {

    public SponsorRoster {
        memberIds = Set.copyOf(memberIds);
    }

    public LoanTier tierFor(UUID memberId) {
        return memberIds.contains(memberId) ? LoanTier.SPONSOR : LoanTier.STANDARD;
    }
}
