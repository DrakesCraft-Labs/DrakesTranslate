package cl.drakescraft.translate.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class LibreTranslateProvider implements TranslationProvider {

    private final String url;
    private final String apiKey;
    private final int timeoutMillis;
    private final Logger logger;
    private final HttpClient httpClient;
    // Un fallo de credenciales afecta a cada mensaje de chat: sin estrangular, un solo
    // problema persistente escribe decenas de WARN identicos por hora (ticket 474).
    private static final long INTERVALO_AVISO_MILLIS = 60_000L;
    private final AtomicLong ultimoAviso = new AtomicLong(0L);
    private final AtomicInteger avisosOmitidos = new AtomicInteger(0);

    public LibreTranslateProvider(String url, String apiKey, int timeoutMillis, Logger logger) {
        this.url = (url != null && !url.isBlank()) ? url : "https://translate.drakescraft.cl/translate";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 5000;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMillis))
                .build();
    }

    @Override
    public String getName() {
        return "LibreTranslate (Local Star)";
    }

    @Override
    public boolean isAvailable() {
        return url != null && !url.isBlank();
    }

    @Override
    public CompletableFuture<TranslationResult> translate(String text, String sourceLanguage, String targetLanguage) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("q", text);
        requestBody.addProperty("source", (sourceLanguage != null && !sourceLanguage.isBlank()) ? sourceLanguage : "auto");
        requestBody.addProperty("target", targetLanguage);
        requestBody.addProperty("format", "text");
        if (!apiKey.isEmpty()) {
            requestBody.addProperty("api_key", apiKey);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
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
                            String translated = root.get("translatedText").getAsString();
                            String detected = sourceLanguage;
                            if (root.has("detectedLanguage") && root.get("detectedLanguage").isJsonObject()) {
                                detected = root.getAsJsonObject("detectedLanguage").get("language").getAsString();
                            }
                            return new TranslationResult(text, translated, detected, targetLanguage, "LibreTranslate", true, false);
                        } catch (Exception e) {
                            logger.warning("[DrakesTranslate] Error parseando JSON de LibreTranslate: " + e.getMessage());
                        }
                    } else {
                        avisarEstrangulado("LibreTranslate HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return TranslationResult.failure(text, sourceLanguage, targetLanguage, "LibreTranslate");
                })
                .exceptionally(ex -> {
                    avisarEstrangulado("Excepción conectando a LibreTranslate: " + ex.getMessage());
                    return TranslationResult.failure(text, sourceLanguage, targetLanguage, "LibreTranslate");
                });
    }

    private void avisarEstrangulado(String mensaje) {
        long ahora = System.currentTimeMillis();
        long previo = ultimoAviso.get();
        if (ahora - previo < INTERVALO_AVISO_MILLIS || !ultimoAviso.compareAndSet(previo, ahora)) {
            avisosOmitidos.incrementAndGet();
            return;
        }
        int omitidos = avisosOmitidos.getAndSet(0);
        logger.warning("[DrakesTranslate] " + mensaje
                + (omitidos > 0 ? " (" + omitidos + " avisos identicos omitidos en el ultimo minuto)" : ""));
    }
}
