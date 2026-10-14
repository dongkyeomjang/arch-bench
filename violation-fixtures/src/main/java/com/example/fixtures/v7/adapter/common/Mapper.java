package com.example.fixtures.v7.adapter.common;

import com.example.fixtures.v7.domain.Book;

// 허용(R7): adapter.. 아래이므로 최상위 화이트리스트를 만족한다(adapter.in/out 이외 하위 패키지는 R7 범위 밖).
public class Mapper {

    public Book toDomain(String title) {
        return new Book(title);
    }
}
