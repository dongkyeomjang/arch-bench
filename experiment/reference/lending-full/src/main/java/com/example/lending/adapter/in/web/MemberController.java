package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.GetMemberUseCase;
import com.example.lending.application.port.in.RegisterMemberUseCase;
import com.example.lending.application.port.in.RegisterMemberUseCase.RegisterMemberCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final RegisterMemberUseCase registerMemberUseCase;
    private final GetMemberUseCase getMemberUseCase;

    public MemberController(RegisterMemberUseCase registerMemberUseCase, GetMemberUseCase getMemberUseCase) {
        this.registerMemberUseCase = registerMemberUseCase;
        this.getMemberUseCase = getMemberUseCase;
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
}
