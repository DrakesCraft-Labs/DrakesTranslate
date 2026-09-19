package cl.drakescraft.translate.provider;

import cl.drakescraft.translate.cache.TranslationCache;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class MultiProviderManager {

    private final String mode;
    private final GoogleCloudProvider googleProvider;
    private final LibreTranslateProvider libreTranslateProvider;
    private final TranslationCache cache;
    private final Logger logger;

    public MultiProviderManager(String mode, GoogleCloudProvider googleProvider, LibreTranslateProvider libreTranslateProvider, TranslationCache cache, Logger logger) {
        this.mode = mode != null ? mode.toLowerCase() : "auto";
        this.googleProvider = googleProvider;
        this.libreTranslateProvider = libreTranslateProvider;
        this.cache = cache;
        this.logger = logger;
    }

    public CompletableFuture<TranslationResult> translate(String text, String sourceLanguage, String targetLanguage) {
        if (text == null || text.isBlank() || targetLanguage == null) {
            return CompletableFuture.completedFuture(TranslationResult.failure(text, sourceLanguage, targetLanguage, "None"));
        }

        // 1. Revisar Caché en RAM
        TranslationResult cached = cache.get(text, targetLanguage);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // 2. Determinar proveedor primario y secundario
        TranslationProvider primary;
        TranslationProvider fallback;

        if (mode.equals("google")) {
            primary = googleProvider;
            fallback = libreTranslateProvider;
        } else if (mode.equals("libretranslate")) {
            primary = libreTranslateProvider;
            fallback = googleProvider;
        } else { // auto
            primary = googleProvider.isAvailable() ? googleProvider : libreTranslateProvider;
            fallback = primary == googleProvider ? libreTranslateProvider : googleProvider;
        }

        return primary.translate(text, sourceLanguage, targetLanguage)
                .thenCompose(result -> {
                    if (result.success()) {
                        cache.put(text, targetLanguage, result);
                        return CompletableFuture.completedFuture(result);
                    }
                    // Si el primario falló y hay un fallback disponible, conmutar
                    if (fallback != null && fallback.isAvailable()) {
                        return fallback.translate(text, sourceLanguage, targetLanguage)
                                .thenApply(fallbackResult -> {
                                    if (fallbackResult.success()) {
                                        cache.put(text, targetLanguage, fallbackResult);
                                    }
                                    return fallbackResult;
                                });
                    }
                    return CompletableFuture.completedFuture(result);
                });
    }

    public boolean isAnyAvailable() {
        return (googleProvider != null && googleProvider.isAvailable())
                || (libreTranslateProvider != null && libreTranslateProvider.isAvailable());
    }

    public String getActiveEngineName() {
        if (googleProvider != null && googleProvider.isAvailable()) {
            return googleProvider.getName();
        }
        if (libreTranslateProvider != null && libreTranslateProvider.isAvailable()) {
            return libreTranslateProvider.getName();
        }
        return "Ninguno (Desconectado / Modo Seguro)";
    }

    public TranslationCache getCache() {
        return cache;
    }
}
