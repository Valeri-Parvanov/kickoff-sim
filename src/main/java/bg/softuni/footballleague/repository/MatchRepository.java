package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.Match;
import bg.softuni.footballleague.model.Team;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "homeTeam", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    List<Match> findAll(Sort sort);

    List<Match> findAllByHomeTeamOrAwayTeam(Team homeTeam, Team awayTeam);

    @EntityGraph(attributePaths = {
            "homeTeam", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    @Query("SELECT m FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId ORDER BY m.playedAt DESC")
    List<Match> findByLeagueId(@Param("leagueId") UUID leagueId);

    @Query("SELECT COUNT(m) > 0 FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId")
    boolean existsByLeagueId(@Param("leagueId") UUID leagueId);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "goals"})
    @Query("SELECT m FROM Match m WHERE m.playedAt <= :to AND m.homeScore = 0 AND m.awayScore = 0")
    List<Match> findZeroZeroMatchesBefore(@Param("to") LocalDateTime to);

    @Query("SELECT COUNT(m) > 0 FROM Match m WHERE (m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId) AND m.playedAt < :now")
    boolean hasPlayedMatchesForLeague(@Param("leagueId") UUID leagueId, @Param("now") LocalDateTime now);
}
