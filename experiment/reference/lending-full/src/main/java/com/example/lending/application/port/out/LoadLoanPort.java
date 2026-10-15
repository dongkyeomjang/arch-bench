package com.example.lending.application.port.out;

import java.util.UUID;

public interface LoadLoanPort {

    long countActiveLoans(UUID memberId);
}
