package com.kickoffsim.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeRequestPayloadValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void squadPayload_validPlayersWithoutTeamId_hasNoViolations() {
        TeamSquadPayload payload = squad(player("Ivan", "Ivanov", 7), player("Georgi", "Georgiev", 9));

        assertThat(validator.validate(payload)).isEmpty();
    }

    @Test
    void squadPayload_blankPlayerName_isRejected() {
        TeamSquadPayload payload = squad(player(" ", "Ivanov", 7));

        assertThat(paths(validator.validate(payload))).contains("players[0].firstName");
    }

    @Test
    void squadPayload_blankPlayerLastName_isRejected() {
        TeamSquadPayload payload = squad(player("Ivan", "", 7));

        assertThat(paths(validator.validate(payload))).contains("players[0].lastName");
    }

    @Test
    void squadPayload_shirtNumberOutOfRange_isRejected() {
        TeamSquadPayload payload = squad(player("Ivan", "Ivanov", 100), player("Georgi", "Georgiev", 0));

        assertThat(paths(validator.validate(payload)))
                .contains("players[0].shirtNumber", "players[1].shirtNumber");
    }

    @Test
    void squadPayload_missingShirtNumber_isRejected() {
        TeamSquadPayload payload = squad(player("Ivan", "Ivanov", null));

        assertThat(paths(validator.validate(payload))).contains("players[0].shirtNumber");
    }

    @Test
    void squadPayload_duplicateShirtNumbers_isRejected() {
        TeamSquadPayload payload = squad(player("Ivan", "Ivanov", 7), player("Georgi", "Georgiev", 7));

        assertThat(paths(validator.validate(payload))).contains("players");
    }

    @Test
    void squadPayload_twoMissingShirtNumbers_doesNotReportDuplicates() {
        TeamSquadPayload payload = squad(player("Ivan", "Ivanov", null), player("Georgi", "Georgiev", null));

        assertThat(paths(validator.validate(payload))).doesNotContain("players");
    }

    @Test
    void squadPayload_withoutTeam_isRejected() {
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.getPlayers().add(player("Ivan", "Ivanov", 7));

        assertThat(paths(validator.validate(payload))).contains("team");
    }

    @Test
    void squadPayload_withoutPlayers_isRejected() {
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team("Levski"));

        assertThat(paths(validator.validate(payload))).contains("players");
    }

    @Test
    void squadPayload_invalidTeamName_isRejected() {
        TeamSquadPayload payload = squad(player("Ivan", "Ivanov", 7));
        payload.getTeam().setName("Levski Sofia");

        assertThat(paths(validator.validate(payload))).contains("team.name");
    }

    @Test
    void leagueBundlePayload_valid_hasNoViolations() {
        LeagueBundlePayload payload = bundle("Efbet Liga");
        payload.getNewTeams().add(squad(player("Ivan", "Ivanov", 7)));

        assertThat(validator.validate(payload)).isEmpty();
    }

    @Test
    void leagueBundlePayload_blankLeagueName_isRejected() {
        LeagueBundlePayload payload = bundle("  ");

        assertThat(paths(validator.validate(payload))).contains("leagueName");
    }

    @Test
    void leagueBundlePayload_invalidNestedSquad_isRejected() {
        LeagueBundlePayload payload = bundle("Efbet Liga");
        payload.getNewTeams().add(squad(player("", "Ivanov", 7)));

        assertThat(paths(validator.validate(payload))).contains("newTeams[0].players[0].firstName");
    }

    private List<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        List<String> paths = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            paths.add(violation.getPropertyPath().toString());
        }
        return paths;
    }

    private TeamSquadPayload squad(PlayerDto... players) {
        TeamSquadPayload payload = new TeamSquadPayload();
        payload.setTeam(team("Levski"));
        payload.setPlayers(new ArrayList<>(List.of(players)));
        return payload;
    }

    private LeagueBundlePayload bundle(String leagueName) {
        LeagueBundlePayload payload = new LeagueBundlePayload();
        payload.setLeagueName(leagueName);
        payload.getExistingTeamIds().add(UUID.randomUUID());
        return payload;
    }

    private TeamDto team(String name) {
        TeamDto team = new TeamDto();
        team.setId(UUID.randomUUID());
        team.setName(name);
        team.setCity("Sofia");
        return team;
    }

    private PlayerDto player(String firstName, String lastName, Integer shirtNumber) {
        PlayerDto player = new PlayerDto();
        player.setFirstName(firstName);
        player.setLastName(lastName);
        player.setShirtNumber(shirtNumber);
        return player;
    }
}
