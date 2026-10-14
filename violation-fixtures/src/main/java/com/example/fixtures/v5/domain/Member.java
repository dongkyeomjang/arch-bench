package com.example.fixtures.v5.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;

// 위반(R5): 도메인 클래스에 @Component, 생성자 파라미터에 @JsonProperty
@Component
public class Member {

    private final String name;

    public Member(@JsonProperty("member_name") String name) {
        this.name = name;
    }
}
