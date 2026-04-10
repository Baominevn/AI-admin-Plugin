package me.aiadmin;

import me.aiadmin.ai.AIChat;
import me.aiadmin.ai.OpenAIService;
import me.aiadmin.command.AdminCommand;
import me.aiadmin.listener.ClickListener;
import me.aiadmin.listener.CombatListener;
import me.aiadmin.listener.ChatListener;
import me.aiadmin.listener.JoinListener;
import me.aiadmin.listener.MovementListener;
import me.aiadmin.listener.QuitListener;
import me.aiadmin.placeholder.AIAdminPlaceholderExpansion;
import me.aiadmin.system.AntiCheatConsoleListener;
import me.aiadmin.system.BotManager;
import me.aiadmin.system.DatabaseManager;
import me.aiadmin.system.LagOptimizer;
import me.aiadmin.system.LearningManager;
import me.aiadmin.system.ServerScanner;
import me.aiadmin.system.StatsManager;
import me.aiadmin.system.SuspicionManager;
import me.aiadmin.ui.SuspicionDashboard;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AIAdmin extends JavaPlugin {

    public enum ConfigProfile {
        ENGLISH("english"),
        VIETNAMESE("vietnamese");

        private final String folderName;

        ConfigProfile(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }

        public static ConfigProfile fromInput(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            String normalized = input.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("en") || normalized.equals("eng") || normalized.equals("english")) {
                return ENGLISH;
            }
            if (normalized.equals("vi") || normalized.equals("vn") || normalized.equals("vietnam") || normalized.equals("vietnamese")) {
                return VIETNAMESE;
            }
            return null;
        }
    }

    private static AIAdmin instance;

    private static final String[] LOCALIZED_RESOURCES = new String[]{
            "config.yml",
            "aichat.yml",
            "option.yml",
            "setting_plugin.yml",
            "liteban.yml",
            "rule.yml",
            "database.yml",
            "learning.yml",
            "bot/bot.yml",
            "bot/bot_body.yml",
            "bot/bot_rule.yml"
    };

    private SuspicionManager suspicionManager;
    private OpenAIService openAIService;
    private AIChat aiChat;
    private ServerScanner serverScanner;
    private AntiCheatConsoleListener antiCheatConsoleListener;
    private BotManager botManager;
    private boolean adminModeEnabled;
    private FileConfiguration optionConfig;
    private FileConfiguration pluginSettingsConfig;
    private FileConfiguration litebanConfig;
    private FileConfiguration ruleConfig;
    private FileConfiguration botRuleConfig;
    private DatabaseManager databaseManager;
    private LearningManager learningManager;
    private StatsManager statsManager;
    private SuspicionDashboard suspicionDashboard;
    private LagOptimizer lagOptimizer;
    private FileConfiguration mainConfig;
    private File languagePreferenceFile;
    private FileConfiguration languagePreferenceConfig;
    private final Map<UUID, ConfigProfile> playerLanguages = new ConcurrentHashMap<>();

    public static AIAdmin getInstance() {
        return instance;
    }

    public SuspicionManager getSuspicionManager() {
        return suspicionManager;
    }

    public AIChat getAiChat() {
        return aiChat;
    }

    public OpenAIService getOpenAIService() {
        return openAIService;
    }

    public ServerScanner getServerScanner() {
        return serverScanner;
    }

    public BotManager getBotManager() {
        return botManager;
    }

    public FileConfiguration getOptionConfig() {
        return optionConfig;
    }

    public FileConfiguration getPluginSettingsConfig() {
        return pluginSettingsConfig;
    }

    public FileConfiguration getLitebanConfig() {
        return litebanConfig;
    }

    public FileConfiguration getRuleConfig() {
        return ruleConfig;
    }

    public FileConfiguration getBotRuleConfig() {
        return botRuleConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LearningManager getLearningManager() {
        return learningManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public SuspicionDashboard getSuspicionDashboard() {
        return suspicionDashboard;
    }

    public LagOptimizer getLagOptimizer() {
        return lagOptimizer;
    }

    @Override
    public FileConfiguration getConfig() {
        return mainConfig != null ? mainConfig : super.getConfig();
    }

    @Override
    public void reloadConfig() {
        mainConfig = loadActiveConfiguration("config.yml", "config.yml");
    }

    public void reloadOptionConfig() {
        optionConfig = loadActiveConfiguration("option.yml", "option.yml");
    }

    public void reloadPluginSettingsConfig() {
        pluginSettingsConfig = loadActiveConfiguration("setting_plugin.yml", "setting_plugin.yml");
    }

    public void reloadLitebanConfig() {
        litebanConfig = loadActiveConfiguration("liteban.yml", "liteban.yml");
    }

    public void reloadRuleConfig() {
        ruleConfig = loadActiveConfiguration("rule.yml", "rule.yml");
    }

    public void reloadBotRuleConfig() {
        botRuleConfig = loadActiveConfiguration("bot/bot_rule.yml", "bot_rule.yml");
    }

    public boolean isPluginIntegrationEnabled(String key) {
        return pluginSettingsConfig != null && pluginSettingsConfig.getBoolean("plugins." + key, false);
    }

    public boolean isPluginIntegrationActive(String key, String pluginName) {
        return isPluginIntegrationEnabled(key) && getServer().getPluginManager().isPluginEnabled(pluginName);
    }

    public void reloadBotConfig() {
        if (botManager == null) {
            botManager = new BotManager(this);
            return;
        }
        botManager.reloadConfig();
    }

    public void reloadDatabaseConfig() {
        if (databaseManager == null) {
            databaseManager = new DatabaseManager(this);
        } else {
            databaseManager.reloadConfig();
        }
        databaseManager.initialize();
    }

    public void reloadLearningConfig() {
        if (learningManager == null) {
            learningManager = new LearningManager(this);
            return;
        }
        learningManager.reloadConfig();
    }

    public boolean isAdminModeEnabled() {
        return adminModeEnabled;
    }

    public void setAdminModeEnabled(boolean adminModeEnabled) {
        this.adminModeEnabled = adminModeEnabled;
    }

    public ConfigProfile getActiveConfigProfile() {
        FileConfiguration englishConfig = loadLocaleConfiguration(ConfigProfile.ENGLISH, "config.yml", null);
        FileConfiguration vietnameseConfig = loadLocaleConfiguration(ConfigProfile.VIETNAMESE, "config.yml", "config.yml");

        boolean englishOn = englishConfig.getBoolean("use-config", false);
        boolean vietnameseOn = vietnameseConfig.getBoolean("use-config", false);

        if (englishOn == vietnameseOn) {
            englishConfig.set("use-config", false);
            vietnameseConfig.set("use-config", true);
            saveLocaleConfiguration(ConfigProfile.ENGLISH, "config.yml", englishConfig);
            saveLocaleConfiguration(ConfigProfile.VIETNAMESE, "config.yml", vietnameseConfig);
            return ConfigProfile.VIETNAMESE;
        }
        return englishOn ? ConfigProfile.ENGLISH : ConfigProfile.VIETNAMESE;
    }

    public boolean setActiveConfigProfile(ConfigProfile profile) {
        if (profile == null) {
            return false;
        }
        FileConfiguration englishConfig = loadLocaleConfiguration(ConfigProfile.ENGLISH, "config.yml", null);
        FileConfiguration vietnameseConfig = loadLocaleConfiguration(ConfigProfile.VIETNAMESE, "config.yml", "config.yml");

        englishConfig.set("use-config", profile == ConfigProfile.ENGLISH);
        vietnameseConfig.set("use-config", profile == ConfigProfile.VIETNAMESE);

        boolean savedEnglish = saveLocaleConfiguration(ConfigProfile.ENGLISH, "config.yml", englishConfig);
        boolean savedVietnamese = saveLocaleConfiguration(ConfigProfile.VIETNAMESE, "config.yml", vietnameseConfig);
        return savedEnglish && savedVietnamese;
    }

    public ConfigProfile getSenderLanguage(CommandSender sender) {
        if (sender instanceof Player) {
            return playerLanguages.getOrDefault(((Player) sender).getUniqueId(), getActiveConfigProfile());
        }
        return getActiveConfigProfile();
    }

    public boolean isEnglish(CommandSender sender) {
        return getSenderLanguage(sender) == ConfigProfile.ENGLISH;
    }

    public String tr(CommandSender sender, String vietnamese, String english) {
        return isEnglish(sender) ? english : vietnamese;
    }

    public String tr(ConfigProfile profile, String vietnamese, String english) {
        return profile == ConfigProfile.ENGLISH ? english : vietnamese;
    }

    public void setPlayerLanguage(Player player, ConfigProfile profile) {
        if (player == null || profile == null) {
            return;
        }
        playerLanguages.put(player.getUniqueId(), profile);
        if (languagePreferenceConfig == null) {
            reloadLanguagePreferences();
        }
        languagePreferenceConfig.set("players." + player.getUniqueId(), profile.name());
        saveLanguagePreferences();
    }

    public FileConfiguration loadLocaleConfiguration(ConfigProfile profile, String relativePath, String legacyName) {
        File file = ensureLocaleFile(profile, relativePath, legacyName);
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);

        String resourcePath = profile.getFolderName() + "/" + relativePath.replace("\\", "/");
        InputStream defaultsStream = getResource(resourcePath);
        if (defaultsStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
            );
            configuration.setDefaults(defaults);
        }
        return configuration;
    }

    public FileConfiguration loadActiveConfiguration(String relativePath, String legacyName) {
        return loadLocaleConfiguration(getActiveConfigProfile(), relativePath, legacyName);
    }

    public File ensureActiveLocaleFile(String relativePath, String legacyName) {
        return ensureLocaleFile(getActiveConfigProfile(), relativePath, legacyName);
    }

    public String getActiveLocaleFolderName() {
        return getActiveConfigProfile().getFolderName();
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ensureLocaleResources();
        reloadLanguagePreferences();
        reloadConfig();
        reloadOptionConfig();
        reloadPluginSettingsConfig();
        reloadLitebanConfig();
        reloadRuleConfig();
        reloadBotRuleConfig();
        reloadDatabaseConfig();
        reloadLearningConfig();

        suspicionManager = new SuspicionManager(this);
        openAIService = new OpenAIService(this);
        aiChat = new AIChat(this, suspicionManager, openAIService);
        botManager = new BotManager(this);
        serverScanner = new ServerScanner(this, suspicionManager, aiChat);
        statsManager = new StatsManager(this);
        suspicionDashboard = new SuspicionDashboard(this, suspicionManager);
        lagOptimizer = new LagOptimizer(this, serverScanner, aiChat);

        getServer().getPluginManager().registerEvents(new JoinListener(suspicionManager), this);
        getServer().getPluginManager().registerEvents(new QuitListener(suspicionManager), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, aiChat), this);
        getServer().getPluginManager().registerEvents(new MovementListener(suspicionManager), this);
        getServer().getPluginManager().registerEvents(new ClickListener(suspicionManager), this);
        getServer().getPluginManager().registerEvents(new CombatListener(suspicionManager), this);
        getServer().getPluginManager().registerEvents(suspicionDashboard, this);

        PluginCommand command = getCommand("aiadmin");
        if (command != null) {
            AdminCommand adminCommand = new AdminCommand(this, serverScanner, suspicionManager, aiChat);
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        }

        antiCheatConsoleListener = new AntiCheatConsoleListener(this, suspicionManager);
        Logger logger = getServer().getLogger().getParent();
        if (logger == null) {
            logger = getServer().getLogger();
        }
        logger.addHandler(antiCheatConsoleListener);

        if (isPluginIntegrationActive("placeholder", "PlaceholderAPI")) {
            new AIAdminPlaceholderExpansion(this).register();
            getLogger().info("Đã đăng ký placeholder nội bộ của AIAdmin.");
        }

        serverScanner.startAutoScan();
        suspicionManager.startLearningTasks();
        lagOptimizer.start();
        if (openAIService.isEnabled()) {
            getLogger().info("Trợ lý AI đã được bật.");
        } else {
            getLogger().info("Trợ lý AI đang tắt. Hãy đặt " + openAIService.getConfiguredApiKeyEnv() + " hoặc openai.api_key để bật.");
        }
        logPluginIntegrationStatus();
        getLogger().info("AIAdmin đã khởi động cho nhánh 1.21.x.");
    }

    @Override
    public void onDisable() {
        Logger logger = getServer().getLogger().getParent();
        if (logger == null) {
            logger = getServer().getLogger();
        }
        if (antiCheatConsoleListener != null) {
            logger.removeHandler(antiCheatConsoleListener);
        }
        if (botManager != null) {
            botManager.shutdown();
        }
        if (learningManager != null) {
            learningManager.shutdown();
        }
        if (suspicionManager != null) {
            suspicionManager.stopLearningTasks();
        }
        if (lagOptimizer != null) {
            lagOptimizer.stop();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    private void logPluginIntegrationStatus() {
        if (pluginSettingsConfig != null && !pluginSettingsConfig.getBoolean("behavior.log_status_on_startup", true)) {
            return;
        }

        boolean liteban = isPluginIntegrationEnabled("liteban");
        boolean tab = isPluginIntegrationEnabled("tab");
        boolean placeholder = isPluginIntegrationEnabled("placeholder");
        boolean citizens = isPluginIntegrationEnabled("citizens");

        getLogger().info("Thiết lập hook plugin => liteban=" + liteban + ", tab=" + tab + ", placeholder=" + placeholder + ", citizens=" + citizens);
        if (liteban && !getServer().getPluginManager().isPluginEnabled("LiteBans")) {
            getLogger().warning("Đã bật tích hợp LiteBans nhưng server chưa cài LiteBans. Sẽ fallback sang tempban nội bộ.");
        }
        if (tab && !getServer().getPluginManager().isPluginEnabled("TAB")) {
            getLogger().warning("Đã bật tích hợp TAB nhưng server chưa cài TAB.");
        }
        if (placeholder && !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().warning("Đã bật tích hợp PlaceholderAPI nhưng server chưa cài PlaceholderAPI.");
        }
    }

    public void reloadAllRuntimeConfigs() {
        reloadConfig();
        reloadOptionConfig();
        reloadPluginSettingsConfig();
        reloadLitebanConfig();
        reloadRuleConfig();
        reloadBotRuleConfig();
        reloadBotConfig();
        reloadDatabaseConfig();
        reloadLearningConfig();
        if (statsManager != null) {
            statsManager.reload();
        }
        if (openAIService != null) {
            openAIService.reloadClient();
        }
        if (aiChat != null) {
            aiChat.reloadCustomChatConfig();
        }
        if (suspicionManager != null) {
            suspicionManager.startLearningTasks();
        }
        if (lagOptimizer != null) {
            lagOptimizer.start();
        }
    }

    private void ensureLocaleResources() {
        for (ConfigProfile profile : ConfigProfile.values()) {
            for (String resource : LOCALIZED_RESOURCES) {
                String localizedResource = profile.getFolderName() + "/" + resource;
                if (getResource(localizedResource) != null) {
                    saveResource(localizedResource, false);
                } else {
                    getLogger().warning("Thiếu resource nội bộ: " + localizedResource);
                }
            }
        }
    }

    private File ensureLocaleFile(ConfigProfile profile, String relativePath, String legacyName) {
        String safeRelative = relativePath.replace("\\", "/");
        File file = new File(getDataFolder(), profile.getFolderName() + File.separator + safeRelative.replace("/", File.separator));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            boolean migrated = false;
            if (profile == ConfigProfile.VIETNAMESE && legacyName != null && !legacyName.isBlank()) {
                File legacy = new File(getDataFolder(), legacyName);
                if (legacy.exists()) {
                    try {
                        Files.copy(legacy.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        migrated = true;
                    } catch (Exception ex) {
                        getLogger().warning("Không thể migrate " + legacyName + " sang " + profile.getFolderName() + "/" + safeRelative + ": " + ex.getMessage());
                    }
                }
            }
            if (!migrated) {
                saveResource(profile.getFolderName() + "/" + safeRelative, false);
            }
        }
        return file;
    }

    private boolean saveLocaleConfiguration(ConfigProfile profile, String relativePath, FileConfiguration configuration) {
        try {
            File file = ensureLocaleFile(profile, relativePath, null);
            configuration.save(file);
            return true;
        } catch (Exception ex) {
            getLogger().warning("Không thể lưu " + profile.getFolderName() + "/" + relativePath + ": " + ex.getMessage());
            return false;
        }
    }

    private void reloadLanguagePreferences() {
        languagePreferenceFile = new File(getDataFolder(), "player_language.yml");
        languagePreferenceConfig = YamlConfiguration.loadConfiguration(languagePreferenceFile);
        playerLanguages.clear();
        if (languagePreferenceConfig.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : languagePreferenceConfig.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uniqueId = UUID.fromString(key);
                ConfigProfile profile = ConfigProfile.fromInput(languagePreferenceConfig.getString("players." + key, ""));
                if (profile != null) {
                    playerLanguages.put(uniqueId, profile);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveLanguagePreferences() {
        try {
            if (languagePreferenceFile == null) {
                languagePreferenceFile = new File(getDataFolder(), "player_language.yml");
            }
            if (languagePreferenceConfig == null) {
                languagePreferenceConfig = new YamlConfiguration();
            }
            languagePreferenceConfig.save(languagePreferenceFile);
        } catch (Exception ex) {
            getLogger().warning("Không thể lưu player_language.yml: " + ex.getMessage());
        }
    }
}
