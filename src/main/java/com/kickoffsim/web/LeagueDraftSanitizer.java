package com.kickoffsim.web;

import com.kickoffsim.dto.LeagueSkeletonDraft;
import com.kickoffsim.dto.PlayerNameDraft;
import com.kickoffsim.dto.PlayerRowDto;
import com.kickoffsim.dto.SquadDraft;
import com.kickoffsim.dto.TeamCreateForm;
import com.kickoffsim.dto.TeamNameDraft;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;

public final class LeagueDraftSanitizer {

    public static final int MIN_SQUAD_SIZE = 6;
    public static final int MAX_SQUAD_SIZE = 12;

    static final int CITY_FALLBACK_ATTEMPTS = 5;

    public static final String[] TEAM_NAMES = {
            "Kalpazanite", "Kyufteta", "Pensionerite", "Shegobiytsi", "Chudatsite",
            "Haimanite", "Marzelivite", "Bosonogite", "Palavnitsi", "Domati",
            "Zabravenite", "Poslednite", "Umornite", "Zakusnelite", "Sanlivite",
            "Shashavite", "Smotanite", "Zaspalite", "Gladnite", "Nepobedimite",
            "Shampionite", "Provalenite", "Bezgrizhnite", "Nadarenite",
            "Neudachnicite", "Divannite", "Birenite", "Pitsarite", "Sirenkite",
            "Banicharite", "Dyunerdzhiite", "Rakidzhiite", "Naglite", "Smahnatite",
            "Otkachalkite", "Propadnalite", "Zhadnite", "Bezpametnite", "Objurkanite",
            "Zagubenite"
    };

    public static final String[] CITIES = {
            "Sofia", "Bankya", "Novi Iskar",
            "Blagoevgrad", "Bansko", "Belitsa", "Gotse Delchev", "Hadzhidimovo", "Kresna",
            "Melnik", "Petrich", "Razlog", "Sandanski", "Simitli", "Yakoruda",
            "Burgas", "Aheloy", "Ahtopol", "Aytos", "Bulgarovo", "Chernomorets",
            "Kableshkovo", "Kameno", "Karnobat", "Malko Tarnovo", "Nesebar", "Obzor",
            "Pomorie", "Primorsko", "Sozopol", "Sredets", "Sungurlare", "Sveti Vlas", "Tsarevo",
            "Varna", "Aksakovo", "Beloslav", "Byala", "Dalgopol", "Devnya",
            "Ignatievo", "Provadia", "Suvorovo", "Valchi Dol",
            "Veliko Tarnovo", "Byala Cherkva", "Debelets", "Elena", "Gorna Oryahovitsa",
            "Kilifarevo", "Lyaskovets", "Pavlikeni", "Polski Trambesh", "Strazhitsa",
            "Suhindol", "Svishtov", "Zlataritsa",
            "Vidin", "Belogradchik", "Bregovo", "Dimovo", "Gramada", "Kula",
            "Vratsa", "Byala Slatina", "Knezha", "Kozloduy", "Krivodol", "Mezdra",
            "Miziya", "Oryahovo", "Roman",
            "Gabrovo", "Dryanovo", "Plachkovtsi", "Sevlievo", "Tryavna",
            "Dobrich", "Balchik", "General Toshevo", "Kavarna", "Shabla", "Tervel",
            "Kardzhali", "Ardino", "Dzhebel", "Krumovgrad", "Momchilgrad",
            "Kyustendil", "Boboshevo", "Bobov Dol", "Dupnitsa", "Kocherinovo",
            "Rila", "Sapareva Banya",
            "Lovech", "Apriltsi", "Letnitsa", "Lukovit", "Teteven", "Troyan",
            "Ugarchin", "Yablanitsa",
            "Montana", "Berkovitsa", "Boychinovtsi", "Brusartsi", "Chiprovtsi",
            "Lom", "Valchedram", "Varshets",
            "Pazardzhik", "Batak", "Belovo", "Bratsigovo", "Kostandovo", "Panagyurishte",
            "Peshtera", "Rakitovo", "Sarnitsa", "Septemvri", "Strelcha", "Velingrad",
            "Pernik", "Batanovtsi", "Breznik", "Radomir", "Tran", "Zemen",
            "Pleven", "Belene", "Dolna Mitropolia", "Dolni Dabnik", "Gulyantsi",
            "Levski", "Nikopol", "Pordim", "Slavyanovo", "Trastenik",
            "Plovdiv", "Asenovgrad", "Banya", "Brezovo", "Hisarya", "Kalofer",
            "Karlovo", "Klisura", "Krichim", "Kuklen", "Laki", "Parvomay",
            "Perushtitsa", "Rakovski", "Sadovo", "Saedinenie", "Sopot", "Stamboliyski",
            "Razgrad", "Isperih", "Kubrat", "Loznitsa", "Senovo", "Tsar Kaloyan", "Zavet",
            "Ruse", "Borovo", "Dve Mogili", "Marten", "Slivo Pole", "Vetovo",
            "Silistra", "Alfatar", "Dulovo", "Glavinitsa", "Tutrakan",
            "Sliven", "Kermen", "Kotel", "Nova Zagora", "Shivachevo",
            "Smolyan", "Chepelare", "Devin", "Dospat", "Madan", "Nedelino",
            "Rudozem", "Zlatograd",
            "Botevgrad", "Dolna Banya", "Dragoman", "Elin Pelin", "Etropole", "Godech",
            "Ihtiman", "Koprivshtitsa", "Kostenets", "Kostinbrod", "Pirdop", "Pravets",
            "Samokov", "Slivnitsa", "Svoge", "Zlatitsa",
            "Stara Zagora", "Chirpan", "Gurkovo", "Gulabovo", "Kazanlak", "Maglizh",
            "Nikolaevo", "Pavel Banya", "Radnevo", "Shipka",
            "Targovishte", "Antonovo", "Omurtag", "Opaka", "Popovo",
            "Haskovo", "Dimitrovgrad", "Harmanli", "Ivaylovgrad", "Lyubimets",
            "Madzharovo", "Merichleri", "Simeonovgrad", "Svilengrad", "Topolovgrad",
            "Shumen", "Kaolinovo", "Kaspichan", "Novi Pazar", "Pliska", "Smyadovo",
            "Varbitsa", "Veliki Preslav",
            "Yambol", "Bolyarovo", "Elhovo", "Straldzha"
    };

    public static final String[] LEAGUE_FOODS = {
            "Banitsa", "Tarator", "Kyufte", "Lyutenitsa", "Shopska",
            "Kavarma", "Musaka", "Mekitsa", "Sirene", "Boza", "Domat", "Skara"
    };

    public static final String[] LEAGUE_TYPES = {
            "Cup", "Bowl", "Derby", "Masters", "Trophy",
            "Open", "Classic", "Kings", "League", "Series", "Shield", "Challenge"
    };

    public static final String[] FIRST_NAMES = {
            "Ivan", "Georgi", "Nikolay", "Stefan", "Martin", "Dimitar",
            "Petar", "Viktor", "Hristo", "Boyan", "Plamen", "Stoyan",
            "Krasimir", "Mihail", "Radoslav", "Yavor", "Valentin", "Deyan",
            "Zhivko", "Kostadin", "Lyubomir", "Todor", "Aleksandar", "Stanimir",
            "Milen", "Angel", "Atanas", "Rosen", "Ilian", "Branimir",
            "Tsvetomir", "Galin", "Emil", "Kalin", "Tihomir", "Blagovest",
            "Momchil", "Dobromir", "Desislav", "Ventsislav"
    };

    public static final String[] LAST_NAMES = {
            "Ivanov", "Petrov", "Georgiev", "Dimitrov", "Stoyanov", "Kolev",
            "Todorov", "Marinov", "Atanasov", "Hristov", "Kostadinov", "Slavov",
            "Popov", "Nikolov", "Yordanov", "Borisov", "Angelov", "Tsonev",
            "Nedyalkov", "Penchev", "Petkov", "Rusev", "Lazarov", "Stoichev",
            "Genov", "Vasilev", "Simeonov", "Spasov", "Tsvetkov", "Stefanov",
            "Mihaylov", "Iliev", "Stanchev", "Raykov", "Nedelchev", "Mitrov",
            "Blagoev", "Zhivkov", "Aleksandrov", "Manchev"
    };

    private LeagueDraftSanitizer() {
    }

    public static String sanitizeLeagueName(String raw) {
        String[] words = foldAccents(raw).replaceAll("[^A-Za-z ]", " ").trim().split("\\s+");
        String food = snap(words[0], LEAGUE_FOODS);
        String type = words.length > 1 ? snap(words[words.length - 1], LEAGUE_TYPES) : null;

        if (food == null) {
            food = LEAGUE_FOODS[Math.floorMod(hash(raw), LEAGUE_FOODS.length)];
        }
        if (type == null) {
            type = LEAGUE_TYPES[Math.floorMod(hash(raw) + 1, LEAGUE_TYPES.length)];
        }
        return food + " " + type;
    }

    public static List<TeamCreateForm> sanitizeTeams(LeagueSkeletonDraft skeleton, int newTeamCount,
                                                     Collection<String> reservedNames,
                                                     BiPredicate<String, String> nameCityTaken) {
        List<TeamCreateForm> teams = new ArrayList<>();
        if (newTeamCount <= 0) {
            return teams;
        }

        List<TeamNameDraft> raw = presentTeams(skeleton);

        Set<String> usedNames = new HashSet<>();
        if (reservedNames != null) {
            for (String reserved : reservedNames) {
                if (reserved != null && !reserved.isBlank()) {
                    usedNames.add(reserved.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        int rotation = hash(skeleton == null ? null : skeleton.leagueName());

        for (int i = 0; i < newTeamCount; i++) {
            TeamNameDraft draft = i < raw.size() ? raw.get(i) : null;

            String city = snap(draft == null ? null : draft.city(), CITIES);
            if (city == null) {
                city = CITIES[Math.floorMod(rotation + i, CITIES.length)];
            }

            String name = snap(draft == null ? null : draft.name(), TEAM_NAMES);
            if (name == null || isUnavailable(name, city, usedNames, nameCityTaken)) {
                String[] picked = pickFromPool(i, city, usedNames, nameCityTaken);
                name = picked[0];
                city = picked[1];
            }

            usedNames.add(name.toLowerCase(Locale.ROOT));

            TeamCreateForm team = new TeamCreateForm();
            team.setName(name);
            team.setCity(city);
            teams.add(team);
        }
        return teams;
    }

    public static void fillSquad(TeamCreateForm team, SquadDraft draft, int squadSize, int teamIndex) {
        int size = clampSquadSize(squadSize);

        List<PlayerNameDraft> raw = new ArrayList<>();
        if (draft != null && draft.players() != null) {
            for (PlayerNameDraft player : draft.players()) {
                if (player != null) {
                    raw.add(player);
                }
            }
        }

        List<PlayerRowDto> rows = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        Set<String> reservedFallbacks = new HashSet<>();
        for (int j = 0; j < size; j++) {
            reservedFallbacks.add(fullNameKey(fallbackFirst(teamIndex, j), fallbackLast(teamIndex, j)));
        }

        for (int j = 0; j < size; j++) {
            PlayerNameDraft player = j < raw.size() ? raw.get(j) : null;

            String first = snap(player == null ? null : player.firstName(), FIRST_NAMES);
            String last = snap(player == null ? null : player.lastName(), LAST_NAMES);

            if (first == null || last == null
                    || reservedFallbacks.contains(fullNameKey(first, last))
                    || !usedNames.add(fullNameKey(first, last))) {
                first = fallbackFirst(teamIndex, j);
                last = fallbackLast(teamIndex, j);
                usedNames.add(fullNameKey(first, last));
            }

            PlayerRowDto row = new PlayerRowDto();
            row.setFirstName(first);
            row.setLastName(last);
            row.setShirtNumber(j + 1);
            rows.add(row);
        }

        for (int j = size; j < MAX_SQUAD_SIZE; j++) {
            PlayerRowDto row = new PlayerRowDto();
            row.setShirtNumber(j + 1);
            rows.add(row);
        }

        team.setPlayers(rows);
    }

    public static int clampSquadSize(Integer squadSize) {
        if (squadSize == null) {
            return MAX_SQUAD_SIZE;
        }
        return Math.min(MAX_SQUAD_SIZE, Math.max(MIN_SQUAD_SIZE, squadSize));
    }

    public static int squadSizeFor(LeagueSkeletonDraft skeleton, int index) {
        List<TeamNameDraft> raw = presentTeams(skeleton);
        if (index < 0 || index >= raw.size()) {
            return MAX_SQUAD_SIZE;
        }
        return clampSquadSize(raw.get(index).squadSize());
    }

    public static String teamNamePool() {
        return String.join(", ", TEAM_NAMES);
    }

    public static String cityPromptOptions(int seed) {
        int window = 40;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < window; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(CITIES[Math.floorMod(seed * window + i, CITIES.length)]);
        }
        return builder.toString();
    }

    public static String leagueFoodPool() {
        return String.join(", ", LEAGUE_FOODS);
    }

    public static String leagueTypePool() {
        return String.join(", ", LEAGUE_TYPES);
    }

    public static String firstNamePool() {
        return String.join(", ", FIRST_NAMES);
    }

    public static String lastNamePool() {
        return String.join(", ", LAST_NAMES);
    }

    static String snap(String raw, String[] pool) {
        String key = normalize(raw);
        if (key.isEmpty()) {
            return null;
        }
        for (String candidate : pool) {
            if (normalize(candidate).equals(key)) {
                return candidate;
            }
        }
        return null;
    }

    private static String[] pickFromPool(int index, String preferredCity, Set<String> usedNames,
                                         BiPredicate<String, String> nameCityTaken) {
        for (int c = 0; c < CITY_FALLBACK_ATTEMPTS; c++) {
            String city = c == 0 ? preferredCity : CITIES[Math.floorMod(index + c, CITIES.length)];
            for (int n = 0; n < TEAM_NAMES.length; n++) {
                String candidate = TEAM_NAMES[Math.floorMod(index + n, TEAM_NAMES.length)];
                if (!isUnavailable(candidate, city, usedNames, nameCityTaken)) {
                    return new String[]{candidate, city};
                }
            }
        }
        return new String[]{TEAM_NAMES[Math.floorMod(index, TEAM_NAMES.length)], preferredCity};
    }

    private static boolean isUnavailable(String name, String city, Set<String> usedNames,
                                         BiPredicate<String, String> nameCityTaken) {
        return usedNames.contains(name.toLowerCase(Locale.ROOT))
                || (nameCityTaken != null && nameCityTaken.test(name, city));
    }

    private static List<TeamNameDraft> presentTeams(LeagueSkeletonDraft skeleton) {
        List<TeamNameDraft> present = new ArrayList<>();
        if (skeleton != null && skeleton.teams() != null) {
            for (TeamNameDraft draft : skeleton.teams()) {
                if (draft != null) {
                    present.add(draft);
                }
            }
        }
        return present;
    }

    private static String foldAccents(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String normalize(String raw) {
        StringBuilder builder = new StringBuilder();
        String folded = foldAccents(raw);
        for (int i = 0; i < folded.length(); i++) {
            char current = folded.charAt(i);
            if ((current >= 'a' && current <= 'z') || (current >= 'A' && current <= 'Z')) {
                builder.append(Character.toLowerCase(current));
            }
        }
        return builder.toString();
    }

    private static int hash(String raw) {
        return raw == null ? 0 : Math.abs(normalize(raw).hashCode());
    }

    private static String fallbackFirst(int teamIndex, int index) {
        return FIRST_NAMES[Math.floorMod(teamIndex * MAX_SQUAD_SIZE + index, FIRST_NAMES.length)];
    }

    private static String fallbackLast(int teamIndex, int index) {
        return LAST_NAMES[Math.floorMod(teamIndex * MAX_SQUAD_SIZE + index, LAST_NAMES.length)];
    }

    private static String fullNameKey(String firstName, String lastName) {
        return (firstName + "|" + lastName).toLowerCase(Locale.ROOT);
    }
}
