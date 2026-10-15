package com.example.lending.application.service;

import com.example.lending.application.port.in.GetMemberUseCase;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.domain.Member;
import com.example.lending.domain.MemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetMemberService implements GetMemberUseCase {

    private final LoadMemberPort loadMemberPort;

    public GetMemberService(LoadMemberPort loadMemberPort) {
        this.loadMemberPort = loadMemberPort;
    }

    @Override
    public Member getMember(UUID memberId) {
        return loadMemberPort.loadMember(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
