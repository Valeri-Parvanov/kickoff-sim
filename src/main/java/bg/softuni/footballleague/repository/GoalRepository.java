package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.Goal;
import bg.softuni.footballleague.model.Half;
import bg.softuni.footballleague.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    @Query("SELECT COUNT(g) FROM Goal g WHERE g.match = :match AND g.scorer.team.id = :teamId AND (:excludeId IS NULL OR g.id != :excludeId)")
    long countByMatchAndScorerTeamIdExcluding(@Param("match") Match match,
                                              @Param("teamId") UUID teamId,
                                              @Param("excludeId") UUID excludeId);

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
}
