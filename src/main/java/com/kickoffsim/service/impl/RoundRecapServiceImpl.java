package com.kickoffsim.service.impl;

import com.kickoffsim.dto.*;
import com.kickoffsim.exception.InvalidLeagueOperationException;
import com.kickoffsim.model.RoundRecap;
import com.kickoffsim.repository.RoundRecapRepository;
import com.kickoffsim.repository.LeagueRepository;
import com.kickoffsim.service.LeagueService;
import com.kickoffsim.service.RoundRecapAiClient;
import com.kickoffsim.service.RoundRecapService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RoundRecapServiceImpl implements RoundRecapService {

    private static final int SEASON_SCOPE = 0;
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

    public RoundRecapServiceImpl(RoundRecapRepository roundRecapRepository,
                                 LeagueRepository leagueRepository,
                                 LeagueService leagueService,
                                 RoundRecapAiClient aiClient) {
        this.roundRecapRepository = roundRecapRepository;
        this.leagueRepository = leagueRepository;
        this.leagueService = leagueService;
        this.aiClient = aiClient;
    }

    @Override
    public Optional<RoundRecapView> find(UUID leagueId, int roundNumber, Locale locale) {
        String localeTag = localeTag(locale);
        return roundRecapRepository.findByLeagueIdAndRoundNumberAndLocaleTag(
                        leagueId, roundNumber, localeTag)
                .map(this::toView);
    }

    @Override
    @Transactional
    public RoundRecapView generate(UUID leagueId, int roundNumber, Locale locale, boolean regenerate) {
        String localeTag = localeTag(locale);
        Optional<RoundRecap> existing = roundRecapRepository
                .findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, roundNumber, localeTag);
        if (existing.isPresent() && !regenerate) {
            return toView(existing.get());
        }

        LeagueDetailView league = leagueService.findDetail(leagueId);
        List<MatchDto> matches = roundMatches(league, roundNumber);
        if (!isRoundComplete(league, roundNumber)) {
            throw new InvalidLeagueOperationException("All matches in the round must be finished first.");
        }

        RoundRecapPromptData promptData = toPromptData(
                league, roundNumber, localeTag, matches, true);
        String fingerprint = fingerprint(promptData);
        String content = aiClient.generate(promptData);

        RoundRecap recap = existing.orElseGet(RoundRecap::new);
        recap.setLeague(leagueRepository.getReferenceById(league.getId()));
        recap.setRoundNumber(roundNumber);
        recap.setLocaleTag(localeTag);
        recap.setContent(content);
        recap.setGeneratedAt(LocalDateTime.now());
        recap.setSourceFingerprint(fingerprint);
        return toView(roundRecapRepository.save(recap));
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
                .map(this::toView);
    }

    @Override
    @Transactional
    public RoundRecapView generateSeason(UUID leagueId, Locale locale, boolean regenerate) {
        String localeTag = localeTag(locale);
        Optional<RoundRecap> existing = roundRecapRepository
                .findByLeagueIdAndRoundNumberAndLocaleTag(leagueId, SEASON_SCOPE, localeTag);
        if (existing.isPresent() && !regenerate) {
            return toView(existing.get());
        }

        LeagueDetailView league = leagueService.findDetail(leagueId);
        List<MatchDto> completedMatches = league.getMatches().stream()
                .filter(this::isMatchComplete)
                .toList();
        if (completedMatches.isEmpty()) {
            throw new InvalidLeagueOperationException("At least one match must be finished first.");
        }

        RoundRecapPromptData promptData = toPromptData(
                league, SEASON_SCOPE, localeTag, completedMatches, false);
        String content = aiClient.generate(promptData);
        RoundRecap recap = existing.orElseGet(RoundRecap::new);
        recap.setLeague(leagueRepository.getReferenceById(league.getId()));
        recap.setRoundNumber(SEASON_SCOPE);
        recap.setLocaleTag(localeTag);
        recap.setContent(content);
        recap.setGeneratedAt(LocalDateTime.now());
        recap.setSourceFingerprint(fingerprint(promptData));
        return toView(roundRecapRepository.save(recap));
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
        return league.getMatches().stream().anyMatch(this::isMatchComplete);
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
                                               boolean includeGoals) {
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
                    row.getGoalsAgainst(), row.getGoalDiff(), row.getPoints()));
        }
        return new RoundRecapPromptData(
                league.getName(), roundNumber, localeTag, LANGUAGES.get(localeTag), matchData, standings);
    }

    private RoundRecapMatchData toMatchData(MatchDto match, boolean includeGoals) {
        List<String> goals = includeGoals ? match.getGoalTimeline().stream()
                .map(goal -> {
                    String team = goal.isHomeGoal()
                            ? teamLabel(match.getHomeTeamName(), match.getHomeTeamCity())
                            : teamLabel(match.getAwayTeamName(), match.getAwayTeamCity());
                    String minute = goal.getMinute() == null ? "minute not recorded" : "minute " + goal.getMinute();
                    String assistant = goal.getAssistantName() == null ? "" : ", assist " + goal.getAssistantName();
                    String type = goal.isOwnGoal() ? ", own goal" : goal.isPenalty() ? ", penalty" : "";
                    return "%s, %s, %s%s%s".formatted(team, goal.getScorerName(), minute, assistant, type);
                })
                .toList() : List.of();
        return new RoundRecapMatchData(
                teamLabel(match.getHomeTeamName(), match.getHomeTeamCity()),
                teamLabel(match.getAwayTeamName(), match.getAwayTeamCity()),
                match.getHomeScore(), match.getAwayScore(), goals);
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
                recap.getContent(), recap.getGeneratedAt(), recap.getLocaleTag(), recap.getSourceFingerprint());
    }
}
