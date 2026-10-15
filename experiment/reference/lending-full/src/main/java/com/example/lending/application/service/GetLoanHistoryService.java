package com.example.lending.application.service;

import com.example.lending.application.port.in.GetLoanHistoryUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.domain.Book;
import com.example.lending.domain.Loan;
import com.example.lending.domain.MemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetLoanHistoryService implements GetLoanHistoryUseCase {

    private final LoadMemberPort loadMemberPort;
    private final LoadLoanPort loadLoanPort;
    private final LoadBookPort loadBookPort;

    public GetLoanHistoryService(LoadMemberPort loadMemberPort, LoadLoanPort loadLoanPort, LoadBookPort loadBookPort) {
        this.loadMemberPort = loadMemberPort;
        this.loadLoanPort = loadLoanPort;
        this.loadBookPort = loadBookPort;
    }

    @Override
    public List<LoanHistoryEntry> getLoanHistory(UUID memberId) {
        if (loadMemberPort.loadMember(memberId).isEmpty()) {
            throw new MemberNotFoundException(memberId);
        }
        List<Loan> loans = loadLoanPort.loadLoansOfMember(memberId);
        Map<UUID, Book> books = loadBookPort.loadBooks(loans.stream().map(Loan::getBookId).distinct().toList()).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
        return loans.stream()
                .sorted(Comparator.comparing(Loan::getLoanedAt).reversed().thenComparing(Loan::getId))
                .map(loan -> new LoanHistoryEntry(loan.getId(), loan.getBookId(), books.get(loan.getBookId()).getTitle(),
                        loan.getLoanedAt(), loan.getReturnedAt(), loan.isReturned() ? loan.lateFee() : null))
                .toList();
    }
}
