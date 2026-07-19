package com.kickoffsim.repository;

import com.kickoffsim.model.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeagueRepository extends JpaRepository<League, UUID> {

    @Query("SELECT l FROM League l LEFT JOIN FETCH l.teams WHERE l.id = :id")
    Optional<League> findByIdWithTeams(@Param("id") UUID id);

    @Query("SELECT l FROM League l WHERE EXISTS (SELECT 1 FROM Match m WHERE m.homeTeam.league = l) "
            + "AND NOT EXISTS (SELECT 1 FROM Match m2 WHERE m2.homeTeam.league = l AND m2.playedAt >= :cutoff)")
    List<League> findFinishedBefore(@Param("cutoff") LocalDateTime cutoff);
}
