package com.example.fixtures.v4.domain;

import com.example.fixtures.v4.application.service.LoanPolicy;

// 위반(R4, 정의상 R1도): 도메인이 애플리케이션 서비스를 호출하고, 그 서비스는 다시 도메인을 참조한다.
public class Book {

    private final Long id;
    private boolean onLoan;

    public Book(Long id) {
        this.id = id;
    }

    public boolean isOnLoan() {
        return onLoan;
    }

    public void loan() {
        if (!new LoanPolicy().canLoan(this)) {
            throw new IllegalStateException("book is not loanable: " + id);
        }
        onLoan = true;
    }
}
