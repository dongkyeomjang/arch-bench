package com.example.lending.adapter.out.persistence;

import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.SaveLoanPort;
import com.example.lending.domain.Loan;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LoanPersistenceAdapter implements LoadLoanPort, SaveLoanPort {

    private final LoanJpaRepository loanJpaRepository;
    private final LoanMapper loanMapper = new LoanMapper();

    public LoanPersistenceAdapter(LoanJpaRepository loanJpaRepository) {
        this.loanJpaRepository = loanJpaRepository;
    }

    @Override
    public long countActiveLoans(UUID memberId) {
        return loanJpaRepository.countByMemberId(memberId);
    }

    @Override
    public void saveLoan(Loan loan) {
        loanJpaRepository.save(loanMapper.toEntity(loan));
    }
}
