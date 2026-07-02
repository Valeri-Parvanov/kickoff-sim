package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    @Override
    @EntityGraph(attributePaths = "team")
    List<Player> findAll(Sort sort);

    @EntityGraph(attributePaths = "team")
    List<Player> findAllByTeam(Team team);

    long countByTeam(Team team);

    Optional<Player> findByTeamAndShirtNumber(Team team, Integer shirtNumber);
}
