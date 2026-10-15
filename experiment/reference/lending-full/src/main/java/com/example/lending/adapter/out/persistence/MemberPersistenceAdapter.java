package com.example.lending.adapter.out.persistence;

import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.application.port.out.SaveMemberPort;
import com.example.lending.domain.Member;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class MemberPersistenceAdapter implements LoadMemberPort, SaveMemberPort {

    private final MemberJpaRepository memberJpaRepository;
    private final MemberMapper memberMapper = new MemberMapper();

    public MemberPersistenceAdapter(MemberJpaRepository memberJpaRepository) {
        this.memberJpaRepository = memberJpaRepository;
    }

    @Override
    public Optional<Member> loadMember(UUID memberId) {
        return memberJpaRepository.findById(memberId).map(memberMapper::toDomain);
    }

    @Override
    public void saveMember(Member member) {
        memberJpaRepository.save(memberMapper.toEntity(member));
    }
}
