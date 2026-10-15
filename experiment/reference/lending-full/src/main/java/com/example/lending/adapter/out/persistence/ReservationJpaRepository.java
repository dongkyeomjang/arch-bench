package com.example.lending.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    List<ReservationJpaEntity> findByBookIdAndFulfilledFalse(UUID bookId);
}
