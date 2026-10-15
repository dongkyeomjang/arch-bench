package com.example.lending.application.service;

import com.example.lending.application.port.in.SuspendMemberUseCase;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.application.port.out.RecordAuditPort;
import com.example.lending.application.port.out.SaveMemberPort;
import com.example.lending.domain.AuditAction;
import com.example.lending.domain.AuditEntry;
import com.example.lending.domain.Member;
import com.example.lending.domain.MemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SuspendMemberService implements SuspendMemberUseCase {

    private final LoadMemberPort loadMemberPort;
    private final SaveMemberPort saveMemberPort;

    private final RecordAuditPort recordAuditPort;

    public SuspendMemberService(LoadMemberPort loadMemberPort, SaveMemberPort saveMemberPort,
                                RecordAuditPort recordAuditPort) {
        this.loadMemberPort = loadMemberPort;
        this.saveMemberPort = saveMemberPort;
        this.recordAuditPort = recordAuditPort;
    }

    @Override
    public Member suspendMember(UUID memberId) {
        Member member = load(memberId);
        member.suspend();
        saveMemberPort.saveMember(member);
        recordAuditPort.record(AuditEntry.of(AuditAction.SUSPEND, member.getId(), member.getId(), Instant.now()));
        return member;
    }

    @Override
    public Member unsuspendMember(UUID memberId) {
        Member member = load(memberId);
        member.unsuspend();
        saveMemberPort.saveMember(member);
        recordAuditPort.record(AuditEntry.of(AuditAction.UNSUSPEND, member.getId(), member.getId(), Instant.now()));
        return member;
    }

    private Member load(UUID memberId) {
        return loadMemberPort.loadMember(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
