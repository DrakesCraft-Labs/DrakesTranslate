package cl.drakescraft.translate.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PluginConfig {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private String providerMode;
    private List<String> googleApiKeys;
    private String googleRotationStrategy;
    private int googleTimeout;
    private String libreTranslateUrl;
    private String libreTranslateKey;
    private int libreTranslateTimeout;
    private boolean cacheEnabled;
    private int cacheMaxEntries;
    private int cacheExpireHours;
    private int maxCharacters;
    private boolean hoverShowOriginal;
    private boolean showInlineTag;
    private boolean autoDetectClientLocale;
    private String tagFormat;

    private String prefix;
    private String enabledMsg;
    private String disabledMsg;
    private String statusOnMsg;
    private String statusOffMsg;
    private String statusEngineMsg;
    private String invalidLangMsg;
    private String reloadedMsg;
    private String noPermissionMsg;
    private String helpMsg;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        this.providerMode = c.getString("provider", "auto");
        this.googleApiKeys = c.getStringList("google.api-keys");
        this.googleRotationStrategy = c.getString("google.rotation-strategy", "round-robin");
        this.googleTimeout = c.getInt("google.timeout-millis", 4000);

        this.libreTranslateUrl = c.getString("libretranslate.url", "https://translate.drakescraft.cl/translate");
        this.libreTranslateKey = c.getString("libretranslate.api-key", "");
        this.libreTranslateTimeout = c.getInt("libretranslate.timeout-millis", 5000);

        this.cacheEnabled = c.getBoolean("cache.enabled", true);
        this.cacheMaxEntries = c.getInt("cache.max-entries", 10000);
        this.cacheExpireHours = c.getInt("cache.expire-after-hours", 24);

        this.maxCharacters = c.getInt("chat.max-characters", 256);
        this.hoverShowOriginal = c.getBoolean("chat.hover-show-original", true);
        this.showInlineTag = c.getBoolean("chat.show-inline-tag", true);
        this.autoDetectClientLocale = c.getBoolean("chat.auto-detect-client-locale", true);
        this.tagFormat = c.getString("chat.tag-format", "<dark_gray>[<aqua>{source}</aqua> <gray>→</gray> <green>{target}</green>]</dark_gray> ");

        this.prefix = c.getString("messages.prefix", "<gradient:#9B59B6:#3498DB><b>Traductor</b></gradient> <dark_gray>»</dark_gray> ");
        this.enabledMsg = c.getString("messages.enabled", "<green>Traducción activada hacia el idioma: <yellow><b>{lang_name}</b> ({lang_code})</yellow>.</green>");
        this.disabledMsg = c.getString("messages.disabled", "<red>Traducción de chat desactivada.</red>");
        this.statusOnMsg = c.getString("messages.status-on", "<gray>Estado:</gray> <green>Activado</green> <gray>| Idioma destino:</gray> <yellow><b>{lang_name}</b> ({lang_code})</yellow>");
        this.statusOffMsg = c.getString("messages.status-off", "<gray>Estado:</gray> <red>Desactivado</red>");
        this.statusEngineMsg = c.getString("messages.status-engine", "<gray>Motor activo:</gray> <aqua>{engine}</aqua> <gray>| En caché:</gray> <aqua>{cache_size}</aqua> <gray>frases</gray>");
        this.invalidLangMsg = c.getString("messages.invalid-lang", "<red>Idioma desconocido: <yellow>{input}</yellow>.</red>");
        this.reloadedMsg = c.getString("messages.reloaded", "<green>Configuración de DrakesTranslate recargada exitosamente.</green>");
        this.noPermissionMsg = c.getString("messages.no-permission", "<red>No tienes permisos para ejecutar este comando.</red>");
        this.helpMsg = c.getString("messages.help", "Guía de DrakesTranslate");
    }

    public Component parse(String rawText, Placeholder... placeholders) {
        return miniMessage.deserialize(rawText);
    }

    public Component getMessage(String template, String... replacements) {
        String res = prefix + template;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            res = res.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return miniMessage.deserialize(res);
    }

    public Component getRawMessage(String template, String... replacements) {
        String res = template;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            res = res.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return miniMessage.deserialize(res);
    }

    public String getProviderMode() { return providerMode; }
    public List<String> getGoogleApiKeys() { return googleApiKeys; }
    public String getGoogleRotationStrategy() { return googleRotationStrategy; }
    public int getGoogleTimeout() { return googleTimeout; }
    public String getLibreTranslateUrl() { return libreTranslateUrl; }
    public String getLibreTranslateKey() { return libreTranslateKey; }
    public int getLibreTranslateTimeout() { return libreTranslateTimeout; }
    public boolean isCacheEnabled() { return cacheEnabled; }
    public int getCacheMaxEntries() { return cacheMaxEntries; }
    public int getCacheExpireHours() { return cacheExpireHours; }
    public int getMaxCharacters() { return maxCharacters; }
    public boolean isHoverShowOriginal() { return hoverShowOriginal; }
    public boolean isShowInlineTag() { return showInlineTag; }
    public boolean isAutoDetectClientLocale() { return autoDetectClientLocale; }
    public String getTagFormat() { return tagFormat; }

    public String getEnabledMsg() { return enabledMsg; }
    public String getDisabledMsg() { return disabledMsg; }
    public String getStatusOnMsg() { return statusOnMsg; }
    public String getStatusOffMsg() { return statusOffMsg; }
    public String getStatusEngineMsg() { return statusEngineMsg; }
    public String getInvalidLangMsg() { return invalidLangMsg; }
    public String getReloadedMsg() { return reloadedMsg; }
    public String getNoPermissionMsg() { return noPermissionMsg; }
    public String getHelpMsg() { return helpMsg; }
}
