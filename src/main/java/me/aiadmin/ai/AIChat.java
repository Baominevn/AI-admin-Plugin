package me.aiadmin.ai;

import me.aiadmin.AIAdmin;
import me.aiadmin.system.SuspicionManager;
import me.aiadmin.system.SuspicionManager.PlayerRiskProfile;
import me.aiadmin.system.SuspicionManager.RiskTier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AIChat {

    private final AIAdmin plugin;
    private final SuspicionManager suspicionManager;
    private final OpenAIService openAIService;

    private final Map<AIAdmin.ConfigProfile, FileConfiguration> customChatConfigs = new EnumMap<>(AIAdmin.ConfigProfile.class);

    public AIChat(AIAdmin plugin, SuspicionManager suspicionManager, OpenAIService openAIService) {
        this.plugin = plugin;
        this.suspicionManager = suspicionManager;
        this.openAIService = openAIService;
        reloadCustomChatConfig();
    }

    public void reloadCustomChatConfig() {
        customChatConfigs.clear();
        for (AIAdmin.ConfigProfile profile : AIAdmin.ConfigProfile.values()) {
            customChatConfigs.put(profile, plugin.loadLocaleConfiguration(profile, "aichat.yml", "aichat.yml"));
        }
    }

    public CompletableFuture<String> answerPlayerQuestion(Player player, String prompt, boolean adminMode) {
        AIAdmin.ConfigProfile language = plugin.getSenderLanguage(player);
        FileConfiguration chatConfig = getChatConfig(language);
        String customReply = findCustomReply(chatConfig, player, prompt);
        boolean customFirst = chatConfig == null || chatConfig.getBoolean("aichat.use_custom_first", true);
        if (customReply != null && customFirst) {
            recordLearningAndLogs(player, prompt, customReply, true, adminMode);
            return CompletableFuture.completedFuture(customReply);
        }

        String defaultReply = chatConfig == null ? "" : chatConfig.getString("aichat.default_reply", "");
        return openAIService.askAssistant(player, prompt, adminMode, language)
                .thenApply(reply -> {
                    if (!reply.isBlank()) {
                        return reply;
                    }
                    if (customReply != null) {
                        return customReply;
                    }
                    if (defaultReply != null && !defaultReply.isBlank()) {
                        return applyPlaceholders(player, defaultReply, prompt);
                    }
                    return buildFallbackReply(player, prompt, language);
                })
                .thenApply(finalReply -> {
                    boolean usedCustom = customReply != null && customReply.equals(finalReply);
                    recordLearningAndLogs(player, prompt, finalReply, usedCustom, adminMode);
                    return finalReply;
                });
    }

    public String buildFallbackReply(Player player, String prompt) {
        return buildFallbackReply(player, prompt, plugin.getSenderLanguage(player));
    }

    public String buildFallbackReply(Player player, String prompt, AIAdmin.ConfigProfile profile) {
        String question = prompt.toLowerCase(Locale.ROOT).trim();
        boolean english = profile == AIAdmin.ConfigProfile.ENGLISH;

        if (question.isEmpty()) {
            return english
                    ? "Use `ai <message>` to ask me something quickly."
                    : "Hãy gọi tôi bằng `ai <nội dung>` để nhận hướng dẫn nhanh.";
        }
        if (containsAny(question, "help", "lenh", "lệnh", "command")) {
            return english
                    ? "Try `/aiadmin help` if you're staff. Players can ask me about rules, guides, and server info."
                    : "Thử `/aiadmin help` nếu bạn là staff. Người chơi có thể hỏi tôi về rule, hướng dẫn và thông tin server.";
        }
        if (containsAny(question, "rule", "luat", "luật")) {
            return english
                    ? "Play fair, respect staff, and do not use hacks, alt-farming, or bug exploits."
                    : "Hãy chơi công bằng, tôn trọng staff, không dùng hack, alt farm hoặc bug exploit.";
        }
        if (containsAny(question, "server", "info", "thong tin", "thông tin")) {
            return english
                    ? "AIAdmin helps staff monitor alerts, scan players, and answer basic server questions."
                    : "AIAdmin đang hỗ trợ staff theo dõi cảnh báo, quét người chơi và trả lời các câu hỏi cơ bản.";
        }
        if (containsAny(question, "ban", "kick", "hack")) {
            return english
                    ? "If the system sees multiple suspicious signs, staff will review the case before taking action."
                    : "Nếu hệ thống thấy nhiều dấu hiệu bất thường, staff sẽ kiểm tra thêm trước khi đưa ra xử lý.";
        }
        if (containsAny(question, "ping", "online")) {
            return english
                    ? "There are currently " + Bukkit.getOnlinePlayers().size() + " players online."
                    : "Hiện có " + Bukkit.getOnlinePlayers().size() + " người đang online.";
        }
        if (containsAny(question, "toi la ai", "tôi là ai", "who am i")) {
            return english ? "You are " + player.getName() + "." : "Bạn đang là " + player.getName() + ".";
        }

        return english
                ? "I'm the server admin assistant. You can ask me about rules, how to play, or general server info."
                : "Tôi là trợ lý admin trong server. Bạn có thể hỏi về rule, cách chơi hoặc thông tin chung.";
    }

    public String buildPlayerAnalysis(String playerName) {
        return buildPlayerAnalysis(playerName, plugin.getActiveConfigProfile());
    }

    public String buildPlayerAnalysis(String playerName, AIAdmin.ConfigProfile language) {
        PlayerRiskProfile profile = suspicionManager.getOrCreateProfile(playerName);
        RiskTier riskTier = suspicionManager.getRiskTier(profile.getSuspicion());
        if (language == AIAdmin.ConfigProfile.ENGLISH) {
            return playerName + ": risk=" + riskTier.name()
                    + ", threat=" + suspicionManager.getThreatLevel(profile.getSuspicion()).name()
                    + ", skill=" + suspicionManager.getSkillClass(profile).name()
                    + ", suspicion=" + profile.getSuspicion()
                    + ", alerts=" + profile.getTotalAlerts()
                    + ", last_ip=" + safe(profile.getLastKnownIp(), language)
                    + ", movement=" + profile.describeMovement()
                    + ", learning=" + profile.describeLearning();
        }
        return playerName + ": mức rủi ro=" + riskTier.name()
                + ", cấp độ=" + suspicionManager.getThreatLevel(profile.getSuspicion()).name()
                + ", phân loại kỹ năng=" + suspicionManager.getSkillClass(profile).name()
                + ", điểm nghi ngờ=" + profile.getSuspicion()
                + ", cảnh báo=" + profile.getTotalAlerts()
                + ", IP gần nhất=" + safe(profile.getLastKnownIp(), language)
                + ", dữ liệu di chuyển=" + profile.describeMovement()
                + ", học quan sát=" + profile.describeLearning();
    }

    public String buildServerReport() {
        return buildServerReport(plugin.getActiveConfigProfile());
    }

    public String buildServerReport(AIAdmin.ConfigProfile profile) {
        List<PlayerRiskProfile> topProfiles = suspicionManager.getTopProfiles(5);
        List<String> entries = new ArrayList<>();
        for (PlayerRiskProfile riskProfile : topProfiles) {
            entries.add(riskProfile.getName() + "=" + riskProfile.getSuspicion());
        }

        if (profile == AIAdmin.ConfigProfile.ENGLISH) {
            return "online=" + Bukkit.getOnlinePlayers().size()
                    + ", suspicious_players=" + suspicionManager.countAtOrAbove(RiskTier.WATCH)
                    + ", low=" + countThreat(SuspicionManager.ThreatLevel.LOW)
                    + ", medium=" + countThreat(SuspicionManager.ThreatLevel.MEDIUM)
                    + ", high=" + countThreat(SuspicionManager.ThreatLevel.HIGH)
                    + ", flagged_ips=" + suspicionManager.countFlaggedIps()
                    + ", total_alerts=" + suspicionManager.getRecordedAlertCount()
                    + ", AI={" + openAIService.getStatusSummary() + "}"
                    + ", top=" + (entries.isEmpty() ? "none" : String.join(", ", entries));
        }
        return "online=" + Bukkit.getOnlinePlayers().size()
                + ", người bị nghi ngờ=" + suspicionManager.countAtOrAbove(RiskTier.WATCH)
                + ", low=" + countThreat(SuspicionManager.ThreatLevel.LOW)
                + ", medium=" + countThreat(SuspicionManager.ThreatLevel.MEDIUM)
                + ", high=" + countThreat(SuspicionManager.ThreatLevel.HIGH)
                + ", IP bị gắn cờ=" + suspicionManager.countFlaggedIps()
                + ", tổng cảnh báo=" + suspicionManager.getRecordedAlertCount()
                + ", AI={" + openAIService.getStatusSummary() + "}"
                + ", top=" + (entries.isEmpty() ? "không có" : String.join(", ", entries));
    }

    public void sendStaffNotice(String message) {
        String formatted = color("&c[AIAdmin] &f" + message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("aiadmin.admin")) {
                player.sendMessage(formatted);
            }
        }
        plugin.getLogger().info("[StaffNotice] " + message);
    }

    public void relayAsAdmin(String response) {
        String fallbackName = plugin.getConfig().getString("chat.admin_mode_name", "Grox");
        String prefix = plugin.getConfig().getString("chat.admin_relay.prefix", "&c&l[ADMIN] ");
        String name = plugin.getConfig().getString("chat.admin_relay.name", "&f" + fallbackName);
        String separator = plugin.getConfig().getString("chat.admin_relay.separator", "&f: ");
        String messageFormat = plugin.getConfig().getString("chat.admin_relay.message", "&b{message}");
        String message = messageFormat.replace("{message}", decorateResponse(response));
        Bukkit.broadcastMessage(color(prefix + name + separator + message));
    }

    public CompletableFuture<String> generateServerAnnouncement(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        String fallback = buildFallbackAnnouncement(input);
        if (openAIService == null || !openAIService.isEnabled()) {
            return CompletableFuture.completedFuture(fallback);
        }

        return openAIService.rewriteAnnouncement(input)
                .thenApply(reply -> {
                    if (reply == null || reply.isBlank()) {
                        return fallback;
                    }
                    return decorateResponse(reply);
                })
                .exceptionally(ex -> fallback);
    }

    public void sendActionPlan(CommandSender sender, String playerName) {
        PlayerRiskProfile profile = suspicionManager.getOrCreateProfile(playerName);
        RiskTier tier = suspicionManager.getRiskTier(profile.getSuspicion());
        sender.sendMessage(color("&6[AIAdmin] &f" + recommendAction(playerName, tier, profile.getAlertCounts(), plugin.getSenderLanguage(sender))));
    }

    public boolean isOpenAIEnabled() {
        return openAIService.isEnabled();
    }

    public String getApiKeyEnvName() {
        return openAIService.getConfiguredApiKeyEnv();
    }

    public String decorateResponse(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }
        if (plugin.getOptionConfig() == null) {
            return response;
        }
        boolean expressionEnabled = plugin.getOptionConfig().getBoolean("ai.expressions.enabled", false);
        if (!expressionEnabled) {
            return response;
        }
        String prefix = plugin.getOptionConfig().getString("ai.expressions.prefix", "");
        String suffix = plugin.getOptionConfig().getString("ai.expressions.suffix", "");
        return (prefix == null ? "" : prefix) + response + (suffix == null ? "" : suffix);
    }

    private String buildFallbackAnnouncement(String input) {
        String cleaned = input.trim();
        String lowered = cleaned.toLowerCase(Locale.ROOT);
        AIAdmin.ConfigProfile profile = plugin.getActiveConfigProfile();
        if (lowered.startsWith("thông báo ")) {
            cleaned = cleaned.substring("thông báo ".length()).trim();
        } else if (lowered.startsWith("thong bao ")) {
            cleaned = cleaned.substring("thong bao ".length()).trim();
        } else if (lowered.startsWith("announce ")) {
            cleaned = cleaned.substring("announce ".length()).trim();
        }
        if (cleaned.isBlank()) {
            return profile == AIAdmin.ConfigProfile.ENGLISH
                    ? "Hey everyone, a new admin announcement is coming soon."
                    : "Mọi người ơi, sắp có thông báo mới từ admin nha.";
        }
        return profile == AIAdmin.ConfigProfile.ENGLISH
                ? "Hey everyone, " + cleaned + "."
                : "Mọi người ơi, " + cleaned + " nha.";
    }

    private String findCustomReply(FileConfiguration chatConfig, Player player, String prompt) {
        if (chatConfig == null) {
            return null;
        }
        ConfigurationSection rulesSection = chatConfig.getConfigurationSection("aichat.rules");
        if (rulesSection == null) {
            return null;
        }

        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT).trim();
        for (String ruleKey : rulesSection.getKeys(false)) {
            ConfigurationSection rule = rulesSection.getConfigurationSection(ruleKey);
            if (rule == null) {
                continue;
            }

            String reply = pickRuleReply(rule);
            if (reply.isBlank()) {
                continue;
            }

            String mode = rule.getString("mode", "contains").toLowerCase(Locale.ROOT);
            List<String> matches = rule.getStringList("match");
            if (matches.isEmpty()) {
                String singleMatch = rule.getString("match", "");
                if (!singleMatch.isBlank()) {
                    matches.add(singleMatch);
                }
            }

            for (String pattern : matches) {
                if (matchesRule(normalizedPrompt, pattern, mode)) {
                    return applyPlaceholders(player, reply, prompt);
                }
            }
        }
        return null;
    }

    private String pickRuleReply(ConfigurationSection rule) {
        List<String> replies = rule.getStringList("replies");
        if (!replies.isEmpty()) {
            int index = ThreadLocalRandom.current().nextInt(replies.size());
            String candidate = replies.get(index);
            return candidate == null ? "" : candidate;
        }
        return rule.getString("reply", "");
    }

    private boolean matchesRule(String normalizedPrompt, String patternText, String mode) {
        if (patternText == null || patternText.isBlank()) {
            return false;
        }
        String pattern = patternText.toLowerCase(Locale.ROOT).trim();
        switch (mode) {
            case "equals":
                return normalizedPrompt.equals(pattern);
            case "starts_with":
            case "startswith":
                return normalizedPrompt.startsWith(pattern);
            case "ends_with":
            case "endswith":
                return normalizedPrompt.endsWith(pattern);
            case "regex":
                try {
                    return Pattern.compile(patternText, Pattern.CASE_INSENSITIVE).matcher(normalizedPrompt).find();
                } catch (PatternSyntaxException ex) {
                    plugin.getLogger().warning("Invalid regex in aichat.yml: " + patternText);
                    return false;
                }
            case "contains":
            default:
                return normalizedPrompt.contains(pattern);
        }
    }

    private String applyPlaceholders(Player player, String text, String prompt) {
        return text
                .replace("{player}", player.getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{prompt}", prompt);
    }

    private String recommendAction(String playerName, RiskTier tier, Map<String, Integer> alertCounts, AIAdmin.ConfigProfile profile) {
        boolean english = profile == AIAdmin.ConfigProfile.ENGLISH;
        if (tier == RiskTier.CLEAR) {
            return english
                    ? playerName + " is currently low risk. Keep monitoring periodically."
                    : playerName + " đang ở mức rủi ro thấp. Tiếp tục theo dõi định kỳ.";
        }
        if (tier == RiskTier.WATCH) {
            return english
                    ? playerName + " shows light suspicious signs. Staff should observe directly and review alert history."
                    : playerName + " có dấu hiệu bất thường nhẹ. Đề xuất staff theo dõi trực tiếp và kiểm tra lịch sử cảnh báo.";
        }
        if (tier == RiskTier.ALERT) {
            return english
                    ? playerName + " is at a high alert level. Staff should compare " + summarizeAlerts(alertCounts, profile) + "."
                    : playerName + " đang ở mức cảnh báo cao. Nên ping staff online và đối chiếu "
                    + summarizeAlerts(alertCounts, profile) + ".";
        }
        if (tier == RiskTier.DANGER) {
            return english
                    ? playerName + " is in the danger zone. Consider kicking first and reviewing for a tempban."
                    : playerName + " đang ở mức nguy hiểm. Có thể kick để chặn tác động tiếp diễn và cân nhắc tempban.";
        }
        return english
                ? playerName + " shows clear violations. Review quickly and ban if staff confirms it."
                : playerName + " có vi phạm rõ ràng. Đề xuất kiểm tra nhanh và xử lý ban nếu staff xác nhận.";
    }

    private String summarizeAlerts(Map<String, Integer> alertCounts, AIAdmin.ConfigProfile profile) {
        if (alertCounts.isEmpty()) {
            return profile == AIAdmin.ConfigProfile.ENGLISH ? "movement data" : "dữ liệu vận động";
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : alertCounts.entrySet()) {
            entries.add(entry.getKey() + " x" + entry.getValue());
        }
        return String.join(", ", entries);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value, AIAdmin.ConfigProfile profile) {
        if (value == null || value.isEmpty()) {
            return profile == AIAdmin.ConfigProfile.ENGLISH ? "unknown" : "không rõ";
        }
        return value;
    }

    private int countThreat(SuspicionManager.ThreatLevel level) {
        int count = 0;
        for (PlayerRiskProfile profile : suspicionManager.getTopProfiles(500)) {
            if (suspicionManager.getRiskTier(profile.getSuspicion()) == RiskTier.CLEAR) {
                continue;
            }
            if (suspicionManager.getThreatLevel(profile.getSuspicion()) == level) {
                count++;
            }
        }
        return count;
    }

    private void recordLearningAndLogs(Player player, String prompt, String reply, boolean customReply, boolean adminMode) {
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().logChatEvent(player.getName(), prompt, reply, customReply, adminMode);
        }
    }

    private FileConfiguration getChatConfig(AIAdmin.ConfigProfile profile) {
        FileConfiguration config = customChatConfigs.get(profile);
        if (config != null) {
            return config;
        }
        return customChatConfigs.get(plugin.getActiveConfigProfile());
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }
}
