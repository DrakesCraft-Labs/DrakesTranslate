package cl.drakescraft.translate.cache;

import cl.drakescraft.translate.provider.TranslationResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TranslationCache {

    private final Map<String, CacheEntry> cache;
    private final int maxEntries;
    private final long ttlMillis;
    private final boolean enabled;

    private record CacheEntry(TranslationResult result, long timestamp) {}

    public TranslationCache(boolean enabled, int maxEntries, long ttlHours) {
        this.enabled = enabled;
        this.maxEntries = maxEntries;
        this.ttlMillis = ttlHours * 3600 * 1000L;

        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxEntries;
            }
        });
    }

    private String buildKey(String text, String targetLanguage) {
        return text.trim() + "::" + targetLanguage.toLowerCase();
    }

    public TranslationResult get(String text, String targetLanguage) {
        if (!enabled || text == null || targetLanguage == null) return null;
        String key = buildKey(text, targetLanguage);
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;

        if (System.currentTimeMillis() - entry.timestamp() > ttlMillis) {
            cache.remove(key);
            return null;
        }

        TranslationResult original = entry.result();
        return new TranslationResult(
                original.originalText(),
                original.translatedText(),
                original.sourceLanguage(),
                original.targetLanguage(),
                original.providerName(),
                original.success(),
                true // Marcar como obtenido de caché
        );
    }

    public void put(String text, String targetLanguage, TranslationResult result) {
        if (!enabled || text == null || targetLanguage == null || result == null || !result.success()) return;
        String key = buildKey(text, targetLanguage);
        cache.put(key, new CacheEntry(result, System.currentTimeMillis()));
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }
}
