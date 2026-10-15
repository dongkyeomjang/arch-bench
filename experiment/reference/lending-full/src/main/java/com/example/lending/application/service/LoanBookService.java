package com.example.lending.application.service;

import com.example.lending.application.port.in.LoanBookUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.application.port.out.SaveLoanPort;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
import com.example.lending.domain.Loan;
import com.example.lending.domain.Member;
import com.example.lending.domain.MemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class LoanBookService implements LoanBookUseCase {

    private final LoadBookPort loadBookPort;
    private final SaveBookPort saveBookPort;
    private final LoadMemberPort loadMemberPort;
    private final LoadLoanPort loadLoanPort;
    private final SaveLoanPort saveLoanPort;

    public LoanBookService(LoadBookPort loadBookPort, SaveBookPort saveBookPort, LoadMemberPort loadMemberPort,
                           LoadLoanPort loadLoanPort, SaveLoanPort saveLoanPort) {
        this.loadBookPort = loadBookPort;
        this.saveBookPort = saveBookPort;
        this.loadMemberPort = loadMemberPort;
        this.loadLoanPort = loadLoanPort;
        this.saveLoanPort = saveLoanPort;
    }

    @Override
    public Loan loanBook(LoanBookCommand command) {
        Member member = loadMemberPort.loadMember(command.memberId())
                .orElseThrow(() -> new MemberNotFoundException(command.memberId()));
        member.ensureCanBorrow(loadLoanPort.countActiveLoans(member.getId()));
        Book book = loadBookPort.loadBook(command.bookId())
                .orElseThrow(() -> new BookNotFoundException(command.bookId()));
        Loan loan = book.loanTo(member.getId(), command.loanedAt() != null ? command.loanedAt() : LocalDate.now());
        saveBookPort.saveBook(book);
        saveLoanPort.saveLoan(loan);
        return loan;
    }
}
