package cl.drakescraft.translate.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class StorageManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Logger logger;
    private final Map<UUID, PlayerPreference> preferences = new ConcurrentHashMap<>();

    public record PlayerPreference(boolean enabled, String targetLanguage) {}

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public synchronized void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                boolean enabled = config.getBoolean(key + ".enabled", false);
                String lang = config.getString(key + ".target", "es");
                preferences.put(uuid, new PlayerPreference(enabled, lang));
            } catch (Exception ignored) {}
        }
    }

    public synchronized void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerPreference> entry : preferences.entrySet()) {
            String key = entry.getKey().toString();
            config.set(key + ".enabled", entry.getValue().enabled());
            config.set(key + ".target", entry.getValue().targetLanguage());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("[DrakesTranslate] Error guardando players.yml: " + e.getMessage());
        }
    }

    public PlayerPreference getPreference(UUID uuid) {
        return preferences.getOrDefault(uuid, new PlayerPreference(false, "es"));
    }

    public void setPreference(UUID uuid, boolean enabled, String targetLanguage) {
        preferences.put(uuid, new PlayerPreference(enabled, targetLanguage));
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> save());
    }

    public boolean isEnabled(UUID uuid) {
        return getPreference(uuid).enabled();
    }

    public String getTargetLanguage(UUID uuid) {
        return getPreference(uuid).targetLanguage();
    }
}
