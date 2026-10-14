package com.example.fixtures.v6.application.port.out;

import com.example.fixtures.v6.domain.Book;

// 위반(R6-a): 포트가 interface가 아니라 abstract class
public abstract class LoadBookPort {

    public abstract Book loadBook(Long bookId);
}
