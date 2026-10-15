package com.example.lending.application.service;

import com.example.lending.application.port.in.RegisterMemberUseCase;
import com.example.lending.application.port.out.SaveMemberPort;
import com.example.lending.domain.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterMemberService implements RegisterMemberUseCase {

    private final SaveMemberPort saveMemberPort;

    public RegisterMemberService(SaveMemberPort saveMemberPort) {
        this.saveMemberPort = saveMemberPort;
    }

    @Override
    public Member registerMember(RegisterMemberCommand command) {
        Member member = Member.register(command.name());
        saveMemberPort.saveMember(member);
        return member;
    }
}
