package com.example.lending.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoanJpaRepository extends JpaRepository<LoanJpaEntity, UUID> {
}
