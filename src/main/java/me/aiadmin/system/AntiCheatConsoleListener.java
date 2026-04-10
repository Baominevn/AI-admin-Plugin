package me.aiadmin.system;

import me.aiadmin.AIAdmin;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiCheatConsoleListener extends Handler {

    private static final Pattern ALERT_PATTERN = Pattern.compile(
            "(?i)([A-Za-z0-9_]{3,16}).{0,50}(KillAura|Speed|Reach|Fly|Scaffold|Timer|Velocity|AutoClicker|AimAssist).{0,50}(failed|flagged|detected|violation|vl)"
    );

    private final AIAdmin plugin;
    private final SuspicionManager suspicionManager;

    public AntiCheatConsoleListener(AIAdmin plugin, SuspicionManager suspicionManager) {
        this.plugin = plugin;
        this.suspicionManager = suspicionManager;
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || record.getMessage() == null) {
            return;
        }

        String message = record.getMessage();
        Matcher matcher = ALERT_PATTERN.matcher(message);
        if (!matcher.find()) {
            return;
        }

        String playerName = matcher.group(1);
        String checkType = matcher.group(2);
        int points = resolvePoints(checkType);
        suspicionManager.recordAlert(playerName, "console", checkType, points, message);

        if (plugin.getConfig().getBoolean("console.debug_matches", false)) {
            plugin.getLogger().info("Parsed anti-cheat alert for " + playerName + " from console.");
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }

    private int resolvePoints(String checkType) {
        String key = "suspicion.points." + checkType.toLowerCase(Locale.ROOT);
        return plugin.getConfig().getInt(key, plugin.getConfig().getInt("suspicion.points.default_alert", 5));
    }
}
