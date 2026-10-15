package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.LoanBookUseCase;
import com.example.lending.application.port.in.LoanBookUseCase.LoanBookCommand;
import com.example.lending.application.port.in.ReturnBookUseCase;
import com.example.lending.application.port.in.ReturnBookUseCase.ReturnBookCommand;
import com.example.lending.domain.Loan;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/loans")
public class LoanController {

    private final LoanBookUseCase loanBookUseCase;
    private final ReturnBookUseCase returnBookUseCase;

    public LoanController(LoanBookUseCase loanBookUseCase, ReturnBookUseCase returnBookUseCase) {
        this.loanBookUseCase = loanBookUseCase;
        this.returnBookUseCase = returnBookUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse loanBook(@RequestBody LoanRequest request) {
        Loan loan = loanBookUseCase.loanBook(new LoanBookCommand(request.bookId(), request.memberId()));
        return LoanResponse.from(loan);
    }

    @PostMapping("/{loanId}/return")
    public ReturnResponse returnBook(@PathVariable UUID loanId) {
        return ReturnResponse.from(returnBookUseCase.returnBook(new ReturnBookCommand(loanId)));
    }
}
