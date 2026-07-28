package com.kickoffsim.service.impl;

import com.kickoffsim.dto.LeagueSkeletonDraft;
import com.kickoffsim.dto.PlayerNameDraft;
import com.kickoffsim.dto.SquadDraft;
import com.kickoffsim.dto.TeamNameDraft;
import com.kickoffsim.service.LeagueDraftFactory;
import com.kickoffsim.web.LeagueDraftSanitizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
public class RandomLeagueDraftFactory implements LeagueDraftFactory {

    private final Random random;

    public RandomLeagueDraftFactory() {
        this(new Random());
    }

    RandomLeagueDraftFactory(Random random) {
        this.random = random;
    }

    @Override
    public LeagueSkeletonDraft createSkeleton(int teamCount) {
        String leagueName = pick(LeagueDraftSanitizer.LEAGUE_FOODS)
                + " " + pick(LeagueDraftSanitizer.LEAGUE_TYPES);

        List<String> names = pickDistinct(LeagueDraftSanitizer.TEAM_NAMES, Math.max(teamCount, 0));

        List<TeamNameDraft> teams = new ArrayList<>();
        for (String name : names) {
            teams.add(new TeamNameDraft(name, pick(LeagueDraftSanitizer.CITIES), randomSquadSize()));
        }
        return new LeagueSkeletonDraft(leagueName, teams);
    }

    @Override
    public SquadDraft createSquad(int squadSize) {
        int size = LeagueDraftSanitizer.clampSquadSize(squadSize);

        List<PlayerNameDraft> players = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();

        while (players.size() < size) {
            String first = pick(LeagueDraftSanitizer.FIRST_NAMES);
            String last = pick(LeagueDraftSanitizer.LAST_NAMES);
            if (used.add(first + "|" + last)) {
                players.add(new PlayerNameDraft(first, last));
            }
        }
        return new SquadDraft(players);
    }

    private int randomSquadSize() {
        return LeagueDraftSanitizer.MIN_SQUAD_SIZE
                + random.nextInt(LeagueDraftSanitizer.MAX_SQUAD_SIZE - LeagueDraftSanitizer.MIN_SQUAD_SIZE + 1);
    }

    private String pick(String[] pool) {
        return pool[random.nextInt(pool.length)];
    }

    private List<String> pickDistinct(String[] pool, int count) {
        List<String> shuffled = new ArrayList<>(List.of(pool));
        java.util.Collections.shuffle(shuffled, random);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
}
