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

        // Agrupar espectadores según su idioma preferido / efectivo
        Map<String, List<Player>> recipientsByTargetLang = new HashMap<>();
        Set<Audience> toRemove = new HashSet<>();

        for (Audience viewer : event.viewers()) {
            if (viewer instanceof Player recipient) {
                // El emisor siempre ve su mensaje original tal como lo escribió
                if (recipient.getUniqueId().equals(sender.getUniqueId())) {
                    continue;
                }
                String effectiveLang = storageManager.getEffectiveLanguage(recipient, config.isAutoDetectClientLocale());
                // Si el jugador desactivó la traducción con /translate off, effectiveLang es null -> ve mensaje original
                if (effectiveLang != null) {
                    recipientsByTargetLang.computeIfAbsent(effectiveLang.toLowerCase(), k -> new ArrayList<>()).add(recipient);
                    toRemove.add(recipient);
                }
            }
        }

        if (recipientsByTargetLang.isEmpty()) {
            return;
        }

        // Excluir a los jugadores que procesaremos manualmente para no enviar duplicados
        event.viewers().removeAll(toRemove);

        // Formato original renderizado por el servidor
        Component originalRendered = event.renderer().render(sender, sender.displayName(), event.message(), sender);

        // Determinar el idioma base estimado del emisor
        String resolvedSenderLang = storageManager.getEffectiveLanguage(sender, config.isAutoDetectClientLocale());
        final String senderLang = (resolvedSenderLang != null) ? resolvedSenderLang : "es";

        // Idioma objetivo primario cruzado:
        // Si el emisor es inglés, el objetivo primario es español ('es')
        // Si el emisor es español o cualquier otro, el objetivo primario es inglés ('en')
        String primaryTarget = "en".equalsIgnoreCase(senderLang) ? "es" : "en";

        plugin.getProviderManager().translate(originalMessage, "auto", primaryTarget).thenAccept(result -> {
            String detectedSource = (result.sourceLanguage() != null && !result.sourceLanguage().isBlank())
                    ? result.sourceLanguage().toLowerCase()
                    : senderLang;

            for (Map.Entry<String, List<Player>> entry : recipientsByTargetLang.entrySet()) {
                String targetLang = entry.getKey();
                List<Player> targetPlayers = entry.getValue();

                // Caso 1: El idioma del receptor coincide con el idioma en que fue escrito el mensaje
                // O el texto traducido es idéntico al original (ej. 'gg', 'lol', emojis)
                // -> Ven el mensaje original directamente sin tags ni latencia
                if (targetLang.equalsIgnoreCase(detectedSource) || result.translatedText().equalsIgnoreCase(originalMessage)) {
                    for (Player p : targetPlayers) {
                        if (p.isOnline()) {
                            p.sendMessage(originalRendered);
                        }
                    }
                }
                // Caso 2: El receptor habla el idioma objetivo primario que acabamos de traducir
                else if (targetLang.equalsIgnoreCase(primaryTarget)) {
                    if (result.success() && !result.translatedText().equalsIgnoreCase(originalMessage)) {
                        Component translatedComponent = buildTranslatedMessage(sender, result, originalMessage);
                        for (Player p : targetPlayers) {
                            if (p.isOnline()) {
                                p.sendMessage(translatedComponent);
                            }
                        }
                    } else {
                        for (Player p : targetPlayers) {
                            if (p.isOnline()) {
                                p.sendMessage(originalRendered);
                            }
                        }
                    }
                }
                // Caso 3: Idioma secundario distinto al primario (ej. receptor portugués o francés)
                else {
                    plugin.getProviderManager().translate(originalMessage, detectedSource, targetLang).thenAccept(secRes -> {
                        if (secRes.success() && !secRes.translatedText().equalsIgnoreCase(originalMessage)) {
                            Component translatedComponent = buildTranslatedMessage(sender, secRes, originalMessage);
                            for (Player p : targetPlayers) {
                                if (p.isOnline()) {
                                    p.sendMessage(translatedComponent);
                                }
                            }
                        } else {
                            for (Player p : targetPlayers) {
                                if (p.isOnline()) {
                                    p.sendMessage(originalRendered);
                                }
                            }
                        }
                    });
                }
            }
        });
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
