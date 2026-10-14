package com.example.fixtures.v8.domain;

import jakarta.validation.constraints.NotNull;

// 위반(R1): 도메인 필드에 jakarta.validation 어노테이션. R5는 validation 어노테이션을 검사하지 않으므로 R1만 걸린다.
public class Member {

    @NotNull
    private final String name;

    public Member(String name) {
        this.name = name;
    }
}
