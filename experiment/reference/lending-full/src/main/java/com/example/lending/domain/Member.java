package com.example.lending.domain;

import java.util.Objects;
import java.util.UUID;

public class Member {

    private final UUID id;
    private final String name;
    private final LoanTier tier;
    private boolean suspended;

    private Member(UUID id, String name, LoanTier tier, boolean suspended) {
        this.id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        this.tier = Objects.requireNonNull(tier, "tier");
        this.suspended = suspended;
    }

    /**
     * 새 회원. id 가 null 이면 새로 만들고, 백오피스가 기존 회원 번호를 주면 그대로 쓴다. 등급은 후원자 목록이 정한다.
     */
    public static Member register(UUID id, String name, SponsorRoster sponsors) {
        UUID memberId = id != null ? id : UUID.randomUUID();
        return new Member(memberId, name, sponsors.tierFor(memberId), false);
    }

    public static Member reconstitute(UUID id, String name, LoanTier tier, boolean suspended) {
        return new Member(id, name, tier, suspended);
    }

    public void ensureCanBorrow(long activeLoanCount) {
        if (suspended) {
            throw new MemberSuspendedException(id);
        }
        if (!tier.allowsAnother(activeLoanCount)) {
            throw new LoanLimitExceededException(id, tier.maxActiveLoans());
        }
    }

    public void suspend() {
        this.suspended = true;
    }

    public void unsuspend() {
        this.suspended = false;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LoanTier getTier() {
        return tier;
    }

    public boolean isSuspended() {
        return suspended;
    }
}
