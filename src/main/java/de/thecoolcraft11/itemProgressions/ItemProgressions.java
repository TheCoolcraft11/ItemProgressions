package de.thecoolcraft11.itemProgressions;

import de.thecoolcraft11.itemProgressions.config.LockConfig;
import de.thecoolcraft11.itemProgressions.listener.LockListeners;
import de.thecoolcraft11.itemProgressions.logic.LockEvaluator;
import de.thecoolcraft11.itemProgressions.service.TimeTrackerService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ItemProgressions extends JavaPlugin {
    private TimeTrackerService timeService;
    private LockConfig lockConfig;
    private BukkitTask ticker;
    private LockListeners listeners;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.lockConfig = new LockConfig(getConfig());
        this.timeService = new TimeTrackerService(getDataFolder());

        LockEvaluator evaluator = new LockEvaluator(lockConfig, timeService);
        this.listeners = new LockListeners(evaluator, lockConfig.blockedMessage, lockConfig.messageCooldownSeconds);
        Bukkit.getPluginManager().registerEvents(listeners, this);

        // Tick time service and periodically decorate inventories
        this.ticker = Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                timeService.tick();
                listeners.tickDecorate();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (timeService != null) timeService.save();
    }
}
