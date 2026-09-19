package cl.drakescraft.translate.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // Circuit Breaker: Desactiva el proveedor si Star está caído para no laggear ni degradar el chat a mensajes del sistema
    private final AtomicBoolean circuitBreakerOpen = new AtomicBoolean(false);
    private final AtomicLong circuitBreakerResetTime = new AtomicLong(0L);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 2;
    private static final long COOLDOWN_MS = 60_000L;

    public LibreTranslateProvider(String url, String apiKey, int timeoutMillis, Logger logger) {
        this.url = (url != null && !url.isBlank()) ? url : "https://translate.drakescraft.cl/translate";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 2000;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMillis))
                .build();

        // Sondear disponibilidad en segundo plano al iniciar
        probeHealthAsync();
    }

    public void probeHealthAsync() {
        try {
            String probeUrl = url.endsWith("/translate") ? url.substring(0, url.lastIndexOf("/translate")) + "/languages" : url;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(probeUrl))
                    .header("User-Agent", "DrakesCraft-DrakesTranslate/1.0")
                    .timeout(Duration.ofMillis(Math.min(timeoutMillis, 2000)))
                    .GET()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(res -> {
                        if (res.statusCode() == 200) {
                            recordSuccess();
                        } else {
                            recordFailure("Sondeo inicial Star HTTP " + res.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        recordFailure("Star no responde al inicio (" + ex.getMessage() + ")");
                        return null;
                    });
        } catch (Exception ignored) {}
    }

    @Override
    public String getName() {
        return "LibreTranslate (Local Star)";
    }

    @Override
    public boolean isAvailable() {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (circuitBreakerOpen.get()) {
            long now = System.currentTimeMillis();
            if (now < circuitBreakerResetTime.get()) {
                return false;
            }
            // Período de enfriamiento cumplido: permitir sondeo
            circuitBreakerOpen.set(false);
        }
        return true;
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreakerOpen.get() && (System.currentTimeMillis() < circuitBreakerResetTime.get());
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        circuitBreakerOpen.set(false);
    }

    private void recordFailure(String errorDetail) {
        int fails = consecutiveFailures.incrementAndGet();
        if (fails >= FAILURE_THRESHOLD) {
            circuitBreakerOpen.set(true);
            circuitBreakerResetTime.set(System.currentTimeMillis() + COOLDOWN_MS);
            avisarEstrangulado("Circuit Breaker activado (" + (COOLDOWN_MS / 1000) + "s apagado) tras fallos de conexión: " + errorDetail);
        } else {
            avisarEstrangulado("Error en LibreTranslate: " + errorDetail);
        }
    }

    @Override
    public CompletableFuture<TranslationResult> translate(String text, String sourceLanguage, String targetLanguage) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(TranslationResult.failure(text, sourceLanguage, targetLanguage, "LibreTranslate (Offline)"));
        }

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
                            recordSuccess();
                            return new TranslationResult(text, translated, detected, targetLanguage, "LibreTranslate", true, false);
                        } catch (Exception e) {
                            logger.warning("[DrakesTranslate] Error parseando JSON de LibreTranslate: " + e.getMessage());
                        }
                    } else {
                        recordFailure("HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return TranslationResult.failure(text, sourceLanguage, targetLanguage, "LibreTranslate");
                })
                .exceptionally(ex -> {
                    recordFailure("Excepción: " + ex.getMessage());
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
