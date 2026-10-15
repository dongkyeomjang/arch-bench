package com.example.lending.adapter.out.persistence;

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

    public MemberJpaEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
