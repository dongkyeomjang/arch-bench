package com.example.lending.domain;

import java.util.UUID;

public class CreditRejectedException extends RuleViolationException {

    public CreditRejectedException(UUID memberId) {
        super("CREDIT_REJECTED", "credit check rejected member " + memberId);
    }
}
