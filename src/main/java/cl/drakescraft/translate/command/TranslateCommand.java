package cl.drakescraft.translate.command;

import cl.drakescraft.translate.DrakesTranslate;
import cl.drakescraft.translate.LanguageRegistry;
import cl.drakescraft.translate.config.PluginConfig;
import cl.drakescraft.translate.storage.StorageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TranslateCommand implements CommandExecutor, TabCompleter {

    private final DrakesTranslate plugin;
    private final PluginConfig config;
    private final StorageManager storageManager;

    public TranslateCommand(DrakesTranslate plugin, PluginConfig config, StorageManager storageManager) {
        this.plugin = plugin;
        this.config = config;
        this.storageManager = storageManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(config.getRawMessage(config.getHelpMsg()));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Subcomando: RELOAD (Requiere permiso staff)
        if (sub.equals("reload")) {
            if (!sender.hasPermission("drakestranslate.admin")) {
                sender.sendMessage(config.getMessage(config.getNoPermissionMsg()));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(config.getMessage(config.getReloadedMsg()));
            return true;
        }

        // Todos los demás subcomandos requieren ser jugador
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando solo puede ser ejecutado por jugadores en el servidor."));
            return true;
        }

        switch (sub) {
            case "on" -> {
                if (args.length < 2) {
                    player.sendMessage(config.getMessage("<yellow>Uso correcto: <white>/translate on <idioma></white> (Ej: <white>/translate on en</white>)</yellow>"));
                    return true;
                }
                String lang = args[1].toLowerCase(Locale.ROOT);
                if (!LanguageRegistry.isValid(lang)) {
                    player.sendMessage(config.getMessage(config.getInvalidLangMsg(), "input", lang));
                    return true;
                }
                storageManager.setPreference(player.getUniqueId(), true, lang);
                player.sendMessage(config.getMessage(config.getEnabledMsg(), "lang_name", LanguageRegistry.getName(lang), "lang_code", lang.toUpperCase()));
            }
            case "off" -> {
                String currentLang = storageManager.getTargetLanguage(player.getUniqueId());
                storageManager.setPreference(player.getUniqueId(), false, currentLang);
                player.sendMessage(config.getMessage(config.getDisabledMsg()));
            }
            case "toggle" -> {
                boolean isEnabled = storageManager.isEnabled(player.getUniqueId());
                String currentLang = storageManager.getTargetLanguage(player.getUniqueId());
                boolean newState = !isEnabled;
                storageManager.setPreference(player.getUniqueId(), newState, currentLang);
                if (newState) {
                    player.sendMessage(config.getMessage(config.getEnabledMsg(), "lang_name", LanguageRegistry.getName(currentLang), "lang_code", currentLang.toUpperCase()));
                } else {
                    player.sendMessage(config.getMessage(config.getDisabledMsg()));
                }
            }
            case "status", "info" -> {
                boolean hasExplicit = storageManager.hasExplicitPreference(player.getUniqueId());
                String effectiveLang = storageManager.getEffectiveLanguage(player, config.isAutoDetectClientLocale());
                boolean isEnabled = effectiveLang != null;
                String currentLang = effectiveLang != null ? effectiveLang : storageManager.getTargetLanguage(player.getUniqueId());
                String modeExtra = hasExplicit ? "" : " <gray>(Auto-detectado de cliente)</gray>";
                String stateMsg = isEnabled
                        ? config.getStatusOnMsg().replace("{lang_name}", LanguageRegistry.getName(currentLang)).replace("{lang_code}", currentLang.toUpperCase()) + modeExtra
                        : config.getStatusOffMsg();
                player.sendMessage(config.getMessage(stateMsg));
                player.sendMessage(config.getMessage(config.getStatusEngineMsg(), "engine", plugin.getProviderManager().getActiveEngineName(), "cache_size", String.valueOf(plugin.getProviderManager().getCache().size())));
            }
            case "me" -> {
                if (args.length < 3) {
                    player.sendMessage(config.getMessage("<yellow>Uso correcto: <white>/translate me <idioma> <mensaje></white></yellow>"));
                    return true;
                }
                String targetLang = args[1].toLowerCase(Locale.ROOT);
                if (!LanguageRegistry.isValid(targetLang)) {
                    player.sendMessage(config.getMessage(config.getInvalidLangMsg(), "input", targetLang));
                    return true;
                }
                String msgToTranslate = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                player.sendMessage(config.getMessage("<gray>Traduciendo y enviando mensaje...</gray>"));

                plugin.getProviderManager().translate(msgToTranslate, "auto", targetLang).thenAccept(result -> {
                    if (result.success()) {
                        String broadcastFormat = "<dark_gray>[<aqua>" + result.sourceLanguage().toUpperCase() + "</aqua> → <green>" + result.targetLanguage().toUpperCase() + "</green>]</dark_gray> " +
                                "<white>" + player.getName() + "</white><gray>: </gray><white>" + result.translatedText() + "</white>";
                        plugin.getServer().broadcast(config.parse(broadcastFormat));
                    } else {
                        player.sendMessage(config.getMessage("<red>Error al traducir tu mensaje saliente. Inténtalo de nuevo.</red>"));
                    }
                });
            }
            default -> player.sendMessage(config.getRawMessage(config.getHelpMsg()));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("on", "off", "toggle", "me", "status", "help"));
            if (sender.hasPermission("drakestranslate.admin")) {
                subs.add("reload");
            }
            String p = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(p)).toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("me"))) {
            return LanguageRegistry.getSuggestions(args[1]);
        }

        return List.of();
    }
}
