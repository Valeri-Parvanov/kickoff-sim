package com.kickoffsim.repository;

import com.kickoffsim.model.League;
import com.kickoffsim.model.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    @Override
    @EntityGraph(attributePaths = "league")
    List<Team> findAll(Sort sort);

    @EntityGraph(attributePaths = "league")
    List<Team> findAllByLeague(League league);

    boolean existsByName(String name);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Team t WHERE t.name = :name AND ((:city IS NULL AND t.city IS NULL) OR (:city IS NOT NULL AND t.city = :city))")
    boolean existsByNameAndCity(@Param("name") String name, @Param("city") String city);

    List<Team> findAllByLeagueIsNull();

    @EntityGraph(attributePaths = "league")
    @Query("SELECT t FROM Team t WHERE LOWER(CONCAT(t.name, ' ', COALESCE(t.city, ''))) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY t.name ASC")
    List<Team> searchTop6ByNameOrCity(@Param("q") String q, Pageable pageable);
}
