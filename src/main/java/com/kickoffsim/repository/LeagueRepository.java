package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LeagueRepository extends JpaRepository<League, UUID> {

    @Query("SELECT l FROM League l LEFT JOIN FETCH l.teams WHERE l.id = :id")
    Optional<League> findByIdWithTeams(@Param("id") UUID id);
}
