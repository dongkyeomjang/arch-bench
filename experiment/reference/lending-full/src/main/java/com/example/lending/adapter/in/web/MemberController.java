package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.GetLoanHistoryUseCase;
import com.example.lending.application.port.in.GetMemberUseCase;
import com.example.lending.application.port.in.ListCurrentLoansUseCase;
import com.example.lending.application.port.in.ListCurrentLoansUseCase.ListCurrentLoansQuery;
import com.example.lending.application.port.in.RegisterMemberUseCase;
import com.example.lending.application.port.in.RegisterMemberUseCase.RegisterMemberCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final RegisterMemberUseCase registerMemberUseCase;
    private final GetMemberUseCase getMemberUseCase;
    private final ListCurrentLoansUseCase listCurrentLoansUseCase;
    private final GetLoanHistoryUseCase getLoanHistoryUseCase;

    public MemberController(RegisterMemberUseCase registerMemberUseCase, GetMemberUseCase getMemberUseCase,
                            ListCurrentLoansUseCase listCurrentLoansUseCase, GetLoanHistoryUseCase getLoanHistoryUseCase) {
        this.registerMemberUseCase = registerMemberUseCase;
        this.getMemberUseCase = getMemberUseCase;
        this.listCurrentLoansUseCase = listCurrentLoansUseCase;
        this.getLoanHistoryUseCase = getLoanHistoryUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse registerMember(@RequestBody RegisterMemberRequest request) {
        return MemberResponse.from(registerMemberUseCase.registerMember(new RegisterMemberCommand(request.name())));
    }

    @GetMapping("/{memberId}")
    public MemberResponse getMember(@PathVariable UUID memberId) {
        return MemberResponse.from(getMemberUseCase.getMember(memberId));
    }

    @GetMapping("/{memberId}/loans")
    public CurrentLoansResponse listCurrentLoans(@PathVariable UUID memberId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return CurrentLoansResponse.from(
                listCurrentLoansUseCase.listCurrentLoans(new ListCurrentLoansQuery(memberId, page, size)));
    }

    @GetMapping("/{memberId}/loans/history")
    public List<LoanHistoryResponse> getLoanHistory(@PathVariable UUID memberId) {
        return getLoanHistoryUseCase.getLoanHistory(memberId).stream().map(LoanHistoryResponse::from).toList();
    }
}
