package com.example.lending.adapter.in.web;

import com.example.lending.domain.Member;

import java.util.UUID;

public record MemberResponse(UUID memberId, String name) {

    static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getName());
    }
}
