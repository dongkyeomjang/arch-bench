package com.example.lending.adapter.in.web;

import java.util.UUID;

public record LoanRequest(UUID bookId, UUID memberId) {
}
