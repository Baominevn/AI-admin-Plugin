package me.aiadmin.system;

import me.aiadmin.AIAdmin;
import me.aiadmin.ai.AIChat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class LagOptimizer {

    private final AIAdmin plugin;
    private final ServerScanner serverScanner;
    private final AIChat aiChat;

    private BukkitRunnable task;
    private double smoothedTps = 20.0D;
    private long lastSampleNanos;
    private long lastNoticeMillis;
    private long lastCriticalCommandMillis;

    public LagOptimizer(AIAdmin plugin, ServerScanner serverScanner, AIChat aiChat) {
        this.plugin = plugin;
        this.serverScanner = serverScanner;
        this.aiChat = aiChat;
    }

    public void start() {
        stop();
        if (!isEnabled()) {
            serverScanner.setLagScanCap(-1);
            return;
        }

        int sampleTicks = Math.max(20, getOptionInt("performance.lag_optimizer.sample_ticks", 20));
        lastSampleNanos = System.nanoTime();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                sampleAndApply(sampleTicks);
            }
        };
        task.runTaskTimer(plugin, sampleTicks, sampleTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        serverScanner.setLagScanCap(-1);
    }

    public String runManualOptimize(CommandSender sender) {
        sampleAndApply(Math.max(20, getOptionInt("performance.lag_optimizer.sample_ticks", 20)));
        String summary = "TPS~" + round(smoothedTps) + ", scanCap=" + serverScanner.getLagScanCap();
        if (sender != null) {
            sender.sendMessage(color("&6[Optimize] &f" + summary));
        }
        return summary;
    }

    public String getStatusSummary() {
        return "bật=" + isEnabled() + ", tps~" + round(smoothedTps) + ", lagScanCap=" + serverScanner.getLagScanCap();
    }

    private void sampleAndApply(int sampleTicks) {
        long now = System.nanoTime();
        double elapsedMs = Math.max(1.0D, (now - lastSampleNanos) / 1_000_000.0D);
        double expectedMs = sampleTicks * 50.0D;
        double sampleTps = 20.0D * (expectedMs / elapsedMs);
        sampleTps = Math.max(1.0D, Math.min(20.0D, sampleTps));
        smoothedTps = (smoothedTps * 0.85D) + (sampleTps * 0.15D);
        lastSampleNanos = now;

        double lowTps = getOptionDouble("performance.lag_optimizer.low_tps_threshold", 17.5D);
        double criticalTps = getOptionDouble("performance.lag_optimizer.critical_tps_threshold", 14.5D);
        int lowCap = Math.max(5, getOptionInt("performance.lag_optimizer.low_scan_cap", 30));
        int criticalCap = Math.max(5, getOptionInt("performance.lag_optimizer.critical_scan_cap", 15));
        int notifyCooldownSeconds = Math.max(10, getOptionInt("performance.lag_optimizer.notify_cooldown_seconds", 90));

        if (smoothedTps <= criticalTps) {
            serverScanner.setLagScanCap(criticalCap);
            maybeNotify("&c[Optimize] TPS critical (" + round(smoothedTps) + "). Scan cap set to " + criticalCap + ".", notifyCooldownSeconds);
            maybeRunCriticalCommands();
            return;
        }
        if (smoothedTps <= lowTps) {
            serverScanner.setLagScanCap(lowCap);
            maybeNotify("&e[Optimize] TPS low (" + round(smoothedTps) + "). Scan cap set to " + lowCap + ".", notifyCooldownSeconds);
            return;
        }
        serverScanner.setLagScanCap(-1);
    }

    private void maybeNotify(String message, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        if (now - lastNoticeMillis < cooldownSeconds * 1000L) {
            return;
        }
        lastNoticeMillis = now;
        aiChat.sendStaffNotice(message);
    }

    private void maybeRunCriticalCommands() {
        if (!getOptionBool("performance.lag_optimizer.run_commands_on_critical", false)) {
            return;
        }
        int cooldownSeconds = Math.max(30, getOptionInt("performance.lag_optimizer.critical_command_cooldown_seconds", 180));
        long now = System.currentTimeMillis();
        if (now - lastCriticalCommandMillis < cooldownSeconds * 1000L) {
            return;
        }
        lastCriticalCommandMillis = now;

        List<String> commands = plugin.getOptionConfig() == null
                ? java.util.Collections.emptyList()
                : plugin.getOptionConfig().getStringList("performance.lag_optimizer.critical_commands");
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String trimmed = command.startsWith("/") ? command.substring(1) : command;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed.trim());
        }
    }

    private boolean isEnabled() {
        return getOptionBool("performance.lag_optimizer.enabled", true);
    }

    private int getOptionInt(String path, int def) {
        return plugin.getOptionConfig() == null ? def : plugin.getOptionConfig().getInt(path, def);
    }

    private double getOptionDouble(String path, double def) {
        return plugin.getOptionConfig() == null ? def : plugin.getOptionConfig().getDouble(path, def);
    }

    private boolean getOptionBool(String path, boolean def) {
        return plugin.getOptionConfig() == null ? def : plugin.getOptionConfig().getBoolean(path, def);
    }

    private String round(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }
}
