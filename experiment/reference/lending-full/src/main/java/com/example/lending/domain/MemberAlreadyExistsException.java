package com.example.lending.domain;

import java.util.UUID;

public class MemberAlreadyExistsException extends RuleViolationException {

    public MemberAlreadyExistsException(UUID memberId) {
        super("MEMBER_ALREADY_EXISTS", "member already exists: " + memberId);
    }
}
