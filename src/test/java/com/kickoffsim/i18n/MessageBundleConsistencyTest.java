package com.kickoffsim.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBundleConsistencyTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path TEMPLATES = RESOURCES.resolve("templates");

    private static final String BASE = "messages.properties";
    private static final Map<String, String> LOCALE_FILES = Map.of(
            "bg", "messages_bg.properties",
            "de", "messages_de.properties");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[^}]*}");
    private static final Pattern TEMPLATE_KEY = Pattern.compile("#\\{\\s*([A-Za-z][A-Za-z0-9_.\\-]*)");

    private static final Map<String, List<String>> FORBIDDEN_TERMS = Map.of(
            "bg", List.of("Тим", "Ранглиста", "Реализатор", "Скуад"),
            "de", List.of("Rangliste", "Verein", "Begegnung", "Partie", "Aufstellung", "Torjäger"));

    private static final List<String> UNTRANSLATED_ENGLISH = List.of(
            "Team", "Match", "Round", "Squad", "Standings", "Fixture", "Player", "Season", "Goal");

    private static final Set<String> ALLOWED_IDENTICAL = Set.of(
            "common.name",
            "matches.tab.live",
            "matches.live",
            "goals.minute",
            "leagues.form.namefood",
            "leagues.detail.excel",
            "leagues.rules.format",
            "leagues.wizard.step.format",
            "teams.position",
            "admin.users.status",
            "profile.optional",
            "recap.story.results.item");

    @Test
    @DisplayName("every locale defines exactly the same keys as the base bundle")
    void allBundlesHaveIdenticalKeys() {
        Properties base = load(BASE);
        List<String> problems = new ArrayList<>();

        LOCALE_FILES.forEach((locale, file) -> {
            Properties translated = load(file);
            new TreeSet<>(base.stringPropertyNames()).stream()
                    .filter(key -> !translated.containsKey(key))
                    .forEach(key -> problems.add(locale + ": missing key '" + key + "'"));
            new TreeSet<>(translated.stringPropertyNames()).stream()
                    .filter(key -> !base.containsKey(key))
                    .forEach(key -> problems.add(locale + ": orphan key '" + key + "'"));
        });

        assertTrue(problems.isEmpty(), () -> "Message bundles are out of sync:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("placeholder indexes are identical across locales")
    void placeholdersMatchAcrossLocales() {
        Properties base = load(BASE);
        List<String> problems = new ArrayList<>();

        LOCALE_FILES.forEach((locale, file) -> {
            Properties translated = load(file);
            for (String key : new TreeSet<>(base.stringPropertyNames())) {
                String value = translated.getProperty(key);
                if (value == null) {
                    continue;
                }
                Set<String> expected = placeholders(base.getProperty(key));
                Set<String> actual = placeholders(value);
                if (!expected.equals(actual)) {
                    problems.add(locale + ": '" + key + "' expects " + expected + " but has " + actual);
                }
            }
        });

        assertTrue(problems.isEmpty(), () -> "Placeholder mismatch:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("no message resolves to a blank value")
    void noBlankValues() {
        List<String> problems = new ArrayList<>();

        Stream.concat(Stream.of(Map.entry("en", BASE)), LOCALE_FILES.entrySet().stream())
                .forEach(entry -> {
                    Properties bundle = load(entry.getValue());
                    for (String key : new TreeSet<>(bundle.stringPropertyNames())) {
                        if (bundle.getProperty(key).isBlank()) {
                            problems.add(entry.getKey() + ": '" + key + "' is blank");
                        }
                    }
                });

        assertTrue(problems.isEmpty(), () -> "Blank messages:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("translations use the project glossary and no competing synonyms")
    void terminologyIsConsistent() {
        List<String> problems = new ArrayList<>();

        FORBIDDEN_TERMS.forEach((locale, terms) -> {
            Properties bundle = load(LOCALE_FILES.get(locale));
            for (String key : new TreeSet<>(bundle.stringPropertyNames())) {
                String value = bundle.getProperty(key);
                terms.stream()
                        .filter(value::contains)
                        .forEach(term -> problems.add(
                                locale + ": '" + key + "' uses off-glossary term '" + term.trim() + "'"));
            }
        });

        assertTrue(problems.isEmpty(), () -> "Terminology drift:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("no untranslated English text leaks into a localized bundle")
    void noUntranslatedEnglishLeaks() {
        Properties base = load(BASE);
        List<String> problems = new ArrayList<>();

        LOCALE_FILES.forEach((locale, file) -> {
            Properties bundle = load(file);
            for (String key : new TreeSet<>(bundle.stringPropertyNames())) {
                String value = bundle.getProperty(key);

                if (!ALLOWED_IDENTICAL.contains(key)
                        && value.trim().length() > 3
                        && value.equals(base.getProperty(key))) {
                    problems.add(locale + ": '" + key + "' is identical to English");
                }

                UNTRANSLATED_ENGLISH.stream()
                        .filter(word -> containsWord(value, word))
                        .forEach(word -> problems.add(locale + ": '" + key + "' contains English word '" + word + "'"));
            }
        });

        assertTrue(problems.isEmpty(), () -> "Untranslated content:\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("every message key referenced from a template exists in the bundle")
    void everyTemplateMessageKeyExists() throws IOException {
        Properties base = load(BASE);
        List<String> problems = new ArrayList<>();

        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            List<Path> templates = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".html"))
                    .toList();

            for (Path template : templates) {
                String content = Files.readString(template, StandardCharsets.UTF_8);
                Matcher matcher = TEMPLATE_KEY.matcher(content);
                Set<String> referenced = new LinkedHashSet<>();
                while (matcher.find()) {
                    referenced.add(matcher.group(1));
                }
                referenced.stream()
                        .filter(key -> key.contains("."))
                        .filter(key -> !base.containsKey(key))
                        .forEach(key -> problems.add(
                                RESOURCES.relativize(template) + ": unknown key '" + key + "'"));
            }
        }

        assertTrue(problems.isEmpty(), () -> "Templates reference missing messages:\n" + String.join("\n", problems));
    }

    private static boolean containsWord(String value, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(value).find();
    }

    private static Set<String> placeholders(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        Set<String> indexes = new TreeSet<>();
        while (matcher.find()) {
            indexes.add(matcher.group(1));
        }
        return indexes;
    }

    private static Properties load(String fileName) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(RESOURCES.resolve(fileName), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + fileName, e);
        }
        return properties;
    }
}
