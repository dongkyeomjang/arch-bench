package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.LoanBookUseCase;
import com.example.lending.application.port.in.LoanBookUseCase.LoanBookCommand;
import com.example.lending.domain.Loan;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/loans")
public class LoanController {

    private final LoanBookUseCase loanBookUseCase;

    public LoanController(LoanBookUseCase loanBookUseCase) {
        this.loanBookUseCase = loanBookUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse loanBook(@RequestBody LoanRequest request) {
        Loan loan = loanBookUseCase.loanBook(new LoanBookCommand(request.bookId(), request.memberId()));
        return LoanResponse.from(loan);
    }
}
