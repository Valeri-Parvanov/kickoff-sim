package bg.softuni.footballleague.repository;

import bg.softuni.footballleague.model.Player;
import bg.softuni.footballleague.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    List<Player> findAllByTeam(Team team);

    long countByTeam(Team team);

    Optional<Player> findByTeamAndShirtNumber(Team team, Integer shirtNumber);
}
