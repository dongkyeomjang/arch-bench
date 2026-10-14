package com.example.lending.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookJpaRepository extends JpaRepository<BookJpaEntity, UUID> {
}
