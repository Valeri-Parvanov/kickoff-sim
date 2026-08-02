package com.kickoffsim.repository;

import com.kickoffsim.model.Match;
import com.kickoffsim.model.Team;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "homeTeam", "homeTeam.league", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    @NonNull List<Match> findAll(@NonNull Sort sort);

    List<Match> findAllByHomeTeamOrAwayTeam(Team homeTeam, Team awayTeam);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Goal g WHERE g.match.id IN "
            + "(SELECT m.id FROM Match m WHERE m.homeTeam.id IN :teamIds OR m.awayTeam.id IN :teamIds)")
    void deleteGoalsForTeams(@Param("teamIds") Collection<UUID> teamIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Match m WHERE m.homeTeam.id IN :teamIds OR m.awayTeam.id IN :teamIds")
    void deleteMatchesForTeams(@Param("teamIds") Collection<UUID> teamIds);

    @EntityGraph(attributePaths = {
            "homeTeam", "homeTeam.league", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    @Query("SELECT m FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId ORDER BY m.playedAt DESC")
    List<Match> findByLeagueId(@Param("leagueId") UUID leagueId);

    @EntityGraph(attributePaths = {
            "homeTeam", "homeTeam.league", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    @Query("SELECT m FROM Match m WHERE m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId ORDER BY m.playedAt ASC")
    List<Match> findByTeamId(@Param("teamId") UUID teamId);

    @Query("SELECT COUNT(m) > 0 FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId")
    boolean existsByLeagueId(@Param("leagueId") UUID leagueId);

    @Query("SELECT COUNT(m) > 0 FROM Match m WHERE m.playedAt BETWEEN :from AND :to")
    boolean existsInWindow(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT MIN(m.playedAt) FROM Match m WHERE m.playedAt > :now")
    LocalDateTime findNextKickoffAfter(@Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "goals"})
    @Query("SELECT m FROM Match m WHERE m.playedAt BETWEEN :from AND :to "
            + "AND NOT EXISTS (SELECT 1 FROM Goal g WHERE g.match = m)")
    List<Match> findGoallessBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(m) > 0 FROM Match m WHERE (m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId) AND m.playedAt < :now")
    boolean hasPlayedMatchesForLeague(@Param("leagueId") UUID leagueId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(m) FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId")
    long countByLeagueId(@Param("leagueId") UUID leagueId);

    @Query("SELECT COUNT(m) FROM Match m WHERE (m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId) AND m.playedAt < :now")
    long countPlayedByLeagueId(@Param("leagueId") UUID leagueId, @Param("now") LocalDateTime now);

    @Query("SELECT COALESCE(hl.id, al.id), COUNT(m), "
            + "SUM(CASE WHEN m.playedAt < :now THEN 1L ELSE 0L END) "
            + "FROM Match m "
            + "LEFT JOIN m.homeTeam h LEFT JOIN h.league hl "
            + "LEFT JOIN m.awayTeam a LEFT JOIN a.league al "
            + "WHERE hl.id IS NOT NULL OR al.id IS NOT NULL "
            + "GROUP BY COALESCE(hl.id, al.id)")
    List<Object[]> countMatchesGroupedByLeague(@Param("now") LocalDateTime now);

    @Query("SELECT hl.id, m.roundNumber FROM Match m "
            + "JOIN m.homeTeam h JOIN h.league hl "
            + "WHERE m.roundNumber IS NOT NULL "
            + "GROUP BY hl.id, m.roundNumber "
            + "HAVING MAX(m.playedAt) <= :cutoff "
            + "AND SUM(CASE WHEN m.homeScore IS NULL OR m.awayScore IS NULL THEN 1 ELSE 0 END) = 0")
    List<Object[]> findCompletedRounds(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT DISTINCT m.playedAt FROM Match m ORDER BY m.playedAt ASC")
    List<LocalDateTime> findAllPlayedAtTimes();

    @Query("SELECT DISTINCT m.playedAt FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId ORDER BY m.playedAt ASC")
    List<LocalDateTime> findPlayedAtTimesByLeagueId(@Param("leagueId") UUID leagueId);

    @Query("SELECT DISTINCT m.playedAt FROM Match m WHERE m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId ORDER BY m.playedAt ASC")
    List<LocalDateTime> findPlayedAtTimesByTeamId(@Param("teamId") UUID teamId);

    @EntityGraph(attributePaths = {"homeTeam", "homeTeam.league", "awayTeam"})
    @Query("SELECT m FROM Match m "
            + "LEFT JOIN m.homeTeam h LEFT JOIN h.league hl "
            + "WHERE m.playedAt >= :start AND m.playedAt < :end "
            + "AND (:leagueId IS NULL OR hl.id = :leagueId) "
            + "AND (:teamId IS NULL OR h.id = :teamId OR m.awayTeam.id = :teamId) "
            + "ORDER BY m.playedAt ASC")
    List<Match> findByDateRangeFilteredWithoutGoals(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("leagueId") UUID leagueId,
            @Param("teamId") UUID teamId);

    @EntityGraph(attributePaths = {
            "homeTeam", "homeTeam.league", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    @Query("SELECT m FROM Match m "
            + "LEFT JOIN m.homeTeam h LEFT JOIN h.league hl "
            + "WHERE m.playedAt >= :start AND m.playedAt < :end "
            + "AND (:leagueId IS NULL OR hl.id = :leagueId) "
            + "AND (:teamId IS NULL OR h.id = :teamId OR m.awayTeam.id = :teamId) "
            + "ORDER BY m.playedAt ASC")
    List<Match> findByDateRangeFiltered(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("leagueId") UUID leagueId,
            @Param("teamId") UUID teamId);

    @EntityGraph(attributePaths = {"homeTeam", "homeTeam.league", "awayTeam"})
    @Query("SELECT m FROM Match m WHERE m.playedAt BETWEEN :from AND :to AND m.kickoffNotified = false")
    List<Match> findForKickoffNotification(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = {"homeTeam", "homeTeam.league", "awayTeam"})
    @Query("SELECT m FROM Match m WHERE m.playedAt BETWEEN :from AND :to AND m.halftimeNotified = false")
    List<Match> findForHalftimeNotification(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = {"homeTeam", "homeTeam.league", "awayTeam"})
    @Query("SELECT m FROM Match m WHERE m.playedAt BETWEEN :from AND :to AND m.secondHalfNotified = false")
    List<Match> findForSecondHalfNotification(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = {"homeTeam", "homeTeam.league", "awayTeam"})
    @Query("SELECT m FROM Match m WHERE m.playedAt BETWEEN :from AND :to AND m.fulltimeNotified = false")
    List<Match> findForFulltimeNotification(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = {
            "homeTeam", "homeTeam.league", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    @Query("SELECT m FROM Match m WHERE m.playedAt >= :start AND m.playedAt < :end ORDER BY m.playedAt ASC")
    List<Match> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = {"homeTeam", "homeTeam.league", "awayTeam"})
    @Query("SELECT m FROM Match m WHERE m.playedAt >= :start AND m.playedAt < :end "
            + "AND (m.homeTeam.id IN :teamIds OR m.awayTeam.id IN :teamIds OR m.id IN :matchIds) "
            + "ORDER BY m.playedAt ASC")
    List<Match> findFollowedByDateRangeWithoutGoals(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("teamIds") Collection<UUID> teamIds,
            @Param("matchIds") Collection<UUID> matchIds);

    @EntityGraph(attributePaths = {
            "homeTeam", "homeTeam.league", "awayTeam",
            "goals", "goals.scorer", "goals.scorer.team", "goals.assistant"})
    @Query("SELECT m FROM Match m WHERE m.playedAt >= :start AND m.playedAt < :end "
            + "AND (m.homeTeam.id IN :teamIds OR m.awayTeam.id IN :teamIds OR m.id IN :matchIds) "
            + "ORDER BY m.playedAt ASC")
    List<Match> findFollowedByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("teamIds") Collection<UUID> teamIds,
            @Param("matchIds") Collection<UUID> matchIds);
}
