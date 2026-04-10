package me.aiadmin.system;

import me.aiadmin.AIAdmin;
import me.aiadmin.ai.AIChat;
import me.aiadmin.system.SuspicionManager.PlayerRiskProfile;
import me.aiadmin.system.SuspicionManager.RiskTier;
import me.aiadmin.system.SuspicionManager.SkillClass;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerScanner {

    public enum BanResult {
        SUCCESS,
        BLOCKED_OP,
        FAILED
    }

    private final AIAdmin plugin;
    private final SuspicionManager suspicionManager;
    private final AIChat aiChat;
    private final Set<String> observingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, BukkitRunnable> observationTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> actionCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> heuristicCooldowns = new ConcurrentHashMap<>();
    private volatile int lagScanCap = -1;

    public ServerScanner(AIAdmin plugin, SuspicionManager suspicionManager, AIChat aiChat) {
        this.plugin = plugin;
        this.suspicionManager = suspicionManager;
        this.aiChat = aiChat;
    }

    public void startAutoScan() {
        long intervalTicks = Math.max(1, plugin.getConfig().getLong("scan.interval_minutes", 5L)) * 60L * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                scanServer(false);
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public void setLagScanCap(int lagScanCap) {
        this.lagScanCap = lagScanCap <= 0 ? -1 : lagScanCap;
    }

    public int getLagScanCap() {
        return lagScanCap;
    }

    public void scanServer(boolean manual) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            return;
        }

        boolean prioritize = plugin.getOptionConfig() == null
                || plugin.getOptionConfig().getBoolean("scan.prioritize_high_risk_first", true);
        if (prioritize) {
            players.sort((a, b) -> Integer.compare(
                    suspicionManager.getSuspicion(b.getName()),
                    suspicionManager.getSuspicion(a.getName())
            ));
        }

        int baseMax = Math.max(1, plugin.getConfig().getInt("scan.max_players_per_scan", 50));
        int maxPerScan = lagScanCap > 0 ? Math.min(baseMax, lagScanCap) : baseMax;
        if (players.size() > maxPerScan) {
            if (!prioritize) {
                Collections.shuffle(players);
            }
            players = new ArrayList<>(players.subList(0, maxPerScan));
        }

        for (Player player : players) {
            evaluatePlayer(player, manual);
        }
    }

    public void evaluatePlayer(Player player, boolean manual) {
        PlayerRiskProfile profile = suspicionManager.getOrCreateProfile(player.getName());
        suspicionManager.applyMovementHeuristics(player.getName());
        applyScanHeuristicBoost(profile);
        int score = profile.getSuspicion();
        RiskTier tier = suspicionManager.getRiskTier(score);
        SkillClass skillClass = suspicionManager.getSkillClass(profile);

        if (manual) {
            aiChat.sendStaffNotice("Manual scan: " + aiChat.buildPlayerAnalysis(player.getName()));
        }

        applyActionPipeline(player, profile, tier, skillClass);

        if (plugin.getBotManager() != null && plugin.getBotManager().shouldTriggerFor(tier)) {
            plugin.getBotManager().observeTarget(player, "risk-tier-" + tier.name());
        }
    }

    private void applyScanHeuristicBoost(PlayerRiskProfile profile) {
        String key = profile.getName().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long last = heuristicCooldowns.getOrDefault(key, 0L);
        if (now - last < 30000L) {
            return;
        }
        heuristicCooldowns.put(key, now);

        int bonus = 0;
        List<String> reasons = new ArrayList<>();

        if (profile.getHighCpsSamples() >= 2) {
            bonus += 2;
            reasons.add("high-cps");
        }
        if (profile.getSuspiciousAimSamples() >= 2) {
            bonus += 2;
            reasons.add("aim-pattern");
        }
        if (profile.getSuspiciousMoveSamples() >= 2) {
            bonus += 2;
            reasons.add("move-pattern");
        }
        if (profile.getHackConfidence() >= 60 && profile.getProConfidence() <= 45) {
            bonus += 3;
            reasons.add("hack-confidence");
        }

        if (bonus <= 0) {
            return;
        }
        suspicionManager.addSuspicion(profile.getName(), bonus, "scan-boost", String.join(",", reasons));
        if (plugin.getLearningManager() != null) {
            plugin.getLearningManager().recordHackSignal(profile.getName(), "scan-boost", bonus, bonus);
        }
    }

    public BanResult banPlayerByName(String playerName, String reason) {
        if (isProtectedOperator(playerName)) {
            sendLitebanNotice("warning", "Skip ban because " + playerName + " is OP.");
            logBan(playerName, reason, "0d", "blocked-op", true);
            return BanResult.BLOCKED_OP;
        }

        String defaultDuration = plugin.getConfig().getString("litebans.ban_duration", "3d");
        if (plugin.getLitebanConfig() != null) {
            defaultDuration = plugin.getLitebanConfig().getString("liteban.default_duration", defaultDuration);
        }
        String duration = suspicionManager.clampBanDuration(defaultDuration);

        String defaultReason = plugin.getConfig().getString("litebans.ban_reason", "AI phát hiện gian lận");
        if (plugin.getLitebanConfig() != null) {
            defaultReason = plugin.getLitebanConfig().getString("liteban.default_reason", defaultReason);
        }
        String finalReason = (reason == null || reason.isBlank()) ? defaultReason : reason;

        boolean litebanFeatureEnabled = plugin.getConfig().getBoolean("litebans.enabled", true);
        if (!litebanFeatureEnabled) {
            return applyInternalTempBan(playerName, finalReason, duration);
        }

        boolean litebanEnabled = plugin.isPluginIntegrationEnabled("liteban");
        boolean litebansInstalled = Bukkit.getPluginManager().isPluginEnabled("LiteBans");
        if (litebanEnabled && litebansInstalled) {
            String template = plugin.getLitebanConfig() == null
                    ? "litebans:ban {player} {duration} {reason}"
                    : plugin.getLitebanConfig().getString("liteban.commands.ban", "litebans:ban {player} {duration} {reason}");
            String command = template
                    .replace("{player}", playerName)
                    .replace("{duration}", duration)
                    .replace("{reason}", finalReason);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            sendLitebanNotice("success", "Đã ban " + playerName + " qua LiteBans trong " + duration + ".");
            logBan(playerName, finalReason, duration, "litebans", false);
            return BanResult.SUCCESS;
        }

        if (litebanEnabled && !litebansInstalled) {
            boolean fallbackAllowed = plugin.getPluginSettingsConfig() == null
                    || plugin.getPluginSettingsConfig().getBoolean("behavior.fallback_to_internal_when_missing", true);
            if (!fallbackAllowed) {
                sendLitebanNotice("error", "Thiếu LiteBans và fallback đang tắt. Không thể ban " + playerName + ".");
                return BanResult.FAILED;
            }
            sendLitebanNotice("warning", "Đã bật LiteBans nhưng plugin chưa có. Chuyển sang tempban nội bộ cho " + playerName + ".");
        }
        return applyInternalTempBan(playerName, finalReason, duration);
    }

    public BanResult tempBanPlayerByName(String playerName, int days, String reason) {
        return tempBanPlayerByName(playerName, days + "d", reason);
    }

    public BanResult tempBanPlayerByName(String playerName, String duration, String reason) {
        if (isProtectedOperator(playerName)) {
            sendLitebanNotice("warning", "Bỏ qua tempban vì " + playerName + " là OP.");
            logBan(playerName, reason, "0d", "blocked-op", true);
            return BanResult.BLOCKED_OP;
        }
        String safeDuration = normalizeDuration(duration);
        String defaultReason = plugin.getLitebanConfig() == null
                ? "AI phát hiện gian lận"
                : plugin.getLitebanConfig().getString("liteban.default_reason", "AI phát hiện gian lận");
        String finalReason = (reason == null || reason.isBlank()) ? defaultReason : reason;
        return applyInternalTempBan(playerName, finalReason, safeDuration);
    }

    public BanResult kickPlayerByName(String playerName, String reason) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online == null || !online.isOnline()) {
            return BanResult.FAILED;
        }

        String finalReason = (reason == null || reason.isBlank())
                ? plugin.tr(plugin.getActiveConfigProfile(),
                "AIAdmin phát hiện hành vi bất thường. Vui lòng chờ staff kiểm tra.",
                "AIAdmin detected unusual behavior. Please wait for staff review.")
                : reason;

        boolean litebanEnabled = plugin.isPluginIntegrationEnabled("liteban");
        boolean litebansInstalled = Bukkit.getPluginManager().isPluginEnabled("LiteBans");
        if (litebanEnabled && litebansInstalled && plugin.getLitebanConfig() != null) {
            String command = plugin.getLitebanConfig()
                    .getString("liteban.commands.kick", "litebans:kick {player} {reason}")
                    .replace("{player}", playerName)
                    .replace("{reason}", finalReason);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            online.kickPlayer(color(finalReason));
        }

        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordKick(playerName);
        }
        logBan(playerName, finalReason, "kick", "kick", false);
        return BanResult.SUCCESS;
    }

    public void reportPlayer(CommandSender reporter, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            reporter.sendMessage(color(plugin.tr(reporter,
                    "&cKhông tìm thấy người chơi online: " + playerName,
                    "&cCould not find an online player named: " + playerName)));
            return;
        }

        int points = plugin.getOptionConfig().getInt("anticheat.report_points", 4);
        suspicionManager.recordAlert(target.getName(), "player-report", "report", points, "reported-by-" + reporter.getName());
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordReport(target.getName());
        }

        if (plugin.getOptionConfig().getBoolean("anticheat.notify_reporter", true)) {
            reporter.sendMessage(color(plugin.tr(reporter,
                    "&aAI đã ghi nhận report của bạn cho &f" + target.getName() + "&a.",
                    "&aAI has recorded your report for &f" + target.getName() + "&a.")));
        }
        logBotEvent(
                plugin.getConfig().getString("chat.admin_mode_name", "Grox"),
                target.getName(),
                "reported by " + reporter.getName(),
                "player_report",
                target
        );
        aiChat.sendStaffNotice(plugin.tr(plugin.getActiveConfigProfile(),
                "Report từ " + reporter.getName() + " về " + target.getName() + ". AI bắt đầu quan sát.",
                "Report from " + reporter.getName() + " about " + target.getName() + ". AI is starting observation."));
        observePlayer(reporter, target.getName(), "player-report", false);
    }
    public boolean observePlayer(CommandSender requester, String playerName, String reason, boolean notifyStaff) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            if (requester != null) {
                requester.sendMessage(color(plugin.tr(requester,
                        "&cKhông thể quan sát: " + playerName + " đang offline hoặc không tồn tại.",
                        "&cCannot observe: " + playerName + " is offline or missing.")));
            }
            return false;
        }

        String safeReason = (reason == null || reason.isBlank()) ? "manual-observe" : reason;
        if (plugin.getBotManager() != null) {
            plugin.getBotManager().observeTarget(target, safeReason);
        }

        boolean started = startObservation(target.getName());
        if (requester != null) {
            if (started) {
                requester.sendMessage(color(plugin.tr(requester,
                        "&aAI đã bắt đầu quan sát &f" + target.getName() + "&a.",
                        "&aAI has started observing &f" + target.getName() + "&a.")));
            } else {
                requester.sendMessage(color(plugin.tr(requester,
                        "&eAI đang quan sát &f" + target.getName() + "&e rồi.",
                        "&eAI is already observing &f" + target.getName() + "&e.")));
            }
        }
        if (notifyStaff && started) {
            aiChat.sendStaffNotice(plugin.tr(plugin.getActiveConfigProfile(),
                    "AI đang quan sát " + target.getName() + " (reason=" + safeReason + ").",
                    "AI is observing " + target.getName() + " (reason=" + safeReason + ")."));
        }
        return started;
    }

    public boolean stopObserving(CommandSender requester, String playerName, boolean notifyStaff) {
        String key = playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
        BukkitRunnable task = observationTasks.remove(key);
        boolean removed = observingPlayers.remove(key);
        if (task != null) {
            task.cancel();
            removed = true;
        }
        if (removed && plugin.getBotManager() != null && playerName != null
                && playerName.equalsIgnoreCase(plugin.getBotManager().getActiveTargetName())) {
            plugin.getBotManager().stopObservation("manual-stop");
        }

        if (requester != null) {
            if (removed) {
                requester.sendMessage(color(plugin.tr(requester,
                        "&aĐã dừng quan sát &f" + playerName + "&a.",
                        "&aStopped observing &f" + playerName + "&a.")));
            } else {
                requester.sendMessage(color(plugin.tr(requester,
                        "&eAI hiện không quan sát &f" + playerName + "&e.",
                        "&eAI is not currently observing &f" + playerName + "&e.")));
            }
        }
        if (notifyStaff && removed) {
            aiChat.sendStaffNotice(plugin.tr(plugin.getActiveConfigProfile(),
                    "AI đã dừng quan sát " + playerName + ".",
                    "AI stopped observing " + playerName + "."));
        }
        return removed;
    }
    private boolean startObservation(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        if (!observingPlayers.add(key)) {
            return false;
        }

        int durationSeconds = Math.max(15, plugin.getOptionConfig().getInt("anticheat.report_observe_seconds", 120));
        int intervalSeconds = Math.max(3, plugin.getOptionConfig().getInt("anticheat.report_observe_interval_seconds", 5));
        long periodTicks = intervalSeconds * 20L;
        int maxRounds = Math.max(1, durationSeconds / intervalSeconds);

        BukkitRunnable task = new BukkitRunnable() {
            private int rounds = 0;

            @Override
            public void run() {
                Player target = Bukkit.getPlayerExact(playerName);
                if (target == null || !target.isOnline()) {
                    observationTasks.remove(key);
                    observingPlayers.remove(key);
                    cancel();
                    return;
                }

                evaluatePlayer(target, false);
                rounds++;
                if (rounds >= maxRounds) {
                    observationTasks.remove(key);
                    observingPlayers.remove(key);
                    aiChat.sendStaffNotice(plugin.tr(plugin.getActiveConfigProfile(),
                            "Chu kỳ quan sát đã hoàn tất cho " + target.getName() + ".",
                            "Observation cycle completed for " + target.getName() + "."));
                    cancel();
                }
            }
        };
        observationTasks.put(key, task);
        task.runTaskTimer(plugin, 0L, periodTicks);
        return true;
    }
    private void applyActionPipeline(Player player, PlayerRiskProfile profile, RiskTier tier, SkillClass skillClass) {
        if (!plugin.getConfig().getBoolean("actions.pipeline.enabled", true)) {
            return;
        }
        String playerName = player.getName();
        int suspicion = profile.getSuspicion();

        RiskTier warnTier = parseTier(plugin.getConfig().getString("actions.pipeline.warn_tier", "WATCH"), RiskTier.WATCH);
        RiskTier flagTier = parseTier(plugin.getConfig().getString("actions.pipeline.flag_tier", "ALERT"), RiskTier.ALERT);
        RiskTier kickTier = parseTier(plugin.getConfig().getString("actions.pipeline.kick_tier", "DANGER"), RiskTier.DANGER);
        RiskTier banTier = parseTier(plugin.getConfig().getString("actions.pipeline.ban_tier", "SEVERE"), RiskTier.SEVERE);

        if (plugin.getConfig().getBoolean("actions.pipeline.do_warn", true)
                && tier.ordinal() >= warnTier.ordinal()
                && canRunAction(playerName, "warn")) {
            aiChat.sendStaffNotice(formatPipelineMessage(
                    plugin.getConfig().getString("actions.pipeline.messages.warn", "&e[Warn] {player} tier={tier} suspicion={suspicion}"),
                    playerName, tier, suspicion, skillClass
            ));
        }

        if (plugin.getConfig().getBoolean("actions.pipeline.do_flag", true)
                && tier.ordinal() >= flagTier.ordinal()
                && canRunAction(playerName, "flag")) {
            aiChat.sendStaffNotice(formatPipelineMessage(
                    plugin.getConfig().getString("actions.pipeline.messages.flag", "&6[Flag] {player} tier={tier} suspicion={suspicion}"),
                    playerName, tier, suspicion, skillClass
            ));
        }

        boolean protectLikelyPro = plugin.getConfig().getBoolean("actions.pipeline.protection.relax_for_likely_pro", true);
        if (protectLikelyPro && skillClass == SkillClass.PRO && tier.ordinal() <= RiskTier.ALERT.ordinal()) {
            return;
        }

        if (plugin.getConfig().getBoolean("actions.pipeline.do_kick", true)
                && tier.ordinal() >= kickTier.ordinal()
                && canRunAction(playerName, "kick")) {
            if (!isProtectedOperator(playerName)) {
                String kickMessage = plugin.getConfig().getString("actions.kick_message", "AIAdmin phát hiện hành vi bất thường. Vui lòng chờ staff kiểm tra.");
                if (kickPlayerByName(playerName, kickMessage) == BanResult.SUCCESS) {
                    aiChat.sendStaffNotice(formatPipelineMessage(
                            plugin.getConfig().getString("actions.pipeline.messages.kick", "&c[Kick] {player} tier={tier} suspicion={suspicion}"),
                            playerName, tier, suspicion, skillClass
                    ));
                }
            }
        }

        boolean autoTempbanHack = plugin.getConfig().getBoolean("actions.pipeline.auto_tempban_hack_likely", true);
        RiskTier tempbanTier = parseTier(plugin.getConfig().getString("actions.pipeline.tempban_tier", "DANGER"), RiskTier.DANGER);
        if (autoTempbanHack
                && tier.ordinal() >= tempbanTier.ordinal()
                && skillClass == SkillClass.HACK_LIKELY
                && canRunAction(playerName, "tempban_hack")) {
            if (!isProtectedOperator(playerName)) {
                String reason = plugin.getConfig().getString("actions.pipeline.hack_tempban_reason", "AI phát hiện hành vi giống hack");
                tempBanPlayerByName(playerName, 1, reason);
                aiChat.sendStaffNotice(formatPipelineMessage(
                        plugin.getConfig().getString("actions.pipeline.messages.ban", "&4[Ban] {player} tier={tier} suspicion={suspicion}"),
                        playerName, tier, suspicion, skillClass
                ) + color(" &7(tempban 1d)"));
            }
        }

        if (plugin.getConfig().getBoolean("actions.pipeline.do_ban", false)
                && tier.ordinal() >= banTier.ordinal()
                && canRunAction(playerName, "ban")) {
            if (!isProtectedOperator(playerName)) {
                String reason = plugin.getConfig().getString("actions.pipeline.ban_reason", "AI phát hiện hành vi gian lận mức rủi ro cao");
                banPlayerByName(playerName, reason);
                aiChat.sendStaffNotice(formatPipelineMessage(
                        plugin.getConfig().getString("actions.pipeline.messages.ban", "&4[Ban] {player} tier={tier} suspicion={suspicion}"),
                        playerName, tier, suspicion, skillClass
                ));
            }
        }
    }

    private boolean canRunAction(String playerName, String actionName) {
        int cooldownSeconds = Math.max(5, plugin.getConfig().getInt("actions.pipeline.action_cooldown_seconds", 45));
        String key = playerName.toLowerCase(Locale.ROOT) + ":" + actionName.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long last = actionCooldowns.getOrDefault(key, 0L);
        if (now - last < cooldownSeconds * 1000L) {
            return false;
        }
        actionCooldowns.put(key, now);
        return true;
    }

    private String formatPipelineMessage(String template, String player, RiskTier tier, int suspicion, SkillClass skillClass) {
        return color(template
                .replace("{player}", player)
                .replace("{tier}", tier.name())
                .replace("{suspicion}", String.valueOf(suspicion))
                .replace("{skill}", skillClass.name()));
    }

    private BanResult applyInternalTempBan(String playerName, String reason, String duration) {
        if (isProtectedOperator(playerName)) {
            return BanResult.BLOCKED_OP;
        }

        String safeDuration = normalizeDuration(duration);
        Date expiresAt = Date.from(Instant.now().plusMillis(parseDurationMillis(safeDuration)));
        Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expiresAt, "AIAdmin");

        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && online.isOnline()) {
            online.kickPlayer("Thời gian cấm: " + safeDuration + " | Lý do: " + reason);
        }
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordTempBan(playerName);
        }
        sendLitebanNotice("info", "Đã tempban nội bộ " + playerName + " trong " + safeDuration + ".");
        logBan(playerName, reason, safeDuration, "internal-tempban", false);
        return BanResult.SUCCESS;
    }

    private boolean isProtectedOperator(String playerName) {
        boolean exemptOps = plugin.getLitebanConfig() == null
                || plugin.getLitebanConfig().getBoolean("liteban.exempt_ops", true);
        if (!exemptOps) {
            return false;
        }

        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && online.isOp()) {
            return true;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        return offline != null && offline.isOp();
    }

    public boolean isDurationToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return false;
        }
        char unit = normalized.charAt(normalized.length() - 1);
        if (unit != 'm' && unit != 'h' && unit != 'd') {
            return false;
        }
        try {
            return Integer.parseInt(normalized.substring(0, normalized.length() - 1)) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String normalizeDuration(String duration) {
        if (!isDurationToken(duration)) {
            return "3d";
        }
        String normalized = duration.trim().toLowerCase(Locale.ROOT);
        int value = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
        char unit = normalized.charAt(normalized.length() - 1);
        long millis = switch (unit) {
            case 'm' -> value * 60_000L;
            case 'h' -> value * 3_600_000L;
            default -> value * 86_400_000L;
        };
        long maxMillis = 45L * 86_400_000L;
        millis = Math.max(60_000L, Math.min(maxMillis, millis));

        if (millis % 86_400_000L == 0L) {
            return (millis / 86_400_000L) + "d";
        }
        if (millis % 3_600_000L == 0L) {
            return (millis / 3_600_000L) + "h";
        }
        return Math.max(1L, millis / 60_000L) + "m";
    }

    private long parseDurationMillis(String duration) {
        String normalized = normalizeDuration(duration);
        int value = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
        char unit = normalized.charAt(normalized.length() - 1);
        return switch (unit) {
            case 'm' -> value * 60_000L;
            case 'h' -> value * 3_600_000L;
            default -> value * 86_400_000L;
        };
    }

    private RiskTier parseTier(String raw, RiskTier def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return RiskTier.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private void logBan(String playerName, String reason, String duration, String method, boolean blockedOp) {
        if (plugin.getDatabaseManager() == null) {
            return;
        }
        plugin.getDatabaseManager().logBanEvent(playerName, reason, duration, method, blockedOp, "AIAdmin");
    }

    private void logBotEvent(String botName, String targetName, String reason, String eventType, Player targetPlayer) {
        if (plugin.getDatabaseManager() == null || targetPlayer == null || targetPlayer.getLocation() == null || targetPlayer.getWorld() == null) {
            return;
        }
        String location = targetPlayer.getWorld().getName() + "@"
                + targetPlayer.getLocation().getBlockX() + ","
                + targetPlayer.getLocation().getBlockY() + ","
                + targetPlayer.getLocation().getBlockZ();
        plugin.getDatabaseManager().logBotEvent(botName, targetName, reason, eventType, location);
    }

    private void sendLitebanNotice(String type, String message) {
        String prefix = plugin.getLitebanConfig() == null
                ? "&8[LiteBans] "
                : plugin.getLitebanConfig().getString("liteban.colors.prefix", "&8[LiteBans] ");
        String typeColor;
        if ("success".equalsIgnoreCase(type)) {
            typeColor = plugin.getLitebanConfig() == null ? "&a" : plugin.getLitebanConfig().getString("liteban.colors.success", "&a");
        } else if ("warning".equalsIgnoreCase(type)) {
            typeColor = plugin.getLitebanConfig() == null ? "&6" : plugin.getLitebanConfig().getString("liteban.colors.warning", "&6");
        } else if ("error".equalsIgnoreCase(type)) {
            typeColor = plugin.getLitebanConfig() == null ? "&c" : plugin.getLitebanConfig().getString("liteban.colors.error", "&c");
        } else {
            typeColor = plugin.getLitebanConfig() == null ? "&b" : plugin.getLitebanConfig().getString("liteban.colors.info", "&b");
        }

        String normalizedType = type == null ? "info" : type.toLowerCase(Locale.ROOT);
        String template = plugin.getLitebanConfig() == null
                ? "{prefix}{color}{message}"
                : plugin.getLitebanConfig().getString("liteban.ui.templates." + normalizedType, "{prefix}{color}{message}");
        if (template == null || template.isBlank()) {
            template = "{prefix}{color}{message}";
        }
        String rendered = template
                .replace("{prefix}", prefix)
                .replace("{color}", typeColor)
                .replace("{message}", message);

        boolean useFrame = plugin.getLitebanConfig() != null
                && plugin.getLitebanConfig().getBoolean("liteban.ui.frame.enabled", false);
        if (useFrame) {
            String top = plugin.getLitebanConfig().getString("liteban.ui.frame.top", "&8&m------------------------------");
            aiChat.sendStaffNotice(color(top));
        }
        aiChat.sendStaffNotice(color(rendered));
        if (useFrame) {
            String bottom = plugin.getLitebanConfig().getString("liteban.ui.frame.bottom", "&8&m------------------------------");
            aiChat.sendStaffNotice(color(bottom));
        }
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }
}
