package com.kickoffsim.repository;

import com.kickoffsim.model.Goal;
import com.kickoffsim.model.Half;
import com.kickoffsim.model.Match;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    @EntityGraph(attributePaths = {"match", "match.homeTeam", "match.homeTeam.league", "match.awayTeam",
            "scorer", "scorer.team"})
    @Query("SELECT g FROM Goal g WHERE g.notified = false AND g.match.playedAt BETWEEN :from AND :to")
    List<Goal> findUnnotifiedForMatchesStartedBetween(@Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to);



    @Query("SELECT COUNT(g) FROM Goal g WHERE g.match = :match " +
           "AND ((g.ownGoal = false AND g.scorer.team.id = :teamId) " +
           "  OR (g.ownGoal = true  AND g.scorer.team.id <> :teamId)) " +
           "AND (:excludeId IS NULL OR g.id <> :excludeId)")
    long countGoalsBenefitingTeam(@Param("match") Match match,
                                   @Param("teamId") UUID teamId,
                                   @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(g) FROM Goal g WHERE g.match = :match AND g.half = :half AND g.minute = :minute AND (:excludeId IS NULL OR g.id != :excludeId)")
    long countByMatchAndHalfAndMinuteExcluding(@Param("match") Match match,
                                               @Param("half") Half half,
                                               @Param("minute") Integer minute,
                                               @Param("excludeId") UUID excludeId);

    @Query("SELECT g.scorer.id, COUNT(g) FROM Goal g WHERE g.scorer.team.id = :teamId AND g.ownGoal = false GROUP BY g.scorer.id")
    List<Object[]> countGoalsByTeamGroupedByScorer(@Param("teamId") UUID teamId);

    @Query("SELECT g.assistant.id, COUNT(g) FROM Goal g WHERE g.assistant.team.id = :teamId GROUP BY g.assistant.id")
    List<Object[]> countAssistsByTeamGroupedByAssistant(@Param("teamId") UUID teamId);
}
