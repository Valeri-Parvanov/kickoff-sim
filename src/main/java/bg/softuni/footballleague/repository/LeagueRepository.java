package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeagueRepository extends JpaRepository<League, UUID> {

    Optional<League> findByName(String name);
}
