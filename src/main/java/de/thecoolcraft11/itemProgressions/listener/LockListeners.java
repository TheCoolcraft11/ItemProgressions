package de.thecoolcraft11.itemProgressions.listener;

import de.thecoolcraft11.itemProgressions.logic.LockEvaluator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LockListeners implements Listener {
    private final LockEvaluator evaluator;
    private final String messageTemplate;
    private final int messageCooldownSeconds;
    private final Map<UUID, Long> lastMsg = new HashMap<>();

    private final Map<UUID, Map<Material, Long>> cooldownExpiry = new HashMap<>();

    private final boolean allowBreaking;

    private final AdvancementGranting advancementGranting;

    public interface AdvancementGranting {
        void grantIfUnlocked(Player p, Material mat);
    }

    public LockListeners(LockEvaluator evaluator, String messageTemplate, int messageCooldownSeconds, boolean allowBreaking) {
        this(evaluator, messageTemplate, messageCooldownSeconds, allowBreaking, null);
    }

    public LockListeners(LockEvaluator evaluator, String messageTemplate, int messageCooldownSeconds, boolean allowBreaking, AdvancementGranting advancementGranting) {
        this.evaluator = evaluator;
        this.messageTemplate = messageTemplate;
        this.messageCooldownSeconds = messageCooldownSeconds;
        this.allowBreaking = allowBreaking;
        this.advancementGranting = advancementGranting;
    }

    private boolean hasBypass(Player p, Material mat) {
        NamespacedKey key = mat.getKey();
        String perm = "itemprogressions.bypass." + key.getKey().toLowerCase(Locale.ROOT);
        return p.hasPermission(perm);
    }

    private boolean check(Player p, Material mat) {
        if (hasBypass(p, mat)) return false;
        LockEvaluator.Result r = evaluator.canUse(p, mat);
        if (!r.allowed()) {
            maybeNotify(p, mat, r.remainingSeconds());
            return true;
        }
        return false;
    }

    private void maybeNotify(Player p, Material mat, long remaining) {
        long now = System.currentTimeMillis();
        long next = lastMsg.getOrDefault(p.getUniqueId(), 0L);
        if (now < next) return;
        lastMsg.put(p.getUniqueId(), now + messageCooldownSeconds * 1000L);
        String msg = messageTemplate
                .replace("%item%", mat.name())
                .replace("%remaining%", LockEvaluator.humanDuration(remaining));

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
        p.sendMessage(component);
    }

    private void maybeNotify(Player p, World.Environment dimension, long remaining) {
        long now = System.currentTimeMillis();
        long next = lastMsg.getOrDefault(p.getUniqueId(), 0L);
        if (now < next) return;
        lastMsg.put(p.getUniqueId(), now + messageCooldownSeconds * 1000L);
        String msg = messageTemplate
                .replace("%item%", dimension.name())
                .replace("%remaining%", LockEvaluator.humanDuration(remaining));

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
        p.sendMessage(component);
    }


    private ItemStack decorateIfLocked(Player p, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        boolean bypass = hasBypass(p, stack.getType());
        LockEvaluator.Result r = bypass ? new LockEvaluator.Result(true, 0) : evaluator.canUse(p, stack.getType());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (!r.allowed()) {

            meta.setEnchantmentGlintOverride(true);

            Component loreLine = Component.text("Locked: unlocks in " + LockEvaluator.humanDuration(r.remainingSeconds()))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false);

            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();

            boolean hasLockLine = false;
            if (!lore.isEmpty()) {
                String plainText = LegacyComponentSerializer.legacySection().serialize(lore.getFirst()).toLowerCase();
                hasLockLine = plainText.contains("locked:");
            }

            if (hasLockLine) {
                lore.set(0, loreLine);
            } else {
                lore.addFirst(loreLine);
            }

            meta.lore(lore);
            stack.setItemMeta(meta);
        } else {
            // Only remove the glint override if the item has no enchantments
            if (meta.hasEnchantmentGlintOverride() && meta.getEnchants().isEmpty()) {
                meta.setEnchantmentGlintOverride(false);
            }


            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                String plainText = LegacyComponentSerializer.legacySection().serialize(lore.getFirst()).toLowerCase();
                if (plainText.contains("locked:")) {
                    lore.removeFirst();
                    meta.lore(lore);
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }


    private void updateCooldowns(Player p) {

        Set<Material> mats = new HashSet<>();
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getContents()) {
            if (s != null && !s.getType().isAir()) mats.add(s.getType());
        }
        ItemStack[] armor = new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots(), inv.getItemInOffHand()};
        for (ItemStack s : armor) {
            if (s != null && !s.getType().isAir()) mats.add(s.getType());
        }

        UUID uuid = p.getUniqueId();
        Map<Material, Long> playerCooldowns = cooldownExpiry.computeIfAbsent(uuid, k -> new HashMap<>());
        long currentTime = System.currentTimeMillis();

        for (Material mat : mats) {
            if (hasBypass(p, mat)) {
                p.setCooldown(mat, 0);
                playerCooldowns.remove(mat);
                continue;
            }
            LockEvaluator.Result r = evaluator.canUse(p, mat);
            if (!r.allowed()) {
                long seconds = Math.max(0L, r.remainingSeconds());
                long expiryTime = currentTime + (seconds * 1000L);


                Long existingExpiry = playerCooldowns.get(mat);
                if (existingExpiry == null || currentTime >= existingExpiry) {
                    int ticks = (int) Math.min(Integer.MAX_VALUE, seconds * 20L);
                    if (ticks == 0) ticks = 1;
                    p.setCooldown(mat, ticks);
                    playerCooldowns.put(mat, expiryTime);
                }
            } else {

                p.setCooldown(mat, 0);
                playerCooldowns.remove(mat);
                if (advancementGranting != null) {
                    advancementGranting.grantIfUnlocked(p, mat);
                }
            }
        }


        playerCooldowns.entrySet().removeIf(entry -> currentTime >= entry.getValue());
    }

    public void tickDecorate() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            decorateInventory(p);
            updateCooldowns(p);
        }
    }

    private void decorateInventory(Player p) {
        PlayerInventory inv = p.getInventory();

        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            contents[i] = decorateIfLocked(p, it);
        }
        inv.setContents(contents);

        inv.setHelmet(decorateIfLocked(p, inv.getHelmet()));
        inv.setChestplate(decorateIfLocked(p, inv.getChestplate()));
        inv.setLeggings(decorateIfLocked(p, inv.getLeggings()));
        inv.setBoots(decorateIfLocked(p, inv.getBoots()));
        inv.setItemInOffHand(decorateIfLocked(p, inv.getItemInOffHand()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        decorateInventory(p);
        updateCooldowns(p);

        if (advancementGranting instanceof de.thecoolcraft11.itemProgressions.advancement.ItemAdvancementManager manager) {
            manager.grantRootAndRules(p);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        World fromWorld = e.getFrom().getWorld();
        World toWorld = e.getTo().getWorld();

        if (fromWorld != null && toWorld != null && !fromWorld.equals(toWorld)) {
            World.Environment toEnv = toWorld.getEnvironment();
            LockEvaluator.Result r = evaluator.canEnterDimension(p, toEnv);
            if (!r.allowed()) {
                e.setCancelled(true);
                maybeNotify(p, toEnv, r.remainingSeconds());
            } else {

                if (advancementGranting instanceof de.thecoolcraft11.itemProgressions.advancement.ItemAdvancementManager manager) {
                    manager.grantDimensionIfUnlocked(p, toEnv);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;
        if (check(e.getPlayer(), item.getType())) {
            if (e.getAction() == Action.LEFT_CLICK_BLOCK && allowBreaking) return;
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (check(e.getPlayer(), item.getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (check(e.getPlayer(), item.getType())) {
            e.setCancelled(true);
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (!inHand.getType().isAir() && check(p, inHand.getType())) {
                e.setCancelled(true);
            }
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onShootBow(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p) {
            ItemStack bow = e.getBow();
            if (bow != null && check(p, bow.getType())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (result == null) return;
        for (HumanEntity viewer : e.getViewers()) {
            if (viewer instanceof Player p) {
                if (hasBypass(p, result.getType())) continue;
                if (!evaluator.canUse(p, result.getType()).allowed()) {
                    e.getInventory().setResult(new ItemStack(Material.AIR));
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCraftItem(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getRecipe().getResult();
        if (!result.getType().isAir()) {
            if (check(p, result.getType())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        ItemStack toCheck = !cursor.getType().isAir() ? cursor : current;
        if (toCheck == null || toCheck.getType().isAir()) {

            if (e.getClick() == ClickType.NUMBER_KEY) {
                int hotbar = e.getHotbarButton();
                if (hotbar >= 0) {
                    ItemStack hotItem = p.getInventory().getItem(hotbar);
                    if (hotItem != null && !hotItem.getType().isAir() && check(p, hotItem.getType())) {
                        e.setCancelled(true);
                    }
                }
            }
            return;
        }


        if (e.getSlotType() == InventoryType.SlotType.RESULT) {
            if (check(p, toCheck.getType())) {
                e.setCancelled(true);
                return;
            }
        }

        if (e.getSlotType() == InventoryType.SlotType.ARMOR || e.isShiftClick()) {
            if (check(p, toCheck.getType())) {
                e.setCancelled(true);
            }
        }

        if (e.getClick() == ClickType.NUMBER_KEY) {
            if (check(p, toCheck.getType())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cursor = e.getOldCursor();
        if (!cursor.getType().isAir() && check(p, cursor.getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffHandItem();
        if (!main.getType().isAir() && check(p, main.getType()) || !off.getType().isAir() && check(p, off.getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result == null) return;
        for (HumanEntity viewer : e.getViewers()) {
            if (viewer instanceof Player p) {
                if (hasBypass(p, result.getType())) continue;
                if (!evaluator.canUse(p, result.getType()).allowed()) {
                    e.setResult(new ItemStack(Material.AIR));
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result == null) return;
        for (HumanEntity viewer : e.getViewers()) {
            if (viewer instanceof Player p) {
                if (hasBypass(p, result.getType())) continue;
                if (!evaluator.canUse(p, result.getType()).allowed()) {
                    e.setResult(new ItemStack(Material.AIR));
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        @NotNull List<Item> items = event.getItems();
        items.forEach(item -> {
            ItemStack stack = item.getItemStack();
            System.out.println("B 1");
            if (hasBypass(player, stack.getType())) return;
            System.out.println("B 2");
            if (evaluator.isGloballyLocked(stack.getType()) || check(player, stack.getType())) {
                item.remove();
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        List<ItemStack> drops = event.getDrops();
        Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            System.out.println("B 1");
            if (stack == null || stack.getType().isAir()) continue;
            System.out.println("B 2");
            if (evaluator.isGloballyLocked(stack.getType())) {
                System.out.println("B 3");
                it.remove();
                continue;
            }
            System.out.println("B 4");
            if (killer == null) continue;
            if (hasBypass(killer, stack.getType())) continue;
            System.out.println("B 5");
            if (check(killer, stack.getType())) {
                System.out.println("B 6");
                it.remove();
            }
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (!hasBypass(player, item.getType()) && check(player, item.getType()) || !hasBypass(player, offhand.getType()) && check(player, offhand.getType())) {
                event.setCancelled(true);
            }
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (hasBypass(player, item.getType())) return;
        if (check(player, item.getType())) {
            event.setCancelled(true);
        }
    }


}
