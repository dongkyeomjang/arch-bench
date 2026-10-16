package com.example.lending.application.service;

import com.example.lending.application.port.in.RegisterMemberUseCase;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.application.port.out.LoadSponsorRosterPort;
import com.example.lending.application.port.out.SaveMemberPort;
import com.example.lending.domain.Member;
import com.example.lending.domain.MemberAlreadyExistsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterMemberService implements RegisterMemberUseCase {

    private final SaveMemberPort saveMemberPort;
    private final LoadMemberPort loadMemberPort;
    private final LoadSponsorRosterPort loadSponsorRosterPort;

    public RegisterMemberService(SaveMemberPort saveMemberPort, LoadMemberPort loadMemberPort,
                                 LoadSponsorRosterPort loadSponsorRosterPort) {
        this.saveMemberPort = saveMemberPort;
        this.loadMemberPort = loadMemberPort;
        this.loadSponsorRosterPort = loadSponsorRosterPort;
    }

    @Override
    public Member registerMember(RegisterMemberCommand command) {
        if (command.memberId() != null && loadMemberPort.loadMember(command.memberId()).isPresent()) {
            throw new MemberAlreadyExistsException(command.memberId());
        }
        Member member = Member.register(command.memberId(), command.name(), loadSponsorRosterPort.loadSponsorRoster());
        saveMemberPort.saveMember(member);
        return member;
    }
}
