package com.crabmods.algocraft.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTranslationCoverageTest {
    private static final Path LANG_DIR = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "resources", "assets", "algocraft", "lang"
    );
    private static final Path SOURCE_DIR = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "java", "com", "crabmods", "algocraft"
    );
    private static final Path WEB_SERVER = SOURCE_DIR.resolve("web").resolve("AlgoCraftWebServer.java");
    private static final Pattern TRANSLATABLE_KEY =
            Pattern.compile("Component\\.translatable(?:WithFallback)?\\(\\s*\"([^\"]+)\"\\s*[,)]");
    private static final Pattern COMPONENT_LITERAL_STRING =
            Pattern.compile("Component\\.literal\\(\"([^\"]*)\"\\)");
    private static final Pattern WEB_I18N_ATTRIBUTE =
            Pattern.compile("data-i18n(?:-placeholder)?=\"([^\"]+)\"");
    private static final Pattern WEB_I18N_FUNCTION =
            Pattern.compile("i18n\\.t\\('([^']+)'\\)");
    private static final Pattern STRING_CONSTANT =
            Pattern.compile("public static final String ([A-Z0-9_]+) = \"([a-z0-9_]+)\";");
    private static final Pattern ACHIEVEMENT_REGISTER =
            Pattern.compile("register\\(\\s*([A-Z0-9_]+)\\s*,", Pattern.MULTILINE);
    private static final Pattern TROPHY_TIER_ITEM_ID =
            Pattern.compile("\\b[A-Z]+\\([^;\\n]*\"([a-z_]+_trophy)\"\\)");
    private static final Pattern CJK = Pattern.compile("\\p{IsHan}");
    private static final Pattern FORMAT_PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?([sd])");
    private static final Set<String> ZH_CN_CJK_EXEMPT_KEYS = Set.of(
            "algocraft.gui.ide_title",
            "algocraft.gui.close_short",
            "algocraft.web.editor.language",
            // Locale-specific punctuation is validated explicitly below.
            "algocraft.format.list_separator"
    );
    private static final Set<String> MOJIBAKE_FRAGMENTS = Set.of(
            "\u6d93",
            "\u7f01",
            "\u935a",
            "\u9428",
            "\u93c1",
            "\u72b2",
            "\u4fd3",
            "\u9239",
            "\u6402",
            "\u9241",
            "\u99c3",
            "\u68f0\u6a3c\u6d30"
    );

    private static final Set<String> CORE_IDE_KEYS = Set.of(
            "algocraft.gui.run",
            "algocraft.gui.submit",
            "algocraft.gui.history",
            "algocraft.gui.view_short",
            "algocraft.gui.web",
            "algocraft.gui.close_short",
            "algocraft.gui.update_official",
            "algocraft.gui.update_official.running",
            "algocraft.gui.update_official.success",
            "algocraft.gui.update_official.error",
            "algocraft.gui.repository.official",
            "algocraft.gui.repository.user",
            "algocraft.gui.repository.builtin",
            "algocraft.gui.repository.unknown",
            "algocraft.gui.import",
            "algocraft.gui.import.do_import",
            "algocraft.gui.import.back",
            "algocraft.gui.open_ingame_ide",
            "algocraft.gui.open_web_ide",
            "algocraft.msg.opened_web_ide"
    );

    private static final Set<String> TROPHY_KEYS = Set.of(
            "algocraft.trophy.delivered_inventory",
            "algocraft.trophy.pending",
            "algocraft.trophy.pending_count",
            "algocraft.trophy.claim_result",
            "algocraft.trophy.recovered",
            "algocraft.trophy.claim_link",
            "algocraft.trophy.status",
            "item.algocraft.bronze_trophy",
            "item.algocraft.silver_trophy",
            "item.algocraft.gold_trophy",
            "item.algocraft.diamond_trophy",
            "item.algocraft.netherite_trophy",
            "item.algocraft.trophy.generic_desc",
            "algocraft.achievement.unlocked",
            "algocraft.achievement.rarity",
            "algocraft.achievement.trophy_prefix",
            "algocraft.trophy.awarded_to",
            "algocraft.trophy.awarded_on",
            "algocraft.trophy.type",
            "algocraft.rarity.common",
            "algocraft.rarity.uncommon",
            "algocraft.rarity.rare",
            "algocraft.rarity.epic",
            "algocraft.rarity.legendary",
            "algocraft.rarity.mythic",
            "algocraft.achievement.type.milestone",
            "algocraft.achievement.type.streak",
            "algocraft.achievement.type.difficulty",
            "algocraft.achievement.type.special"
    );

    @Test
    void coreIdeButtonsHaveEnglishAndChineseTranslations() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");

        for (String key : CORE_IDE_KEYS) {
            assertTranslated(english, "en_us", key);
            assertTranslated(chinese, "zh_cn", key);
        }
    }

    @Test
    void trophyTextHasEnglishAndChineseTranslations() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");

        for (String key : TROPHY_KEYS) {
            assertTranslated(english, "en_us", key);
            assertTranslated(chinese, "zh_cn", key);
        }
    }

    @Test
    void everyLiteralClientTranslationKeyHasEnglishAndChineseTranslations() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");

        Set<String> sourceKeys = collectSourceTranslationKeys();
        assertFalse(sourceKeys.isEmpty(), "source scan should find literal translation keys");

        for (String key : sourceKeys) {
            assertTranslated(english, "en_us", key);
            assertTranslated(chinese, "zh_cn", key);
        }
    }

    @Test
    void everyWebIdeTranslationKeyHasEnglishAndChineseTranslations() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");

        Set<String> webKeys = collectWebFrontendTranslationKeys();
        assertFalse(webKeys.isEmpty(), "web IDE source scan should find i18n keys");

        for (String shortKey : webKeys) {
            String key = "algocraft.web." + shortKey;
            assertTranslated(english, "en_us", key);
            assertTranslated(chinese, "zh_cn", key);
        }
    }

    @Test
    void webIdeDoesNotExposeUnsupportedOrMojibakedLocaleControls() throws IOException {
        String source = Files.readString(WEB_SERVER, StandardCharsets.UTF_8);

        assertFalse(source.contains("<option value=\"ja\""),
                "web IDE must not offer a locale that has no supported lang resource");
        assertFalse(source.contains("history.load_error"),
                "web IDE history error state must use the existing history.error translation key");
        assertFalse(source.contains("return this.translations[key] || key"),
                "missing web translations must not leak raw i18n keys to players");
        assertFalse(source.contains("Search problems or tags"),
                "web IDE placeholders must use data-i18n-placeholder translations");
        assertFalse(source.contains("馃") || source.contains("鉁") || source.contains("鏃") || source.contains("\uFFFD"),
                "web IDE markup must not contain mojibake from emoji/CJK text");

        assertTrue(source.contains("data-i18n=\"problem.passed\""),
                "passed badge text should be localized");
        assertTrue(source.contains("data-i18n=\"editor.language\""),
                "editor language label should stay covered by web translations");
    }

    @Test
    void repositorySelectorLocalizesBuiltInRepositoryNames() throws IOException {
        String screen = readSource("client/gui/modern/ModernAlgorithmScreen.java");
        String displayNames = readSource("client/gui/modern/RepositoryDisplayNames.java");

        assertTrue(screen.contains("CycleButton.builder(RepositoryDisplayNames::componentFor)"),
                "repository selector must route display names through the localization helper");
        assertFalse(screen.contains("Component.literal(repo.getName())"),
                "repository selector must not render built-in repository names as raw English literals");

        assertTrue(displayNames.contains("case \"Official\" -> Component.translatable(\"algocraft.gui.repository.official\")"));
        assertTrue(displayNames.contains("case \"User\" -> Component.translatable(\"algocraft.gui.repository.user\")"));
        assertTrue(displayNames.contains("case \"Built-in\" -> Component.translatable(\"algocraft.gui.repository.builtin\")"));
        assertTrue(displayNames.contains("Component.translatable(\"algocraft.gui.repository.unknown\")"));
        assertTrue(displayNames.contains("Component.literal(name == null ? \"\" : name)"),
                "custom imported repository names should remain player-provided literals");
    }

    @Test
    void userFacingComponentLiteralsAreNotHardCodedInSource() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_DIR)) {
            for (Path file : files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = COMPONENT_LITERAL_STRING.matcher(source);
                while (matcher.find()) {
                    String value = matcher.group(1);
                    if (!value.isBlank()) {
                        violations.add(SOURCE_DIR.relativize(file) + ":" + lineNumber(source, matcher.start())
                                + " uses Component.literal(\"" + value + "\")");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "User-facing UI/chat text must use Component.translatable keys: " + violations);
    }

    @Test
    void everyRegisteredAchievementNameAndDescriptionHasEnglishAndChineseTranslations() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");

        for (String achievementId : registeredAchievementIds()) {
            assertTranslated(english, "en_us", "algocraft.achievement." + achievementId + ".name");
            assertTranslated(chinese, "zh_cn", "algocraft.achievement." + achievementId + ".name");
            assertTranslated(english, "en_us", "algocraft.achievement." + achievementId + ".desc");
            assertTranslated(chinese, "zh_cn", "algocraft.achievement." + achievementId + ".desc");
        }
    }

    @Test
    void everyTrophyTierTranslationKeyHasEnglishAndChineseTranslations() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");

        for (String trophyItemId : trophyTierItemIds()) {
            assertTranslated(english, "en_us", "item.algocraft." + trophyItemId);
            assertTranslated(chinese, "zh_cn", "item.algocraft." + trophyItemId);
        }
    }

    @Test
    void chineseClientTranslationsAreReadableAndNotMojibake() throws IOException {
        JsonObject chinese = readLang("zh_cn");

        for (String key : chinese.keySet()) {
            if (key.startsWith("_comment")) {
                continue;
            }
            String value = chinese.get(key).getAsString();
            assertNoMojibake("zh_cn", key, value);
            if (!ZH_CN_CJK_EXEMPT_KEYS.contains(key)) {
                assertTrue(CJK.matcher(value).find(),
                        "zh_cn translation should contain readable Chinese for " + key + ": " + value);
            }
        }
    }

    @Test
    void clientTranslationsPreserveFormatPlaceholdersAcrossLanguages() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");
        assertEquals(", ", english.get("algocraft.format.list_separator").getAsString());
        assertEquals("、", chinese.get("algocraft.format.list_separator").getAsString());

        for (String key : english.keySet()) {
            assertTrue(chinese.has(key), "zh_cn is missing translation key " + key);
            assertEquals(
                    formatPlaceholders(english.get(key).getAsString()),
                    formatPlaceholders(chinese.get(key).getAsString()),
                    "zh_cn placeholder contract differs for " + key);
        }
    }

    @Test
    void fixedWidthButtonTranslationsStayWithinTheirPixelBudgets() throws IOException {
        JsonObject english = readLang("en_us");
        JsonObject chinese = readLang("zh_cn");
        Map<String, Integer> budgets = Map.ofEntries(
                Map.entry("algocraft.gui.run", 36),
                Map.entry("algocraft.gui.submit", 50),
                Map.entry("algocraft.gui.history", 50),
                Map.entry("algocraft.gui.view_short", 44),
                Map.entry("algocraft.gui.web", 38),
                Map.entry("algocraft.gui.close_short", 12),
                Map.entry("algocraft.gui.update_official", 122),
                Map.entry("algocraft.gui.repository.official", 122),
                Map.entry("algocraft.gui.repository.user", 122),
                Map.entry("algocraft.gui.repository.builtin", 122),
                Map.entry("algocraft.gui.repository.unknown", 122),
                Map.entry("algocraft.gui.import", 122),
                Map.entry("algocraft.gui.import.mode.remote", 192),
                Map.entry("algocraft.gui.import.mode.local", 192),
                Map.entry("algocraft.gui.import.mode.unknown", 192),
                Map.entry("algocraft.gui.import.do_import", 192),
                Map.entry("algocraft.gui.import.back", 192),
                Map.entry("algocraft.gui.back", 192),
                Map.entry("algocraft.gui.open_ingame_ide", 192),
                Map.entry("algocraft.gui.open_web_ide", 192)
        );

        for (Map.Entry<String, Integer> budget : budgets.entrySet()) {
            assertButtonTextFits(english, "en_us", budget.getKey(), budget.getValue());
            assertButtonTextFits(chinese, "zh_cn", budget.getKey(), budget.getValue());
        }
    }

    private static JsonObject readLang(String language) throws IOException {
        Path path = LANG_DIR.resolve(language + ".json");
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void assertTranslated(JsonObject lang, String language, String key) {
        assertTrue(lang.has(key), language + " is missing translation key " + key);
        String value = lang.get(key).getAsString();
        assertFalse(value.isBlank(), language + " translation is blank for " + key);
        assertFalse(value.equals(key), language + " translation should not fall back to the raw key " + key);
        assertNoMojibake(language, key, value);
    }

    private static void assertNoMojibake(String language, String key, String value) {
        for (String fragment : MOJIBAKE_FRAGMENTS) {
            assertFalse(value.contains(fragment),
                    language + " translation appears mojibaked for " + key + ": " + value);
        }
    }

    private static void assertButtonTextFits(JsonObject lang, String language, String key, int pixelBudget) {
        assertTranslated(lang, language, key);
        String value = lang.get(key).getAsString();
        int estimatedWidth = estimateMinecraftTextWidth(value);
        assertTrue(estimatedWidth <= pixelBudget,
                language + " button text for " + key + " is estimated at " + estimatedWidth
                        + "px, exceeding " + pixelBudget + "px: " + value);
    }

    private static int estimateMinecraftTextWidth(String value) {
        int width = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\u00A7' && i < value.length()) {
                i += Character.charCount(value.codePointAt(i));
                continue;
            }
            if (codePoint == ' ') {
                width += 4;
            } else if (isCjk(codePoint)) {
                width += 8;
            } else {
                width += 6;
            }
        }
        return width;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static List<String> formatPlaceholders(String value) {
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = FORMAT_PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    private static Set<String> collectSourceTranslationKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_DIR)) {
            for (Path file : files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = TRANSLATABLE_KEY.matcher(source);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (key.startsWith("algocraft.")) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }

    private static Set<String> collectWebFrontendTranslationKeys() throws IOException {
        String source = Files.readString(WEB_SERVER, StandardCharsets.UTF_8);
        Set<String> keys = new TreeSet<>();

        Matcher attributeMatcher = WEB_I18N_ATTRIBUTE.matcher(source);
        while (attributeMatcher.find()) {
            keys.add(attributeMatcher.group(1));
        }

        Matcher functionMatcher = WEB_I18N_FUNCTION.matcher(source);
        while (functionMatcher.find()) {
            keys.add(functionMatcher.group(1));
        }

        return keys;
    }

    private static String readSource(String relativePath) throws IOException {
        return Files.readString(SOURCE_DIR.resolve(relativePath.replace("/", java.io.File.separator)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static Set<String> registeredAchievementIds() throws IOException {
        String source = Files.readString(SOURCE_DIR.resolve("logic").resolve("AchievementRegistry.java"), StandardCharsets.UTF_8);
        Set<String> ids = new TreeSet<>();
        java.util.Map<String, String> constants = new java.util.HashMap<>();
        Matcher constantMatcher = STRING_CONSTANT.matcher(source);
        while (constantMatcher.find()) {
            constants.put(constantMatcher.group(1), constantMatcher.group(2));
        }

        Matcher registerMatcher = ACHIEVEMENT_REGISTER.matcher(source);
        while (registerMatcher.find()) {
            String id = constants.get(registerMatcher.group(1));
            if (id != null) {
                ids.add(id);
            }
        }
        assertFalse(ids.isEmpty(), "achievement registry source scan should find registered achievements");
        return ids;
    }

    private static Set<String> trophyTierItemIds() throws IOException {
        String source = Files.readString(SOURCE_DIR.resolve("item").resolve("TrophyItem.java"), StandardCharsets.UTF_8);
        Set<String> ids = new TreeSet<>();
        Matcher matcher = TROPHY_TIER_ITEM_ID.matcher(source);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        assertFalse(ids.isEmpty(), "trophy tier source scan should find trophy item ids");
        return ids;
    }

    private static int lineNumber(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
