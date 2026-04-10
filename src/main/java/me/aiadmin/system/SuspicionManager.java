package me.aiadmin.system;

import me.aiadmin.AIAdmin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SuspicionManager {

    public enum RiskTier {
        CLEAR,
        WATCH,
        ALERT,
        DANGER,
        SEVERE
    }

    public enum ThreatLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum SkillClass {
        PRO,
        BALANCED,
        HACK_LIKELY
    }

    public static class PlayerRiskProfile {
        private final String name;
        private int suspicion;
        private int totalAlerts;
        private String lastKnownIp;
        private String lastSuspiciousWorld;
        private double lastSuspiciousX;
        private double lastSuspiciousY;
        private double lastSuspiciousZ;
        private long lastSuspiciousAt;

        private double totalDistance;
        private double maxMoveDelta;
        private int movementSamples;

        private int hackConfidence;
        private int proConfidence;
        private int suspiciousAimSamples;
        private int suspiciousMoveSamples;
        private int highCpsSamples;
        private int legitCombatSamples;
        private long lastCombatMillis;

        private final Map<String, Integer> alertCounts = new HashMap<>();
        private final Deque<String> recentReasons = new ArrayDeque<>();
        private final Deque<Long> clickTimes = new ArrayDeque<>();

        public PlayerRiskProfile(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public int getSuspicion() {
            return suspicion;
        }

        public int getTotalAlerts() {
            return totalAlerts;
        }

        public String getLastKnownIp() {
            return lastKnownIp;
        }

        public long getLastSuspiciousAt() {
            return lastSuspiciousAt;
        }

        public String getLastSuspiciousLocationSummary() {
            if (lastSuspiciousWorld == null || lastSuspiciousWorld.isBlank()) {
                return "chưa rõ";
            }
            return lastSuspiciousWorld + " @ "
                    + round(lastSuspiciousX) + ", "
                    + round(lastSuspiciousY) + ", "
                    + round(lastSuspiciousZ);
        }

        public Map<String, Integer> getAlertCounts() {
            return Collections.unmodifiableMap(alertCounts);
        }

        public int getHackConfidence() {
            return hackConfidence;
        }

        public int getProConfidence() {
            return proConfidence;
        }

        public int getSuspiciousAimSamples() {
            return suspiciousAimSamples;
        }

        public int getHighCpsSamples() {
            return highCpsSamples;
        }

        public int getSuspiciousMoveSamples() {
            return suspiciousMoveSamples;
        }

        public int getLegitCombatSamples() {
            return legitCombatSamples;
        }

        public long getLastCombatMillis() {
            return lastCombatMillis;
        }

        public void setLastKnownIp(String lastKnownIp) {
            this.lastKnownIp = lastKnownIp;
        }

        public void markSuspiciousLocation(Location location) {
            if (location == null || location.getWorld() == null) {
                return;
            }
            this.lastSuspiciousWorld = location.getWorld().getName();
            this.lastSuspiciousX = location.getX();
            this.lastSuspiciousY = location.getY();
            this.lastSuspiciousZ = location.getZ();
            this.lastSuspiciousAt = System.currentTimeMillis();
        }

        public void setLastCombatMillis(long lastCombatMillis) {
            this.lastCombatMillis = lastCombatMillis;
        }

        public void addSuspicion(int amount, String reason) {
            suspicion = Math.max(0, suspicion + amount);
            if (reason != null && !reason.isEmpty()) {
                recentReasons.addFirst(reason);
                while (recentReasons.size() > 8) {
                    recentReasons.removeLast();
                }
            }
        }

        public void reduceSuspicion(int amount) {
            suspicion = Math.max(0, suspicion - Math.max(0, amount));
        }

        public void registerAlert(String type, int amount, String detail) {
            totalAlerts++;
            alertCounts.merge(type.toLowerCase(Locale.ROOT), 1, Integer::sum);
            addSuspicion(amount, type + ": " + detail);
        }

        public void recordMove(double delta) {
            movementSamples++;
            totalDistance += delta;
            maxMoveDelta = Math.max(maxMoveDelta, delta);
        }

        public double consumeMaxMoveDelta() {
            double snapshot = maxMoveDelta;
            maxMoveDelta = 0D;
            return snapshot;
        }

        public void recordClick(long nowMillis) {
            clickTimes.addLast(nowMillis);
            trimClicks(nowMillis, 3000L);
        }

        public int getCps(long nowMillis, long windowMillis) {
            trimClicks(nowMillis, windowMillis);
            return clickTimes.size();
        }

        public void markSuspiciousAim() {
            suspiciousAimSamples++;
        }

        public void markSuspiciousMove() {
            suspiciousMoveSamples++;
        }

        public void markHighCps() {
            highCpsSamples++;
        }

        public void markLegitCombat() {
            legitCombatSamples++;
        }

        public void boostHackConfidence(int amount) {
            hackConfidence = clamp100(hackConfidence + Math.max(0, amount));
            if (amount > 0) {
                proConfidence = clamp100(proConfidence - Math.max(0, amount / 3));
            }
        }

        public void boostProConfidence(int amount) {
            proConfidence = clamp100(proConfidence + Math.max(0, amount));
            if (amount > 0) {
                hackConfidence = clamp100(hackConfidence - Math.max(0, amount / 4));
            }
        }

        public String describeMovement() {
            if (movementSamples == 0) {
                return "chưa có mẫu";
            }
            double average = totalDistance / movementSamples;
            return "trung bình=" + round(average) + ", tối đa=" + round(maxMoveDelta) + ", mẫu=" + movementSamples;
        }

        public String describeLearning() {
            return "hack_conf=" + hackConfidence
                    + ", pro_conf=" + proConfidence
                    + ", aim=" + suspiciousAimSamples
                    + ", cps_cao=" + highCpsSamples
                    + ", combat_hợp_lệ=" + legitCombatSamples;
        }

        private void trimClicks(long nowMillis, long windowMillis) {
            while (!clickTimes.isEmpty() && nowMillis - clickTimes.peekFirst() > windowMillis) {
                clickTimes.removeFirst();
            }
        }

        private int clamp100(int value) {
            return Math.max(0, Math.min(100, value));
        }

        private String round(double value) {
            return String.format(Locale.US, "%.2f", value);
        }
    }

    private static class IpProfile {
        private final Set<String> accounts = new HashSet<>();
        private final Deque<Long> joinTimes = new ArrayDeque<>();
        private boolean flagged;
    }

    private final AIAdmin plugin;
    private final Map<String, PlayerRiskProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, IpProfile> ipProfiles = new ConcurrentHashMap<>();
    private int recordedAlertCount;
    private BukkitRunnable decayTask;

    public SuspicionManager(AIAdmin plugin) {
        this.plugin = plugin;
    }

    public void startLearningTasks() {
        stopLearningTasks();
        if (plugin.getOptionConfig() == null) {
            return;
        }
        if (!plugin.getOptionConfig().getBoolean("anticheat.suspicion_decay.enabled", true)) {
            return;
        }

        int intervalMinutes = Math.max(1, plugin.getOptionConfig().getInt("anticheat.suspicion_decay.interval_minutes", 20));
        int decayPoints = Math.max(1, plugin.getOptionConfig().getInt("anticheat.suspicion_decay.points_per_interval", 1));
        long periodTicks = intervalMinutes * 60L * 20L;

        decayTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerRiskProfile profile : profiles.values()) {
                    SkillClass skillClass = getSkillClass(profile);
                    if (skillClass == SkillClass.PRO && profile.getSuspicion() > 0) {
                        profile.reduceSuspicion(decayPoints);
                    }
                }
            }
        };
        decayTask.runTaskTimer(plugin, periodTicks, periodTicks);
    }

    public void stopLearningTasks() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    public void handleJoin(Player player) {
        String playerName = player.getName();
        PlayerRiskProfile profile = getOrCreateProfile(playerName);
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return;
        }

        String ip = address.getAddress().getHostAddress();
        profile.setLastKnownIp(ip);

        IpProfile ipProfile = ipProfiles.computeIfAbsent(ip, key -> new IpProfile());
        ipProfile.accounts.add(playerName.toLowerCase(Locale.ROOT));
        ipProfile.joinTimes.addLast(Instant.now().toEpochMilli());
        trimOldJoins(ipProfile.joinTimes);

        int maxAccounts = plugin.getConfig().getInt("scan.max_accounts_per_ip", 4);
        int joinBurstLimit = plugin.getConfig().getInt("scan.join_burst_limit", 3);

        if (ipProfile.accounts.size() > maxAccounts) {
            ipProfile.flagged = true;
            addBehaviorSuspicion(profile, "alt-account", plugin.getConfig().getInt("suspicion.points.alt_account", 4), "too-many-accounts-on-ip");
            notifyStaff(playerName + " đang dùng IP có " + ipProfile.accounts.size() + " tài khoản.");
        }

        if (ipProfile.joinTimes.size() >= joinBurstLimit) {
            ipProfile.flagged = true;
            addBehaviorSuspicion(profile, "join-burst", plugin.getConfig().getInt("suspicion.points.join_burst", 3), "join-burst-detected");
            notifyStaff("Phát hiện đăng nhập dồn dập trên cùng IP của " + playerName + ".");
        }

        if (plugin.getConfig().getBoolean("scan.warn_player_on_alt_limit", true) && ipProfile.accounts.size() > maxAccounts) {
            player.sendMessage(color("&cHệ thống phát hiện quá nhiều tài khoản trên cùng IP. Staff sẽ kiểm tra thêm."));
        }
    }

    public void handleQuit(Player player) {
        PlayerRiskProfile profile = getOrCreateProfile(player.getName());
        InetSocketAddress address = player.getAddress();
        if (address != null && address.getAddress() != null) {
            profile.setLastKnownIp(address.getAddress().getHostAddress());
        }
    }

    public void captureMovement(Player player, Location from, Location to) {
        if (player == null || from == null || to == null || from.getWorld() != to.getWorld()) {
            return;
        }
        if (!shouldTrackBehavior(player)) {
            return;
        }

        double delta = from.distance(to);
        if (delta <= 0D) {
            return;
        }
        PlayerRiskProfile profile = getOrCreateProfile(player.getName());
        profile.recordMove(delta);

        double horizontal = Math.sqrt(Math.pow(to.getX() - from.getX(), 2) + Math.pow(to.getZ() - from.getZ(), 2));
        double verticalUp = Math.max(0D, to.getY() - from.getY());

        double speedThreshold = getOptionDouble("anticheat.behavior.speed_threshold_blocks_per_tick", 0.95D);
        double flyUpThreshold = getOptionDouble("anticheat.behavior.vertical_up_threshold", 0.75D);
        double tunedSpeedThreshold = Math.max(0.65D, speedThreshold * 0.92D);
        double burstSpeedThreshold = Math.max(tunedSpeedThreshold + 0.15D, tunedSpeedThreshold * 1.22D);
        double tunedFlyUpThreshold = Math.max(0.45D, flyUpThreshold * 0.90D);
        if (horizontal > tunedSpeedThreshold) {
            profile.markSuspiciousLocation(to);
            profile.markSuspiciousMove();
            addBehaviorSuspicion(profile, "speed", plugin.getConfig().getInt("suspicion.points.speed", 5), "horizontal=" + round(horizontal));
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "speed-move", horizontal, 4);
            }
            if (horizontal > burstSpeedThreshold) {
                profile.markSuspiciousLocation(to);
                addBehaviorSuspicion(profile, "speed-burst", 2, "burst-horizontal=" + round(horizontal));
                profile.boostHackConfidence(4);
                if (plugin.getLearningManager() != null) {
                    plugin.getLearningManager().recordHackSignal(profile.getName(), "speed-burst", horizontal, 5);
                }
            }
        } else {
            profile.boostProConfidence(1);
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordLegitSignal(profile.getName(), "move-legit", horizontal, 1);
            }
        }
        if (verticalUp > tunedFlyUpThreshold && !player.isOnGround() && !player.getAllowFlight()) {
            profile.markSuspiciousLocation(to);
            profile.markSuspiciousMove();
            addBehaviorSuspicion(profile, "fly", plugin.getConfig().getInt("suspicion.points.fly", 6), "vertical-up=" + round(verticalUp));
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "fly-up", verticalUp, 5);
            }
        }

        double yawDelta = angleDistance(from.getYaw(), to.getYaw());
        double pitchDelta = Math.abs(to.getPitch() - from.getPitch());
        double snapYaw = Math.max(40.0D, getOptionDouble("anticheat.behavior.snap_yaw_threshold", 65.0D) * 0.90D);
        double snapPitch = Math.max(1.0D, getOptionDouble("anticheat.behavior.snap_pitch_threshold", 1.2D) * 1.15D);
        if (yawDelta > snapYaw && pitchDelta < snapPitch) {
            profile.markSuspiciousLocation(to);
            profile.markSuspiciousAim();
            if (profile.getSuspiciousAimSamples() % 2 == 0) {
                addBehaviorSuspicion(profile, "aimassist", plugin.getConfig().getInt("suspicion.points.aimassist", 5), "snap-aim");
            } else {
                profile.boostHackConfidence(3);
            }
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "aim-snap", yawDelta, 3);
            }
        } else if (yawDelta > 0.4D) {
            profile.boostProConfidence(1);
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordLegitSignal(profile.getName(), "aim-legit", yawDelta, 1);
            }
        }
    }

    public void recordClick(Player player) {
        if (player == null || !shouldTrackBehavior(player)) {
            return;
        }
        PlayerRiskProfile profile = getOrCreateProfile(player.getName());
        long now = System.currentTimeMillis();
        profile.recordClick(now);
        int cps = profile.getCps(now, 1000L);

        int warnCps = Math.max(10, getOptionInt("anticheat.behavior.cps_warn_threshold", 14) - 1);
        int flagCps = Math.max(warnCps + 2, getOptionInt("anticheat.behavior.cps_flag_threshold", 18) - 1);
        if (cps >= flagCps) {
            profile.markSuspiciousLocation(player.getLocation());
            profile.markHighCps();
            addBehaviorSuspicion(profile, "autoclicker", plugin.getConfig().getInt("suspicion.points.autoclicker", 4), "cps=" + cps);
            profile.boostHackConfidence(7);
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "cps-flag", cps, 6);
            }
        } else if (cps >= warnCps) {
            profile.markSuspiciousLocation(player.getLocation());
            profile.markHighCps();
            addBehaviorSuspicion(profile, "click-cps", 1, "cps=" + cps);
            profile.boostHackConfidence(2);
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "cps-warn", cps, 2);
            }
        } else if (cps >= 6 && cps <= 12) {
            profile.markLegitCombat();
            profile.boostProConfidence(1);
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordLegitSignal(profile.getName(), "cps-legit", cps, 1);
            }
        }
    }

    public void recordCombatHit(Player damager, Player victim) {
        if (damager == null || victim == null || !shouldTrackBehavior(damager)) {
            return;
        }
        PlayerRiskProfile profile = getOrCreateProfile(damager.getName());
        long now = System.currentTimeMillis();

        double reach = damager.getEyeLocation().distance(victim.getLocation());
        double reachThreshold = Math.max(2.8D, getOptionDouble("anticheat.behavior.reach_threshold", 3.4D) - 0.10D);
        if (reach > reachThreshold) {
            profile.markSuspiciousLocation(damager.getLocation());
            addBehaviorSuspicion(profile, "reach", plugin.getConfig().getInt("suspicion.points.reach", 5), "reach=" + round(reach));
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "reach", reach, 5);
            }
        } else {
            profile.boostProConfidence(1);
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordLegitSignal(profile.getName(), "reach-legit", reach, 1);
            }
        }

        int minInterval = getOptionInt("anticheat.behavior.min_hit_interval_ms", 65) + 10;
        if (profile.getLastCombatMillis() > 0L && now - profile.getLastCombatMillis() < minInterval) {
            profile.markSuspiciousLocation(damager.getLocation());
            addBehaviorSuspicion(profile, "killaura", plugin.getConfig().getInt("suspicion.points.killaura", 6), "hit-interval-ms=" + (now - profile.getLastCombatMillis()));
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "hit-interval", now - profile.getLastCombatMillis(), 5);
            }
        } else {
            profile.boostProConfidence(1);
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordLegitSignal(profile.getName(), "combat-legit", minInterval, 1);
            }
        }
        profile.setLastCombatMillis(now);
        profile.markLegitCombat();
    }

    public void applyMovementHeuristics(String playerName) {
        PlayerRiskProfile profile = getOrCreateProfile(playerName);
        double maxMoveDelta = profile.consumeMaxMoveDelta();
        if (maxMoveDelta > plugin.getConfig().getDouble("scan.max_move_delta_watch", 1.25D)) {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null && online.isOnline()) {
                profile.markSuspiciousLocation(online.getLocation());
            }
            addBehaviorSuspicion(profile, "movement-spike", plugin.getConfig().getInt("suspicion.points.movement_spike", 1), "max-delta=" + round(maxMoveDelta));
            if (plugin.getLearningManager() != null) {
                plugin.getLearningManager().recordHackSignal(profile.getName(), "movement-spike", maxMoveDelta, 2);
            }
        }
    }

    public void addSuspicion(String playerName, int amount, String source, String detail) {
        PlayerRiskProfile profile = getOrCreateProfile(playerName);
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && online.isOnline()) {
            profile.markSuspiciousLocation(online.getLocation());
        }
        int adjusted = adjustPointsByLearning(profile, amount);
        profile.addSuspicion(adjusted, source + ": " + detail);
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordSuspicion(profile.getName());
        }
    }

    public void recordAlert(String playerName, String source, String type, int points, String detail) {
        PlayerRiskProfile profile = getOrCreateProfile(playerName);
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && online.isOnline()) {
            profile.markSuspiciousLocation(online.getLocation());
        }
        int adjusted = adjustPointsByLearning(profile, points);
        profile.registerAlert(type, adjusted, source + " " + detail);
        profile.boostHackConfidence(source.equalsIgnoreCase("console") ? 10 : 6);
        if (plugin.getLearningManager() != null) {
            plugin.getLearningManager().recordHackSignal(profile.getName(), "alert-" + type, adjusted, Math.max(2, adjusted));
        }
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordSuspicion(profile.getName());
        }
        recordedAlertCount++;

        RiskTier tier = getRiskTier(profile.getSuspicion());
        if (tier.ordinal() >= RiskTier.ALERT.ordinal()) {
            notifyStaff(playerName + " bị gắn cờ " + type + " từ " + source + " (+" + adjusted + ", tier=" + tier.name() + ").");
        }
    }

    public int getSuspicion(String playerName) {
        return getOrCreateProfile(playerName).getSuspicion();
    }

    public PlayerRiskProfile getOrCreateProfile(String playerName) {
        return profiles.computeIfAbsent(playerName.toLowerCase(Locale.ROOT), key -> new PlayerRiskProfile(playerName));
    }

    public List<PlayerRiskProfile> getTopProfiles(int limit) {
        List<PlayerRiskProfile> values = new ArrayList<>(profiles.values());
        values.sort(Comparator.comparingInt(PlayerRiskProfile::getSuspicion).reversed());
        return values.subList(0, Math.min(limit, values.size()));
    }

    public List<PlayerRiskProfile> getSuspiciousProfiles(int limit) {
        List<PlayerRiskProfile> values = new ArrayList<>();
        for (PlayerRiskProfile profile : profiles.values()) {
            if (getRiskTier(profile.getSuspicion()).ordinal() >= RiskTier.WATCH.ordinal()) {
                values.add(profile);
            }
        }
        values.sort(Comparator.comparingInt(PlayerRiskProfile::getSuspicion).reversed());
        return values.subList(0, Math.min(limit, values.size()));
    }

    public SkillClass getSkillClass(String playerName) {
        return getSkillClass(getOrCreateProfile(playerName));
    }

    public SkillClass getSkillClass(PlayerRiskProfile profile) {
        if (profile.getHackConfidence() >= profile.getProConfidence() + 15 && profile.getSuspicion() >= plugin.getConfig().getInt("scan.suspicion_watch", 7)) {
            return SkillClass.HACK_LIKELY;
        }
        if (profile.getProConfidence() >= profile.getHackConfidence() + 20 && profile.getSuspicion() < plugin.getConfig().getInt("scan.suspicion_danger", 22)) {
            return SkillClass.PRO;
        }
        return SkillClass.BALANCED;
    }

    public ThreatLevel getThreatLevel(int suspicion) {
        RiskTier tier = getRiskTier(suspicion);
        if (tier == RiskTier.ALERT) {
            return ThreatLevel.MEDIUM;
        }
        if (tier == RiskTier.DANGER || tier == RiskTier.SEVERE) {
            return ThreatLevel.HIGH;
        }
        return ThreatLevel.LOW;
    }

    public int countAtOrAbove(RiskTier tier) {
        int count = 0;
        for (PlayerRiskProfile profile : profiles.values()) {
            if (getRiskTier(profile.getSuspicion()).ordinal() >= tier.ordinal()) {
                count++;
            }
        }
        return count;
    }

    public int countFlaggedIps() {
        int count = 0;
        for (IpProfile ipProfile : ipProfiles.values()) {
            if (ipProfile.flagged) {
                count++;
            }
        }
        return count;
    }

    public int getRecordedAlertCount() {
        return recordedAlertCount;
    }

    public RiskTier getRiskTier(int suspicion) {
        int watch = plugin.getConfig().getInt("scan.suspicion_watch", 7);
        int alert = plugin.getConfig().getInt("scan.suspicion_alert", 13);
        int danger = plugin.getConfig().getInt("scan.suspicion_danger", 22);
        int severe = plugin.getConfig().getInt("scan.suspicion_severe", 32);

        if (suspicion >= severe) {
            return RiskTier.SEVERE;
        }
        if (suspicion >= danger) {
            return RiskTier.DANGER;
        }
        if (suspicion >= alert) {
            return RiskTier.ALERT;
        }
        if (suspicion >= watch) {
            return RiskTier.WATCH;
        }
        return RiskTier.CLEAR;
    }

    public String clampBanDuration(String configuredDuration) {
        String raw = configuredDuration == null ? "3d" : configuredDuration.trim().toLowerCase(Locale.ROOT);
        if (!raw.endsWith("d")) {
            return "3d";
        }
        try {
            int days = Integer.parseInt(raw.substring(0, raw.length() - 1));
            return Math.min(days, 45) + "d";
        } catch (NumberFormatException ex) {
            return "3d";
        }
    }

    private void addBehaviorSuspicion(PlayerRiskProfile profile, String source, int basePoints, String detail) {
        int adjusted = adjustPointsByLearning(profile, basePoints);
        profile.addSuspicion(adjusted, source + ": " + detail);
        profile.boostHackConfidence(Math.max(1, adjusted));
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordSuspicion(profile.getName());
        }
    }

    private int adjustPointsByLearning(PlayerRiskProfile profile, int basePoints) {
        int safeBase = Math.max(1, basePoints);
        double modifier = 1.0D + ((double) (profile.getHackConfidence() - profile.getProConfidence()) / 140.0D);
        modifier = Math.max(0.5D, Math.min(1.6D, modifier));
        int adjusted = Math.max(1, (int) Math.round(safeBase * modifier));
        if (plugin.getLearningManager() != null) {
            adjusted = plugin.getLearningManager().applyAdaptivePoints(profile.getName(), adjusted);
        }
        return adjusted;
    }

    private boolean shouldTrackBehavior(Player player) {
        if (player == null) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private void notifyStaff(String message) {
        String formatted = color("&c[AIAdmin] &f" + message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("aiadmin.admin")) {
                player.sendMessage(formatted);
            }
        }
        plugin.getLogger().info(message);
    }

    private void trimOldJoins(Deque<Long> joinTimes) {
        long now = Instant.now().toEpochMilli();
        long windowMillis = plugin.getConfig().getLong("scan.join_burst_window_seconds", 120L) * 1000L;
        while (!joinTimes.isEmpty() && now - joinTimes.peekFirst() > windowMillis) {
            joinTimes.removeFirst();
        }
    }

    private double angleDistance(float a, float b) {
        double delta = Math.abs(a - b) % 360.0D;
        return delta > 180.0D ? 360.0D - delta : delta;
    }

    private int getOptionInt(String path, int def) {
        return plugin.getOptionConfig() == null ? def : plugin.getOptionConfig().getInt(path, def);
    }

    private double getOptionDouble(String path, double def) {
        return plugin.getOptionConfig() == null ? def : plugin.getOptionConfig().getDouble(path, def);
    }

    private String round(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }
}
