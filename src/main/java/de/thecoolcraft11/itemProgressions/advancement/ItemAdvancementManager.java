package de.thecoolcraft11.itemProgressions.advancement;

import com.google.gson.JsonObject;
import de.thecoolcraft11.itemProgressions.config.LockConfig;
import de.thecoolcraft11.itemProgressions.logic.LockEvaluator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;


public class ItemAdvancementManager implements de.thecoolcraft11.itemProgressions.listener.LockListeners.AdvancementGranting {
    private final Plugin plugin;
    private final LockEvaluator evaluator;
    private final AdvancementRegistry registry;

    private final String tabKey;
    private final String tabTitle;
    private final String tabDescription;
    private final String tabIcon;
    private final String tabBackground;

    private final String defaultName;
    private final String defaultIcon;
    private final String defaultRealtimeDesc;
    private final String defaultPerPlayerDesc;
    private final String defaultGlobalDesc;
    private final String dateFormat;
    private final Object gridLayout;
    private final int gridWidth;

    private final Map<Material, NamespacedKey> itemKeys = new HashMap<>();
    private final Map<World.Environment, NamespacedKey> dimensionKeys = new HashMap<>();
    private final Map<LockConfig.LockRule, String> ruleAdvancementKeys = new HashMap<>();
    private final Map<LockConfig.DimensionLockRule, String> dimensionRuleAdvancementKeys = new HashMap<>();
    private final List<String> dummyAdvancementKeys = new ArrayList<>();
    private int dummyCounter = 0;

    public ItemAdvancementManager(Plugin plugin, LockEvaluator evaluator, FileConfiguration cfg) {
        this.plugin = plugin;
        this.evaluator = evaluator;
        this.registry = new AdvancementRegistry(plugin);

        String root = "itemAdvancements";
        boolean advancementsEnabled = cfg.getBoolean(root + ".enabled", true);


        Object gridLayoutObj = cfg.get(root + ".gridLayout");
        if (gridLayoutObj instanceof String) {
            this.gridLayout = gridLayoutObj;
        } else if (gridLayoutObj instanceof Boolean) {
            this.gridLayout = gridLayoutObj;
        } else {
            this.gridLayout = false;
        }

        this.gridWidth = Math.max(1, cfg.getInt(root + ".gridWidth", 2));
        this.tabKey = cfg.getString(root + ".tab.key", plugin.getName().toLowerCase(Locale.ROOT) + ":locked_items");
        this.tabTitle = cfg.getString(root + ".tab.title", "Locked Items");
        this.tabDescription = cfg.getString(root + ".tab.description", "Items managed by ItemProgressions");
        this.tabIcon = cfg.getString(root + ".tab.icon", "minecraft:barrier");
        this.tabBackground = cfg.getString(root + ".tab.background", "minecraft:textures/gui/advancement/backgrounds/adventure.png");

        this.defaultName = cfg.getString(root + ".defaults.name", "");
        this.defaultIcon = cfg.getString(root + ".defaults.icon", "");
        this.defaultRealtimeDesc = cfg.getString(root + ".defaults.realtimeDescription", "%name% can be used at %time%");
        this.defaultPerPlayerDesc = cfg.getString(root + ".defaults.perPlayerDescription", "%name% can be used after %playtime%");
        this.defaultGlobalDesc = cfg.getString(root + ".defaults.globalDescription", "%name% can be used after %servertime%");
        this.dateFormat = cfg.getString(root + ".defaults.dateFormat", "yyyy-MM-dd HH:mm:ss Z");

        if (!advancementsEnabled) {
            plugin.getLogger().info("Item advancements are disabled in config");
            return;
        }

        registerTab();
        registerItemRuleAdvancements();
        registerItemAdvancements();
        registerDimensionRuleAdvancements();
        registerDimensionAdvancements();

        for (Player p : Bukkit.getOnlinePlayers()) {
            grantRootAndRules(p);
        }
    }

    private void registerTab() {
        AdvancementBuilder builder = AdvancementBuilder.createTab(tabTitle, tabDescription, tabIcon, tabBackground)
                .setShowToast(false)
                .setAnnounceToChat(false)
                .setHidden(false)
                .addSimpleCriteria("root");
        JsonObject json = builder.build();
        registry.register(tabKey, json);
    }

    private void registerItemRuleAdvancements() {
        List<LockConfig.LockRule> rules = evaluator.config().rules;

        List<RuleWithTime> rulesWithTimes = new ArrayList<>();
        for (LockConfig.LockRule rule : rules) {
            long unlockTime = calculateRuleUnlockTime(rule);
            rulesWithTimes.add(new RuleWithTime(rule, unlockTime));
        }

        rulesWithTimes.sort(Comparator.comparingLong(a -> a.unlockTime));

        String previousAdvancementKey = tabKey;

        for (RuleWithTime rwt : rulesWithTimes) {
            LockConfig.LockRule rule = rwt.rule;
            String ruleIcon = validateIcon(rule.iconId() != null ? rule.iconId() : "minecraft:barrier");
            String ruleName = rule.displayName() != null ? rule.displayName() : "Rule";
            String ruleDesc = rule.description() != null ? rule.description() : formatRuleDescription(rule);

            AdvancementBuilder builder = new AdvancementBuilder(ruleName, ruleDesc)
                    .setParent(previousAdvancementKey)
                    .setIcon(ruleIcon)
                    .setFrameType("challenge")
                    .setShowToast(false)
                    .setAnnounceToChat(false)
                    .setHidden(false)
                    .addSimpleCriteria("rule_unlock");

            JsonObject json = builder.build();
            String ruleKeyStr = plugin.getName().toLowerCase(Locale.ROOT) + ":rule_" + ruleHashKey(rule);
            registry.register(ruleKeyStr, json);
            ruleAdvancementKeys.put(rule, ruleKeyStr);

            previousAdvancementKey = ruleKeyStr;
        }
    }

    private void registerItemAdvancements() {
        Set<Material> locked = evaluator.getAllLockedMaterials();
        List<Material> itemList = new ArrayList<>();


        for (Material mat : locked) {
            if (!mat.isItem()) {
                plugin.getLogger().info("Skipping advancement for " + mat.name() + " (not an item)");
                continue;
            }
            itemList.add(mat);
        }

        if (gridLayout instanceof String gridMode) {
            switch (gridMode) {
                case "columns" -> registerItemAdvancementsColumns(itemList);
                case "square" -> registerItemAdvancementsGrid(itemList);
                case "auto" -> registerItemAdvancementsAuto(itemList);
                default -> registerItemAdvancementsLinear(itemList);
            }
        } else if (gridLayout instanceof Boolean && (Boolean) gridLayout) {
            registerItemAdvancementsGrid(itemList);
        } else {
            registerItemAdvancementsLinear(itemList);
        }
    }

    private void registerItemAdvancementsLinear(List<Material> itemList) {
        for (Material mat : itemList) {
            registerSingleItemAdvancement(mat, null);
        }
    }

    private void registerItemAdvancementsColumns(List<Material> itemList) {
        if (itemList.isEmpty()) {
            return;
        }


        Map<LockConfig.LockRule, List<Material>> itemsByRule = new HashMap<>();
        for (Material mat : itemList) {
            for (LockConfig.LockRule rule : evaluator.config().rules) {
                if (rule.matches(mat)) {
                    itemsByRule.computeIfAbsent(rule, k -> new ArrayList<>()).add(mat);
                    break;
                }
            }
        }


        for (LockConfig.LockRule rule : evaluator.config().rules) {
            List<Material> ruleItems = itemsByRule.get(rule);
            if (ruleItems == null || ruleItems.isEmpty()) {
                continue;
            }

            String ruleKey = ruleAdvancementKeys.get(rule);
            if (ruleKey == null) {
                continue;
            }

            plugin.getLogger().info("Creating column grid for rule " + (rule.displayName() != null ? rule.displayName() : "Rule") + " with " + ruleItems.size() + " items");

            final String namespacePrefix = plugin.getName().toLowerCase(Locale.ROOT);
            String lastItemKey = null;

            for (Material mat : ruleItems) {
                String itemParent = (lastItemKey == null) ? ruleKey : lastItemKey;
                registerSingleItemAdvancementWithParent(mat, itemParent);
                lastItemKey = namespacePrefix + ":lock_" + mat.name().toLowerCase(Locale.ROOT);
            }

            if (lastItemKey != null) {
                createDummyAdvancement(lastItemKey);
            }
        }

    }

    private void registerItemAdvancementsAuto(List<Material> itemList) {
        if (itemList.isEmpty()) {
            return;
        }

        Map<LockConfig.LockRule, List<Material>> itemsByRule = new HashMap<>();
        for (Material mat : itemList) {
            for (LockConfig.LockRule rule : evaluator.config().rules) {
                if (rule.matches(mat)) {
                    itemsByRule.computeIfAbsent(rule, k -> new ArrayList<>()).add(mat);
                    break;
                }
            }
        }

        java.util.function.Function<Material, String> getAdvKey = (mat) ->
                plugin.getName().toLowerCase(Locale.ROOT) + ":lock_" + mat.name().toLowerCase(Locale.ROOT);

        for (LockConfig.LockRule rule : evaluator.config().rules) {
            List<Material> ruleItems = itemsByRule.get(rule);
            if (ruleItems == null || ruleItems.isEmpty()) {
                continue;
            }

            String ruleKey = ruleAdvancementKeys.get(rule);
            if (ruleKey == null) {
                continue;
            }

            int total = ruleItems.size();
            int width = (int) Math.ceil(Math.sqrt(total));
            width = Math.max(1, width);
            int rows = (int) Math.ceil(total / (double) width);

            plugin.getLogger().info("Creating auto grid (" + width + "x" + rows + ") for rule " + (rule.displayName() != null ? rule.displayName() : "Rule") + " with " + total + " items");

            for (int row = 0; row < rows; row++) {
                int rowStart = row * width;
                int rowEnd = Math.min(total, rowStart + width);
                String previousItemKey = null;

                for (int idx = rowStart; idx < rowEnd; idx++) {
                    Material mat = ruleItems.get(idx);
                    String parentKey = (idx == rowStart) ? ruleKey : previousItemKey;
                    registerSingleItemAdvancementWithParent(mat, parentKey);
                    previousItemKey = getAdvKey.apply(mat);
                }

                if (previousItemKey != null) {
                    createDummyAdvancement(previousItemKey);
                }
            }
        }
    }

    private String createDummyAdvancement(String parentKey) {
        String dummyName = "Dummy " + dummyCounter;
        dummyCounter++;

        AdvancementBuilder builder = new AdvancementBuilder(dummyName, "Divider")
                .setParent(parentKey)
                .setIcon("minecraft:barrier")
                .setFrameType("task")
                .setShowToast(false)
                .setAnnounceToChat(false)
                .setHidden(true)
                .addSimpleCriteria("dummy");

        JsonObject json = builder.build();
        String dummyKeyStr = plugin.getName().toLowerCase(Locale.ROOT) + ":dummy_" + dummyCounter;
        registry.register(dummyKeyStr, json);
        dummyAdvancementKeys.add(dummyKeyStr);
        return dummyKeyStr;
    }


    private void registerItemAdvancementsGrid(List<Material> itemList) {
        if (itemList.isEmpty()) {
            return;
        }


        Map<LockConfig.LockRule, List<Material>> itemsByRule = new HashMap<>();
        for (Material mat : itemList) {
            for (LockConfig.LockRule rule : evaluator.config().rules) {
                if (rule.matches(mat)) {
                    itemsByRule.computeIfAbsent(rule, k -> new ArrayList<>()).add(mat);
                    break;
                }
            }
        }

        java.util.function.Function<Material, String> getAdvKey = (mat) ->
                plugin.getName().toLowerCase(Locale.ROOT) + ":lock_" + mat.name().toLowerCase(Locale.ROOT);


        for (LockConfig.LockRule rule : evaluator.config().rules) {
            List<Material> ruleItems = itemsByRule.get(rule);
            if (ruleItems == null || ruleItems.isEmpty()) {
                continue;
            }

            String ruleKey = ruleAdvancementKeys.get(rule);
            if (ruleKey == null) {
                continue;
            }

            int total = ruleItems.size();
            int height = (int) Math.ceil(total / (double) gridWidth);
            plugin.getLogger().info("Creating " + gridWidth + "x" + height + " square grid for rule " + (rule.displayName() != null ? rule.displayName() : "Rule") + " with " + total + " items");


            String previousDummy = null;

            for (int col = 0; col < gridWidth; col++) {
                String parentKey = (col == 0) ? ruleKey : previousDummy;
                String lastItemKey = null;

                for (int row = 0; row < height; row++) {
                    int itemIndex = row + (col * height);

                    if (itemIndex >= total) {
                        break;
                    }

                    Material mat = ruleItems.get(itemIndex);
                    String itemParent;

                    if (row == 0) {

                        itemParent = parentKey;
                    } else {

                        itemParent = lastItemKey;
                    }

                    registerSingleItemAdvancementWithParent(mat, itemParent);
                    lastItemKey = getAdvKey.apply(mat);
                }


                if (lastItemKey != null) {
                    previousDummy = createDummyAdvancement(lastItemKey);
                }
            }
        }
    }


    private void registerSingleItemAdvancement(Material mat, String customParent) {
        String matKey = mat.name().toLowerCase(Locale.ROOT);
        String title = getItemName(mat);
        String icon = getIconForMaterial(mat);

        String parentAdvancementKey = customParent != null ? customParent : tabKey;
        LockConfig.UnlockCondition.Type condType = null;
        Instant at = null;
        long seconds = 0L;


        for (LockConfig.LockRule rule : evaluator.config().rules) {
            if (rule.matches(mat)) {
                if (customParent == null) {

                    String ruleKey = ruleAdvancementKeys.get(rule);
                    if (ruleKey != null) {
                        parentAdvancementKey = ruleKey;
                    }
                }
                condType = rule.condition().type();
                at = rule.condition().at();
                seconds = rule.condition().seconds();
                break;
            }
        }

        String descriptionTemplate = "Progress to unlock usage.";
        if (condType != null) {
            descriptionTemplate = switch (condType) {
                case REALTIME -> defaultRealtimeDesc;
                case PER_PLAYER -> defaultPerPlayerDesc;
                case GLOBAL -> defaultGlobalDesc;
            };
        }

        String finalDescription = null;
        if (condType != null) {
            finalDescription = formatDescription(descriptionTemplate, title, condType, at, seconds);
        }

        AdvancementBuilder builder = new AdvancementBuilder(title, finalDescription)
                .setParent(parentAdvancementKey)
                .setIcon(icon)
                .setShowToast(true)
                .setAnnounceToChat(false)
                .setHidden(false)
                .addSimpleCriteria("lock_done");

        JsonObject json = builder.build();
        String keyStr = plugin.getName().toLowerCase(Locale.ROOT) + ":lock_" + matKey;
        registry.register(keyStr, json);
        itemKeys.put(mat, NamespacedKey.fromString(keyStr));
    }

    private void registerSingleItemAdvancementWithParent(Material mat, String parentKey) {
        registerSingleItemAdvancement(mat, parentKey);
    }

    private void registerDimensionRuleAdvancements() {
        List<LockConfig.DimensionLockRule> dimRules = evaluator.config().dimensionLocks;

        List<DimensionRuleWithTime> dimRulesWithTimes = new ArrayList<>();
        for (LockConfig.DimensionLockRule rule : dimRules) {
            long unlockTime = calculateDimensionRuleUnlockTime(rule);
            dimRulesWithTimes.add(new DimensionRuleWithTime(rule, unlockTime));
        }

        dimRulesWithTimes.sort(Comparator.comparingLong(a -> a.unlockTime));

        String previousAdvancementKey = tabKey;

        for (DimensionRuleWithTime drwt : dimRulesWithTimes) {
            LockConfig.DimensionLockRule rule = drwt.rule;
            String ruleIcon = validateIcon(rule.iconId() != null ? rule.iconId() : "minecraft:barrier");
            String ruleName = rule.displayName() != null ? rule.displayName() : rule.dimension().name();
            String ruleDesc = rule.description() != null ? rule.description() : formatDimensionRuleDescription(rule);

            AdvancementBuilder builder = new AdvancementBuilder(ruleName, ruleDesc)
                    .setParent(previousAdvancementKey)
                    .setIcon(ruleIcon)
                    .setFrameType("challenge")
                    .setShowToast(false)
                    .setAnnounceToChat(false)
                    .setHidden(false)
                    .addSimpleCriteria("dim_rule_unlock");

            JsonObject json = builder.build();
            String ruleKeyStr = plugin.getName().toLowerCase(Locale.ROOT) + ":dim_rule_" + rule.dimension().name().toLowerCase(Locale.ROOT);
            registry.register(ruleKeyStr, json);
            dimensionRuleAdvancementKeys.put(rule, ruleKeyStr);

            previousAdvancementKey = ruleKeyStr;
        }
    }

    private void registerDimensionAdvancements() {
        for (LockConfig.DimensionLockRule rule : evaluator.config().dimensionLocks) {
            World.Environment dim = rule.dimension();
            String dimKey = dim.name().toLowerCase(Locale.ROOT);
            String title = prettifyName(dim.name());
            String icon = getIconForDimension(dim);

            String parentAdvancementKey = tabKey;
            String ruleKey = dimensionRuleAdvancementKeys.get(rule);
            if (ruleKey != null) {
                parentAdvancementKey = ruleKey;
            }

            LockConfig.UnlockCondition cond = rule.condition();
            String descriptionTemplate = switch (cond.type()) {
                case REALTIME -> defaultRealtimeDesc;
                case PER_PLAYER -> defaultPerPlayerDesc;
                case GLOBAL -> defaultGlobalDesc;
            };

            String finalDescription = formatDescription(descriptionTemplate, title, cond.type(), cond.at(), cond.seconds());

            AdvancementBuilder builder = new AdvancementBuilder(title, finalDescription)
                    .setParent(parentAdvancementKey)
                    .setIcon(icon)
                    .setShowToast(true)
                    .setAnnounceToChat(false)
                    .setHidden(false)
                    .addSimpleCriteria("dim_unlock");

            JsonObject json = builder.build();
            String keyStr = plugin.getName().toLowerCase(Locale.ROOT) + ":dim_" + dimKey;
            registry.register(keyStr, json);
            dimensionKeys.put(dim, NamespacedKey.fromString(keyStr));
        }
    }

    private long calculateRuleUnlockTime(LockConfig.LockRule rule) {
        LockConfig.UnlockCondition cond = rule.condition();
        return switch (cond.type()) {
            case REALTIME -> {
                if (cond.at() == null) yield Long.MAX_VALUE;
                long now = Instant.now().getEpochSecond();
                long target = cond.at().getEpochSecond();
                long remaining = Math.max(0, target - now);
                yield remaining * 1000;
            }
            case PER_PLAYER, GLOBAL -> cond.seconds() * 1000;
        };
    }

    private long calculateDimensionRuleUnlockTime(LockConfig.DimensionLockRule rule) {
        LockConfig.UnlockCondition cond = rule.condition();
        return switch (cond.type()) {
            case REALTIME -> {
                if (cond.at() == null) yield Long.MAX_VALUE;
                long now = Instant.now().getEpochSecond();
                long target = cond.at().getEpochSecond();
                long remaining = Math.max(0, target - now);
                yield remaining * 1000;
            }
            case PER_PLAYER, GLOBAL -> cond.seconds() * 1000;
        };
    }

    private String formatRuleDescription(LockConfig.LockRule rule) {
        LockConfig.UnlockCondition cond = rule.condition();
        return switch (cond.type()) {
            case REALTIME -> "Available at " + (cond.at() != null ? cond.at().toString() : "unknown time");
            case PER_PLAYER -> "Unlocks after " + LockEvaluator.humanDuration(cond.seconds());
            case GLOBAL -> "Server unlock after " + LockEvaluator.humanDuration(cond.seconds());
        };
    }

    private String formatDimensionRuleDescription(LockConfig.DimensionLockRule rule) {
        LockConfig.UnlockCondition cond = rule.condition();
        return switch (cond.type()) {
            case REALTIME -> "Available at " + (cond.at() != null ? cond.at().toString() : "unknown time");
            case PER_PLAYER -> "Unlocks after " + LockEvaluator.humanDuration(cond.seconds());
            case GLOBAL -> "Server unlock after " + LockEvaluator.humanDuration(cond.seconds());
        };
    }

    private String getItemName(Material mat) {
        if (defaultName != null && !defaultName.isEmpty()) {
            return defaultName;
        }

        try {
            ItemStack stack = new ItemStack(mat);
            String translationKey = stack.translationKey();
            if (!translationKey.isEmpty()) {
                String[] parts = translationKey.split("\\.");
                if (parts.length > 0) {
                    String name = parts[parts.length - 1];
                    return prettifyName(name);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get translation key for " + mat.name() + ": " + e.getMessage());
        }

        return prettifyName(mat.name());
    }

    private String getIconForMaterial(Material mat) {
        String materialIcon = "minecraft:" + mat.name().toLowerCase(Locale.ROOT);
        if (defaultIcon == null || defaultIcon.isEmpty()) {
            return materialIcon;
        }
        return defaultIcon;
    }

    private String getIconForDimension(World.Environment dim) {
        return switch (dim) {
            case NETHER -> "minecraft:netherrack";
            case THE_END -> "minecraft:end_stone";
            case NORMAL -> "minecraft:grass_block";
            default -> "minecraft:barrier";
        };
    }

    private String formatDescription(String template, String title,
                                     LockConfig.UnlockCondition.Type condType,
                                     Instant at, long seconds) {
        String result = template.replace("%name%", title);

        switch (condType) {
            case REALTIME -> {
                if (at != null) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
                        sdf.setTimeZone(TimeZone.getDefault());
                        String formattedDate = sdf.format(new java.util.Date(at.toEpochMilli()));
                        result = result.replace("%time%", formattedDate);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to format date: " + e.getMessage());
                        result = result.replace("%time%", at.toString());
                    }
                } else {
                    result = result.replace("%time%", "unknown time");
                }
            }
            case PER_PLAYER -> {
                String duration = seconds > 0 ? LockEvaluator.humanDuration(seconds) : "unknown playtime";
                result = result.replace("%playtime%", duration);
            }
            case GLOBAL -> {
                String duration = seconds > 0 ? LockEvaluator.humanDuration(seconds) : "unknown server time";
                result = result.replace("%servertime%", duration);
            }
        }

        return result;
    }

    private String validateIcon(String iconId) {
        if (iconId == null || iconId.isEmpty()) {
            return "minecraft:barrier";
        }

        if (!iconId.contains(":")) {
            iconId = "minecraft:" + iconId;
        }
        return iconId;
    }

    private String prettifyName(String name) {
        String s = name.replace('_', ' ');
        StringBuilder out = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                out.append(Character.toUpperCase(c));
                cap = false;
            } else {
                out.append(c);
                if (c == ' ') cap = true;
            }
        }
        return out.toString();
    }

    private String ruleHashKey(LockConfig.LockRule rule) {
        return Integer.toHexString(rule.hashCode());
    }

    public void grantRootAndRules(Player p) {
        org.bukkit.advancement.Advancement rootAdv = Bukkit.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString(tabKey)));
        if (rootAdv != null) {
            p.getAdvancementProgress(rootAdv).awardCriteria("root");
        }

        for (String ruleKey : ruleAdvancementKeys.values()) {
            org.bukkit.advancement.Advancement ruleAdv = Bukkit.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString(ruleKey)));
            if (ruleAdv != null) {
                p.getAdvancementProgress(ruleAdv).awardCriteria("rule_unlock");
            }
        }

        for (String dimRuleKey : dimensionRuleAdvancementKeys.values()) {
            org.bukkit.advancement.Advancement dimRuleAdv = Bukkit.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString(dimRuleKey)));
            if (dimRuleAdv != null) {
                p.getAdvancementProgress(dimRuleAdv).awardCriteria("dim_rule_unlock");
            }
        }


        for (String dummyKey : dummyAdvancementKeys) {
            org.bukkit.advancement.Advancement dummyAdv = Bukkit.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString(dummyKey)));
            if (dummyAdv != null) {
                p.getAdvancementProgress(dummyAdv).awardCriteria("dummy");
            }
        }
    }

    @Override
    public void grantIfUnlocked(Player p, Material mat) {
        NamespacedKey key = itemKeys.get(mat);
        if (key == null) return;
        org.bukkit.advancement.Advancement adv = Bukkit.getAdvancement(key);
        if (adv == null) return;
        p.getAdvancementProgress(adv).awardCriteria("lock_done");
    }

    public void grantDimensionIfUnlocked(Player p, World.Environment dim) {
        NamespacedKey key = dimensionKeys.get(dim);
        if (key == null) return;
        org.bukkit.advancement.Advancement adv = Bukkit.getAdvancement(key);
        if (adv == null) return;
        p.getAdvancementProgress(adv).awardCriteria("dim_unlock");
    }

    private record RuleWithTime(LockConfig.LockRule rule, long unlockTime) {
    }


    private record DimensionRuleWithTime(LockConfig.DimensionLockRule rule, long unlockTime) {
    }
}

