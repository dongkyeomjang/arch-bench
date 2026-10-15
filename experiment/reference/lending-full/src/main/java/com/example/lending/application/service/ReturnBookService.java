package com.example.lending.application.service;

import com.example.lending.application.port.in.ReturnBookUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.RecordAuditPort;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.application.port.out.SaveLoanPort;
import com.example.lending.domain.AuditAction;
import com.example.lending.domain.AuditEntry;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
import com.example.lending.domain.Loan;
import com.example.lending.domain.LoanNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@Transactional
public class ReturnBookService implements ReturnBookUseCase {

    private final LoadLoanPort loadLoanPort;
    private final SaveLoanPort saveLoanPort;
    private final LoadBookPort loadBookPort;
    private final SaveBookPort saveBookPort;

    private final RecordAuditPort recordAuditPort;

    public ReturnBookService(LoadLoanPort loadLoanPort, SaveLoanPort saveLoanPort, LoadBookPort loadBookPort,
                             SaveBookPort saveBookPort, RecordAuditPort recordAuditPort) {
        this.loadLoanPort = loadLoanPort;
        this.saveLoanPort = saveLoanPort;
        this.loadBookPort = loadBookPort;
        this.saveBookPort = saveBookPort;
        this.recordAuditPort = recordAuditPort;
    }

    @Override
    public Loan returnBook(ReturnBookCommand command) {
        Loan loan = loadLoanPort.loadLoan(command.loanId())
                .orElseThrow(() -> new LoanNotFoundException(command.loanId()));
        loan.returnOn(command.returnedAt() != null ? command.returnedAt() : LocalDate.now());
        Book book = loadBookPort.loadBook(loan.getBookId())
                .orElseThrow(() -> new BookNotFoundException(loan.getBookId()));
        book.receiveReturn();
        saveLoanPort.saveLoan(loan);
        saveBookPort.saveBook(book);
        recordAuditPort.record(AuditEntry.of(AuditAction.RETURN, loan.getId(), loan.getMemberId(), Instant.now()));
        return loan;
    }
}
