package com.kickoffsim.repository;

import com.kickoffsim.model.RoundRecap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoundRecapRepository extends JpaRepository<RoundRecap, UUID> {

    Optional<RoundRecap> findByLeagueIdAndRoundNumberAndLocaleTag(
            UUID leagueId, Integer roundNumber, String localeTag);

    List<RoundRecap> findByLeagueIdAndLocaleTagAndRoundNumberBetweenOrderByRoundNumberDesc(
            UUID leagueId, String localeTag, Integer lower, Integer upper);

    @Query("SELECT r.league.id, r.roundNumber FROM RoundRecap r WHERE r.localeTag = :localeTag")
    List<Object[]> findScopesByLocale(@Param("localeTag") String localeTag);
}
