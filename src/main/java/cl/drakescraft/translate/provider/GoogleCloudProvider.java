package cl.drakescraft.translate.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class GoogleCloudProvider implements TranslationProvider {

    private final List<String> apiKeys;
    private final String rotationStrategy;
    private final int timeoutMillis;
    private final Logger logger;
    private final HttpClient httpClient;
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    public GoogleCloudProvider(List<String> apiKeys, String rotationStrategy, int timeoutMillis, Logger logger) {
        this.apiKeys = new ArrayList<>();
        if (apiKeys != null) {
            for (String k : apiKeys) {
                if (k != null && !k.trim().isEmpty() && !k.equals("\"\"")) {
                    this.apiKeys.add(k.trim());
                }
            }
        }
        this.rotationStrategy = rotationStrategy != null ? rotationStrategy.toLowerCase() : "round-robin";
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 4000;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMillis))
                .build();
    }

    @Override
    public String getName() {
        return "Google Cloud (" + apiKeys.size() + " keys)";
    }

    @Override
    public boolean isAvailable() {
        return !apiKeys.isEmpty();
    }

    private String getNextApiKey() {
        if (apiKeys.isEmpty()) return null;
        int idx = Math.abs(keyIndex.getAndIncrement() % apiKeys.size());
        return apiKeys.get(idx);
    }

    @Override
    public CompletableFuture<TranslationResult> translate(String text, String sourceLanguage, String targetLanguage) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(TranslationResult.failure(text, sourceLanguage, targetLanguage, "Google Cloud (No Keys)"));
        }

        String apiKey = getNextApiKey();
        String endpoint = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey;

        JsonObject requestBody = new JsonObject();
        JsonArray qArray = new JsonArray();
        qArray.add(text);
        requestBody.add("q", qArray);
        requestBody.addProperty("target", targetLanguage);
        requestBody.addProperty("format", "text");
        if (sourceLanguage != null && !sourceLanguage.isBlank() && !sourceLanguage.equalsIgnoreCase("auto")) {
            requestBody.addProperty("source", sourceLanguage);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("User-Agent", "DrakesCraft-DrakesTranslate/1.0")
                .timeout(Duration.ofMillis(timeoutMillis))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonObject data = root.getAsJsonObject("data");
                            JsonArray translations = data.getAsJsonArray("translations");
                            if (translations != null && !translations.isEmpty()) {
                                JsonObject first = translations.get(0).getAsJsonObject();
                                String translated = unescapeHtml(first.get("translatedText").getAsString());
                                String detected = first.has("detectedSourceLanguage") ? first.get("detectedSourceLanguage").getAsString() : sourceLanguage;
                                return new TranslationResult(text, translated, detected, targetLanguage, "Google Cloud", true, false);
                            }
                        } catch (Exception e) {
                            logger.warning("[DrakesTranslate] Error procesando JSON de Google Cloud: " + e.getMessage());
                        }
                    } else if (response.statusCode() == 429 || response.statusCode() == 403) {
                        logger.warning("[DrakesTranslate] Cuota excedida o clave inválida en Google Cloud (HTTP " + response.statusCode() + ").");
                    } else {
                        logger.warning("[DrakesTranslate] Google Cloud retornó HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return TranslationResult.failure(text, sourceLanguage, targetLanguage, "Google Cloud");
                })
                .exceptionally(ex -> {
                    logger.warning("[DrakesTranslate] Excepción conectando a Google Cloud: " + ex.getMessage());
                    return TranslationResult.failure(text, sourceLanguage, targetLanguage, "Google Cloud");
                });
    }

    private String unescapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
