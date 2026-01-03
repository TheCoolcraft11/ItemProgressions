package de.thecoolcraft11.itemProgressions.service;

import de.thecoolcraft11.itemProgressions.ItemProgressions;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TimeTrackerService {
    private final File file;
    private final Map<UUID, Long> playerSeconds = new HashMap<>();
    private long globalSeconds = 0L;

    private long lastTickNanos = System.nanoTime();

    public TimeTrackerService(File dataFolder) {
        this.file = new File(dataFolder, "time-data.yml");
        load();
    }

    public synchronized void tick() {
        long currentNanos = System.nanoTime();
        long deltaNanos = Math.max(0L, currentNanos - lastTickNanos);
        lastTickNanos = currentNanos;

        
        long add = deltaNanos / 1_000_000_000L;
        if (add <= 0) return;

        globalSeconds += add;

        Server server = Bukkit.getServer();
        server.getOnlinePlayers().forEach(p -> playerSeconds.merge(p.getUniqueId(), add, Long::sum));
    }

    public synchronized long getGlobalSeconds() {
        return globalSeconds;
    }

    public synchronized long getPlayerSeconds(UUID uuid) {
        return playerSeconds.getOrDefault(uuid, 0L);
    }

    public synchronized void save() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("globalSeconds", globalSeconds);
        for (Map.Entry<UUID, Long> e : playerSeconds.entrySet()) {
            cfg.set("players." + e.getKey(), e.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            ItemProgressions.getPlugin(ItemProgressions.class).getLogger().warning("Could not save time data!: " + e.getMessage());
        }
    }

    public final void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.globalSeconds = cfg.getLong("globalSeconds", 0L);
        if (cfg.isConfigurationSection("players")) {
            for (String key : Objects.requireNonNull(cfg.getConfigurationSection("players")).getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    long secs = (long) cfg.getDouble("players." + key, 0D);
                    playerSeconds.put(id, secs);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
