package com.example.lending.application.service;

import com.example.lending.application.port.in.ReturnBookUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.application.port.out.SaveLoanPort;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
import com.example.lending.domain.Loan;
import com.example.lending.domain.LoanNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class ReturnBookService implements ReturnBookUseCase {

    private final LoadLoanPort loadLoanPort;
    private final SaveLoanPort saveLoanPort;
    private final LoadBookPort loadBookPort;
    private final SaveBookPort saveBookPort;

    public ReturnBookService(LoadLoanPort loadLoanPort, SaveLoanPort saveLoanPort, LoadBookPort loadBookPort,
                             SaveBookPort saveBookPort) {
        this.loadLoanPort = loadLoanPort;
        this.saveLoanPort = saveLoanPort;
        this.loadBookPort = loadBookPort;
        this.saveBookPort = saveBookPort;
    }

    @Override
    public Loan returnBook(ReturnBookCommand command) {
        Loan loan = loadLoanPort.loadLoan(command.loanId())
                .orElseThrow(() -> new LoanNotFoundException(command.loanId()));
        loan.returnOn(LocalDate.now());
        Book book = loadBookPort.loadBook(loan.getBookId())
                .orElseThrow(() -> new BookNotFoundException(loan.getBookId()));
        book.receiveReturn();
        saveLoanPort.saveLoan(loan);
        saveBookPort.saveBook(book);
        return loan;
    }
}
