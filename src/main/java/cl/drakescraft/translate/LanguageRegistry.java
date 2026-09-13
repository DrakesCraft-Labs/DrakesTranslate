package cl.drakescraft.translate;

import java.util.*;

public final class LanguageRegistry {

    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("es", "Español");
        LANGUAGES.put("en", "English");
        LANGUAGES.put("pt", "Português");
        LANGUAGES.put("fr", "Français");
        LANGUAGES.put("de", "Deutsch");
        LANGUAGES.put("it", "Italiano");
        LANGUAGES.put("ru", "Русский");
        LANGUAGES.put("ja", "日本語");
        LANGUAGES.put("zh", "中文");
        LANGUAGES.put("ko", "한국어");
        LANGUAGES.put("pl", "Polski");
        LANGUAGES.put("nl", "Nederlands");
        LANGUAGES.put("tr", "Türkçe");
        LANGUAGES.put("uk", "Українська");
        LANGUAGES.put("vi", "Tiếng Việt");
        LANGUAGES.put("ar", "العربية");
        LANGUAGES.put("hi", "हिन्दी");
        LANGUAGES.put("sv", "Svenska");
        LANGUAGES.put("cs", "Čeština");
        LANGUAGES.put("ro", "Română");
        LANGUAGES.put("hu", "Magyar");
        LANGUAGES.put("el", "Ελληνικά");
        LANGUAGES.put("da", "Dansk");
        LANGUAGES.put("fi", "Suomi");
        LANGUAGES.put("no", "Norsk");
        LANGUAGES.put("id", "Bahasa Indonesia");
        LANGUAGES.put("th", "ไทย");
        LANGUAGES.put("ca", "Català");
        LANGUAGES.put("eu", "Euskara");
        LANGUAGES.put("gl", "Galego");
        LANGUAGES.put("sk", "Slovenčina");
        LANGUAGES.put("bg", "Български");
        LANGUAGES.put("he", "עברית");
        LANGUAGES.put("fa", "فارسی");
        LANGUAGES.put("ms", "Bahasa Melayu");
        LANGUAGES.put("tl", "Tagalog");
    }

    private LanguageRegistry() {}

    public static boolean isValid(String code) {
        if (code == null) return false;
        return LANGUAGES.containsKey(code.toLowerCase(Locale.ROOT));
    }

    public static String getName(String code) {
        if (code == null) return "Desconocido";
        return LANGUAGES.getOrDefault(code.toLowerCase(Locale.ROOT), code.toUpperCase(Locale.ROOT));
    }

    public static Set<String> getAllCodes() {
        return Collections.unmodifiableSet(LANGUAGES.keySet());
    }

    public static List<String> getSuggestions(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return new ArrayList<>(LANGUAGES.keySet());
        }
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : LANGUAGES.entrySet()) {
            if (entry.getKey().startsWith(p) || entry.getValue().toLowerCase(Locale.ROOT).startsWith(p)) {
                list.add(entry.getKey());
            }
        }
        return list;
    }
}
