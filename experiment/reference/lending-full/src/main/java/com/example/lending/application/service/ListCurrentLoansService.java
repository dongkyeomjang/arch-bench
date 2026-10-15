package com.example.lending.application.service;

import com.example.lending.application.port.in.ListCurrentLoansUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadLoanPort;
import com.example.lending.application.port.out.LoadLoanPort.LoanSlice;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.domain.Book;
import com.example.lending.domain.Loan;
import com.example.lending.domain.MemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ListCurrentLoansService implements ListCurrentLoansUseCase {

    private final LoadMemberPort loadMemberPort;
    private final LoadLoanPort loadLoanPort;
    private final LoadBookPort loadBookPort;

    public ListCurrentLoansService(LoadMemberPort loadMemberPort, LoadLoanPort loadLoanPort, LoadBookPort loadBookPort) {
        this.loadMemberPort = loadMemberPort;
        this.loadLoanPort = loadLoanPort;
        this.loadBookPort = loadBookPort;
    }

    @Override
    public CurrentLoansPage listCurrentLoans(ListCurrentLoansQuery query) {
        if (loadMemberPort.loadMember(query.memberId()).isEmpty()) {
            throw new MemberNotFoundException(query.memberId());
        }
        LoanSlice slice = loadLoanPort.loadActiveLoans(query.memberId(), query.page(), query.size());
        Map<UUID, Book> books = loadBookPort.loadBooks(slice.loans().stream().map(Loan::getBookId).toList()).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
        List<CurrentLoan> items = slice.loans().stream()
                .map(loan -> new CurrentLoan(loan.getId(), loan.getBookId(), books.get(loan.getBookId()).getTitle(),
                        loan.getLoanedAt(), loan.getDueAt()))
                .toList();
        return new CurrentLoansPage(items, query.page(), query.size(), slice.totalCount());
    }
}
