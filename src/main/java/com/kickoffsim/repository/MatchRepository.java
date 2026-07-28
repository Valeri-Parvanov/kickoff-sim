package com.kickoffsim.repository;

import com.kickoffsim.model.Match;
import com.kickoffsim.model.Team;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "goals"})
    @Query("SELECT m FROM Match m WHERE m.playedAt <= :to AND SIZE(m.goals) = 0")
    List<Match> findGoallessBefore(@Param("to") LocalDateTime to);

    @Query("SELECT COUNT(m) > 0 FROM Match m WHERE (m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId) AND m.playedAt < :now")
    boolean hasPlayedMatchesForLeague(@Param("leagueId") UUID leagueId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(m) FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId")
    long countByLeagueId(@Param("leagueId") UUID leagueId);

    @Query("SELECT COUNT(m) FROM Match m WHERE (m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId) AND m.playedAt < :now")
    long countPlayedByLeagueId(@Param("leagueId") UUID leagueId, @Param("now") LocalDateTime now);

    @Query("SELECT m.playedAt FROM Match m ORDER BY m.playedAt ASC")
    List<LocalDateTime> findAllPlayedAtTimes();

    @Query("SELECT m.playedAt FROM Match m WHERE m.homeTeam.league.id = :leagueId OR m.awayTeam.league.id = :leagueId ORDER BY m.playedAt ASC")
    List<LocalDateTime> findPlayedAtTimesByLeagueId(@Param("leagueId") UUID leagueId);

    @Query("SELECT m.playedAt FROM Match m WHERE m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId ORDER BY m.playedAt ASC")
    List<LocalDateTime> findPlayedAtTimesByTeamId(@Param("teamId") UUID teamId);

    @EntityGraph(attributePaths = {"homeTeam", "homeTeam.league", "awayTeam"})
    @Query("SELECT m FROM Match m WHERE m.playedAt >= :start AND m.playedAt < :end ORDER BY m.playedAt ASC")
    List<Match> findByDateRangeWithoutGoals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

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
