package com.example.fixtures.v4.application.service;

import com.example.fixtures.v4.domain.Book;

public class LoanPolicy {

    public boolean canLoan(Book book) {
        return !book.isOnLoan();
    }
}
