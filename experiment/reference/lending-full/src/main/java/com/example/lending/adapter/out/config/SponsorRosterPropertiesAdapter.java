package com.example.lending.adapter.out.config;

import com.example.lending.application.port.out.LoadSponsorRosterPort;
import com.example.lending.domain.SponsorRoster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 후원자 목록을 설정(lending.sponsor-member-ids)에서 읽는다. 목록이 바뀌면 설정만 고친다.
 */
@Component
public class SponsorRosterPropertiesAdapter implements LoadSponsorRosterPort {

    private final SponsorRoster roster;

    public SponsorRosterPropertiesAdapter(@Value("${lending.sponsor-member-ids:}") List<String> memberIds) {
        this.roster = new SponsorRoster(memberIds.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toSet()));
    }

    @Override
    public SponsorRoster loadSponsorRoster() {
        return roster;
    }
}
