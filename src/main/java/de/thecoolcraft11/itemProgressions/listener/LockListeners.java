package de.thecoolcraft11.itemProgressions.listener;

import de.thecoolcraft11.itemProgressions.logic.LockEvaluator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LockListeners implements Listener {
    private final LockEvaluator evaluator;
    private final String messageTemplate;
    private final int messageCooldownSeconds;
    private final Map<UUID, Long> lastMsg = new HashMap<>();
    // Track last cooldown ticks per player/material to avoid upward jumps near unlock
    private final Map<UUID, Map<Material, Integer>> lastCooldowns = new HashMap<>();

    public LockListeners(LockEvaluator evaluator, String messageTemplate, int messageCooldownSeconds) {
        this.evaluator = evaluator;
        this.messageTemplate = messageTemplate;
        this.messageCooldownSeconds = messageCooldownSeconds;
    }

    private boolean check(Player p, Material mat) {
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
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    // Visual decoration helpers
    private ItemStack decorateIfLocked(Player p, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        LockEvaluator.Result r = evaluator.canUse(p, stack.getType());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (!r.allowed()) {
            // Add glow and lore using UNBREAKING as a harmless hidden enchant
            if (!meta.hasEnchant(Enchantment.UNBREAKING)) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            String loreLine = ChatColor.translateAlternateColorCodes('&', "&cLocked: unlocks in " + LockEvaluator.humanDuration(r.remainingSeconds()));
            java.util.List<String> lore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
            if (lore == null) lore = new java.util.ArrayList<>();
            if (lore.isEmpty() || !ChatColor.stripColor(lore.get(0)).toLowerCase().startsWith("locked:")) {
                lore.add(0, loreLine);
            } else {
                lore.set(0, loreLine);
            }
            meta.setLore(lore);
            stack.setItemMeta(meta);
        } else {
            // Remove decoration if present
            if (meta.hasEnchant(Enchantment.UNBREAKING)) {
                meta.removeEnchant(Enchantment.UNBREAKING);
            }
            if (meta.hasLore()) {
                java.util.List<String> lore = meta.getLore();
                if (lore != null && !lore.isEmpty() && ChatColor.stripColor(lore.get(0)).toLowerCase().startsWith("locked:")) {
                    lore.remove(0);
                    meta.setLore(lore);
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // Update the vanilla client cooldown overlay for locked materials
    private void updateCooldowns(Player p) {
        java.util.Set<Material> mats = new java.util.HashSet<>();
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getContents()) {
            if (s != null && !s.getType().isAir()) mats.add(s.getType());
        }
        ItemStack[] armor = new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots(), inv.getItemInOffHand()};
        for (ItemStack s : armor) {
            if (s != null && !s.getType().isAir()) mats.add(s.getType());
        }
        Map<Material, Integer> prev = lastCooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        for (Material mat : mats) {
            LockEvaluator.Result r = evaluator.canUse(p, mat);
            if (!r.allowed()) {
                long seconds = Math.max(0L, r.remainingSeconds());
                int ticks = (int) Math.min(Integer.MAX_VALUE, seconds * 20L);
                // Ensure at least 1 tick so overlay shows when near unlock
                if (ticks == 0) ticks = 1;
                int last = prev.getOrDefault(mat, 0);
                // Ensure cooldown is monotonic non-increasing: never jump upward
                if (last > 0 && ticks > last) {
                    ticks = Math.max(1, last - 1);
                }
                p.setCooldown(mat, ticks);
                prev.put(mat, ticks);
            } else {
                p.setCooldown(mat, 0);
                prev.remove(mat);
            }
        }
        // Cleanup entries for materials no longer present
        prev.keySet().removeIf(m -> !mats.contains(m));
    }

    public void tickDecorate() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            decorateInventory(p);
            updateCooldowns(p);
        }
    }

    private void decorateInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        // Main contents
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            contents[i] = decorateIfLocked(p, it);
        }
        inv.setContents(contents);
        // Armor and offhand
        inv.setHelmet(decorateIfLocked(p, inv.getHelmet()));
        inv.setChestplate(decorateIfLocked(p, inv.getChestplate()));
        inv.setLeggings(decorateIfLocked(p, inv.getLeggings()));
        inv.setBoots(decorateIfLocked(p, inv.getBoots()));
        inv.setItemInOffHand(decorateIfLocked(p, inv.getItemInOffHand()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        decorateInventory(e.getPlayer());
        updateCooldowns(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;
        if (check(e.getPlayer(), item.getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;
        if (check(e.getPlayer(), item.getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item == null) return;
        if (check(e.getPlayer(), item.getType())) {
            e.setCancelled(true);
        }
    }

    // Prevent attacking with locked items
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (inHand != null && !inHand.getType().isAir() && check(p, inHand.getType())) {
                e.setCancelled(true);
            }
        }
    }

    // Prevent bow shooting
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
                if (!evaluator.canUse(p, result.getType()).allowed()) {
                    e.getInventory().setResult(new ItemStack(Material.AIR));
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        ItemStack toCheck = cursor != null && !cursor.getType().isAir() ? cursor : current;
        if (toCheck == null || toCheck.getType().isAir()) {
            // also check hotbar swap and number key
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

        // Result slot (crafting etc.)
        if (e.getSlotType() == InventoryType.SlotType.RESULT) {
            if (check(p, toCheck.getType())) {
                e.setCancelled(true);
                return;
            }
        }
        // Armor slot or moving armor via shift-click
        if (e.getSlotType() == InventoryType.SlotType.ARMOR || e.isShiftClick()) {
            if (check(p, toCheck.getType())) {
                e.setCancelled(true);
            }
        }
        // Using number keys to equip
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
        if (cursor != null && !cursor.getType().isAir() && check(p, cursor.getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack main = e.getMainHandItem();
        ItemStack off = e.getOffHandItem();
        if ((main != null && !main.getType().isAir() && check(p, main.getType())) ||
            (off != null && !off.getType().isAir() && check(p, off.getType()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result == null) return;
        for (HumanEntity viewer : e.getViewers()) {
            if (viewer instanceof Player p) {
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
                if (!evaluator.canUse(p, result.getType()).allowed()) {
                    e.setResult(new ItemStack(Material.AIR));
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCraftItem(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (!evaluator.canUse(p, result.getType()).allowed()) {
            e.setCancelled(true);
            // Also blank out the result to avoid ghost items in some clients
            if (e.getInventory() != null) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
            }
            maybeNotify(p, result.getType(), evaluator.canUse(p, result.getType()).remainingSeconds());
        }
    }
}
