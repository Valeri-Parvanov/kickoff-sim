package com.kickoffsim.repository;

import com.kickoffsim.model.Player;
import com.kickoffsim.model.Team;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    @Override
    @EntityGraph(attributePaths = "team")
    @NonNull List<Player> findAll(@NonNull Sort sort);

    @EntityGraph(attributePaths = "team")
    List<Player> findAllByTeam(Team team);

    long countByTeam(Team team);

    @Query("SELECT p.team.id, COUNT(p) FROM Player p GROUP BY p.team.id")
    List<Object[]> countAllGroupedByTeam();

    Optional<Player> findByTeamAndShirtNumber(Team team, Integer shirtNumber);

    @EntityGraph(attributePaths = "team")
    @Query("SELECT p FROM Player p WHERE LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Player> searchTop6ByFullName(@Param("q") String q, Pageable pageable);
}
