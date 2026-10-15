package com.example.lending.domain;

import java.util.UUID;

public class LoanNotFoundException extends RuntimeException {

    public LoanNotFoundException(UUID loanId) {
        super("loan not found: " + loanId);
    }
}
