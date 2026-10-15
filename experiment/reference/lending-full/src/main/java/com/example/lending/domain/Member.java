package com.example.lending.domain;

import java.util.Objects;
import java.util.UUID;

public class Member {

    static final int MAX_ACTIVE_LOANS = 5;

    private final UUID id;
    private final String name;
    private boolean suspended;

    private Member(UUID id, String name, boolean suspended) {
        this.id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        this.suspended = suspended;
    }

    public static Member register(String name) {
        return new Member(UUID.randomUUID(), name, false);
    }

    public static Member reconstitute(UUID id, String name, boolean suspended) {
        return new Member(id, name, suspended);
    }

    public void ensureCanBorrow(long activeLoanCount) {
        if (suspended) {
            throw new MemberSuspendedException(id);
        }
        if (activeLoanCount >= MAX_ACTIVE_LOANS) {
            throw new LoanLimitExceededException(id, MAX_ACTIVE_LOANS);
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

    public boolean isSuspended() {
        return suspended;
    }
}
