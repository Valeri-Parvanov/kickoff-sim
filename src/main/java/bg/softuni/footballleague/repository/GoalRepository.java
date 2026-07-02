package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.Goal;
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
}
