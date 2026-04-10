package me.aiadmin.listener;

import me.aiadmin.AIAdmin;
import me.aiadmin.ai.AIChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private final AIAdmin plugin;
    private final AIChat aiChat;
    private final Map<UUID, Long> lastRequestTimes = new ConcurrentHashMap<>();

    public ChatListener(AIAdmin plugin, AIChat aiChat) {
        this.plugin = plugin;
        this.aiChat = aiChat;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        String prefix = plugin.getConfig().getString("chat.trigger_prefix", "ai ");
        if (!plugin.getConfig().getBoolean("chat.enable_chat_ai", true)) {
            return;
        }
        if (!message.toLowerCase().startsWith(prefix.toLowerCase())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String prompt = message.substring(prefix.length()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> respond(player, prompt, prefix));
    }

    private void respond(Player player, String prompt, String prefix) {
        if (prompt.isEmpty()) {
            player.sendMessage(color(plugin.tr(player,
                    "&cHãy nhập nội dung sau '" + prefix + "'",
                    "&cPlease enter a message after '" + prefix + "'")));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = plugin.getConfig().getLong("chat.request_cooldown_seconds", 3L) * 1000L;
        long lastRequest = lastRequestTimes.getOrDefault(player.getUniqueId(), 0L);
        if (cooldownMillis > 0 && now - lastRequest < cooldownMillis) {
            long waitSeconds = Math.max(1L, (cooldownMillis - (now - lastRequest) + 999L) / 1000L);
            player.sendMessage(color(plugin.tr(player,
                    "&cBạn đang hỏi quá nhanh. Thử lại sau " + waitSeconds + "s.",
                    "&cYou're asking too quickly. Try again in " + waitSeconds + "s.")));
            return;
        }
        lastRequestTimes.put(player.getUniqueId(), now);

        int maxPromptChars = Math.max(40, plugin.getConfig().getInt("chat.max_prompt_chars", 700));
        String normalizedPrompt = prompt.length() > maxPromptChars ? prompt.substring(0, maxPromptChars) : prompt;
        boolean relayEnabledInOptions = plugin.getOptionConfig() == null
                || plugin.getOptionConfig().getBoolean("admin_mode.relay_publicly", true);
        boolean adminRelayMode = relayEnabledInOptions
                && plugin.isAdminModeEnabled()
                && player.hasPermission("aiadmin.admin");

        aiChat.answerPlayerQuestion(player, normalizedPrompt, adminRelayMode)
                .thenAccept(response -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (response == null || response.isBlank()) {
                        player.sendMessage(color(plugin.tr(player,
                                "&cAI tạm thời không phản hồi được. Thử lại sau.",
                                "&cAI is temporarily unavailable. Please try again later.")));
                        return;
                    }

                    if (adminRelayMode) {
                        aiChat.relayAsAdmin(response);
                        return;
                    }

                    player.sendMessage(color("&bAI &7> &f" + aiChat.decorateResponse(response)));
                }));
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }
}
