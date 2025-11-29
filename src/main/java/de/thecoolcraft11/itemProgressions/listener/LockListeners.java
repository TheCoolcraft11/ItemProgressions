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

    
    private ItemStack decorateIfLocked(Player p, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        LockEvaluator.Result r = evaluator.canUse(p, stack.getType());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (!r.allowed()) {
            
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
        
        for (Material mat : mats) {
            LockEvaluator.Result r = evaluator.canUse(p, mat);
            if (!r.allowed()) {
                long seconds = Math.max(0L, r.remainingSeconds());
                int ticks = (int) Math.min(Integer.MAX_VALUE, seconds * 20L);
                
                if (ticks == 0) ticks = 1;
                p.setCooldown(mat, ticks);
            } else {
                
                p.setCooldown(mat, 0);
            }
        }
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

    
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (inHand != null && !inHand.getType().isAir() && check(p, inHand.getType())) {
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
}
