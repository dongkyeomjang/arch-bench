package com.example.lending.application.service;

import com.example.lending.application.port.in.SuspendMemberUseCase;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.application.port.out.SaveMemberPort;
import com.example.lending.domain.Member;
import com.example.lending.domain.MemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SuspendMemberService implements SuspendMemberUseCase {

    private final LoadMemberPort loadMemberPort;
    private final SaveMemberPort saveMemberPort;

    public SuspendMemberService(LoadMemberPort loadMemberPort, SaveMemberPort saveMemberPort) {
        this.loadMemberPort = loadMemberPort;
        this.saveMemberPort = saveMemberPort;
    }

    @Override
    public Member suspendMember(UUID memberId) {
        Member member = load(memberId);
        member.suspend();
        saveMemberPort.saveMember(member);
        return member;
    }

    @Override
    public Member unsuspendMember(UUID memberId) {
        Member member = load(memberId);
        member.unsuspend();
        saveMemberPort.saveMember(member);
        return member;
    }

    private Member load(UUID memberId) {
        return loadMemberPort.loadMember(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
