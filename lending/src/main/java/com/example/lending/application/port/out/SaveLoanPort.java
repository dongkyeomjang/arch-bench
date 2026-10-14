package com.example.lending.application.port.out;

import com.example.lending.domain.Loan;

public interface SaveLoanPort {

    void saveLoan(Loan loan);
}
