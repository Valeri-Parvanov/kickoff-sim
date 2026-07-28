package com.kickoffsim.web;

import com.kickoffsim.dto.LeagueSkeletonDraft;
import com.kickoffsim.dto.PlayerNameDraft;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.SquadDraft;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.dto.TeamNameDraft;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueDraftSanitizerTest {

    private static final BiPredicate<String, String> NEVER_TAKEN = (name, city) -> false;

    private static final List<String> TEAM_POOL = List.of(LeagueDraftSanitizer.TEAM_NAMES);
    private static final List<String> CITY_POOL = List.of(LeagueDraftSanitizer.CITIES);
    private static final List<String> FIRST_POOL = List.of(LeagueDraftSanitizer.FIRST_NAMES);
    private static final List<String> LAST_POOL = List.of(LeagueDraftSanitizer.LAST_NAMES);
    private static final List<String> FOOD_POOL = List.of(LeagueDraftSanitizer.LEAGUE_FOODS);
    private static final List<String> TYPE_POOL = List.of(LeagueDraftSanitizer.LEAGUE_TYPES);

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        validatorFactory.close();
    }

    @Test
    void snap_exactMatch_isReturned() {
        assertThat(LeagueDraftSanitizer.snap("Sofia", LeagueDraftSanitizer.CITIES)).isEqualTo("Sofia");
    }

    @Test
    void snap_ignoresCasePunctuationAndAccents() {
        assertThat(LeagueDraftSanitizer.snap("  s-o-f-i-a  ", LeagueDraftSanitizer.CITIES)).isEqualTo("Sofia");
        assertThat(LeagueDraftSanitizer.snap("Sófia", LeagueDraftSanitizer.CITIES)).isEqualTo("Sofia");
    }

    @Test
    void snap_multiWordCity_isMatchedWithoutSpaces() {
        assertThat(LeagueDraftSanitizer.snap("StaraZagora", LeagueDraftSanitizer.CITIES)).isEqualTo("Stara Zagora");
        assertThat(LeagueDraftSanitizer.snap("Stara Zagora", LeagueDraftSanitizer.CITIES)).isEqualTo("Stara Zagora");
    }

    @Test
    void snap_unknownOrBlank_returnsNull() {
        assertThat(LeagueDraftSanitizer.snap("Atlantis", LeagueDraftSanitizer.CITIES)).isNull();
        assertThat(LeagueDraftSanitizer.snap(null, LeagueDraftSanitizer.CITIES)).isNull();
        assertThat(LeagueDraftSanitizer.snap("   ", LeagueDraftSanitizer.CITIES)).isNull();
        assertThat(LeagueDraftSanitizer.snap("Банско", LeagueDraftSanitizer.CITIES)).isNull();
    }

    @Test
    void snap_rejectsNameWithAppendedDigits() {
        assertThat(LeagueDraftSanitizer.snap("Banicharite2", LeagueDraftSanitizer.TEAM_NAMES))
                .isEqualTo("Banicharite");
    }

    @Test
    void sanitizeLeagueName_recognisedPair_isKept() {
        assertThat(LeagueDraftSanitizer.sanitizeLeagueName("Banitsa Cup")).isEqualTo("Banitsa Cup");
        assertThat(LeagueDraftSanitizer.sanitizeLeagueName("Tarator Derby")).isEqualTo("Tarator Derby");
    }

    @Test
    void sanitizeLeagueName_alwaysUsesPoolWords() {
        List<String> inputs = Arrays.asList(null, "", "   ", "!!!", "Premier Invitational",
                "Баница Купа", "Random Words Here", "Banitsa", "Cup", "A".repeat(300));

        for (String input : inputs) {
            String result = LeagueDraftSanitizer.sanitizeLeagueName(input);
            String[] parts = result.split(" ");

            assertThat(parts).as("league name for '%s'", input).hasSize(2);
            assertThat(FOOD_POOL).contains(parts[0]);
            assertThat(TYPE_POOL).contains(parts[1]);
        }
    }

    @Test
    void sanitizeLeagueName_isDeterministic() {
        assertThat(LeagueDraftSanitizer.sanitizeLeagueName("Premier Invitational"))
                .isEqualTo(LeagueDraftSanitizer.sanitizeLeagueName("Premier Invitational"));
    }

    @Test
    void sanitizeTeams_zeroOrNegativeCount_returnsEmptyList() {
        assertThat(LeagueDraftSanitizer.sanitizeTeams(skeleton("Domati"), 0, List.of(), NEVER_TAKEN)).isEmpty();
        assertThat(LeagueDraftSanitizer.sanitizeTeams(skeleton("Domati"), -3, List.of(), NEVER_TAKEN)).isEmpty();
    }

    @Test
    void sanitizeTeams_recognisedNames_areKept() {
        LeagueSkeletonDraft draft = new LeagueSkeletonDraft("Banitsa Cup", List.of(
                new TeamNameDraft("Banicharite", "Sofia", 9),
                new TeamNameDraft("Sirenkite", "Plovdiv", 7)));

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(draft, 2, List.of(), NEVER_TAKEN);

        assertThat(teams).extracting(TeamCreateForm::getName).containsExactly("Banicharite", "Sirenkite");
        assertThat(teams).extracting(TeamCreateForm::getCity).containsExactly("Sofia", "Plovdiv");
    }

    @Test
    void sanitizeTeams_inventedNames_areReplacedFromPool() {
        LeagueSkeletonDraft draft = new LeagueSkeletonDraft("Banitsa Cup", List.of(
                new TeamNameDraft("Real Madrid", "New York", 9),
                new TeamNameDraft("Banicharite2", "Atlantis", 9)));

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(draft, 2, List.of(), NEVER_TAKEN);

        assertThat(teams).extracting(TeamCreateForm::getName).allSatisfy(n -> assertThat(TEAM_POOL).contains(n));
        assertThat(teams).extracting(TeamCreateForm::getCity).allSatisfy(c -> assertThat(CITY_POOL).contains(c));
        assertThat(teams.get(1).getName()).isEqualTo("Banicharite");
    }

    @Test
    void sanitizeTeams_neverProducesDigitSuffixes() {
        LeagueSkeletonDraft draft = new LeagueSkeletonDraft("Banitsa Cup", List.of(
                new TeamNameDraft("Banicharite", "Sofia", 9),
                new TeamNameDraft("Banicharite", "Plovdiv", 9),
                new TeamNameDraft("Banicharite", "Varna", 9)));

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(draft, 3, List.of(), NEVER_TAKEN);

        assertThat(teams).extracting(TeamCreateForm::getName).doesNotHaveDuplicates();
        assertThat(teams).extracting(TeamCreateForm::getName)
                .allSatisfy(n -> assertThat(n).doesNotMatch(".*\\d.*"));
        assertThat(teams).extracting(TeamCreateForm::getName).allSatisfy(n -> assertThat(TEAM_POOL).contains(n));
    }

    @Test
    void sanitizeTeams_nullSkeletonOrList_stillFillsFromPool() {
        for (LeagueSkeletonDraft draft : Arrays.asList(null,
                new LeagueSkeletonDraft("Banitsa Cup", null),
                new LeagueSkeletonDraft("Banitsa Cup", List.of()),
                new LeagueSkeletonDraft("Banitsa Cup", Collections.singletonList(null)))) {

            List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(draft, 4, List.of(), NEVER_TAKEN);

            assertThat(teams).hasSize(4);
            assertThat(teams).extracting(TeamCreateForm::getName).doesNotHaveDuplicates();
            assertThat(teams).extracting(TeamCreateForm::getName).allSatisfy(n -> assertThat(TEAM_POOL).contains(n));
            assertThat(teams).extracting(TeamCreateForm::getCity).allSatisfy(c -> assertThat(CITY_POOL).contains(c));
        }
    }

    @Test
    void sanitizeTeams_tooFewTeams_padsToRequestedCount() {
        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(
                skeleton("Domati", "Kyufteta"), 5, List.of(), NEVER_TAKEN);

        assertThat(teams).hasSize(5);
        assertThat(teams).extracting(TeamCreateForm::getName).doesNotHaveDuplicates();
    }

    @Test
    void sanitizeTeams_tooManyTeams_truncatesToRequestedCount() {
        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(
                skeleton("Domati", "Kyufteta", "Gladnite", "Zaspalite"), 2, List.of(), NEVER_TAKEN);

        assertThat(teams).extracting(TeamCreateForm::getName).containsExactly("Domati", "Kyufteta");
    }

    @Test
    void sanitizeTeams_nullElementInList_isSkipped() {
        List<TeamNameDraft> drafts = new ArrayList<>();
        drafts.add(new TeamNameDraft("Domati", "Sofia", 9));
        drafts.add(null);
        drafts.add(new TeamNameDraft("Kyufteta", "Varna", 9));

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(
                new LeagueSkeletonDraft("Banitsa Cup", drafts), 2, List.of(), NEVER_TAKEN);

        assertThat(teams).extracting(TeamCreateForm::getName).containsExactly("Domati", "Kyufteta");
    }

    @Test
    void sanitizeTeams_reservedName_isAvoided() {
        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(
                skeleton("Domati"), 1, List.of("domati"), NEVER_TAKEN);

        assertThat(teams.get(0).getName()).isNotEqualToIgnoringCase("Domati");
        assertThat(TEAM_POOL).contains(teams.get(0).getName());
    }

    @Test
    void sanitizeTeams_nullAndBlankReservedNames_areIgnored() {
        List<String> reserved = Arrays.asList(null, "   ", "Kyufteta");

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(
                skeleton("Domati"), 1, reserved, NEVER_TAKEN);

        assertThat(teams.get(0).getName()).isEqualTo("Domati");
    }

    @Test
    void sanitizeTeams_nullReservedNamesOrPredicate_areTolerated() {
        assertThat(LeagueDraftSanitizer.sanitizeTeams(skeleton("Domati"), 1, null, NEVER_TAKEN))
                .extracting(TeamCreateForm::getName).containsExactly("Domati");
        assertThat(LeagueDraftSanitizer.sanitizeTeams(skeleton("Domati"), 1, List.of(), null))
                .extracting(TeamCreateForm::getName).containsExactly("Domati");
    }

    @Test
    void sanitizeTeams_nameTakenInDatabase_picksAnotherPoolName() {
        BiPredicate<String, String> taken = (name, city) -> "Domati".equals(name);

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(
                skeleton("Domati"), 1, List.of(), taken);

        assertThat(teams.get(0).getName()).isNotEqualTo("Domati");
        assertThat(TEAM_POOL).contains(teams.get(0).getName());
    }

    @Test
    void sanitizeTeams_everythingTaken_stillReturnsPoolNameAndCity() {
        BiPredicate<String, String> alwaysTaken = (name, city) -> true;

        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(
                skeleton("Domati"), 2, List.of(), alwaysTaken);

        assertThat(teams).hasSize(2);
        assertThat(teams).extracting(TeamCreateForm::getName).allSatisfy(n -> assertThat(TEAM_POOL).contains(n));
        assertThat(teams).extracting(TeamCreateForm::getCity).allSatisfy(c -> assertThat(CITY_POOL).contains(c));
    }

    @Test
    void sanitizeTeams_differentLeagueNames_rotateCities() {
        List<TeamCreateForm> first = LeagueDraftSanitizer.sanitizeTeams(
                new LeagueSkeletonDraft("Banitsa Cup", List.of(new TeamNameDraft("Domati", "Atlantis", 9))),
                1, List.of(), NEVER_TAKEN);
        List<TeamCreateForm> second = LeagueDraftSanitizer.sanitizeTeams(
                new LeagueSkeletonDraft("Tarator Derby", List.of(new TeamNameDraft("Domati", "Atlantis", 9))),
                1, List.of(), NEVER_TAKEN);

        assertThat(CITY_POOL).contains(first.get(0).getCity(), second.get(0).getCity());
    }

    @Test
    void fillSquad_recognisedNames_areKept() {
        SquadDraft draft = new SquadDraft(List.of(
                new PlayerNameDraft("Ivan", "Petrov"),
                new PlayerNameDraft("Georgi", "Kolev")));

        TeamCreateForm team = new TeamCreateForm();
        LeagueDraftSanitizer.fillSquad(team, draft, 6, 0);

        assertThat(team.getPlayers().get(0).getFirstName()).isEqualTo("Ivan");
        assertThat(team.getPlayers().get(0).getLastName()).isEqualTo("Petrov");
        assertThat(team.getPlayers().get(1).getFirstName()).isEqualTo("Georgi");
    }

    @Test
    void fillSquad_alwaysUsesPoolNames() {
        SquadDraft draft = new SquadDraft(Arrays.asList(
                new PlayerNameDraft("Cristiano", "Ronaldo"),
                new PlayerNameDraft("Ivan Petrov", "Georgiev Kolev"),
                new PlayerNameDraft("", ""),
                new PlayerNameDraft(null, null),
                new PlayerNameDraft("Иван", "Петров"),
                new PlayerNameDraft("Ivan2", "Petrov9"),
                null));

        TeamCreateForm team = new TeamCreateForm();
        LeagueDraftSanitizer.fillSquad(team, draft, 12, 0);

        assertThat(filled(team)).hasSize(12);
        for (PlayerRowDto row : filled(team)) {
            assertThat(FIRST_POOL).contains(row.getFirstName());
            assertThat(LAST_POOL).contains(row.getLastName());
        }
    }

    @Test
    void fillSquad_duplicateNames_areMadeUniqueFromPool() {
        SquadDraft draft = new SquadDraft(List.of(
                new PlayerNameDraft("Ivan", "Petrov"),
                new PlayerNameDraft("Ivan", "Petrov"),
                new PlayerNameDraft("Ivan", "Petrov")));

        TeamCreateForm team = new TeamCreateForm();
        LeagueDraftSanitizer.fillSquad(team, draft, 6, 0);

        assertThat(filled(team)).extracting(r -> r.getFirstName() + " " + r.getLastName())
                .doesNotHaveDuplicates();
        for (PlayerRowDto row : filled(team)) {
            assertThat(FIRST_POOL).contains(row.getFirstName());
            assertThat(LAST_POOL).contains(row.getLastName());
        }
    }

    @Test
    void fillSquad_knownFirstNameWithUnknownLastName_fallsBackForBoth() {
        SquadDraft draft = new SquadDraft(List.of(new PlayerNameDraft("Ivan", "Ronaldo")));

        TeamCreateForm team = new TeamCreateForm();
        LeagueDraftSanitizer.fillSquad(team, draft, 6, 0);

        assertThat(FIRST_POOL).contains(team.getPlayers().get(0).getFirstName());
        assertThat(LAST_POOL).contains(team.getPlayers().get(0).getLastName());
        assertThat(team.getPlayers().get(0).getLastName()).isNotEqualTo("Ronaldo");
    }

    @Test
    void fillSquad_draftNameCollidingWithAReservedFallback_isReplaced() {
        String reservedFirst = LeagueDraftSanitizer.FIRST_NAMES[3];
        String reservedLast = LeagueDraftSanitizer.LAST_NAMES[3];
        SquadDraft draft = new SquadDraft(List.of(new PlayerNameDraft(reservedFirst, reservedLast)));

        TeamCreateForm team = new TeamCreateForm();
        LeagueDraftSanitizer.fillSquad(team, draft, 6, 0);

        assertThat(team.getPlayers().get(0).getFirstName() + " " + team.getPlayers().get(0).getLastName())
                .isNotEqualTo(reservedFirst + " " + reservedLast);
        assertThat(filled(team)).extracting(r -> r.getFirstName() + " " + r.getLastName())
                .doesNotHaveDuplicates();
    }

    @Test
    void fillSquad_everyTeamIndex_producesDistinctNamesWithinTheSquad() {
        for (int teamIndex = 0; teamIndex < 16; teamIndex++) {
            TeamCreateForm team = new TeamCreateForm();
            LeagueDraftSanitizer.fillSquad(team, null, 12, teamIndex);

            assertThat(filled(team)).extracting(r -> r.getFirstName() + " " + r.getLastName())
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void fillSquad_nullDraftOrPlayers_producesFullFillerSquad() {
        for (SquadDraft draft : Arrays.asList(null, new SquadDraft(null), new SquadDraft(List.of()))) {
            TeamCreateForm team = new TeamCreateForm();
            LeagueDraftSanitizer.fillSquad(team, draft, 9, 1);

            assertThat(team.getPlayers()).hasSize(12);
            assertThat(filled(team)).hasSize(9);
        }
    }

    @Test
    void fillSquad_sizeIsClampedAndRowsAlwaysTwelve() {
        int[][] cases = {{3, 6}, {6, 6}, {9, 9}, {12, 12}, {20, 12}};

        for (int[] testCase : cases) {
            TeamCreateForm team = new TeamCreateForm();
            LeagueDraftSanitizer.fillSquad(team, null, testCase[0], 0);

            assertThat(team.getPlayers()).hasSize(12);
            assertThat(filled(team)).hasSize(testCase[1]);
            assertThat(team.getPlayers()).extracting(PlayerRowDto::getShirtNumber)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        }
    }

    @Test
    void fillSquad_trailingRowsAreEmpty() {
        TeamCreateForm team = new TeamCreateForm();

        LeagueDraftSanitizer.fillSquad(team, null, 6, 0);

        assertThat(team.getPlayers().subList(6, 12)).allMatch(PlayerRowDto::isEmpty);
    }

    @Test
    void fillSquad_differentTeamIndexes_useDifferentFillerNames() {
        TeamCreateForm first = new TeamCreateForm();
        TeamCreateForm second = new TeamCreateForm();

        LeagueDraftSanitizer.fillSquad(first, null, 12, 0);
        LeagueDraftSanitizer.fillSquad(second, null, 12, 1);

        assertThat(first.getPlayers().get(0).getFirstName())
                .isNotEqualTo(second.getPlayers().get(0).getFirstName());
    }

    @Test
    void clampSquadSize_handlesNullAndRange() {
        assertThat(LeagueDraftSanitizer.clampSquadSize(null)).isEqualTo(12);
        assertThat(LeagueDraftSanitizer.clampSquadSize(9)).isEqualTo(9);
        assertThat(LeagueDraftSanitizer.clampSquadSize(2)).isEqualTo(6);
        assertThat(LeagueDraftSanitizer.clampSquadSize(40)).isEqualTo(12);
    }

    @Test
    void squadSizeFor_missingOrOutOfRange_fallsBackToMaximum() {
        assertThat(LeagueDraftSanitizer.squadSizeFor(null, 0)).isEqualTo(12);
        assertThat(LeagueDraftSanitizer.squadSizeFor(new LeagueSkeletonDraft("Banitsa Cup", null), 0)).isEqualTo(12);
        assertThat(LeagueDraftSanitizer.squadSizeFor(skeleton("Domati"), 5)).isEqualTo(12);
        assertThat(LeagueDraftSanitizer.squadSizeFor(skeleton("Domati"), -1)).isEqualTo(12);
    }

    @Test
    void squadSizeFor_readsAndClampsPerTeamValue() {
        LeagueSkeletonDraft draft = new LeagueSkeletonDraft("Banitsa Cup", List.of(
                new TeamNameDraft("Domati", "Sofia", 7),
                new TeamNameDraft("Kyufteta", "Varna", 2),
                new TeamNameDraft("Gladnite", "Ruse", 40),
                new TeamNameDraft("Zaspalite", "Lom", null)));

        assertThat(LeagueDraftSanitizer.squadSizeFor(draft, 0)).isEqualTo(7);
        assertThat(LeagueDraftSanitizer.squadSizeFor(draft, 1)).isEqualTo(6);
        assertThat(LeagueDraftSanitizer.squadSizeFor(draft, 2)).isEqualTo(12);
        assertThat(LeagueDraftSanitizer.squadSizeFor(draft, 3)).isEqualTo(12);
    }

    @Test
    void squadSizeFor_skipsNullElementsLikeSanitizeTeams() {
        List<TeamNameDraft> drafts = new ArrayList<>();
        drafts.add(null);
        drafts.add(new TeamNameDraft("Domati", "Sofia", 8));

        assertThat(LeagueDraftSanitizer.squadSizeFor(new LeagueSkeletonDraft("Banitsa Cup", drafts), 0))
                .isEqualTo(8);
    }

    @Test
    void cityPromptOptions_returnsPoolCitiesOnly() {
        String options = LeagueDraftSanitizer.cityPromptOptions(3);

        assertThat(options.split(", ")).hasSize(40);
        for (String city : options.split(", ")) {
            assertThat(CITY_POOL).contains(city);
        }
    }

    @Test
    void pools_areExposedForThePrompt() {
        assertThat(LeagueDraftSanitizer.teamNamePool()).contains("Banicharite");
        assertThat(LeagueDraftSanitizer.leagueFoodPool()).contains("Banitsa");
        assertThat(LeagueDraftSanitizer.leagueTypePool()).contains("Cup");
        assertThat(LeagueDraftSanitizer.firstNamePool()).contains("Ivan");
        assertThat(LeagueDraftSanitizer.lastNamePool()).contains("Petrov");
    }

    @ParameterizedTest
    @MethodSource("adversarialSkeletons")
    void sanitizedDraft_alwaysPassesSquadRowValidator(LeagueSkeletonDraft skeleton) {
        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(skeleton, 4, List.of(), NEVER_TAKEN);

        for (int i = 0; i < teams.size(); i++) {
            TeamCreateForm team = teams.get(i);
            LeagueDraftSanitizer.fillSquad(team, adversarialSquad(),
                    LeagueDraftSanitizer.squadSizeFor(skeleton, i), i);

            BindingResult bindingResult = new BeanPropertyBindingResult(team, "team");
            List<Integer> filledRows = SquadRowValidator.validate(team.getPlayers(),
                    "newTeams[" + i + "].players", Set.of(), bindingResult);

            assertThat(bindingResult.getAllErrors()).isEmpty();
            assertThat(filledRows.size()).isBetween(6, 12);
        }
    }

    @ParameterizedTest
    @MethodSource("adversarialSkeletons")
    void sanitizedDraft_alwaysPassesBeanValidation(LeagueSkeletonDraft skeleton) {
        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(skeleton, 4, List.of(), NEVER_TAKEN);

        for (int i = 0; i < teams.size(); i++) {
            TeamCreateForm team = teams.get(i);
            LeagueDraftSanitizer.fillSquad(team, adversarialSquad(),
                    LeagueDraftSanitizer.squadSizeFor(skeleton, i), i);

            assertThat(validator.validate(team)).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("adversarialSkeletons")
    void sanitizedDraft_alwaysUsesPoolValues(LeagueSkeletonDraft skeleton) {
        List<TeamCreateForm> teams = LeagueDraftSanitizer.sanitizeTeams(skeleton, 4, List.of(), NEVER_TAKEN);

        assertThat(teams).extracting(TeamCreateForm::getName).doesNotHaveDuplicates();
        for (TeamCreateForm team : teams) {
            assertThat(TEAM_POOL).contains(team.getName());
            assertThat(CITY_POOL).contains(team.getCity());
        }
    }

    private static Stream<LeagueSkeletonDraft> adversarialSkeletons() {
        return Stream.of(
                null,
                new LeagueSkeletonDraft(null, null),
                new LeagueSkeletonDraft("", List.of()),
                new LeagueSkeletonDraft("Banitsa Cup", List.of(
                        new TeamNameDraft("Real Madrid", "New York", 12),
                        new TeamNameDraft("Real Madrid", "New York", 0),
                        new TeamNameDraft("", "", null),
                        new TeamNameDraft(null, null, 999))),
                new LeagueSkeletonDraft("Banitsa Cup", List.of(
                        new TeamNameDraft("Левски", "София", -5),
                        new TeamNameDraft("⚽🔥", "🏟️", 7),
                        new TeamNameDraft("A".repeat(500), "B".repeat(500), 12),
                        new TeamNameDraft("   ", "\t\n", 6))),
                new LeagueSkeletonDraft("Banitsa Cup", Collections.singletonList(null)));
    }

    private static SquadDraft adversarialSquad() {
        return new SquadDraft(Arrays.asList(
                new PlayerNameDraft("Ivan Petrov", "Georgiev Kolev"),
                new PlayerNameDraft("Ivan", "Petrov"),
                new PlayerNameDraft("Ivan", "Petrov"),
                new PlayerNameDraft("", ""),
                new PlayerNameDraft(null, null),
                new PlayerNameDraft("Иван", "Петров"),
                new PlayerNameDraft("X".repeat(300), "Y".repeat(300)),
                null));
    }

    private static LeagueSkeletonDraft skeleton(String... names) {
        List<TeamNameDraft> teams = new ArrayList<>();
        for (String name : names) {
            teams.add(new TeamNameDraft(name, "Sofia", 12));
        }
        return new LeagueSkeletonDraft("Banitsa Cup", teams);
    }

    private static List<PlayerRowDto> filled(TeamCreateForm team) {
        return team.getPlayers().stream().filter(row -> !row.isEmpty()).toList();
    }
}
