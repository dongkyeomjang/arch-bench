package com.example.lending.domain;

/**
 * 회원의 동시 대출 한도 등급. 후원자는 한도가 없다.
 */
public enum LoanTier {
    STANDARD(5),
    SPONSOR(Integer.MAX_VALUE);

    private final int maxActiveLoans;

    LoanTier(int maxActiveLoans) {
        this.maxActiveLoans = maxActiveLoans;
    }

    boolean allowsAnother(long activeLoanCount) {
        return activeLoanCount < maxActiveLoans;
    }

    int maxActiveLoans() {
        return maxActiveLoans;
    }
}
