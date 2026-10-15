package com.example.lending.domain;

import java.util.UUID;

public class LoanLimitExceededException extends RuleViolationException {

    public LoanLimitExceededException(UUID memberId, int limit) {
        super("LOAN_LIMIT_EXCEEDED", "member " + memberId + " already has " + limit + " books on loan");
    }
}
