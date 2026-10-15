package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "member")
public class MemberJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    protected MemberJpaEntity() {
    }

    static MemberJpaEntity from(Member member) {
        MemberJpaEntity e = new MemberJpaEntity();
        e.id = member.getId();
        e.name = member.getName();
        return e;
    }

    Member toDomain() {
        return Member.reconstitute(id, name);
    }
}
