package com.kickoffsim.repository;

import com.kickoffsim.model.RoundRecap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoundRecapRepository extends JpaRepository<RoundRecap, UUID> {

    Optional<RoundRecap> findByLeagueIdAndRoundNumberAndLocaleTag(
            UUID leagueId, Integer roundNumber, String localeTag);
}
