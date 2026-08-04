package com.kickoffsim.service.impl;

import com.kickoffsim.dto.*;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.model.Half;
import com.kickoffsim.model.RoundRecap;
import com.kickoffsim.repository.RoundRecapRepository;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.RoundRecapAiClient;
import com.kickoffsim.service.RoundRecapService;
import com.kickoffsim.web.RecapStoryParser;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RoundRecapServiceImpl implements RoundRecapService {

    private static final int SEASON_SCOPE = 0;
    private static final int SEASON_MIN_MATCHES_PER_TEAM = 4;
    private static final Map<String, String> LANGUAGES = Map.of(
            "bg", "Bulgarian",
            "en", "English",
            "de", "German");
    private static final List<Locale> RECAP_LOCALES = List.of(
            Locale.forLanguageTag("bg"),
            Locale.ENGLISH,
            Locale.GERMAN);

    private final RoundRecapRepository roundRecapRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueService leagueService;
    private final RoundRecapAiClient aiClient;
    private final FactCollector factCollector;
    private final LeagueContextBuilder leagueContextBuilder;
    private final StorylineMemory storylineMemory;
    private final RoundRecapEnhancer enhancer;
    private final boolean ollamaEnabled;

    public RoundRecapServiceImpl(RoundRecapRepository roundRecapRepository,
                                 LeagueRepository leagueRepository,
                                 LeagueService leagueService,
                                 RoundRecapAiClient aiClient,
                                 FactCollector factCollector,
                                 LeagueContextBuilder leagueContextBuilder,
                                 StorylineMemory storylineMemory,
                                 RoundRecapEnhancer enhancer,
                                 @Value("${kickoffsim.ollama.enabled:true}") boolean ollamaEnabled) {
        this.roundRecapRepository = roundRecapRepository;
        this.leagueRepository = leagueRepository;
        this.leagueService = leagueService;
        this.aiClient = aiClient;
        this.factCollector = factCollector;
        this.leagueContextBuilder = leagueContextBuilder;
        this.storylineMemory = storylineMemory;
        this.enhancer = enhancer;
        this.ollamaEnabled = ollamaEnabled;
    }

    @Override
    public Optional<RoundRecapView> find(UUID leagueId, int roundNumber, Locale locale) {
        String localeTag = localeTag(locale);
        return roundRecapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(
                        leagueId, roundNumber, localeTag)
                .filter(this::isCurrentFormat)
                .map(this::toView);
    }

    @Override
    @Transactional
    public RoundRecapView generate(UUID leagueId, int roundNumber, Locale locale, boolean regenerate) {
        String localeTag = localeTag(locale);
        Optional<RoundRecap> existing = roundRecapRepository
                .findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, roundNumber, localeTag);
        if (existing.isPresent() && !regenerate && isCurrentFormat(existing.get())) {
            return toView(existing.get());
        }

        LeagueDetailView league = leagueService.findDetail(leagueId);
        List<MatchDto> matches = roundMatches(league, roundNumber);
        if (!isRoundComplete(league, roundNumber)) {
            throw new InvalidLeagueOperationException("All matches in the round must be finished first.");
        }

        RoundRecapPromptData promptData = toPromptData(
                league, roundNumber, localeTag, matches, true, existing.isPresent());
        String fingerprint = fingerprint(promptData);
        String content = aiClient.generate(promptData);

        RoundRecap recap = existing.orElseGet(RoundRecap::new);
        recap.setLeague(leagueRepository.getReferenceById(league.getId()));
        recap.setRoundNumber(roundNumber);
        recap.setLocaleTag(localeTag);
        recap.setContent(content);
        recap.setGeneratedAt(LocalDateTime.now());
        recap.setSourceFingerprint(fingerprint);
        RoundRecap saved = roundRecapRepository.save(recap);
        scheduleEnhancement(saved.getId(), promptData);
        return toView(saved);
    }

    @Override
    @Transactional
    public void generateAllLanguages(UUID leagueId, int roundNumber, boolean regenerate) {
        for (Locale locale : RECAP_LOCALES) {
            generate(leagueId, roundNumber, locale, regenerate);
        }
    }

    @Override
    public boolean isRoundComplete(LeagueDetailView league, int roundNumber) {
        List<MatchDto> matches = roundMatches(league, roundNumber);
        return matches.stream().allMatch(this::isMatchComplete);
    }

    @Override
    public Optional<RoundRecapView> findSeason(UUID leagueId, Locale locale) {
        return roundRecapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(
                        leagueId, SEASON_SCOPE, localeTag(locale))
                .filter(this::isCurrentFormat)
                .map(this::toView);
    }

    @Override
    @Transactional
    public RoundRecapView generateSeason(UUID leagueId, Locale locale, boolean regenerate) {
        String localeTag = localeTag(locale);
        Optional<RoundRecap> existing = roundRecapRepository
                .findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, SEASON_SCOPE, localeTag);
        if (existing.isPresent() && !regenerate && isCurrentFormat(existing.get())) {
            return toView(existing.get());
        }

        LeagueDetailView league = leagueService.findSettledDetail(leagueId);
        List<MatchDto> completedMatches = league.getMatches().stream()
                .filter(this::isMatchComplete)
                .toList();
        if (!isSeasonRecapReady(league)) {
            throw new InvalidLeagueOperationException(
                    "Every team must have played at least " + SEASON_MIN_MATCHES_PER_TEAM + " matches first.");
        }

        RoundRecapPromptData promptData = toPromptData(
                league, SEASON_SCOPE, localeTag, completedMatches, false, existing.isPresent());
        String content = aiClient.generate(promptData);
        RoundRecap recap = existing.orElseGet(RoundRecap::new);
        recap.setLeague(leagueRepository.getReferenceById(league.getId()));
        recap.setRoundNumber(SEASON_SCOPE);
        recap.setLocaleTag(localeTag);
        recap.setContent(content);
        recap.setGeneratedAt(LocalDateTime.now());
        recap.setSourceFingerprint(fingerprint(promptData));
        RoundRecap saved = roundRecapRepository.save(recap);
        scheduleEnhancement(saved.getId(), promptData);
        return toView(saved);
    }

    @Override
    @Transactional
    public void generateSeasonAllLanguages(UUID leagueId, boolean regenerate) {
        for (Locale locale : RECAP_LOCALES) {
            generateSeason(leagueId, locale, regenerate);
        }
    }

    @Override
    public boolean isSeasonRecapReady(LeagueDetailView league) {
        return matchesPlayedByEveryTeam(league) >= SEASON_MIN_MATCHES_PER_TEAM;
    }

    private int matchesPlayedByEveryTeam(LeagueDetailView league) {
        List<StandingRow> standings = league.getStandings();
        return standings == null ? 0 : standings.stream()
                .mapToInt(StandingRow::getPlayed)
                .min()
                .orElse(0);
    }

    private List<MatchDto> roundMatches(LeagueDetailView league, int roundNumber) {
        if (roundNumber < 1) {
            throw new InvalidLeagueOperationException("Round number must be positive.");
        }
        List<MatchDto> matches = league.getMatches().stream()
                .filter(match -> Objects.equals(match.getRoundNumber(), roundNumber))
                .toList();
        if (matches.isEmpty()) {
            throw new InvalidLeagueOperationException("The selected round does not exist in this league.");
        }
        return matches;
    }

    private RoundRecapPromptData toPromptData(LeagueDetailView league, int roundNumber,
                                               String localeTag, List<MatchDto> matches,
                                               boolean includeGoals, boolean overwriting) {
        List<RoundRecapMatchData> matchData = matches.stream()
                .sorted(Comparator.comparing(MatchDto::getPlayedAt)
                        .thenComparing(match -> match.getId().toString()))
                .map(match -> toMatchData(match, includeGoals))
                .toList();
        List<RoundRecapStandingData> standings = new ArrayList<>();
        for (int i = 0; i < league.getStandings().size(); i++) {
            StandingRow row = league.getStandings().get(i);
            standings.add(new RoundRecapStandingData(
                    i + 1, teamLabel(row.getTeamName(), row.getTeamCity()), row.getPlayed(),
                    row.getWins(), row.getDraws(), row.getLosses(), row.getGoalsFor(),
                    row.getGoalsAgainst(), row.getGoalDiff(), row.getPoints(), row.isChampion()));
        }
        List<MatchFact> matchFacts = factCollector.collect(matches, includeGoals);
        LeagueContext context = buildContext(league, roundNumber);
        RecapMemory memory = storylineMemory.recall(league.getId(), roundNumber, localeTag, overwriting);
        return new RoundRecapPromptData(
                league.getName(), roundNumber, localeTag, LANGUAGES.get(localeTag), matchData, standings,
                league.getFormat() == null ? 0 : league.getFormat().getTotalRounds(),
                toPlayerData(league.getTopScorers()),
                toPlayerData(league.getTopAssists()),
                league.getChampionClinchRound(),
                matchFacts, context, memory);
    }

    private LeagueContext buildContext(LeagueDetailView league, int roundNumber) {
        if (roundNumber == SEASON_SCOPE) {
            return null;
        }
        List<MatchDto> completed = league.getMatches().stream()
                .filter(this::isMatchComplete)
                .toList();
        List<MatchFact> facts = factCollector.collect(completed, false);
        int totalRounds = league.getFormat() == null ? roundNumber : league.getFormat().getTotalRounds();
        return leagueContextBuilder.build(facts, roundNumber, totalRounds);
    }

    private List<RoundRecapPlayerData> toPlayerData(List<PlayerStatRow> rows) {
        return rows == null ? List.of() : rows.stream()
                .map(row -> new RoundRecapPlayerData(
                        row.getPlayerName(), teamLabel(row.getTeamName(), row.getTeamCity()), row.getCount()))
                .toList();
    }

    private RoundRecapMatchData toMatchData(MatchDto match, boolean includeGoals) {
        List<String> goals = includeGoals ? match.getGoalTimeline().stream()
                .map(goal -> {
                    String team = goal.isHomeGoal()
                            ? teamLabel(match.getHomeTeamName(), match.getHomeTeamCity())
                            : teamLabel(match.getAwayTeamName(), match.getAwayTeamCity());
                    String minute = goal.getMinute() == null ? "minute not recorded" : "minute " + goal.getMinute();
                    String half = goal.getHalf() == null ? ""
                            : ", " + (goal.getHalf() == Half.SECOND ? "second half" : "first half");
                    String assistant = goal.getAssistantName() == null ? "" : ", assist " + goal.getAssistantName();
                    String type = goal.isOwnGoal() ? ", own goal" : goal.isPenalty() ? ", penalty" : "";
                    return "%s, %s, %s%s%s%s".formatted(team, goal.getScorerName(), minute, half, assistant, type);
                })
                .toList() : List.of();
        return new RoundRecapMatchData(
                teamLabel(match.getHomeTeamName(), match.getHomeTeamCity()),
                teamLabel(match.getAwayTeamName(), match.getAwayTeamCity()),
                match.getHomeScore(), match.getAwayScore(), goals, match.getId().toString());
    }

    private String teamLabel(String name, String city) {
        return city == null || city.isBlank() ? name : name + " (" + city + ")";
    }

    private boolean isMatchComplete(MatchDto match) {
        LocalDateTime finishedBefore = LocalDateTime.now().minusMinutes(46);
        return match.getPlayedAt() != null
                && !match.getPlayedAt().isAfter(finishedBefore)
                && match.getHomeScore() != null
                && match.getAwayScore() != null;
    }

    private String localeTag(Locale locale) {
        String language = locale == null ? "en" : locale.getLanguage().toLowerCase(Locale.ROOT);
        return LANGUAGES.containsKey(language) ? language : "en";
    }

    private String fingerprint(RoundRecapPromptData data) {
        String source = data.leagueName() + "|" + data.roundNumber()
                + "|" + data.matches() + "|" + data.standings();
        return DigestUtils.sha256Hex(source);
    }

    private RoundRecapView toView(RoundRecap recap) {
        return new RoundRecapView(
                recap.getContent(), recap.getGeneratedAt(), recap.getLocaleTag(), recap.getSourceFingerprint(),
                RecapStoryParser.parse(recap.getContent()));
    }

    private void scheduleEnhancement(UUID recapId, RoundRecapPromptData promptData) {
        if (!ollamaEnabled) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enhancer.enhance(recapId, promptData);
                }
            });
        } else {
            enhancer.enhance(recapId, promptData);
        }
    }

    private boolean isCurrentFormat(RoundRecap recap) {
        List<RecapStory> stories = RecapStoryParser.parse(recap.getContent());
        if (stories.isEmpty()) {
            return false;
        }
        return stories.stream()
                .filter(story -> story.kind() == RecapStoryKind.RESULTS)
                .flatMap(story -> story.results().stream())
                .allMatch(link -> link.id() != null);
    }
}
