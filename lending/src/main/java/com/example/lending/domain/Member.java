package com.example.lending.domain;

import java.util.Objects;
import java.util.UUID;

public class Member {

    private final UUID id;
    private final String name;

    private Member(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
    }

    public static Member register(String name) {
        return new Member(UUID.randomUUID(), name);
    }

    public static Member reconstitute(UUID id, String name) {
        return new Member(id, name);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
