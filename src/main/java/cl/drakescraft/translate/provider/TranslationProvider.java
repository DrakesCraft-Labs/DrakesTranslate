package cl.drakescraft.translate.provider;

import java.util.concurrent.CompletableFuture;

public interface TranslationProvider {
    String getName();
    boolean isAvailable();
    CompletableFuture<TranslationResult> translate(String text, String sourceLanguage, String targetLanguage);
}
