package com.example.lending.adapter.out.persistence;

import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.LoadLoanPort.LoanSlice;
import com.example.lending.application.port.out.SaveLoanPort;
import com.example.lending.domain.Loan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class LoanPersistenceAdapter implements LoadLoanPort, SaveLoanPort {

    private final LoanJpaRepository loanJpaRepository;
    private final LoanMapper loanMapper = new LoanMapper();

    public LoanPersistenceAdapter(LoanJpaRepository loanJpaRepository) {
        this.loanJpaRepository = loanJpaRepository;
    }

    @Override
    public Optional<Loan> loadLoan(UUID loanId) {
        return loanJpaRepository.findById(loanId).map(loanMapper::toDomain);
    }

    @Override
    public long countActiveLoans(UUID memberId) {
        return loanJpaRepository.countByMemberIdAndReturnedAtIsNull(memberId);
    }

    @Override
    public LoanSlice loadActiveLoans(UUID memberId, int page, int size) {
        // Spring Data 의 Page/Pageable 은 이 어댑터 안에서만 쓰고, 포트 밖으로는 LoanSlice 로 내보낸다
        Page<LoanJpaEntity> result = loanJpaRepository.findByMemberIdAndReturnedAtIsNull(
                memberId, PageRequest.of(page, size, Sort.by("loanedAt", "id")));
        return new LoanSlice(result.map(loanMapper::toDomain).getContent(), result.getTotalElements());
    }

    @Override
    public void saveLoan(Loan loan) {
        loanJpaRepository.save(loanMapper.toEntity(loan));
    }
}
