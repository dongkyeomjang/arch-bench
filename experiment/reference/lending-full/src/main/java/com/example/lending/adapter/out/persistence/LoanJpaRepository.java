package com.example.lending.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanJpaRepository extends JpaRepository<LoanJpaEntity, UUID> {

    long countByMemberIdAndReturnedAtIsNull(UUID memberId);

    List<LoanJpaEntity> findByMemberId(UUID memberId);

    Page<LoanJpaEntity> findByMemberIdAndReturnedAtIsNull(UUID memberId, Pageable pageable);
}
