package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Member;

class MemberMapper {

    Member toDomain(MemberJpaEntity entity) {
        return Member.reconstitute(entity.getId(), entity.getName());
    }

    MemberJpaEntity toEntity(Member member) {
        return new MemberJpaEntity(member.getId(), member.getName());
    }
}
