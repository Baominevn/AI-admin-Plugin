package me.aiadmin.command;

import me.aiadmin.AIAdmin;
import me.aiadmin.ai.AIChat;
import me.aiadmin.system.BotManager;
import me.aiadmin.system.ServerScanner;
import me.aiadmin.system.SuspicionManager;
import me.aiadmin.system.SuspicionManager.PlayerRiskProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AdminCommand implements CommandExecutor, TabCompleter {
    private final AIAdmin plugin;
    private final ServerScanner scanner;
    private final SuspicionManager suspicionManager;
    private final AIChat aiChat;

    public AdminCommand(AIAdmin plugin, ServerScanner scanner, SuspicionManager suspicionManager, AIChat aiChat) {
        this.plugin = plugin;
        this.scanner = scanner;
        this.suspicionManager = suspicionManager;
        this.aiChat = aiChat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isAdmin = sender.hasPermission("aiadmin.admin");
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, isAdmin);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("lang")) {
            return handleLanguageCommand(sender, args);
        }
        if (sub.equals("use") && args.length >= 2 && args[1].equalsIgnoreCase("config")) {
            return handleUseConfigCommand(sender, args, isAdmin);
        }
        if (sub.equals("bot") && args.length >= 2 && args[1].equalsIgnoreCase("help")) {
            return sendBotHelp(sender);
        }

        if (!isAdmin) {
            sendMemberRestriction(sender);
            return true;
        }

        switch (sub) {
            case "scan":
                scanner.scanServer(true);
                sender.sendMessage(color(t(sender, "&aAI scan Ä‘Ã£ Ä‘Æ°á»£c khá»Ÿi cháº¡y.", "&aAI scan has started.")));
                return true;
            case "status":
                return handleStatusCommand(sender);
            case "reload":
                plugin.reloadAllRuntimeConfigs();
                sender.sendMessage(color(t(sender,
                        "&aÄÃ£ reload toÃ n bá»™ config cá»§a bá»™ hiá»‡n táº¡i.",
                        "&aReloaded all configs for the active config set.")));
                return true;
            case "dashboard":
                plugin.getSuspicionDashboard().openDashboard(sender, 0);
                return true;
            case "optimize":
                if (plugin.getLagOptimizer() == null) {
                    sender.sendMessage(color(t(sender,
                            "&cLag optimizer chÆ°a kháº£ dá»¥ng.",
                            "&cLag optimizer is not available.")));
                    return true;
                }
                plugin.getLagOptimizer().runManualOptimize(sender);
                return true;
            case "check":
                return handleCheckCommand(sender, args);
            case "checkgui":
                return handleCheckGuiCommand(sender, args);
            case "suspicion":
                return handleSuspicionCommand(sender, args);
            case "addsus":
                return handleAddSuspicionCommand(sender, args);
            case "flag":
                return handleFlagCommand(sender, args);
            case "observe":
            case "watch":
                return handleObserveCommand(sender, args);
            case "kick":
                return handleKickCommand(sender, args);
            case "ban":
                return handleBanCommand(sender, args);
            case "termban":
                return handleTempBanCommand(sender, args);
            case "thongbao":
            case "announce":
                return handleAnnouncementCommand(sender, args);
            case "createbot":
                return handleCreateBotCommand(sender, args);
            case "choose":
                return handleChooseBotCommand(sender, args);
            case "bot":
                return handleBotCommand(sender, args);
            case "admode":
            case "adminmode":
                return handleAdminModeCommand(sender, args);
            default:
                sendHelp(sender, true);
                return true;
        }
    }

    private boolean handleStatusCommand(CommandSender sender) {
        sender.sendMessage(color("&6[AIAdmin] &fAI API: " + (aiChat.isOpenAIEnabled()
                ? t(sender, "Sáº´N SÃ€NG", "READY")
                : t(sender, "ÄANG Táº®T", "DISABLED"))));
        sender.sendMessage(color(t(sender,
                "&7DÃ¹ng " + aiChat.getApiKeyEnvName() + " hoáº·c config openai.api_key Ä‘á»ƒ báº­t káº¿t ná»‘i.",
                "&7Use " + aiChat.getApiKeyEnvName() + " or config openai.api_key to enable the connection.")));
        sender.sendMessage(color("&7LiteBans: &f" + integrationState(sender, "liteban", "LiteBans")));
        sender.sendMessage(color("&7TAB: &f" + integrationState(sender, "tab", "TAB")));
        sender.sendMessage(color("&7PlaceholderAPI: &f" + integrationState(sender, "placeholder", "PlaceholderAPI")));
        sender.sendMessage(color("&7Database: &f" + (plugin.getDatabaseManager() != null
                ? plugin.getDatabaseManager().getStatusSummary()
                : "disabled")));
        sender.sendMessage(color("&7Learning: &f" + (plugin.getLearningManager() != null
                ? plugin.getLearningManager().getStatusSummary()
                : "disabled")));
        sender.sendMessage(color("&7LagOptimizer: &f" + (plugin.getLagOptimizer() != null
                ? plugin.getLagOptimizer().getStatusSummary()
                : "disabled")));
        sender.sendMessage(color("&7Config folder: &f" + plugin.getActiveLocaleFolderName()));
        return true;
    }

    private boolean handleCheckCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai check <player> [gui|observe]",
                    "&cUsage: /ai check <player> [gui|observe]")));
            return true;
        }

        String playerName = args[1];
        sender.sendMessage(color("&6[AIAdmin] &f" + aiChat.buildPlayerAnalysis(playerName, plugin.getSenderLanguage(sender))));
        aiChat.sendActionPlan(sender, playerName);

        if (args.length >= 3) {
            String mode = args[2].toLowerCase(Locale.ROOT);
            if (mode.equals("gui")) {
                if (sender instanceof Player) {
                    plugin.getSuspicionDashboard().openPlayerCheck(sender, playerName);
                } else {
                    sender.sendMessage(color(t(sender,
                            "&cChá»‰ ngÆ°á»i chÆ¡i má»›i má»Ÿ Ä‘Æ°á»£c GUI check.",
                            "&cOnly players can open the check GUI.")));
                }
                return true;
            }
            if (mode.equals("observe") || mode.equals("watch")) {
                if (plugin.getStatsManager() != null) {
                    plugin.getStatsManager().recordCheck(playerName);
                }
                scanner.observePlayer(sender, playerName, "manual-check", true);
                return true;
            }
        }

        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordCheck(playerName);
        }
        return true;
    }

    private boolean handleCheckGuiCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai checkgui <player>",
                    "&cUsage: /ai checkgui <player>")));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(t(sender,
                    "&cLá»‡nh nÃ y chá»‰ dÃ¹ng trong game.",
                    "&cThis command can only be used in-game.")));
            return true;
        }
        plugin.getSuspicionDashboard().openPlayerCheck(sender, args[1]);
        return true;
    }

    private boolean handleSuspicionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai suspicion <player>",
                    "&cUsage: /ai suspicion <player>")));
            return true;
        }

        PlayerRiskProfile profile = suspicionManager.getOrCreateProfile(args[1]);
        sender.sendMessage(color(t(sender,
                "&eÄiá»ƒm nghi ngá» cá»§a " + profile.getName() + ": &f" + profile.getSuspicion(),
                "&eSuspicion score of " + profile.getName() + ": &f" + profile.getSuspicion())));
        sender.sendMessage(color(t(sender,
                "&7Má»©c: &f" + suspicionManager.getRiskTier(profile.getSuspicion()).name() + " &7Cáº£nh bÃ¡o: &f" + profile.getTotalAlerts(),
                "&7Tier: &f" + suspicionManager.getRiskTier(profile.getSuspicion()).name() + " &7Alerts: &f" + profile.getTotalAlerts())));
        sender.sendMessage(color(t(sender,
                "&7Vá»‹ trÃ­ nghi ngá» gáº§n nháº¥t: &f" + profile.getLastSuspiciousLocationSummary(),
                "&7Last suspicious location: &f" + profile.getLastSuspiciousLocationSummary())));
        return true;
    }

    private boolean handleAddSuspicionCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai addsus <player> <amount>",
                    "&cUsage: /ai addsus <player> <amount>")));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            suspicionManager.addSuspicion(args[1], amount, "manual", "Staff update");
            sender.sendMessage(color(t(sender,
                    "&aÄÃ£ cá»™ng Ä‘iá»ƒm nghi ngá» cho " + args[1] + ". Má»©c má»›i: " + suspicionManager.getSuspicion(args[1]),
                    "&aAdded suspicion to " + args[1] + ". New score: " + suspicionManager.getSuspicion(args[1]))));
        } catch (NumberFormatException ex) {
            sender.sendMessage(color(t(sender,
                    "&cAmount pháº£i lÃ  sá»‘.",
                    "&cAmount must be a number.")));
        }
        return true;
    }

    private boolean handleFlagCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai flag <player> <type> [points] [details]",
                    "&cUsage: /ai flag <player> <type> [points] [details]")));
            return true;
        }

        int points = 5;
        int detailsIndex = 3;
        if (args.length >= 4) {
            try {
                points = Integer.parseInt(args[3]);
                detailsIndex = 4;
            } catch (NumberFormatException ignored) {
                detailsIndex = 3;
            }
        }

        String details = args.length > detailsIndex
                ? String.join(" ", Arrays.copyOfRange(args, detailsIndex, args.length))
                : "manual anti-cheat alert";
        suspicionManager.recordAlert(args[1], "manual", args[2], points, details);
        sender.sendMessage(color(t(sender,
                "&aÄÃ£ ghi nháº­n cáº£nh bÃ¡o cho " + args[1] + ".",
                "&aAlert recorded for " + args[1] + ".")));
        scanner.observePlayer(sender, args[1], "manual-flag-" + args[2], true);
        return true;
    }

    private boolean handleObserveCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai observe <player> <on/off>",
                    "&cUsage: /ai observe <player> <on/off>")));
            return true;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("on")) {
            scanner.observePlayer(sender, args[1], "manual-observe", true);
            return true;
        }
        if (mode.equals("off")) {
            scanner.stopObserving(sender, args[1], true);
            return true;
        }

        sender.sendMessage(color(t(sender,
                "&cTham sá»‘ cuá»‘i pháº£i lÃ  on hoáº·c off.",
                "&cThe last argument must be on or off.")));
        return true;
    }

    private boolean handleKickCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai kick <player> [reason]",
                    "&cUsage: /ai kick <player> [reason]")));
            return true;
        }

        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : t(sender,
                "AIAdmin phÃ¡t hiá»‡n hÃ nh vi báº¥t thÆ°á»ng. Vui lÃ²ng chá» staff kiá»ƒm tra.",
                "AIAdmin detected unusual behavior. Please wait for staff review.");
        ServerScanner.BanResult result = scanner.kickPlayerByName(args[1], reason);
        if (result == ServerScanner.BanResult.BLOCKED_OP) {
            sender.sendMessage(color(t(sender,
                    "&eKhÃ´ng thá»ƒ kick ngÆ°á»i chÆ¡i OP: " + args[1],
                    "&eCannot kick OP player: " + args[1])));
            return true;
        }
        if (result == ServerScanner.BanResult.FAILED) {
            sender.sendMessage(color(t(sender,
                    "&cKick tháº¥t báº¡i. Chá»‰ kick Ä‘Æ°á»£c ngÆ°á»i chÆ¡i Ä‘ang online.",
                    "&cKick failed. Only online players can be kicked.")));
            return true;
        }

        sender.sendMessage(color(t(sender,
                "&aÄÃ£ kick " + args[1] + ".",
                "&aKicked " + args[1] + ".")));
        return true;
    }

    private boolean handleBanCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai ban <player> [reason]",
                    "&cUsage: /ai ban <player> [reason]")));
            return true;
        }

        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : t(sender, "AIAdmin manual ban", "AIAdmin manual ban");
        ServerScanner.BanResult result = scanner.banPlayerByName(args[1], reason);
        if (result == ServerScanner.BanResult.BLOCKED_OP) {
            sender.sendMessage(color(t(sender,
                    "&eKhÃ´ng thá»ƒ ban ngÆ°á»i chÆ¡i OP: " + args[1],
                    "&eCannot ban OP player: " + args[1])));
            return true;
        }
        if (result == ServerScanner.BanResult.FAILED) {
            sender.sendMessage(color(t(sender,
                    "&cBan tháº¥t báº¡i cho " + args[1] + ". Kiá»ƒm tra config plugin.",
                    "&cBan failed for " + args[1] + ". Check plugin config.")));
            return true;
        }

        sender.sendMessage(color(t(sender,
                "&aÄÃ£ xá»­ lÃ½ ban cho " + args[1] + ".",
                "&aBan handled for " + args[1] + ".")));
        return true;
    }

    private boolean handleTempBanCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai termban <player> <reason> <time>",
                    "&cUsage: /ai termban <player> <reason> <time>")));
            return true;
        }

        String playerName = args[1];
        String duration = null;
        String reason = t(sender,
                "AIAdmin phÃ¡t hiá»‡n hÃ nh vi báº¥t thÆ°á»ng",
                "AIAdmin detected suspicious behavior");

        if (scanner.isDurationToken(args[args.length - 1])) {
            duration = scanner.normalizeDuration(args[args.length - 1]);
            if (args.length > 3) {
                reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
            }
        } else if (scanner.isDurationToken(args[2])) {
            duration = scanner.normalizeDuration(args[2]);
            if (args.length > 3) {
                reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            }
        }

        if (duration == null) {
            sender.sendMessage(color(t(sender,
                    "&cThiáº¿u thá»i gian há»£p lá»‡. VÃ­ dá»¥: 30m, 12h, 3d.",
                    "&cMissing a valid duration. Example: 30m, 12h, 3d.")));
            return true;
        }

        ServerScanner.BanResult result = scanner.tempBanPlayerByName(playerName, duration, reason);
        if (result == ServerScanner.BanResult.BLOCKED_OP) {
            sender.sendMessage(color(t(sender,
                    "&eKhÃ´ng thá»ƒ termban ngÆ°á»i chÆ¡i OP: " + playerName,
                    "&eCannot temp-ban OP player: " + playerName)));
            return true;
        }
        if (result == ServerScanner.BanResult.FAILED) {
            sender.sendMessage(color(t(sender,
                    "&cTermban tháº¥t báº¡i cho " + playerName + ".",
                    "&cTemp-ban failed for " + playerName + ".")));
            return true;
        }

        sender.sendMessage(color(t(sender,
                "&aÄÃ£ termban " + playerName + " trong &f" + duration + "&a.",
                "&aTemp-banned " + playerName + " for &f" + duration + "&a.")));
        return true;
    }

    private boolean handleAnnouncementCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai thongbao <ná»™i dung>",
                    "&cUsage: /ai announce <message>")));
            return true;
        }

        String announcement = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (announcement.isBlank()) {
            sender.sendMessage(color(t(sender,
                    "&cNá»™i dung thÃ´ng bÃ¡o trá»‘ng.",
                    "&cAnnouncement text is empty.")));
            return true;
        }

        sender.sendMessage(color(t(sender,
                "&eAI Ä‘ang soáº¡n thÃ´ng bÃ¡o...",
                "&eAI is drafting the announcement...")));
        aiChat.generateServerAnnouncement(announcement)
                .thenAccept(reply -> Bukkit.getScheduler().runTask(plugin, () -> {
                    String finalReply = (reply == null || reply.isBlank()) ? announcement : reply;
                    aiChat.relayAsAdmin(finalReply);
                    sender.sendMessage(color(t(sender,
                            "&aÄÃ£ gá»­i thÃ´ng bÃ¡o Ä‘áº¿n toÃ n server.",
                            "&aAnnouncement sent to the whole server.")));
                }))
                .exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(color(t(sender,
                            "&cKhÃ´ng thá»ƒ táº¡o thÃ´ng bÃ¡o AI lÃºc nÃ y.",
                            "&cCould not generate an AI announcement right now."))));
                    return null;
                });
        return true;
    }

    private boolean handleCreateBotCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai createbot <name>",
                    "&cUsage: /ai createbot <name>")));
            return true;
        }
        BotManager botManager = plugin.getBotManager();
        if (botManager == null) {
            sender.sendMessage(color(t(sender,
                    "&cBot manager chÆ°a kháº£ dá»¥ng.",
                    "&cBot manager is not available.")));
            return true;
        }
        sender.sendMessage(color(botManager.createBotBody(sender, args[1])));
        return true;
    }

    private boolean handleChooseBotCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("bot")) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai choose bot <name>",
                    "&cUsage: /ai choose bot <name>")));
            return true;
        }
        BotManager botManager = plugin.getBotManager();
        if (botManager == null) {
            sender.sendMessage(color(t(sender,
                    "&cBot manager chÆ°a kháº£ dá»¥ng.",
                    "&cBot manager is not available.")));
            return true;
        }
        sender.sendMessage(color(botManager.chooseBot(args[2])));
        return true;
    }

    private boolean handleLanguageCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(t(sender,
                    "&cChá»‰ ngÆ°á»i chÆ¡i má»›i Ä‘á»•i Ä‘Æ°á»£c ngÃ´n ngá»¯ cÃ¡ nhÃ¢n.",
                    "&cOnly players can change personal language.")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color(t(sender,
                    "&eCÃ¡ch dÃ¹ng: /ai lang <english/vietnam>",
                    "&eUsage: /ai lang <english/vietnam>")));
            return true;
        }

        AIAdmin.ConfigProfile profile = AIAdmin.ConfigProfile.fromInput(args[1]);
        if (profile == null) {
            sender.sendMessage(color(t(sender,
                    "&cNgÃ´n ngá»¯ khÃ´ng há»£p lá»‡. DÃ¹ng english hoáº·c vietnam.",
                    "&cInvalid language. Use english or vietnam.")));
            return true;
        }

        plugin.setPlayerLanguage((Player) sender, profile);
        sender.sendMessage(color(profile == AIAdmin.ConfigProfile.ENGLISH
                ? "&aYour personal AI language is now English."
                : "&aNgÃ´n ngá»¯ AI cÃ¡ nhÃ¢n cá»§a báº¡n Ä‘Ã£ chuyá»ƒn sang tiáº¿ng Viá»‡t."));
        return true;
    }

    private boolean handleUseConfigCommand(CommandSender sender, String[] args, boolean isAdmin) {
        if (!isAdmin) {
            sender.sendMessage(color(t(sender,
                    "&cBáº¡n khÃ´ng cÃ³ quyá»n Ä‘á»•i bá»™ config.",
                    "&cYou do not have permission to switch config sets.")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(color(t(sender,
                    "&eCÃ¡ch dÃ¹ng: /ai use config <english/vietnam>",
                    "&eUsage: /ai use config <english/vietnam>")));
            return true;
        }

        AIAdmin.ConfigProfile profile = AIAdmin.ConfigProfile.fromInput(args[2]);
        if (profile == null) {
            sender.sendMessage(color(t(sender,
                    "&cBá»™ config khÃ´ng há»£p lá»‡. DÃ¹ng english hoáº·c vietnam.",
                    "&cInvalid config set. Use english or vietnam.")));
            return true;
        }

        if (!plugin.setActiveConfigProfile(profile)) {
            sender.sendMessage(color(t(sender,
                    "&cKhÃ´ng thá»ƒ Ä‘á»•i bá»™ config.",
                    "&cCould not switch config set.")));
            return true;
        }

        plugin.reloadAllRuntimeConfigs();
        sender.sendMessage(color(profile == AIAdmin.ConfigProfile.ENGLISH
                ? "&aGlobal config set switched to English."
                : "&aÄÃ£ chuyá»ƒn bá»™ config toÃ n server sang tiáº¿ng Viá»‡t."));
        return true;
    }

    private boolean handleAdminModeCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean toggled = !plugin.isAdminModeEnabled();
            plugin.setAdminModeEnabled(toggled);
            sender.sendMessage(color(t(sender,
                    "&eAdmode Ä‘Ã£ chuyá»ƒn sang &f" + (toggled ? "ON" : "OFF"),
                    "&eAdmode is now &f" + (toggled ? "ON" : "OFF"))));
            return true;
        }

        if (args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(color("&eAdmode: &f" + (plugin.isAdminModeEnabled() ? "ON" : "OFF")));
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (!mode.equals("on") && !mode.equals("off") && !mode.equals("true") && !mode.equals("false")) {
            sender.sendMessage(color(t(sender,
                    "&cCÃ¡ch dÃ¹ng: /ai admode <on|off|status>",
                    "&cUsage: /ai admode <on|off|status>")));
            return true;
        }

        boolean enable = mode.equals("on") || mode.equals("true");
        plugin.setAdminModeEnabled(enable);
        sender.sendMessage(color(t(sender,
                "&eAdmode Ä‘Ã£ chuyá»ƒn sang &f" + (enable ? "ON" : "OFF"),
                "&eAdmode is now &f" + (enable ? "ON" : "OFF"))));
        return true;
    }

    private boolean handleBotCommand(CommandSender sender, String[] args) {
        BotManager botManager = plugin.getBotManager();
        if (botManager == null) {
            sender.sendMessage(color(t(sender,
                    "&cBot manager chÆ°a kháº£ dá»¥ng.",
                    "&cBot manager is not available.")));
            return true;
        }

        if (args.length < 2) {
            return sendBotHelp(sender);
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("help")) {
            return sendBotHelp(sender);
        }
        if (action.equals("list")) {
            for (String line : botManager.listBots()) {
                sender.sendMessage(color(line));
            }
            return true;
        }
        if (action.equals("remove")) {
            sender.sendMessage(color(botManager.removeBot()));
            return true;
        }
        if (action.equals("status") || (action.equals("setup") && args.length >= 3 && args[2].equalsIgnoreCase("show"))) {
            for (String line : botManager.describeSetup()) {
                sender.sendMessage(color(line));
            }
            return true;
        }
        if (action.equals("action")) {
            if (args.length < 5 || !args[2].equalsIgnoreCase("add")) {
                sender.sendMessage(color(t(sender,
                        "&cCÃ¡ch dÃ¹ng: /ai bot action add move <x1> <y1> <z1> <x2> <y2> <z2>",
                        "&cUsage: /ai bot action add move <x1> <y1> <z1> <x2> <y2> <z2>")));
                return true;
            }
            String actionName = args[3];
            String[] params = Arrays.copyOfRange(args, 4, args.length);
            sender.sendMessage(color(botManager.addBotAction(actionName, params)));
            return true;
        }
        if (!action.equals("setup")) {
            sender.sendMessage(color(t(sender, "&cCÃ¡ch dÃ¹ng: /ai bot help", "&cUsage: /ai bot help")));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(color(t(sender,
                    "&cThiáº¿u tham sá»‘. CÃ¡ch dÃ¹ng: /ai bot setup <key> <value>",
                    "&cMissing arguments. Usage: /ai bot setup <key> <value>")));
            return true;
        }

        String key = args[2];
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        sender.sendMessage(color(botManager.setupBot(key, value)));
        return true;
    }

    private boolean sendBotHelp(CommandSender sender) {
        BotManager botManager = plugin.getBotManager();
        if (botManager == null) {
            sender.sendMessage(color(t(sender,
                    "&cBot manager chÆ°a kháº£ dá»¥ng.",
                    "&cBot manager is not available.")));
            return true;
        }
        for (String line : botManager.getBotHelpLines()) {
            sender.sendMessage(color(line));
        }
        return true;
    }

    private void sendHelp(CommandSender sender, boolean isAdmin) {
        boolean english = plugin.isEnglish(sender);
        sender.sendMessage(color("&6===== AIAdmin Commands ====="));
        if (!isAdmin) {
            sender.sendMessage(color(english
                    ? "&e/ai lang <english/vietnam> &7- Change your personal AI language"
                    : "&e/ai lang <english/vietnam> &7- Äá»•i ngÃ´n ngá»¯ AI cÃ¡ nhÃ¢n"));
            sender.sendMessage(color(english
                    ? "&e/ai help &7- Show the basic help section"
                    : "&e/ai help &7- Hiá»‡n hÆ°á»›ng dáº«n cÆ¡ báº£n"));
            sender.sendMessage(color(english
                    ? "&eChat with AI: &fai <message>"
                    : "&eChat vá»›i AI: &fai <ná»™i dung>"));
            return;
        }

        sender.sendMessage(color(english ? "&e/ai scan &7- Start a server scan now" : "&e/ai scan &7- QuÃ©t server ngay láº­p tá»©c"));
        sender.sendMessage(color(english ? "&e/ai dashboard &7- Open suspicious players dashboard" : "&e/ai dashboard &7- Má»Ÿ GUI danh sÃ¡ch ngÆ°á»i chÆ¡i nghi váº¥n"));
        sender.sendMessage(color(english ? "&e/ai check <player> [gui|observe] &7- Analyze a player" : "&e/ai check <player> [gui|observe] &7- PhÃ¢n tÃ­ch ngÆ°á»i chÆ¡i"));
        sender.sendMessage(color(english ? "&e/ai checkgui <player> &7- Open the detailed check GUI" : "&e/ai checkgui <player> &7- Má»Ÿ GUI kiá»ƒm tra chi tiáº¿t"));
        sender.sendMessage(color(english ? "&e/ai suspicion <player> &7- View suspicion score" : "&e/ai suspicion <player> &7- Xem Ä‘iá»ƒm nghi ngá»"));
        sender.sendMessage(color(english ? "&e/ai addsus <player> <amount> &7- Add suspicion manually" : "&e/ai addsus <player> <amount> &7- Cá»™ng Ä‘iá»ƒm nghi ngá» thá»§ cÃ´ng"));
        sender.sendMessage(color(english ? "&e/ai flag <player> <type> [points] [details] &7- Record an alert and observe" : "&e/ai flag <player> <type> [points] [details] &7- Gáº¯n cá» vÃ  báº¯t Ä‘áº§u quan sÃ¡t"));
        sender.sendMessage(color(english ? "&e/ai observe <player> <on/off> &7- Start or stop observation" : "&e/ai observe <player> <on/off> &7- Báº­t hoáº·c táº¯t quan sÃ¡t"));
        sender.sendMessage(color(english ? "&e/ai kick <player> [reason] &7- Kick an online player" : "&e/ai kick <player> [reason] &7- Kick ngÆ°á»i chÆ¡i Ä‘ang online"));
        sender.sendMessage(color(english ? "&e/ai termban <player> <reason> <time> &7- Temp-ban with 30m/12h/3d" : "&e/ai termban <player> <reason> <time> &7- Termban vá»›i 30m/12h/3d"));
        sender.sendMessage(color(english ? "&e/ai ban <player> [reason] &7- Ban using LiteBans or internal fallback" : "&e/ai ban <player> [reason] &7- Ban qua LiteBans hoáº·c fallback ná»™i bá»™"));
        sender.sendMessage(color(english ? "&e/ai thongbao <message> &7- Let AI rewrite and announce it" : "&e/ai thongbao <ná»™i dung> &7- Äá»ƒ AI viáº¿t láº¡i vÃ  thÃ´ng bÃ¡o toÃ n server"));
        sender.sendMessage(color(english ? "&e/ai admode <on|off|status> &7- Toggle public admin relay mode" : "&e/ai admode <on|off|status> &7- Báº­t hoáº·c táº¯t relay cÃ´ng khai"));
        sender.sendMessage(color(english ? "&e/ai use config <english/vietnam> &7- Switch the global config set" : "&e/ai use config <english/vietnam> &7- Chuyá»ƒn bá»™ config toÃ n server"));
        sender.sendMessage(color(english ? "&e/ai bot help &7- Open bot command help" : "&e/ai bot help &7- Xem hÆ°á»›ng dáº«n bot"));
        sender.sendMessage(color(english ? "&eChat with AI: &fai <message>" : "&eChat vá»›i AI: &fai <ná»™i dung>"));
    }

    private void sendMemberRestriction(CommandSender sender) {
        sender.sendMessage(color(t(sender,
                "&cBáº¡n khÃ´ng cÃ³ quyá»n dÃ¹ng lá»‡nh nÃ y.",
                "&cYou do not have permission to use this command.")));
        sender.sendMessage(color(t(sender,
                "&7Member chá»‰ dÃ¹ng Ä‘Æ°á»£c: /ai lang <english/vietnam>, /ai help",
                "&7Members can only use: /ai lang <english/vietnam>, /ai help")));
    }

    private String integrationState(CommandSender sender, String key, String pluginName) {
        boolean enabled = plugin.isPluginIntegrationEnabled(key);
        boolean installed = Bukkit.getPluginManager().isPluginEnabled(pluginName);
        if (!enabled) {
            return t(sender, "Táº®T (setting_plugin.yml)", "OFF (setting_plugin.yml)");
        }
        return installed
                ? t(sender, "Báº¬T", "ON")
                : t(sender, "Báº¬T nhÆ°ng thiáº¿u plugin", "ON but plugin missing");
    }

    private String t(CommandSender sender, String vietnamese, String english) {
        return plugin.tr(sender, vietnamese, english);
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = sender.hasPermission("aiadmin.admin");
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("help", "lang"));
            if (isAdmin) {
                base.addAll(Arrays.asList(
                        "scan", "status", "reload", "dashboard", "optimize", "check", "checkgui",
                        "suspicion", "addsus", "flag", "observe", "watch", "kick", "ban", "termban",
                        "thongbao", "announce", "admode", "adminmode", "createbot", "choose", "bot", "use"
                ));
            }
            return filterSuggestions(base, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("lang") && args.length == 2) {
            return filterSuggestions(Arrays.asList("english", "vietnam"), args[1]);
        }
        if (!isAdmin) {
            return Collections.emptyList();
        }

        if (sub.equals("use")) {
            if (args.length == 2) {
                return filterSuggestions(Collections.singletonList("config"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("config")) {
                return filterSuggestions(Arrays.asList("english", "vietnam"), args[2]);
            }
        }

        if ((sub.equals("check") || sub.equals("checkgui") || sub.equals("suspicion") || sub.equals("addsus")
                || sub.equals("flag") || sub.equals("observe") || sub.equals("watch")
                || sub.equals("kick") || sub.equals("ban") || sub.equals("termban")) && args.length == 2) {
            return filterSuggestions(onlinePlayerNames(), args[1]);
        }

        if (sub.equals("check") && args.length == 3) {
            return filterSuggestions(Arrays.asList("gui", "observe"), args[2]);
        }
        if (sub.equals("addsus") && args.length == 3) {
            return filterSuggestions(Arrays.asList("1", "3", "5", "8", "10"), args[2]);
        }
        if (sub.equals("flag") && args.length == 3) {
            return filterSuggestions(Arrays.asList("killaura", "reach", "speed", "fly", "aim", "autoclick"), args[2]);
        }
        if ((sub.equals("observe") || sub.equals("watch")) && args.length == 3) {
            return filterSuggestions(Arrays.asList("on", "off"), args[2]);
        }
        if ((sub.equals("admode") || sub.equals("adminmode")) && args.length == 2) {
            return filterSuggestions(Arrays.asList("on", "off", "status"), args[1]);
        }
        if (sub.equals("termban") && args.length >= 3) {
            return filterSuggestions(Arrays.asList("30m", "12h", "1d", "3d", "7d"), args[args.length - 1]);
        }
        if (sub.equals("choose")) {
            if (args.length == 2) {
                return filterSuggestions(Collections.singletonList("bot"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("bot")) {
                BotManager botManager = plugin.getBotManager();
                return filterSuggestions(botManager == null ? Collections.emptyList() : botManager.getBotNames(), args[2]);
            }
        }
        if (sub.equals("bot")) {
            if (args.length == 2) {
                return filterSuggestions(Arrays.asList("help", "list", "remove", "status", "setup", "action"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("setup")) {
                return filterSuggestions(Arrays.asList(
                        "show", "name", "follow", "look", "walk", "jump", "hit",
                        "turn_ground", "invulnerable", "tier", "observe", "ai", "ai_llm", "ai_interval", "respawn"
                ), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("setup")) {
                String key = args[2].toLowerCase(Locale.ROOT);
                if (Arrays.asList("follow", "look", "walk", "jump", "hit", "turn_ground", "invulnerable", "ai", "ai_llm").contains(key)) {
                    return filterSuggestions(Arrays.asList("true", "false", "on", "off"), args[3]);
                }
                if (key.equals("tier") || key.equals("trigger_tier")) {
                    return filterSuggestions(Arrays.asList("CLEAR", "WATCH", "ALERT", "DANGER", "SEVERE"), args[3]);
                }
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("action")) {
                return filterSuggestions(Collections.singletonList("add"), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("action") && args[2].equalsIgnoreCase("add")) {
                return filterSuggestions(Collections.singletonList("move"), args[3]);
            }
        }
        if (sub.equals("createbot") && args.length == 2) {
            return filterSuggestions(Collections.singletonList("Grox"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> filterSuggestions(Collection<String> input, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : input) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
