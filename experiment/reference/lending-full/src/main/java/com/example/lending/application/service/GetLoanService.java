package com.example.lending.application.service;

import com.example.lending.application.port.in.GetLoanUseCase;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.domain.Loan;
import com.example.lending.domain.LoanNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetLoanService implements GetLoanUseCase {

    private final LoadLoanPort loadLoanPort;

    public GetLoanService(LoadLoanPort loadLoanPort) {
        this.loadLoanPort = loadLoanPort;
    }

    @Override
    public Loan getLoan(UUID loanId) {
        return loadLoanPort.loadLoan(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
    }
}
