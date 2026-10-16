package com.example.lending.application.service;

import com.example.lending.application.port.in.LoanBookUseCase;
import com.example.lending.application.port.out.CreditCheckPort;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.application.port.out.LoadReservationPort;
import com.example.lending.application.port.out.RecordAuditPort;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.application.port.out.SaveLoanPort;
import com.example.lending.application.port.out.SaveReservationPort;
import com.example.lending.domain.AuditAction;
import com.example.lending.domain.AuditEntry;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
import com.example.lending.domain.Loan;
import com.example.lending.domain.Member;
import com.example.lending.domain.MemberNotFoundException;
import com.example.lending.domain.ReservationQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@Transactional
public class LoanBookService implements LoanBookUseCase {

    private final LoadBookPort loadBookPort;
    private final SaveBookPort saveBookPort;
    private final LoadMemberPort loadMemberPort;
    private final LoadLoanPort loadLoanPort;
    private final SaveLoanPort saveLoanPort;
    private final LoadReservationPort loadReservationPort;
    private final SaveReservationPort saveReservationPort;
    private final CreditCheckPort creditCheckPort;

    private final RecordAuditPort recordAuditPort;

    public LoanBookService(LoadBookPort loadBookPort, SaveBookPort saveBookPort, LoadMemberPort loadMemberPort,
                           LoadLoanPort loadLoanPort, SaveLoanPort saveLoanPort, LoadReservationPort loadReservationPort,
                           SaveReservationPort saveReservationPort, CreditCheckPort creditCheckPort,
                           RecordAuditPort recordAuditPort) {
        this.loadBookPort = loadBookPort;
        this.saveBookPort = saveBookPort;
        this.loadMemberPort = loadMemberPort;
        this.loadLoanPort = loadLoanPort;
        this.saveLoanPort = saveLoanPort;
        this.loadReservationPort = loadReservationPort;
        this.saveReservationPort = saveReservationPort;
        this.creditCheckPort = creditCheckPort;
        this.recordAuditPort = recordAuditPort;
    }

    @Override
    public Loan loanBook(LoanBookCommand command) {
        Member member = loadMemberPort.loadMember(command.memberId())
                .orElseThrow(() -> new MemberNotFoundException(command.memberId()));
        member.ensureCanBorrow(loadLoanPort.countActiveLoans(member.getId()));
        Book book = loadBookPort.loadBook(command.bookId())
                .orElseThrow(() -> new BookNotFoundException(command.bookId()));
        Loan loan = book.loanTo(member.getId(), command.loanedAt() != null ? command.loanedAt() : LocalDate.now());
        ReservationQueue queue = ReservationQueue.of(book.getId(), loadReservationPort.loadWaitingReservations(book.getId()));
        queue.admit(member.getId()).ifPresent(saveReservationPort::saveReservation);
        // 외부 조회는 내부 규칙(한도·책 상태·예약)을 모두 통과한 뒤 한 번만 부른다
        loan.recordCreditCheck(creditCheckPort.check(member));
        saveBookPort.saveBook(book);
        saveLoanPort.saveLoan(loan);
        recordAuditPort.record(AuditEntry.of(AuditAction.LOAN, loan.getId(), member.getId(), Instant.now()));
        return loan;
    }
}
