package cl.drakescraft.translate;

import cl.drakescraft.translate.cache.TranslationCache;
import cl.drakescraft.translate.command.TranslateCommand;
import cl.drakescraft.translate.config.PluginConfig;
import cl.drakescraft.translate.listener.ChatListener;
import cl.drakescraft.translate.provider.GoogleCloudProvider;
import cl.drakescraft.translate.provider.LibreTranslateProvider;
import cl.drakescraft.translate.provider.MultiProviderManager;
import cl.drakescraft.translate.storage.StorageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DrakesTranslate extends JavaPlugin {

    private PluginConfig pluginConfig;
    private StorageManager storageManager;
    private TranslationCache cache;
    private MultiProviderManager providerManager;

    @Override
    public void onEnable() {
        getLogger().info("Iniciando DrakesTranslate v" + getPluginMeta().getVersion() + " (Paper 1.21.1)...");

        // 1. Cargar Configuración
        this.pluginConfig = new PluginConfig(this);

        // 2. Almacenamiento de Preferencias
        this.storageManager = new StorageManager(this);

        // 3. Inicializar Caché en Memoria
        this.cache = new TranslationCache(
                pluginConfig.isCacheEnabled(),
                pluginConfig.getCacheMaxEntries(),
                pluginConfig.getCacheExpireHours()
        );

        // 4. Inicializar Proveedores
        GoogleCloudProvider google = new GoogleCloudProvider(
                pluginConfig.getGoogleApiKeys(),
                pluginConfig.getGoogleRotationStrategy(),
                pluginConfig.getGoogleTimeout(),
                getLogger()
        );

        LibreTranslateProvider libre = new LibreTranslateProvider(
                pluginConfig.getLibreTranslateUrl(),
                pluginConfig.getLibreTranslateKey(),
                pluginConfig.getLibreTranslateTimeout(),
                getLogger()
        );

        this.providerManager = new MultiProviderManager(
                pluginConfig.getProviderMode(),
                google,
                libre,
                cache,
                getLogger()
        );

        // 5. Registrar Eventos
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, pluginConfig, storageManager, providerManager),
                this
        );

        // 6. Registrar Comandos
        TranslateCommand translateCmd = new TranslateCommand(this, pluginConfig, storageManager, providerManager);
        PluginCommand cmd = getCommand("translate");
        if (cmd != null) {
            cmd.setExecutor(translateCmd);
            cmd.setTabCompleter(translateCmd);
        }

        getLogger().info("DrakesTranslate iniciado con éxito. Motor activo: " + providerManager.getActiveEngineName());
    }

    @Override
    public void onDisable() {
        getLogger().info("Guardando preferencias de jugadores...");
        if (storageManager != null) {
            storageManager.save();
        }
        if (cache != null) {
            cache.clear();
        }
        getLogger().info("DrakesTranslate desactivado limpiamente.");
    }

    public void reloadPlugin() {
        pluginConfig.load();
        if (storageManager != null) {
            storageManager.load();
        }

        GoogleCloudProvider google = new GoogleCloudProvider(
                pluginConfig.getGoogleApiKeys(),
                pluginConfig.getGoogleRotationStrategy(),
                pluginConfig.getGoogleTimeout(),
                getLogger()
        );

        LibreTranslateProvider libre = new LibreTranslateProvider(
                pluginConfig.getLibreTranslateUrl(),
                pluginConfig.getLibreTranslateKey(),
                pluginConfig.getLibreTranslateTimeout(),
                getLogger()
        );

        this.providerManager = new MultiProviderManager(
                pluginConfig.getProviderMode(),
                google,
                libre,
                cache,
                getLogger()
        );

        getLogger().info("DrakesTranslate recargado. Motor activo: " + providerManager.getActiveEngineName());
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public StorageManager getStorageManager() { return storageManager; }
    public MultiProviderManager getProviderManager() { return providerManager; }
}
