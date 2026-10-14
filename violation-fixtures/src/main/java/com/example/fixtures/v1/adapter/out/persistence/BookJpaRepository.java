package com.example.fixtures.v1.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookJpaRepository extends JpaRepository<BookJpaEntity, Long> {
}
