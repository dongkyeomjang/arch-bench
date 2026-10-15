package com.example.lending.domain;

import java.util.UUID;

public class LoanAlreadyReturnedException extends RuleViolationException {

    public LoanAlreadyReturnedException(UUID loanId) {
        super("ALREADY_RETURNED", "loan is already returned: " + loanId);
    }
}
