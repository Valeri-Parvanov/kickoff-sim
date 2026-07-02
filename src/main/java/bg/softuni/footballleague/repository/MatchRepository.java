package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "homeTeam", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    List<Match> findAll(Sort sort);

    List<Match> findAllByHomeTeamOrAwayTeam(Team homeTeam, Team awayTeam);
}
