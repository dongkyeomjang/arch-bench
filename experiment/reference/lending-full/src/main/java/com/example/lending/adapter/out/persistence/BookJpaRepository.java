package com.example.lending.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookJpaRepository extends JpaRepository<BookJpaEntity, UUID> {

    List<BookJpaEntity> findByTitleContainingIgnoreCase(String keyword);
}
