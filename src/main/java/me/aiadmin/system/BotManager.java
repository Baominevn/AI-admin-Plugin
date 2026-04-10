package me.aiadmin.system;

import me.aiadmin.AIAdmin;
import me.aiadmin.system.SuspicionManager.RiskTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class BotManager {

    private static final String BOT_RESOURCE = "bot/bot.yml";
    private static final String BODY_RESOURCE = "bot/bot_body.yml";
    private static final String HIDDEN_NAMETAG_TEAM = "aiadmin_bothidden";

    private static class BotProfile {
        String key;
        String name;
        String displayName;
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        boolean followEnabled;
        boolean lookEnabled;
        boolean walkEnabled;
        boolean jumpEnabled;
        boolean hitEnabled;
        boolean turnGroundEnabled;
        boolean invulnerable;
        long lastHitMillis;
        final List<String> actions = new ArrayList<>();

        String entityTag() {
            return "aiadmin_bot_" + key;
        }

        String labelTag() {
            return "aiadmin_bot_label_" + key;
        }
    }

    private final AIAdmin plugin;
    private File botFile;
    private File bodyFile;
    private FileConfiguration botConfig;
    private FileConfiguration bodyConfig;

    private final Map<String, BotProfile> bots = new LinkedHashMap<>();
    private String selectedBotKey;

    private String activeBotKey;
    private String activeTargetName;
    private String activeReason = "none";
    private BukkitRunnable observeTask;
    private long observeStartedAt;
    private long lastAiInsightMillis;
    private Location lastObservedLocation;
    private long lastAdaptiveSuspicionMillis;
    private int consecutiveSuspiciousPasses;

    public BotManager(AIAdmin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        botFile = plugin.ensureActiveLocaleFile(BOT_RESOURCE, BOT_RESOURCE);
        botConfig = plugin.loadActiveConfiguration(BOT_RESOURCE, BOT_RESOURCE);

        bodyFile = plugin.ensureActiveLocaleFile(BODY_RESOURCE, BODY_RESOURCE);
        bodyConfig = plugin.loadActiveConfiguration(BODY_RESOURCE, BODY_RESOURCE);

        loadBotsFromConfig();
        selectedBotKey = normalizeKey(botConfig.getString("bot.selected", ""));
        if (!bots.containsKey(selectedBotKey)) {
            selectedBotKey = bots.isEmpty() ? null : bots.keySet().iterator().next();
            saveSelection();
        }
    }

    public boolean isEnabled() {
        return botConfig != null && botConfig.getBoolean("bot.enabled", true);
    }

    public List<String> getBotNames() {
        List<String> names = new ArrayList<>();
        for (BotProfile bot : bots.values()) {
            names.add(bot.name);
        }
        return names;
    }

    public int getBotCount() {
        return bots.size();
    }

    public String getActiveTargetName() {
        return activeTargetName;
    }

    public boolean shouldTriggerFor(RiskTier tier) {
        if (!isEnabled()) {
            return false;
        }
        String configuredTier = botConfig.getString("bot.trigger_tier", "ALERT");
        RiskTier threshold = parseTier(configuredTier);
        return tier.ordinal() >= threshold.ordinal();
    }

    public void observeTarget(Player target, String reason) {
        if (!isEnabled() || target == null || !target.isOnline()) {
            return;
        }
        if (bots.isEmpty()) {
            createBotInternal(defaultBotName(), target.getLocation());
        }
        BotProfile bot = getSelectedBot();
        if (bot == null) {
            return;
        }

        activeBotKey = bot.key;
        activeTargetName = target.getName();
        activeReason = reason == null ? "unknown" : reason;
        observeStartedAt = System.currentTimeMillis();
        lastAiInsightMillis = 0L;
        lastObservedLocation = null;
        lastAdaptiveSuspicionMillis = 0L;
        consecutiveSuspiciousPasses = 0;

        runConfiguredCommands(botConfig.getStringList("bot.commands.spawn"), target, bot);
        logBotEvent("observe_start", target, activeReason);
        startObserveLoop();
    }

    public void stopObservation(String reason) {
        activeReason = reason == null ? "stopped" : reason;
        if (observeTask != null) {
            observeTask.cancel();
            observeTask = null;
        }

        BotProfile bot = getActiveBot();
        Player target = activeTargetName == null ? null : Bukkit.getPlayerExact(activeTargetName);
        if (bot != null) {
            runConfiguredCommands(botConfig.getStringList("bot.commands.remove"), target, bot);
        }

        logBotEvent("observe_stop", target, activeReason);
        activeBotKey = null;
        activeTargetName = null;
        lastObservedLocation = null;
        consecutiveSuspiciousPasses = 0;
    }

    public void shutdown() {
        stopObservation("plugin-disable");
        if (bodyConfig != null && bodyConfig.getBoolean("body.remove_on_shutdown", false)) {
            removeAllBotsFromWorld();
        }
    }

    public String createBotBody(CommandSender sender, String desiredName) {
        Location spawnLoc = resolveSpawnLocation(sender);
        if (spawnLoc == null || spawnLoc.getWorld() == null) {
            return "&cKhông tìm thấy vị trí để tạo bot.";
        }

        String inputName = desiredName == null || desiredName.isBlank() ? defaultBotName() : desiredName.trim();
        BotProfile bot = createBotInternal(inputName, spawnLoc);
        if (bot == null) {
            return "&cKhông thể tạo bot mannequin.";
        }
        return "&aĐã tạo bot mannequin: &f" + bot.name + "&a. Dùng &f/ai choose bot " + bot.name + "&a để chọn bot.";
    }

    public String removeBotBody() {
        return removeBot();
    }

    public String removeBot() {
        BotProfile selected = getSelectedBot();
        if (selected == null) {
            return "&eChưa có bot nào để xóa.";
        }
        stopObservation("manual-remove");
        despawnMannequin(selected);
        bots.remove(selected.key);
        saveBots();

        selectedBotKey = bots.isEmpty() ? null : bots.keySet().iterator().next();
        saveSelection();
        return "&aĐã remove bot: &f" + selected.name;
    }

    public List<String> listBots() {
        List<String> lines = new ArrayList<>();
        lines.add("&6[Bot] Danh sách bot:");
        if (bots.isEmpty()) {
            lines.add("&7- Chưa có bot nào.");
            return lines;
        }
        for (BotProfile bot : bots.values()) {
            String mark = bot.key.equals(selectedBotKey) ? "&a*" : "&7-";
            lines.add(mark + " &f" + bot.name + " &7(" + bot.world + " @ "
                    + round(bot.x) + ", " + round(bot.y) + ", " + round(bot.z) + ")");
        }
        return lines;
    }

    public String chooseBot(String name) {
        if (name == null || name.isBlank()) {
            return "&cCách dùng: /ai choose bot <name>";
        }
        BotProfile found = findBot(name);
        if (found == null) {
            return "&cKhông tìm thấy bot: " + name;
        }
        selectedBotKey = found.key;
        saveSelection();
        return "&aĐã chọn bot: &f" + found.name;
    }

    public String addBotAction(String action, String[] params) {
        BotProfile bot = getSelectedBot();
        if (bot == null) {
            return "&cChưa có bot được chọn. Dùng /ai choose bot <name>.";
        }
        if (action == null || action.isBlank()) {
            return "&cCách dùng: /ai bot action add move <x1> <y1> <z1> <x2> <y2> <z2>";
        }

        String normalized = action.toLowerCase(Locale.ROOT);
        if (!normalized.equals("move")) {
            return "&eAction chưa hỗ trợ: " + action + ". Hiện có: move";
        }
        if (params == null || params.length < 6) {
            return "&cThiếu tham số. Ví dụ: /ai bot action add move 100 0 100 300 0 100";
        }
        try {
            double x1 = Double.parseDouble(params[0]);
            double y1 = Double.parseDouble(params[1]);
            double z1 = Double.parseDouble(params[2]);
            double x2 = Double.parseDouble(params[3]);
            double y2 = Double.parseDouble(params[4]);
            double z2 = Double.parseDouble(params[5]);

            World world = Bukkit.getWorld(bot.world);
            if (world == null) {
                return "&cWorld của bot không tồn tại: " + bot.world;
            }

            bot.actions.add("move " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2);
            saveBots();
            runMoveAction(bot, new Location(world, x1, y1, z1), new Location(world, x2, y2, z2));
            return "&aĐã thêm action move cho bot &f" + bot.name + "&a.";
        } catch (NumberFormatException ex) {
            return "&cTọa độ không hợp lệ.";
        }
    }

    public List<String> getBotHelpLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&6===== AI Bot Help =====");
        lines.add("&e/ai bot help &7- Hiển thị hướng dẫn bot");
        lines.add("&e/aiadmin createbot <name> &7- Tạo bot mannequin");
        lines.add("&e/ai choose bot <name> &7- Chọn bot để thiết lập");
        lines.add("&e/ai bot list &7- Xem danh sách bot + vị trí");
        lines.add("&e/ai bot remove &7- Xóa bot đang được chọn");
        lines.add("&e/ai bot setup <key> <value> &7- Chỉnh thiết lập bot");
        lines.add("&e/ai bot action add move <x1> <y1> <z1> <x2> <y2> <z2> &7- Thêm action di chuyển");
        lines.add("&7Các key hỗ trợ: name, follow, look, walk, jump, hit, turn_ground, invulnerable, tier, observe, ai, ai_llm, ai_interval");
        return lines;
    }

    public String setupBot(String key, String value) {
        if (key == null || value == null) {
            return "&cThiếu key hoặc value.";
        }
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        String normalizedValue = value.trim();

        try {
            switch (normalizedKey) {
                case "tier":
                case "trigger_tier": {
                    RiskTier tier = parseTier(normalizedValue);
                    botConfig.set("bot.trigger_tier", tier.name());
                    return saveBotConfig() ? "&aĐã cập nhật trigger_tier = &f" + tier.name() : "&cLưu bot.yml thất bại.";
                }
                case "observe":
                case "observe_seconds": {
                    int observe = Math.max(30, Math.min(7200, Integer.parseInt(normalizedValue)));
                    botConfig.set("bot.observe_seconds", observe);
                    return saveBotConfig() ? "&aĐã cập nhật observe_seconds = &f" + observe : "&cLưu bot.yml thất bại.";
                }
                case "follow_interval":
                case "follow_interval_ticks": {
                    int ticks = Math.max(5, Math.min(200, Integer.parseInt(normalizedValue)));
                    botConfig.set("bot.follow_interval_ticks", ticks);
                    return saveBotConfig() ? "&aĐã cập nhật follow_interval_ticks = &f" + ticks : "&cLưu bot.yml thất bại.";
                }
                case "ai":
                case "brain": {
                    boolean enabled = parseBoolean(normalizedValue);
                    botConfig.set("bot.ai.enabled", enabled);
                    return saveBotConfig() ? "&aĐã cập nhật bot.ai.enabled = &f" + enabled : "&cLưu bot.yml thất bại.";
                }
                case "ai_llm":
                case "brain_llm": {
                    boolean enabled = parseBoolean(normalizedValue);
                    if (enabled && !isRuleAllowed("bot_rule.ai.allow_llm", true)) {
                        return "&cLLM mode đang bị khóa trong bot_rule.yml.";
                    }
                    botConfig.set("bot.ai.use_llm", enabled);
                    return saveBotConfig() ? "&aĐã cập nhật bot.ai.use_llm = &f" + enabled : "&cLưu bot.yml thất bại.";
                }
                case "ai_interval":
                case "ai_report_interval": {
                    int interval = Math.max(5, Math.min(120, Integer.parseInt(normalizedValue)));
                    botConfig.set("bot.ai.report_interval_seconds", interval);
                    return saveBotConfig()
                            ? "&aĐã cập nhật bot.ai.report_interval_seconds = &f" + interval
                            : "&cLưu bot.yml thất bại.";
                }
                default:
                    break;
            }

            BotProfile bot = getSelectedBot();
            if (bot == null) {
                return "&cChưa có bot được chọn. Dùng /ai choose bot <name>.";
            }

            switch (normalizedKey) {
                case "name":
                    bot.name = normalizedValue;
                    bot.displayName = buildDisplayName(bot.name);
                    updateMannequinDisplay(bot);
                    saveBots();
                    return "&aĐã đổi tên bot thành: &f" + bot.name;
                case "follow":
                    bot.followEnabled = parseBoolean(normalizedValue);
                    saveBots();
                    return "&aĐã cập nhật follow = &f" + bot.followEnabled;
                case "look":
                    bot.lookEnabled = parseBoolean(normalizedValue);
                    saveBots();
                    return "&aĐã cập nhật look = &f" + bot.lookEnabled;
                case "walk":
                    bot.walkEnabled = parseBoolean(normalizedValue);
                    saveBots();
                    return "&aĐã cập nhật walk = &f" + bot.walkEnabled;
                case "jump":
                    bot.jumpEnabled = parseBoolean(normalizedValue);
                    saveBots();
                    return "&aĐã cập nhật jump = &f" + bot.jumpEnabled;
                case "hit":
                    bot.hitEnabled = parseBoolean(normalizedValue);
                    saveBots();
                    return "&aĐã cập nhật hit = &f" + bot.hitEnabled;
                case "turn_ground":
                case "turnaround":
                case "turn_around":
                    bot.turnGroundEnabled = parseBoolean(normalizedValue);
                    saveBots();
                    return "&aĐã cập nhật turn_ground = &f" + bot.turnGroundEnabled;
                case "invulnerable":
                case "god":
                    bot.invulnerable = parseBoolean(normalizedValue);
                    updateMannequinDisplay(bot);
                    saveBots();
                    return "&aĐã cập nhật invulnerable = &f" + bot.invulnerable;
                case "respawn":
                    spawnMannequin(bot);
                    return "&aĐã gọi lại bot mannequin: &f" + bot.name;
                default:
                    return "&eKey không hỗ trợ. Dùng: name, follow, look, walk, jump, hit, turn_ground, invulnerable, tier, observe, ai, ai_llm, ai_interval";
            }
        } catch (NumberFormatException ex) {
            return "&cGiá trị số không hợp lệ cho key: " + normalizedKey;
        }
    }

    public List<String> describeSetup() {
        List<String> lines = new ArrayList<>();
        lines.add("&6[Bot] Cấu hình hiện tại:");
        lines.add("&7- trigger_tier: &f" + botConfig.getString("bot.trigger_tier", "ALERT"));
        lines.add("&7- observe_seconds: &f" + botConfig.getInt("bot.observe_seconds", 600));
        lines.add("&7- follow_interval_ticks: &f" + botConfig.getInt("bot.follow_interval_ticks", 20));
        lines.add("&7- bot.ai.enabled: &f" + botConfig.getBoolean("bot.ai.enabled", true));
        lines.add("&7- bot.ai.use_llm: &f" + botConfig.getBoolean("bot.ai.use_llm", false));
        lines.add("&7- bot.ai.report_interval_seconds: &f" + botConfig.getInt("bot.ai.report_interval_seconds", 12));

        BotProfile selected = getSelectedBot();
        if (selected == null) {
            lines.add("&7- selected_bot: &fchưa chọn bot");
            return lines;
        }

        lines.add("&7- selected_bot: &f" + selected.name);
        lines.add("&7- world: &f" + selected.world);
        lines.add("&7- position: &f" + round(selected.x) + ", " + round(selected.y) + ", " + round(selected.z));
        lines.add("&7- follow: &f" + selected.followEnabled);
        lines.add("&7- look: &f" + selected.lookEnabled);
        lines.add("&7- walk: &f" + selected.walkEnabled);
        lines.add("&7- jump: &f" + selected.jumpEnabled);
        lines.add("&7- hit: &f" + selected.hitEnabled);
        lines.add("&7- turn_ground: &f" + selected.turnGroundEnabled);
        lines.add("&7- invulnerable: &f" + selected.invulnerable);
        lines.add("&7- actions: &f" + selected.actions.size());
        return lines;
    }

    private void startObserveLoop() {
        if (observeTask != null) {
            observeTask.cancel();
        }
        long period = Math.max(5L, botConfig.getLong("bot.follow_interval_ticks", 20L));
        long maxObserveMillis = Math.max(30L, botConfig.getLong("bot.observe_seconds", 600L)) * 1000L;

        observeTask = new BukkitRunnable() {
            @Override
            public void run() {
                BotProfile bot = getActiveBot();
                if (bot == null || activeTargetName == null) {
                    stopObservation("bot-null");
                    cancel();
                    return;
                }

                Player target = Bukkit.getPlayerExact(activeTargetName);
                if (target == null || !target.isOnline()) {
                    stopObservation("target-offline");
                    cancel();
                    return;
                }

                if (System.currentTimeMillis() - observeStartedAt > maxObserveMillis) {
                    stopObservation("timeout");
                    cancel();
                    return;
                }

                runConfiguredCommands(botConfig.getStringList("bot.commands.follow"), target, bot);
                applyBotBehavior(bot, target);

                if (plugin.getSuspicionManager() != null) {
                    SuspicionManager.PlayerRiskProfile profile = plugin.getSuspicionManager().getOrCreateProfile(target.getName());
                    applyObservationLearning(target, profile);
                    emitAiInsightIfNeeded(bot, target, profile);
                }
            }
        };
        observeTask.runTaskTimer(plugin, 0L, period);
    }

    private void applyBotBehavior(BotProfile bot, Player target) {
        World world = Bukkit.getWorld(bot.world);
        if (world == null) {
            world = target.getWorld();
        }
        Location current = new Location(world, bot.x, bot.y, bot.z, bot.yaw, bot.pitch);
        Location desired = current.clone();

        if (bot.followEnabled) {
            Location targetLoc = target.getLocation().clone();
            double xOffset = bodyConfig.getDouble("body.follow_offsets.x", 1.0D);
            double yOffset = bodyConfig.getDouble("body.follow_offsets.y", 0.0D);
            double zOffset = bodyConfig.getDouble("body.follow_offsets.z", 1.0D);
            desired = targetLoc.add(xOffset, yOffset, zOffset);
        }

        Location next = desired.clone();

        if (bot.followEnabled && bot.walkEnabled) {
            double walkStep = clamp(bodyConfig.getDouble("body.behavior.walk_step", 0.35D), 0.05D, 1.0D);
            next.setX(lerp(current.getX(), desired.getX(), walkStep));
            next.setY(lerp(current.getY(), desired.getY(), walkStep));
            next.setZ(lerp(current.getZ(), desired.getZ(), walkStep));
        }

        if (bot.jumpEnabled) {
            double jumpAmp = Math.max(0D, bodyConfig.getDouble("body.behavior.jump_height", 0.35D));
            long jumpCycleMs = Math.max(600L, bodyConfig.getLong("body.behavior.jump_cycle_ms", 1200L));
            long elapsed = Math.max(0L, System.currentTimeMillis() - observeStartedAt);
            double phase = (elapsed % jumpCycleMs) / (double) jumpCycleMs;
            double jumpOffset = 0.0D;
            if (phase < 0.25D) {
                jumpOffset = jumpAmp * (phase / 0.25D);
            } else if (phase < 0.5D) {
                jumpOffset = jumpAmp * (1.0D - ((phase - 0.25D) / 0.25D));
            }
            next.setY(next.getY() + jumpOffset);
        }

        float yaw = bot.yaw;
        float pitch = bot.pitch;
        if (bot.lookEnabled) {
            Location eye = target.getEyeLocation();
            Location look = next.clone();
            faceLocation(look, eye);
            yaw = look.getYaw();
            pitch = look.getPitch();
        } else if (bot.turnGroundEnabled) {
            pitch = 0.0F;
        }

        teleportMannequin(bot, next, yaw, pitch);
        maybeHit(bot, target);
    }

    private void maybeHit(BotProfile bot, Player target) {
        if (!bot.hitEnabled || target == null || !target.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        int cooldown = Math.max(800, bodyConfig.getInt("body.behavior.hit_cooldown_ms", 1800));
        if (now - bot.lastHitMillis < cooldown) {
            return;
        }
        Location botLoc = new Location(target.getWorld(), bot.x, bot.y, bot.z);
        if (botLoc.distanceSquared(target.getLocation()) > 6.25D) {
            return;
        }
        runConfiguredCommands(bodyConfig.getStringList("body.behavior.hit_commands"), target, bot);
        bot.lastHitMillis = now;
    }

    private void runMoveAction(BotProfile bot, Location from, Location to) {
        if (bot == null || from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        int duration = Math.max(20, bodyConfig.getInt("body.actions.move_duration_ticks", 80));
        teleportMannequin(bot, from, bot.yaw, bot.pitch);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!bots.containsKey(bot.key)) {
                    cancel();
                    return;
                }
                tick++;
                double t = Math.min(1.0D, (double) tick / duration);
                double x = lerp(from.getX(), to.getX(), t);
                double y = lerp(from.getY(), to.getY(), t);
                double z = lerp(from.getZ(), to.getZ(), t);
                Location step = new Location(from.getWorld(), x, y, z);

                float yaw = bot.yaw;
                float pitch = bot.pitch;
                if (bot.lookEnabled) {
                    Location look = step.clone();
                    faceLocation(look, to);
                    yaw = look.getYaw();
                    pitch = look.getPitch();
                } else if (bot.turnGroundEnabled) {
                    pitch = 0.0F;
                }

                teleportMannequin(bot, step, yaw, pitch);
                if (t >= 1.0D) {
                    cancel();
                    saveBots();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private BotProfile createBotInternal(String botName, Location location) {
        String keyBase = normalizeKey(botName);
        if (keyBase.isBlank()) {
            keyBase = "bot";
        }
        String key = keyBase;
        int index = 2;
        while (bots.containsKey(key)) {
            key = keyBase + "_" + index++;
        }

        BotProfile bot = new BotProfile();
        bot.key = key;
        bot.name = botName;
        bot.displayName = buildDisplayName(botName);
        bot.world = location.getWorld().getName();
        bot.x = location.getX();
        bot.y = location.getY();
        bot.z = location.getZ();
        bot.yaw = location.getYaw();
        bot.pitch = location.getPitch();
        bot.followEnabled = bodyConfig.getBoolean("body.follow_target", true);
        bot.lookEnabled = bodyConfig.getBoolean("body.behavior.look_enabled", true);
        bot.walkEnabled = bodyConfig.getBoolean("body.behavior.walk_enabled", true);
        bot.jumpEnabled = bodyConfig.getBoolean("body.behavior.jump_enabled", false);
        bot.hitEnabled = bodyConfig.getBoolean("body.behavior.hit_enabled", false);
        bot.turnGroundEnabled = bodyConfig.getBoolean("body.behavior.turn_ground_enabled", true);
        bot.invulnerable = true;

        bots.put(bot.key, bot);
        selectedBotKey = bot.key;
        saveBots();
        saveSelection();

        spawnMannequin(bot);
        return bot;
    }

    private void spawnMannequin(BotProfile bot) {
        if (bot == null) {
            return;
        }
        despawnMannequin(bot);

        String legacyName = color(bot.displayName);
        String nbt = "{Tags:[\"aiadmin_bot\",\"" + bot.entityTag() + "\"],Invulnerable:" + (bot.invulnerable ? "1b" : "0b") + ",CustomNameVisible:0b}";

        String summon = "summon minecraft:mannequin " + format(bot.x) + " " + format(bot.y) + " " + format(bot.z) + " " + nbt;
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), summon);
        if (!ok) {
            String fallback = bodyConfig.getString("body.mannequin.spawn_command", "summon minecraft:mannequin {x} {y} {z}");
            String cmd = fallback
                    .replace("{x}", format(bot.x))
                    .replace("{y}", format(bot.y))
                    .replace("{z}", format(bot.z))
                    .replace("{bot_name}", bot.name)
                    .replace("{display_name}", legacyName)
                    .replace("{world}", bot.world);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        hideMannequinNametag(bot);
        spawnNameLabel(bot);
        updateMannequinDisplay(bot);
        runConfiguredCommands(bodyConfig.getStringList("body.mannequin.post_spawn_commands"), null, bot);
    }

    private void despawnMannequin(BotProfile bot) {
        if (bot == null) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kill @e[type=minecraft:mannequin,tag=" + bot.entityTag() + "]");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kill @e[type=mannequin,tag=" + bot.entityTag() + "]");
        despawnNameLabel(bot);
    }

    private void removeAllBotsFromWorld() {
        for (BotProfile bot : new ArrayList<>(bots.values())) {
            despawnMannequin(bot);
        }
        bots.clear();
        selectedBotKey = null;
        saveBots();
        saveSelection();
    }

    private void teleportMannequin(BotProfile bot, Location location, float yaw, float pitch) {
        if (bot == null || location == null || location.getWorld() == null) {
            return;
        }
        bot.world = location.getWorld().getName();
        bot.x = location.getX();
        bot.y = location.getY();
        bot.z = location.getZ();
        bot.yaw = yaw;
        bot.pitch = pitch;
        Entity mannequin = selectFirstEntity("@e[tag=" + bot.entityTag() + ",limit=1]");
        if (mannequin != null) {
            Location next = new Location(location.getWorld(), bot.x, bot.y, bot.z, bot.yaw, bot.pitch);
            mannequin.teleport(next);
        }
        teleportNameLabel(bot);
    }

    private void updateMannequinDisplay(BotProfile bot) {
        if (bot == null) {
            return;
        }
        String nbt = "{CustomNameVisible:0b,Invulnerable:" + (bot.invulnerable ? "1b" : "0b") + "}";
        String mergeMannequin = "execute as @e[type=minecraft:mannequin,tag=" + bot.entityTag() + ",limit=1] at @s run data merge entity @s " + nbt;
        boolean mergeOk = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), mergeMannequin);
        if (!mergeOk) {
            String mergeFallback = "execute as @e[type=mannequin,tag=" + bot.entityTag() + ",limit=1] at @s run data merge entity @s " + nbt;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), mergeFallback);
        }
        hideMannequinNametag(bot);
        updateNameLabelText(bot);
        teleportNameLabel(bot);
    }

    private void ensureNametagHiddenTeam() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "team add " + HIDDEN_NAMETAG_TEAM);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "team modify " + HIDDEN_NAMETAG_TEAM + " nametagVisibility never");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "team modify " + HIDDEN_NAMETAG_TEAM + " collisionRule never");
    }

    private void hideMannequinNametag(BotProfile bot) {
        if (bot == null) {
            return;
        }
        ensureNametagHiddenTeam();
        String joinMannequin = "execute as @e[type=minecraft:mannequin,tag=" + bot.entityTag() + ",limit=1] at @s run team join " + HIDDEN_NAMETAG_TEAM + " @s";
        boolean joined = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), joinMannequin);
        if (!joined) {
            String joinFallback = "execute as @e[type=mannequin,tag=" + bot.entityTag() + ",limit=1] at @s run team join " + HIDDEN_NAMETAG_TEAM + " @s";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), joinFallback);
        }
    }

    private void spawnNameLabel(BotProfile bot) {
        if (bot == null) {
            return;
        }
        despawnNameLabel(bot);
        String jsonName = buildDisplayNameJson(bot);
        String relativeY = "~" + format(labelOffsetY());
        String summon = "execute as @e[type=minecraft:mannequin,tag=" + bot.entityTag() + ",limit=1] at @s run summon minecraft:text_display ~ "
                + relativeY + " ~ {Tags:[\"aiadmin_bot_label\",\"" + bot.labelTag() + "\"],billboard:\"vertical\",background:0,shadow:1b,see_through:1b,text:" + jsonName + "}";
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), summon);
        if (!ok) {
            String fallback = "execute as @e[type=mannequin,tag=" + bot.entityTag() + ",limit=1] at @s run summon text_display ~ "
                    + relativeY + " ~ {Tags:[\"aiadmin_bot_label\",\"" + bot.labelTag() + "\"],billboard:\"vertical\",background:0,shadow:1b,see_through:1b,text:" + jsonName + "}";
            ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fallback);
        }
        if (!ok) {
            double labelY = bot.y + labelOffsetY();
            String absoluteFallback = "summon minecraft:text_display " + format(bot.x) + " " + format(labelY) + " " + format(bot.z)
                    + " {Tags:[\"aiadmin_bot_label\",\"" + bot.labelTag() + "\"],billboard:\"vertical\",background:0,shadow:1b,see_through:1b,text:" + jsonName + "}";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), absoluteFallback);
        }
        teleportNameLabel(bot);
    }

    private void despawnNameLabel(BotProfile bot) {
        if (bot == null) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kill @e[type=minecraft:text_display,tag=" + bot.labelTag() + "]");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kill @e[type=text_display,tag=" + bot.labelTag() + "]");
    }

    private void updateNameLabelText(BotProfile bot) {
        if (bot == null) {
            return;
        }
        String jsonName = buildDisplayNameJson(bot);
        String update = "execute as @e[type=minecraft:text_display,tag=" + bot.labelTag() + ",limit=1] at @s run data modify entity @s text set value " + jsonName;
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), update);
        if (!ok) {
            String fallback = "execute as @e[type=text_display,tag=" + bot.labelTag() + ",limit=1] at @s run data modify entity @s text set value " + jsonName;
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fallback)) {
                spawnNameLabel(bot);
            }
        }
    }

    private void teleportNameLabel(BotProfile bot) {
        if (bot == null) {
            return;
        }
        Entity label = selectFirstEntity("@e[tag=" + bot.labelTag() + ",limit=1]");
        World world = Bukkit.getWorld(bot.world);
        if (label == null || world == null) {
            return;
        }
        Location labelLocation = new Location(world, bot.x, bot.y + labelOffsetY(), bot.z);
        label.teleport(labelLocation);
    }

    private double labelOffsetY() {
        return clamp(bodyConfig.getDouble("body.name_label.y_offset", 2.15D), 1.0D, 4.0D);
    }

    private void runConfiguredCommands(List<String> templates, Player target, BotProfile bot) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        for (String template : templates) {
            String cmd = applyPlaceholders(template, target, bot);
            if (cmd.isBlank()) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private String applyPlaceholders(String template, Player target, BotProfile bot) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (bot != null) {
            result = result.replace("{bot_name}", bot.name);
            result = result.replace("{display_name}", color(bot.displayName));
            result = result.replace("{bot_tag}", bot.entityTag());
            result = result.replace("{world}", bot.world == null ? "" : bot.world);
            result = result.replace("{x}", format(bot.x));
            result = result.replace("{y}", format(bot.y));
            result = result.replace("{z}", format(bot.z));
            result = result.replace("{yaw}", format(bot.yaw));
            result = result.replace("{pitch}", format(bot.pitch));
        } else {
            result = result.replace("{bot_name}", "");
            result = result.replace("{display_name}", "");
            result = result.replace("{bot_tag}", "");
            result = result.replace("{world}", "");
            result = result.replace("{x}", "0");
            result = result.replace("{y}", "0");
            result = result.replace("{z}", "0");
            result = result.replace("{yaw}", "0");
            result = result.replace("{pitch}", "0");
        }

        if (target != null) {
            result = result.replace("{player}", target.getName());
        } else {
            result = result.replace("{player}", "");
        }
        result = result.replace("{reason}", activeReason == null ? "" : activeReason);

        if (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result.trim();
    }

    private void applyObservationLearning(Player target, SuspicionManager.PlayerRiskProfile profile) {
        if (target == null || profile == null || plugin.getLearningManager() == null || !plugin.getLearningManager().isEnabled()) {
            return;
        }
        if (shouldApplyBotRules() && !getBotRuleBoolean("bot_rule.learning.enabled", true)) {
            return;
        }

        Location current = target.getLocation();
        if (current == null || current.getWorld() == null) {
            return;
        }
        if (lastObservedLocation == null || lastObservedLocation.getWorld() != current.getWorld()) {
            lastObservedLocation = current.clone();
            return;
        }

        long now = System.currentTimeMillis();
        double deltaX = current.getX() - lastObservedLocation.getX();
        double deltaZ = current.getZ() - lastObservedLocation.getZ();
        double horizontal = Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        double verticalUp = Math.max(0D, current.getY() - lastObservedLocation.getY());
        double yawDelta = angleDistance(lastObservedLocation.getYaw(), current.getYaw());
        double pitchDelta = Math.abs(current.getPitch() - lastObservedLocation.getPitch());
        int cps = profile.getCps(now, 1000L);

        double moveThreshold = getBotRuleDouble("bot_rule.learning.movement_spike_threshold", 1.05D);
        double verticalThreshold = getBotRuleDouble("bot_rule.learning.vertical_spike_threshold", 0.58D);
        double fastYawThreshold = getBotRuleDouble("bot_rule.learning.fast_yaw_threshold", 70.0D);
        double lowPitchThreshold = getBotRuleDouble("bot_rule.learning.low_pitch_change_threshold", 1.4D);
        int cpsWarn = getBotRuleInt("bot_rule.learning.cps_warn", 14);
        int cpsFlag = getBotRuleInt("bot_rule.learning.cps_flag", 18);
        double hackMultiplier = Math.max(0.5D, getBotRuleDouble("bot_rule.learning.hack_weight_multiplier", 1.15D));
        double legitMultiplier = Math.max(0.5D, getBotRuleDouble("bot_rule.learning.legit_weight_multiplier", 1.05D));

        int suspiciousSignals = 0;
        int legitSignals = 0;

        if (horizontal > moveThreshold) {
            plugin.getLearningManager().recordHackSignal(target.getName(), "camera-move", horizontal, scaledWeight(3, hackMultiplier));
            suspiciousSignals++;
        }
        if (verticalUp > verticalThreshold && !target.isOnGround() && !target.getAllowFlight()) {
            plugin.getLearningManager().recordHackSignal(target.getName(), "camera-vertical", verticalUp, scaledWeight(4, hackMultiplier));
            suspiciousSignals++;
        }
        if (yawDelta > fastYawThreshold && pitchDelta < lowPitchThreshold) {
            plugin.getLearningManager().recordHackSignal(target.getName(), "camera-aim-snap", yawDelta, scaledWeight(3, hackMultiplier));
            suspiciousSignals++;
        }
        if (cps >= cpsFlag) {
            plugin.getLearningManager().recordHackSignal(target.getName(), "camera-cps-flag", cps, scaledWeight(5, hackMultiplier));
            suspiciousSignals++;
        } else if (cps >= cpsWarn) {
            plugin.getLearningManager().recordHackSignal(target.getName(), "camera-cps-warn", cps, scaledWeight(2, hackMultiplier));
            suspiciousSignals++;
        }

        if (horizontal > 0.05D && horizontal < moveThreshold * 0.75D && cps >= 5 && cps <= 13) {
            plugin.getLearningManager().recordLegitSignal(target.getName(), "camera-move-legit", horizontal, scaledWeight(2, legitMultiplier));
            legitSignals++;
        }
        if (yawDelta > 3.0D && yawDelta < 40.0D && pitchDelta > 1.0D) {
            plugin.getLearningManager().recordLegitSignal(target.getName(), "camera-aim-legit", yawDelta, scaledWeight(1, legitMultiplier));
            legitSignals++;
        }

        if (suspiciousSignals >= 2) {
            consecutiveSuspiciousPasses++;
        } else if (legitSignals > 0) {
            consecutiveSuspiciousPasses = Math.max(0, consecutiveSuspiciousPasses - 1);
        } else {
            consecutiveSuspiciousPasses = 0;
        }

        int extraSuspicion = Math.max(0, getBotRuleInt("bot_rule.learning.extra_suspicion_when_consistent", 1));
        long cooldownMillis = Math.max(5000L, getBotRuleLong("bot_rule.learning.extra_suspicion_cooldown_ms", 15000L));
        if (extraSuspicion > 0
                && consecutiveSuspiciousPasses >= 3
                && plugin.getSuspicionManager() != null
                && now - lastAdaptiveSuspicionMillis >= cooldownMillis) {
            plugin.getSuspicionManager().addSuspicion(target.getName(), extraSuspicion, "camera-learning", "consistent-signals");
            plugin.getLearningManager().recordHackSignal(target.getName(), "camera-consistent", consecutiveSuspiciousPasses, scaledWeight(3, hackMultiplier));
            lastAdaptiveSuspicionMillis = now;
            consecutiveSuspiciousPasses = 0;
        }

        lastObservedLocation = current.clone();
    }

    private void emitAiInsightIfNeeded(BotProfile bot, Player target, SuspicionManager.PlayerRiskProfile profile) {
        if (bot == null || target == null || profile == null) {
            return;
        }
        if (plugin.getAiChat() == null || plugin.getSuspicionManager() == null) {
            return;
        }
        if (!botConfig.getBoolean("bot.ai.enabled", true)) {
            return;
        }
        if (!isRuleAllowed("bot_rule.ai.enabled", true)) {
            return;
        }

        int interval = Math.max(5, botConfig.getInt("bot.ai.report_interval_seconds", 12));
        long now = System.currentTimeMillis();
        if (now - lastAiInsightMillis < interval * 1000L) {
            return;
        }
        lastAiInsightMillis = now;

        RiskTier tier = plugin.getSuspicionManager().getRiskTier(profile.getSuspicion());
        String learningSummary = plugin.getLearningManager() == null
                ? "learning=off"
                : plugin.getLearningManager().getObservationSummary(target.getName());

        if (botConfig.getBoolean("bot.ai.use_llm", false)
                && isRuleAllowed("bot_rule.ai.allow_llm", true)
                && plugin.getOpenAIService() != null
                && plugin.getOpenAIService().isEnabled()) {
            String llmPrefix = botConfig.getString("bot.ai.llm_prefix", "[BotAI] ");
            String persona = getBotRuleString("bot_rule.ai.personality", "thông minh, rõ ràng, hữu ích");
            String styleHint = getBotRuleString("bot_rule.ai.style_hint", "nói gọn, tự nhiên, không dài dòng");
            String prompt = "Camera observation mode. Analyze this player quickly and naturally.\n"
                    + "Bot: " + bot.name + "\n"
                    + "Player: " + target.getName() + "\n"
                    + "Tier: " + tier.name() + "\n"
                    + "Suspicion: " + profile.getSuspicion() + "\n"
                    + "Alerts: " + profile.getTotalAlerts() + "\n"
                    + "Learning: " + learningSummary + "\n"
                    + "Persona: " + persona + "\n"
                    + "Style: " + styleHint + "\n"
                    + "Give one short line for staff only.";

            plugin.getOpenAIService().askAssistant(target, prompt, true).thenAccept(reply -> {
                String safeReply = sanitizeBotReply(reply);
                if (safeReply.isBlank()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getAiChat().sendStaffNotice(llmPrefix + safeReply));
            });
            return;
        }

        String line = pickBotVoiceLine(bot.name, tier, target.getName(), profile.getSuspicion(), profile.getTotalAlerts(), learningSummary);
        line = sanitizeBotReply(line);
        if (!line.isBlank()) {
            plugin.getAiChat().sendStaffNotice(line);
        }
    }

    private String pickBotVoiceLine(String botName, RiskTier tier, String playerName, int suspicion, int alerts, String learning) {
        List<String> normal = botConfig.getStringList("bot.ai.voice.normal");
        List<String> playful = botConfig.getStringList("bot.ai.voice.playful");
        List<String> danger = botConfig.getStringList("bot.ai.voice.danger");

        String template;
        double playfulRate = Math.max(0.0D, Math.min(1.0D, botConfig.getDouble("bot.ai.playful_rate", 0.18D)));
        if (tier.ordinal() >= RiskTier.DANGER.ordinal() && !danger.isEmpty()) {
            template = danger.get(ThreadLocalRandom.current().nextInt(danger.size()));
        } else if (ThreadLocalRandom.current().nextDouble() < playfulRate && !playful.isEmpty()) {
            template = playful.get(ThreadLocalRandom.current().nextInt(playful.size()));
        } else if (!normal.isEmpty()) {
            template = normal.get(ThreadLocalRandom.current().nextInt(normal.size()));
        } else {
            template = "[BotAI] {bot} tracking {player} | tier={tier} | suspicion={suspicion} | alerts={alerts}";
        }

        return template
                .replace("{bot}", botName)
                .replace("{player}", playerName)
                .replace("{tier}", tier.name())
                .replace("{suspicion}", String.valueOf(suspicion))
                .replace("{alerts}", String.valueOf(alerts))
                .replace("{learning}", learning == null ? "learning=off" : learning);
    }

    private String sanitizeBotReply(String raw) {
        if (raw == null) {
            return "";
        }
        String clean = raw.replaceAll("\\s+", " ").trim();
        if (clean.isBlank()) {
            return "";
        }

        String safeReply = getBotRuleString("bot_rule.ai.safe_reply", "Nội dung này không phù hợp với luật bot.");
        for (String topic : getBotRuleList("bot_rule.ai.blocked_topics")) {
            if (!topic.isBlank() && containsIgnoreCase(clean, topic)) {
                return safeReply;
            }
        }
        for (String phrase : getBotRuleList("bot_rule.ai.blocked_phrases")) {
            if (!phrase.isBlank() && containsIgnoreCase(clean, phrase)) {
                return safeReply;
            }
        }

        int maxChars = Math.max(50, getBotRuleInt("bot_rule.ai.max_reply_chars", 220));
        if (clean.length() > maxChars) {
            clean = clean.substring(0, maxChars).trim() + "...";
        }
        return clean;
    }

    private File ensureDataFile(String resourcePath, String legacyName) {
        File file = new File(plugin.getDataFolder(), resourcePath.replace("/", File.separator));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!file.exists()) {
            File legacy = new File(plugin.getDataFolder(), legacyName);
            if (legacy.exists()) {
                try {
                    Files.copy(legacy.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Không thể migrate " + legacyName + " -> " + resourcePath + ": " + ex.getMessage());
                }
            } else {
                plugin.saveResource(resourcePath, false);
            }
        }
        return file;
    }

    private void loadBotsFromConfig() {
        bots.clear();
        if (bodyConfig == null) {
            return;
        }
        ConfigurationSection section = bodyConfig.getConfigurationSection("bots");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null) {
                continue;
            }
            BotProfile bot = new BotProfile();
            bot.key = normalizeKey(key);
            bot.name = node.getString("name", key);
            bot.displayName = node.getString("display_name", buildDisplayName(bot.name));
            bot.world = node.getString("world", "world");
            bot.x = node.getDouble("x", 0D);
            bot.y = node.getDouble("y", 64D);
            bot.z = node.getDouble("z", 0D);
            bot.yaw = (float) node.getDouble("yaw", 0F);
            bot.pitch = (float) node.getDouble("pitch", 0F);
            bot.followEnabled = node.getBoolean("follow", true);
            bot.lookEnabled = node.getBoolean("look", true);
            bot.walkEnabled = node.getBoolean("walk", true);
            bot.jumpEnabled = node.getBoolean("jump", false);
            bot.hitEnabled = node.getBoolean("hit", false);
            bot.turnGroundEnabled = node.getBoolean("turn_ground", true);
            bot.invulnerable = node.getBoolean("invulnerable", true);
            bot.actions.addAll(node.getStringList("actions"));
            bots.put(bot.key, bot);
        }
    }

    private void saveBots() {
        if (bodyConfig == null) {
            return;
        }
        bodyConfig.set("bots", null);
        for (BotProfile bot : bots.values()) {
            String path = "bots." + bot.key;
            bodyConfig.set(path + ".name", bot.name);
            bodyConfig.set(path + ".display_name", bot.displayName);
            bodyConfig.set(path + ".world", bot.world);
            bodyConfig.set(path + ".x", bot.x);
            bodyConfig.set(path + ".y", bot.y);
            bodyConfig.set(path + ".z", bot.z);
            bodyConfig.set(path + ".yaw", bot.yaw);
            bodyConfig.set(path + ".pitch", bot.pitch);
            bodyConfig.set(path + ".follow", bot.followEnabled);
            bodyConfig.set(path + ".look", bot.lookEnabled);
            bodyConfig.set(path + ".walk", bot.walkEnabled);
            bodyConfig.set(path + ".jump", bot.jumpEnabled);
            bodyConfig.set(path + ".hit", bot.hitEnabled);
            bodyConfig.set(path + ".turn_ground", bot.turnGroundEnabled);
            bodyConfig.set(path + ".invulnerable", bot.invulnerable);
            bodyConfig.set(path + ".actions", new ArrayList<>(bot.actions));
        }
        saveBodyConfig();
    }

    private void saveSelection() {
        if (botConfig == null) {
            return;
        }
        botConfig.set("bot.selected", selectedBotKey == null ? "" : selectedBotKey);
        saveBotConfig();
    }

    private BotProfile getSelectedBot() {
        if (selectedBotKey == null || !bots.containsKey(selectedBotKey)) {
            if (bots.isEmpty()) {
                return null;
            }
            selectedBotKey = bots.keySet().iterator().next();
            saveSelection();
        }
        return bots.get(selectedBotKey);
    }

    private BotProfile getActiveBot() {
        if (activeBotKey == null) {
            return null;
        }
        return bots.get(activeBotKey);
    }

    private BotProfile findBot(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = normalizeKey(input);
        if (bots.containsKey(normalized)) {
            return bots.get(normalized);
        }
        for (BotProfile bot : bots.values()) {
            if (bot.name.equalsIgnoreCase(input)) {
                return bot;
            }
        }
        return null;
    }

    private Location resolveSpawnLocation(CommandSender sender) {
        if (sender instanceof Player) {
            return ((Player) sender).getLocation();
        }
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }
        return worlds.get(0).getSpawnLocation();
    }

    private String defaultBotName() {
        if (bodyConfig != null) {
            String name = bodyConfig.getString("body.name", "");
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return botConfig == null ? "Grox" : botConfig.getString("bot.bot_name", "Grox");
    }

    private String buildDisplayName(String botName) {
        String safe = botName == null || botName.isBlank() ? "Bot" : botName.trim();
        if (safe.equalsIgnoreCase("Grox")) {
            return "&c&l[ADMIN] &bGrox";
        }
        String template = bodyConfig == null ? "&f{bot_name}" : bodyConfig.getString("body.display_name", "&f{bot_name}");
        return template.replace("{bot_name}", safe);
    }

    private String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private RiskTier parseTier(String raw) {
        if (raw == null || raw.isBlank()) {
            return RiskTier.ALERT;
        }
        try {
            return RiskTier.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RiskTier.ALERT;
        }
    }

    private boolean parseBoolean(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        return normalized.equals("true")
                || normalized.equals("on")
                || normalized.equals("yes")
                || normalized.equals("1")
                || normalized.equals("bat")
                || normalized.equals("enable");
    }

    private void applyDefaults(FileConfiguration config, String resourceName) {
        InputStream defaultsStream = plugin.getResource(resourceName);
        if (defaultsStream == null) {
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultsStream, StandardCharsets.UTF_8));
        config.setDefaults(defaults);
    }

    private boolean saveBotConfig() {
        try {
            botConfig.save(botFile);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Không thể lưu bot.yml: " + ex.getMessage());
            return false;
        }
    }

    private boolean saveBodyConfig() {
        try {
            bodyConfig.save(bodyFile);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Không thể lưu bot_body.yml: " + ex.getMessage());
            return false;
        }
    }

    private void logBotEvent(String eventType, Player target, String reason) {
        if (plugin.getDatabaseManager() == null) {
            return;
        }
        BotProfile bot = getActiveBot();
        String botName = bot == null ? defaultBotName() : bot.name;
        String targetName = target == null ? "" : target.getName();
        String location = "";
        if (target != null && target.getLocation() != null && target.getWorld() != null) {
            location = target.getWorld().getName() + "@"
                    + target.getLocation().getBlockX() + ","
                    + target.getLocation().getBlockY() + ","
                    + target.getLocation().getBlockZ();
        }
        plugin.getDatabaseManager().logBotEvent(botName, targetName, reason, eventType, location);
    }

    private boolean shouldApplyBotRules() {
        return plugin.getBotRuleConfig() != null && plugin.getBotRuleConfig().getBoolean("bot_rule.enabled", true);
    }

    private boolean isRuleAllowed(String path, boolean def) {
        if (!shouldApplyBotRules()) {
            return def;
        }
        return getBotRuleBoolean(path, def);
    }

    private boolean getBotRuleBoolean(String path, boolean def) {
        return plugin.getBotRuleConfig() == null ? def : plugin.getBotRuleConfig().getBoolean(path, def);
    }

    private int getBotRuleInt(String path, int def) {
        return plugin.getBotRuleConfig() == null ? def : plugin.getBotRuleConfig().getInt(path, def);
    }

    private long getBotRuleLong(String path, long def) {
        return plugin.getBotRuleConfig() == null ? def : plugin.getBotRuleConfig().getLong(path, def);
    }

    private double getBotRuleDouble(String path, double def) {
        return plugin.getBotRuleConfig() == null ? def : plugin.getBotRuleConfig().getDouble(path, def);
    }

    private String getBotRuleString(String path, String def) {
        return plugin.getBotRuleConfig() == null ? def : plugin.getBotRuleConfig().getString(path, def);
    }

    private List<String> getBotRuleList(String path) {
        if (plugin.getBotRuleConfig() == null) {
            return List.of();
        }
        List<String> raw = plugin.getBotRuleConfig().getStringList(path);
        List<String> cleaned = new ArrayList<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return cleaned;
    }

    private int scaledWeight(int base, double multiplier) {
        return Math.max(1, (int) Math.round(Math.max(1, base) * multiplier));
    }

    private void faceLocation(Location source, Location target) {
        if (source == null || target == null || source.getWorld() != target.getWorld()) {
            return;
        }
        if (source.toVector().distanceSquared(target.toVector()) < 0.0001D) {
            return;
        }
        source.setDirection(target.toVector().subtract(source.toVector()));
    }

    private double angleDistance(float a, float b) {
        double delta = Math.abs(a - b) % 360.0D;
        return delta > 180.0D ? 360.0D - delta : delta;
    }

    private double lerp(double from, double to, double t) {
        return from + ((to - from) * clamp(t, 0.0D, 1.0D));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String buildDisplayNameJson(BotProfile bot) {
        String safeName = bot == null || bot.name == null || bot.name.isBlank() ? "Bot" : bot.name.trim();
        if (safeName.equalsIgnoreCase("Grox")) {
            return "{\"text\":\"\",\"extra\":[{\"text\":\"[ADMIN] \",\"color\":\"red\",\"bold\":true},{\"text\":\"Grox\",\"color\":\"aqua\"}]}";
        }

        String plain = safeName;
        String jsonColor = "white";
        boolean bold = false;

        StringBuilder json = new StringBuilder("{\"text\":\"")
                .append(escapeForJson(plain))
                .append("\"");
        if (!jsonColor.isBlank()) {
            json.append(",\"color\":\"").append(jsonColor).append("\"");
        }
        if (bold) {
            json.append(",\"bold\":true");
        }
        json.append("}");
        return json.toString();
    }

    private String escapeForJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String round(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private Entity selectFirstEntity(String selector) {
        try {
            List<Entity> entities = Bukkit.selectEntities(Bukkit.getConsoleSender(), selector);
            return entities.isEmpty() ? null : entities.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }
}
