package com.example.lending.domain;

import java.util.UUID;

public class MemberSuspendedException extends RuleViolationException {

    public MemberSuspendedException(UUID memberId) {
        super("MEMBER_SUSPENDED", "member is suspended: " + memberId);
    }
}
