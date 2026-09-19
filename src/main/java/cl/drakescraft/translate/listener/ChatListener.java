package cl.drakescraft.translate.listener;

import cl.drakescraft.translate.DrakesTranslate;
import cl.drakescraft.translate.config.PluginConfig;
import cl.drakescraft.translate.provider.TranslationResult;
import cl.drakescraft.translate.storage.StorageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChatListener implements Listener {

    private final DrakesTranslate plugin;
    private final PluginConfig config;
    private final StorageManager storageManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();

    public ChatListener(DrakesTranslate plugin, PluginConfig config, StorageManager storageManager) {
        this.plugin = plugin;
        this.config = config;
        this.storageManager = storageManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        // Fast-path / Circuit Breaker: Si Star está caído o no hay ningún motor de traducción disponible,
        // abortar de inmediato. NO tocar event.viewers(), garantizando que Paper envíe paquetes nativos
        // firmados de chat de jugador (ClientboundPlayerChatPacket) con 0 ms de lag y sin romper mods de clientes.
        if (!plugin.getProviderManager().isAnyAvailable()) {
            return;
        }

        Player sender = event.getPlayer();
        String originalMessage = plainSerializer.serialize(event.message()).trim();

        if (originalMessage.isEmpty() || originalMessage.length() > config.getMaxCharacters()) {
            return;
        }

        // Agrupar espectadores que tienen la traducción activada por idioma destino
        Map<String, List<Player>> languageGroups = new HashMap<>();
        Set<Audience> toRemove = new HashSet<>();

        for (Audience viewer : event.viewers()) {
            if (viewer instanceof Player recipient) {
                // El emisor siempre ve lo que escribió directamente (a menos que use /translate me)
                if (recipient.getUniqueId().equals(sender.getUniqueId())) {
                    continue;
                }
                if (storageManager.isEnabled(recipient.getUniqueId())) {
                    String targetLang = storageManager.getTargetLanguage(recipient.getUniqueId());
                    languageGroups.computeIfAbsent(targetLang.toLowerCase(), k -> new ArrayList<>()).add(recipient);
                    toRemove.add(recipient);
                }
            }
        }

        if (languageGroups.isEmpty()) {
            return;
        }

        // Excluir a los jugadores que recibirán la versión traducida para evitar duplicados
        event.viewers().removeAll(toRemove);

        // Obtener el formato original del render del evento
        Component originalRendered = event.renderer().render(sender, sender.displayName(), event.message(), sender);

        // Procesar cada idioma objetivo de forma asíncrona (1 petición por idioma, no por jugador)
        for (Map.Entry<String, List<Player>> entry : languageGroups.entrySet()) {
            String targetLang = entry.getKey();
            List<Player> recipients = entry.getValue();

            plugin.getProviderManager().translate(originalMessage, "auto", targetLang).thenAccept(result -> {
                if (result.success() && !result.translatedText().equalsIgnoreCase(originalMessage)) {
                    Component translatedComponent = buildTranslatedMessage(sender, result, originalMessage);
                    for (Player recipient : recipients) {
                        if (recipient.isOnline()) {
                            recipient.sendMessage(translatedComponent);
                        }
                    }
                } else {
                    // Fallback si no fue necesaria traducción o falló la API
                    for (Player recipient : recipients) {
                        if (recipient.isOnline()) {
                            recipient.sendMessage(originalRendered);
                        }
                    }
                }
            });
        }
    }

    private Component buildTranslatedMessage(Player sender, TranslationResult result, String originalText) {
        Component tag = Component.empty();
        if (config.isShowInlineTag()) {
            String tagFormatted = config.getTagFormat()
                    .replace("{source}", result.sourceLanguage().toUpperCase())
                    .replace("{target}", result.targetLanguage().toUpperCase());
            tag = miniMessage.deserialize(tagFormatted);
        }

        Component messageBody = Component.text(result.translatedText());

        if (config.isHoverShowOriginal()) {
            String hoverText = "<dark_gray><b>Traducción Automática:</b></dark_gray>\n" +
                    "<gray>Original (" + result.sourceLanguage() + "):</gray> <white>" + originalText + "</white>\n" +
                    "<dark_gray>Motor:</dark_gray> <aqua>" + result.providerName() + (result.fromCache() ? " (Caché)" : "") + "</aqua>";
            messageBody = messageBody.hoverEvent(HoverEvent.showText(miniMessage.deserialize(hoverText)));
        }

        return tag.append(sender.displayName()).append(Component.text(": ")).append(messageBody);
    }
}
