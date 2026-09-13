package cl.drakescraft.translate.provider;

public record TranslationResult(
        String originalText,
        String translatedText,
        String sourceLanguage,
        String targetLanguage,
        String providerName,
        boolean success,
        boolean fromCache
) {
    public static TranslationResult failure(String originalText, String sourceLanguage, String targetLanguage, String providerName) {
        return new TranslationResult(originalText, originalText, sourceLanguage, targetLanguage, providerName, false, false);
    }
}
