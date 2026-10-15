package com.example.lending.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LoanJpaRepository extends JpaRepository<LoanJpaEntity, UUID> {

    long countByMemberIdAndReturnedAtIsNull(UUID memberId);

    List<LoanJpaEntity> findByMemberId(UUID memberId);

    List<LoanJpaEntity> findByLoanedAtBetween(LocalDate from, LocalDate to);

    List<LoanJpaEntity> findByReturnedAtBetween(LocalDate from, LocalDate to);

    Page<LoanJpaEntity> findByMemberIdAndReturnedAtIsNull(UUID memberId, Pageable pageable);
}
