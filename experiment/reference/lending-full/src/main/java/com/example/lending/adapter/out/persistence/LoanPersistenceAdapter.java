package com.example.lending.adapter.out.persistence;

import com.example.lending.application.port.out.SaveLoanPort;
import com.example.lending.domain.Loan;
import org.springframework.stereotype.Component;

@Component
public class LoanPersistenceAdapter implements SaveLoanPort {

    private final LoanJpaRepository loanJpaRepository;
    private final LoanMapper loanMapper = new LoanMapper();

    public LoanPersistenceAdapter(LoanJpaRepository loanJpaRepository) {
        this.loanJpaRepository = loanJpaRepository;
    }

    @Override
    public void saveLoan(Loan loan) {
        loanJpaRepository.save(loanMapper.toEntity(loan));
    }
}
