package com.example.lending.domain;

import java.util.UUID;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(UUID memberId) {
        super("member not found: " + memberId);
    }
}
