package com.example.lending.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

public record LoanRequest(UUID bookId, UUID memberId, LocalDate loanedAt) {
}
