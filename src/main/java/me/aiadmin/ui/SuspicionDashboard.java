package me.aiadmin.ui;

import me.aiadmin.AIAdmin;
import me.aiadmin.system.SuspicionManager;
import me.aiadmin.system.SuspicionManager.PlayerRiskProfile;
import me.aiadmin.system.SuspicionManager.RiskTier;
import me.aiadmin.system.SuspicionManager.SkillClass;
import me.aiadmin.system.SuspicionManager.ThreatLevel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SuspicionDashboard implements Listener {

    private static final int PAGE_SIZE = 45;

    private final AIAdmin plugin;
    private final SuspicionManager suspicionManager;

    public SuspicionDashboard(AIAdmin plugin, SuspicionManager suspicionManager) {
        this.plugin = plugin;
        this.suspicionManager = suspicionManager;
    }

    public void openDashboard(CommandSender sender, int page) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(t(sender,
                    "&cChá»‰ ngÆ°á»i chÆ¡i má»›i má»Ÿ Ä‘Æ°á»£c GUI dashboard.",
                    "&cOnly players can open the dashboard GUI.")));
            return;
        }

        Player viewer = (Player) sender;
        List<PlayerRiskProfile> suspicious = suspicionManager.getSuspiciousProfiles(200);
        int totalPages = Math.max(1, (int) Math.ceil((double) suspicious.size() / PAGE_SIZE));
        int safePage = Math.max(0, Math.min(totalPages - 1, page));
        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, suspicious.size());
        List<PlayerRiskProfile> slice = suspicious.isEmpty()
                ? Collections.emptyList()
                : suspicious.subList(start, end);

        DashboardHolder holder = new DashboardHolder(safePage, totalPages);
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&c&lAIAdmin Dashboard"));
        holder.inventory = inventory;

        int slot = 0;
        for (PlayerRiskProfile profile : slice) {
            inventory.setItem(slot++, buildProfileItem(viewer, profile));
        }
        if (slice.isEmpty()) {
            inventory.setItem(22, summaryItem(
                    Material.LIME_STAINED_GLASS_PANE,
                    t(viewer, "&aKhÃ´ng cÃ³ ngÆ°á»i chÆ¡i nghi váº¥n", "&aNo suspicious players"),
                    t(viewer, "&7Há»‡ thá»‘ng chÆ°a tháº¥y má»¥c tiÃªu nÃ o cáº§n theo dÃµi", "&7The system has not flagged anyone yet")
            ));
        }

        inventory.setItem(45, button(Material.ARROW,
                t(viewer, "&eTrang trÆ°á»›c", "&ePrevious page"),
                t(viewer, "&7Vá» trang trÆ°á»›c", "&7Go to the previous page")));
        inventory.setItem(46, summaryItem(
                Material.YELLOW_STAINED_GLASS_PANE,
                t(viewer, "&eMá»©c LOW", "&eLOW tier"),
                "&7" + t(viewer, "Sá»‘ lÆ°á»£ng", "Count") + ": &f" + countThreat(ThreatLevel.LOW)
        ));
        inventory.setItem(47, button(Material.PAPER,
                t(viewer, "&fTrang", "&fPage"),
                "&7" + (safePage + 1) + " / " + totalPages));
        inventory.setItem(48, summaryItem(
                Material.ORANGE_STAINED_GLASS_PANE,
                t(viewer, "&6Má»©c MEDIUM", "&6MEDIUM tier"),
                "&7" + t(viewer, "Sá»‘ lÆ°á»£ng", "Count") + ": &f" + countThreat(ThreatLevel.MEDIUM)
        ));
        inventory.setItem(49, button(Material.CLOCK,
                t(viewer, "&bLÃ m má»›i", "&bRefresh"),
                t(viewer, "&7Táº£i láº¡i danh sÃ¡ch nghi váº¥n", "&7Reload the suspicious list")));
        inventory.setItem(50, summaryItem(
                Material.RED_STAINED_GLASS_PANE,
                t(viewer, "&cMá»©c HIGH", "&cHIGH tier"),
                "&7" + t(viewer, "Sá»‘ lÆ°á»£ng", "Count") + ": &f" + countThreat(ThreatLevel.HIGH)
        ));
        inventory.setItem(51, summaryItem(
                Material.ENDER_EYE,
                t(viewer, "&dIP gáº¯n cá»", "&dFlagged IPs"),
                "&7IP: &f" + suspicionManager.countFlaggedIps()
        ));
        inventory.setItem(52, summaryItem(
                Material.BOOK,
                t(viewer, "&bTá»•ng cáº£nh bÃ¡o", "&bTotal alerts"),
                "&7" + t(viewer, "Sá»‘ cáº£nh bÃ¡o", "Alert count") + ": &f" + suspicionManager.getRecordedAlertCount()
        ));
        inventory.setItem(53, button(Material.ARROW,
                t(viewer, "&eTrang sau", "&eNext page"),
                t(viewer, "&7Sang trang káº¿ tiáº¿p", "&7Go to the next page")));

        viewer.openInventory(inventory);
    }

    public void openPlayerCheck(CommandSender sender, String playerName) {
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordCheck(playerName);
        }

        PlayerRiskProfile profile = suspicionManager.getOrCreateProfile(playerName);
        ThreatLevel level = suspicionManager.getThreatLevel(profile.getSuspicion());
        SkillClass skillClass = suspicionManager.getSkillClass(profile);

        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&6[Check] &f" + profile.getName()
                    + " | " + t(sender, "Ä‘iá»ƒm nghi ngá»", "suspicion") + "=" + profile.getSuspicion()
                    + " | tier=" + suspicionManager.getRiskTier(profile.getSuspicion()).name()
                    + " | " + t(sender, "má»©c nguy cÆ¡", "threat") + "=" + level.name()
                    + " | " + t(sender, "phÃ¢n loáº¡i ká»¹ nÄƒng", "skill class") + "=" + skillClass.name()
                    + " | " + t(sender, "vá»‹ trÃ­ nghi ngá»", "last suspicious location") + "=" + profile.getLastSuspiciousLocationSummary()));
            return;
        }

        openPlayerCheck((Player) sender, playerName, -1);
    }

    private void openPlayerCheck(Player viewer, String playerName, int backPage) {
        PlayerRiskProfile profile = suspicionManager.getOrCreateProfile(playerName);
        ThreatLevel level = suspicionManager.getThreatLevel(profile.getSuspicion());
        SkillClass skillClass = suspicionManager.getSkillClass(profile);

        CheckHolder holder = new CheckHolder(profile.getName(), backPage);
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&b&lCheck: &f" + profile.getName()));
        holder.inventory = inventory;

        fillCheckBackground(inventory);
        inventory.setItem(4, buildProfileItem(viewer, profile));
        inventory.setItem(19, buildSuspiciousLocationCard(viewer, profile));
        inventory.setItem(21, buildCurrentLocationCard(viewer, profile.getName()));
        inventory.setItem(23, buildThreatCard(viewer, profile, level, skillClass));
        inventory.setItem(25, buildLearningCard(viewer, profile));
        inventory.setItem(31, buildActionCard(viewer, level, skillClass));
        inventory.setItem(45, button(Material.ENDER_EYE,
                t(viewer, "&dBáº¯t Ä‘áº§u quan sÃ¡t", "&dStart observing"),
                t(viewer, "&7Cho AI vÃ  bot theo dÃµi ngÆ°á»i chÆ¡i nÃ y", "&7Let AI and the bot observe this player")));
        inventory.setItem(46, statCard(
                Material.REDSTONE,
                t(viewer, "&cÄiá»ƒm nghi ngá»", "&cSuspicion score"),
                "&f" + profile.getSuspicion(),
                "&7Alert: &f" + profile.getTotalAlerts()
        ));
        inventory.setItem(47, statCard(
                Material.BLAZE_POWDER,
                t(viewer, "&6Hack / Pro", "&6Hack / Pro"),
                "&f" + profile.getHackConfidence() + " / " + profile.getProConfidence(),
                "&7Skill: &f" + skillClass.name()
        ));
        inventory.setItem(48, statCard(
                Material.CLOCK,
                t(viewer, "&bTelemetry", "&bTelemetry"),
                "&fAim: " + profile.getSuspiciousAimSamples(),
                "&fMove: " + profile.getSuspiciousMoveSamples()
        ));
        inventory.setItem(49, backPage >= 0
                ? button(Material.ARROW,
                t(viewer, "&eQuay láº¡i", "&eBack"),
                t(viewer, "&7Trá»Ÿ vá» dashboard", "&7Return to the dashboard"))
                : button(Material.BARRIER,
                t(viewer, "&cÄÃ³ng", "&cClose"),
                t(viewer, "&7ÄÃ³ng giao diá»‡n kiá»ƒm tra", "&7Close the check interface")));
        inventory.setItem(53, button(Material.CLOCK,
                t(viewer, "&bLÃ m má»›i dá»¯ liá»‡u", "&bRefresh data"),
                t(viewer, "&7Táº£i láº¡i há»“ sÆ¡ ngÆ°á»i chÆ¡i nÃ y", "&7Reload this player's profile")));

        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof DashboardHolder) && !(holder instanceof CheckHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player viewer = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (holder instanceof DashboardHolder) {
            handleDashboardClick(viewer, (DashboardHolder) holder, slot, event.getCurrentItem());
            return;
        }
        handleCheckClick(viewer, (CheckHolder) holder, slot);
    }

    private void handleDashboardClick(Player viewer, DashboardHolder holder, int slot, ItemStack clicked) {
        if (slot == 45) {
            openDashboard(viewer, holder.page - 1);
            return;
        }
        if (slot == 49) {
            openDashboard(viewer, holder.page);
            return;
        }
        if (slot == 53) {
            openDashboard(viewer, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE || clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            return;
        }

        String playerName = ChatColor.stripColor(displayName).trim();
        if (playerName.isBlank()) {
            return;
        }
        openPlayerCheck(viewer, playerName, holder.page);
    }

    private void handleCheckClick(Player viewer, CheckHolder holder, int slot) {
        if (slot == 45) {
            if (plugin.getServerScanner() != null) {
                plugin.getServerScanner().observePlayer(viewer, holder.playerName, "check-gui", true);
            }
            openPlayerCheck(viewer, holder.playerName, holder.backPage);
            return;
        }
        if (slot == 53) {
            openPlayerCheck(viewer, holder.playerName, holder.backPage);
            return;
        }
        if (slot != 49) {
            return;
        }
        if (holder.backPage >= 0) {
            openDashboard(viewer, holder.backPage);
            return;
        }
        viewer.closeInventory();
    }

    private ItemStack buildProfileItem(CommandSender viewer, PlayerRiskProfile profile) {
        ThreatLevel level = suspicionManager.getThreatLevel(profile.getSuspicion());
        SkillClass skillClass = suspicionManager.getSkillClass(profile);

        Player online = Bukkit.getPlayerExact(profile.getName());
        ItemStack item = online != null ? new ItemStack(Material.PLAYER_HEAD, 1) : new ItemStack(materialForLevel(level), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (meta instanceof SkullMeta && online != null) {
            ((SkullMeta) meta).setOwningPlayer(online);
        }
        meta.setDisplayName(color(levelColor(level) + profile.getName()));
        meta.setLore(buildProfileLore(viewer, profile, level, skillClass));
        item.setItemMeta(meta);
        return item;
    }

    private List<String> buildProfileLore(CommandSender viewer, PlayerRiskProfile profile, ThreatLevel level, SkillClass skillClass) {
        List<String> lore = new ArrayList<>();
        lore.add(color("&7" + t(viewer, "Má»©c nguy cÆ¡", "Threat") + ": " + levelLabel(viewer, level)));
        lore.add(color("&7Risk tier: &f" + suspicionManager.getRiskTier(profile.getSuspicion()).name()));
        lore.add(color("&7" + t(viewer, "Äiá»ƒm nghi ngá»", "Suspicion") + ": &f" + profile.getSuspicion()));
        lore.add(color("&7" + t(viewer, "Tá»•ng cáº£nh bÃ¡o", "Alerts") + ": &f" + profile.getTotalAlerts()));
        lore.add(color("&7" + t(viewer, "PhÃ¢n loáº¡i ká»¹ nÄƒng", "Skill class") + ": &f" + skillClass.name()));
        lore.add(color("&7Hack / Pro: &f" + profile.getHackConfidence() + " / " + profile.getProConfidence()));
        lore.add(color("&7" + t(viewer, "Vá»‹ trÃ­ nghi ngá»", "Suspicious location") + ": &f" + profile.getLastSuspiciousLocationSummary()));
        lore.add(color("&7IP: &f" + safe(profile.getLastKnownIp())));
        lore.add(color(t(viewer, "&8Báº¥m Ä‘á»ƒ má»Ÿ há»“ sÆ¡ kiá»ƒm tra chi tiáº¿t", "&8Click to open the detailed check profile")));
        return lore;
    }

    private ItemStack buildSuspiciousLocationCard(CommandSender viewer, PlayerRiskProfile profile) {
        ItemStack item = new ItemStack(Material.COMPASS, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color(t(viewer, "&bVá»‹ trÃ­ nghi ngá» hack", "&bSuspicious location")));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7" + t(viewer, "Tá»a Ä‘á»™", "Coordinates") + ": &f" + profile.getLastSuspiciousLocationSummary()));
        if (profile.getLastSuspiciousAt() > 0L) {
            long agoSeconds = Math.max(0L, (System.currentTimeMillis() - profile.getLastSuspiciousAt()) / 1000L);
            lore.add(color("&7" + t(viewer, "Cáº­p nháº­t", "Updated") + ": &f" + agoSeconds + "s"));
        } else {
            lore.add(color("&7" + t(viewer, "Cáº­p nháº­t", "Updated") + ": &f" + t(viewer, "chÆ°a cÃ³ dá»¯ liá»‡u", "no data yet")));
        }
        lore.add(color(t(viewer,
                "&8Vá»‹ trÃ­ gáº§n nháº¥t mÃ  AI tháº¥y cÃ³ dáº¥u hiá»‡u báº¥t thÆ°á»ng",
                "&8The last location where AI noticed suspicious behavior")));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCurrentLocationCard(CommandSender viewer, String playerName) {
        ItemStack item = new ItemStack(Material.MAP, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color(t(viewer, "&aVá»‹ trÃ­ hiá»‡n táº¡i", "&aCurrent location")));
        List<String> lore = new ArrayList<>();
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && online.isOnline() && online.getWorld() != null) {
            lore.add(color("&7" + t(viewer, "Tháº¿ giá»›i", "World") + ": &f" + online.getWorld().getName()));
            lore.add(color("&7" + t(viewer, "Tá»a Ä‘á»™", "Coordinates") + ": &f"
                    + online.getLocation().getBlockX() + ", "
                    + online.getLocation().getBlockY() + ", "
                    + online.getLocation().getBlockZ()));
        } else {
            lore.add(color(t(viewer, "&7NgÆ°á»i chÆ¡i Ä‘ang offline", "&7Player is currently offline")));
        }
        lore.add(color(t(viewer,
                "&8DÃ¹ng Ä‘á»ƒ Ä‘á»‘i chiáº¿u vá»›i vá»‹ trÃ­ nghi ngá» á»Ÿ bÃªn trÃ¡i",
                "&8Use this to compare against the suspicious location on the left")));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildThreatCard(CommandSender viewer, PlayerRiskProfile profile, ThreatLevel level, SkillClass skillClass) {
        ItemStack item = new ItemStack(materialForLevel(level), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color(t(viewer, "&eTá»•ng quan nguy cÆ¡", "&eThreat overview")));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Threat: " + levelLabel(viewer, level)));
        lore.add(color("&7Risk tier: &f" + suspicionManager.getRiskTier(profile.getSuspicion()).name()));
        lore.add(color("&7" + t(viewer, "PhÃ¢n loáº¡i ká»¹ nÄƒng", "Skill class") + ": &f" + skillClass.name()));
        lore.add(color("&7" + t(viewer, "Äiá»ƒm nghi ngá»", "Suspicion") + ": &f" + profile.getSuspicion()));
        lore.add(color("&7" + t(viewer, "Cáº£nh bÃ¡o", "Alerts") + ": &f" + profile.getTotalAlerts()));
        lore.add(color("&7Hack / Pro confidence: &f" + profile.getHackConfidence() + " / " + profile.getProConfidence()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildLearningCard(CommandSender viewer, PlayerRiskProfile profile) {
        ItemStack item = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color(t(viewer, "&dTelemetry hÃ nh vi", "&dBehavior telemetry")));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Aim: &f" + profile.getSuspiciousAimSamples()));
        lore.add(color("&7Move: &f" + profile.getSuspiciousMoveSamples()));
        lore.add(color("&7CPS: &f" + profile.getHighCpsSamples()));
        lore.add(color("&7Legit combat: &f" + profile.getLegitCombatSamples()));
        lore.add(color("&7IP: &f" + safe(profile.getLastKnownIp())));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildActionCard(CommandSender viewer, ThreatLevel level, SkillClass skillClass) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color(t(viewer, "&6Äá» xuáº¥t xá»­ lÃ½", "&6Recommended action")));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Skill class: &f" + skillClass.name()));
        lore.add(color("&7Threat: " + levelLabel(viewer, level)));
        lore.add(color("&f" + recommendAction(viewer, level, skillClass)));
        lore.add(color(t(viewer,
                "&8Báº¡n cÃ³ thá»ƒ báº¥m nÃºt quan sÃ¡t Ä‘á»ƒ AI theo dÃµi thÃªm",
                "&8You can click observe to let the AI watch longer")));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String recommendAction(CommandSender viewer, ThreatLevel level, SkillClass skillClass) {
        if (level == ThreatLevel.HIGH && skillClass == SkillClass.HACK_LIKELY) {
            return t(viewer,
                    "Äá» xuáº¥t kick ngay vÃ  xem xÃ©t termban.",
                    "Recommend kicking immediately and reviewing a temp-ban.");
        }
        if (level == ThreatLevel.HIGH) {
            return t(viewer,
                    "Cáº§n staff theo dÃµi gáº¥p Ä‘á»ƒ xÃ¡c minh.",
                    "Staff should verify this player urgently.");
        }
        if (level == ThreatLevel.MEDIUM) {
            return t(viewer,
                    "Tiáº¿p tá»¥c quan sÃ¡t vÃ  Ä‘á»‘i chiáº¿u alert anti-cheat.",
                    "Keep observing and compare with anti-cheat alerts.");
        }
        if (skillClass == SkillClass.PRO) {
            return t(viewer,
                    "CÃ³ thá»ƒ lÃ  ngÆ°á»i chÆ¡i ká»¹ nÄƒng cao, cáº§n giáº£m false positive.",
                    "This may be a skilled player, so reduce false positives.");
        }
        return t(viewer,
                "Rá»§i ro tháº¥p, tiáº¿p tá»¥c scan Ä‘á»‹nh ká»³.",
                "Low risk for now. Keep periodic scans running.");
    }

    private void fillCheckBackground(Inventory inventory) {
        int[] borderSlots = new int[]{
                0, 1, 2, 3, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16, 17,
                18, 20, 22, 24, 26,
                27, 28, 29, 30, 32, 33, 34, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44,
                50, 51, 52
        };
        for (int slot : borderSlots) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler(slot % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
            }
        }
    }

    private ItemStack statCard(Material material, String name, String line1, String line2) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color(name));
        List<String> lore = new ArrayList<>();
        lore.add(color(line1));
        lore.add(color(line2));
        lore.add(color("&8Quick summary"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, String loreLine) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color(name));
        meta.setLore(Collections.singletonList(color(loreLine)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack summaryItem(Material material, String name, String loreLine) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color(name));
        meta.setLore(Collections.singletonList(color(loreLine)));
        item.setItemMeta(meta);
        return item;
    }

    private int countThreat(ThreatLevel level) {
        if (level == ThreatLevel.HIGH) {
            return suspicionManager.countAtOrAbove(RiskTier.DANGER);
        }
        if (level == ThreatLevel.MEDIUM) {
            return Math.max(0, suspicionManager.countAtOrAbove(RiskTier.ALERT) - suspicionManager.countAtOrAbove(RiskTier.DANGER));
        }
        return Math.max(0, suspicionManager.countAtOrAbove(RiskTier.WATCH) - suspicionManager.countAtOrAbove(RiskTier.ALERT));
    }

    private Material materialForLevel(ThreatLevel level) {
        switch (level) {
            case HIGH:
                return Material.REDSTONE_BLOCK;
            case MEDIUM:
                return Material.ORANGE_CONCRETE;
            default:
                return Material.YELLOW_CONCRETE;
        }
    }

    private String levelColor(ThreatLevel level) {
        switch (level) {
            case HIGH:
                return "&c";
            case MEDIUM:
                return "&6";
            default:
                return "&e";
        }
    }

    private String levelLabel(CommandSender viewer, ThreatLevel level) {
        switch (level) {
            case HIGH:
                return color(t(viewer, "&cHIGH", "&cHIGH"));
            case MEDIUM:
                return color(t(viewer, "&6MEDIUM", "&6MEDIUM"));
            default:
                return color(t(viewer, "&eLOW", "&eLOW"));
        }
    }

    private String safe(String input) {
        return (input == null || input.isBlank()) ? "unknown" : input;
    }

    private String t(CommandSender sender, String vietnamese, String english) {
        return plugin.tr(sender, vietnamese, english);
    }

    private String color(String input) {
        return input.replace("&", "\u00A7");
    }

    private static final class DashboardHolder implements InventoryHolder {
        private final int page;
        private final int totalPages;
        private Inventory inventory;

        private DashboardHolder(int page, int totalPages) {
            this.page = page;
            this.totalPages = totalPages;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class CheckHolder implements InventoryHolder {
        private final String playerName;
        private final int backPage;
        private Inventory inventory;

        private CheckHolder(String playerName, int backPage) {
            this.playerName = playerName;
            this.backPage = backPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
