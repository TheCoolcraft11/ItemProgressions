package de.thecoolcraft11.itemProgressions.config;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class LockConfig {
    public record UnlockCondition(Type type, long seconds, Instant at) {
        public enum Type {REALTIME, PER_PLAYER, GLOBAL}
    }

    public record LockRule(List<Pattern> itemPatterns, UnlockCondition condition, String displayName, String iconId,
                           String description) {
        public boolean matches(Material mat) {
            String name = mat.name();
            for (Pattern p : itemPatterns) {
                if (p.matcher(name).matches()) return true;
            }
            return false;
        }
    }

    public record DimensionLockRule(World.Environment dimension, UnlockCondition condition, String displayName,
                                    String iconId, String description) {
    }

    public final List<LockRule> rules = new ArrayList<>();
    public final List<DimensionLockRule> dimensionLocks = new ArrayList<>();
    public final String blockedMessage;
    public final int messageCooldownSeconds;
    public final boolean allowBreaking;
    public final boolean allowDropping;

    public LockConfig(FileConfiguration cfg) {
        this.blockedMessage = cfg.getString("message", "&cYou can't use %item% yet. Unlocks in %remaining%.");
        this.messageCooldownSeconds = cfg.getInt("messageCooldownSeconds", 1);
        this.allowBreaking = cfg.getBoolean("allowBreaking", false);
        this.allowDropping = cfg.getBoolean("allowDropping", false);

        List<Map<?, ?>> list = cfg.getMapList("locks");
        for (Map<?, ?> raw : list) {
            Object itemsObj = raw.get("items");
            if (!(itemsObj instanceof List<?> items)) continue;

            List<Pattern> patterns = new ArrayList<>();
            for (Object i : items) {
                if (i == null) continue;
                String s = String.valueOf(i).trim().toUpperCase(Locale.ROOT);
                String regex = s.replace("*", ".*");
                patterns.add(Pattern.compile(regex));
            }

            Object unlockObj = raw.get("unlock");
            if (!(unlockObj instanceof Map<?, ?> unlock)) continue;
            String typeStr = String.valueOf(unlock.get("type")).toLowerCase(Locale.ROOT);
            UnlockCondition.Type type;
            long seconds = 0L;
            Instant at = null;
            switch (typeStr) {
                case "realtime" -> {
                    type = UnlockCondition.Type.REALTIME;
                    Object atObj = unlock.get("at");
                    if (atObj != null) {
                        try {
                            at = Instant.parse(String.valueOf(atObj));
                        } catch (DateTimeParseException ignored) {
                        }
                    }
                }
                case "per-player", "perplayer", "player" -> {
                    type = UnlockCondition.Type.PER_PLAYER;
                    seconds = asLong(unlock.get("seconds"), 0L);
                }
                case "global", "server" -> {
                    type = UnlockCondition.Type.GLOBAL;
                    seconds = asLong(unlock.get("seconds"), 0L);
                }
                default -> {
                    continue;
                }
            }

            String iconId = strOrNull(unlock.get("icon"));
            String displayName = strOrNull(unlock.get("name"));
            String description = strOrNull(unlock.get("description"));

            UnlockCondition condition = new UnlockCondition(type, seconds, at);
            rules.add(new LockRule(patterns, condition, displayName, iconId, description));
        }


        List<Map<?, ?>> dimList = cfg.getMapList("dimensionLocks");
        for (Map<?, ?> raw : dimList) {
            Object dimObj = raw.get("dimension");
            if (dimObj == null) continue;

            World.Environment env;
            try {
                env = World.Environment.valueOf(String.valueOf(dimObj).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Object unlockObj = raw.get("unlock");
            if (!(unlockObj instanceof Map<?, ?> unlock)) continue;
            String typeStr = String.valueOf(unlock.get("type")).toLowerCase(Locale.ROOT);
            UnlockCondition.Type type;
            long seconds = 0L;
            Instant at = null;
            switch (typeStr) {
                case "realtime" -> {
                    type = UnlockCondition.Type.REALTIME;
                    Object atObj = unlock.get("at");
                    if (atObj != null) {
                        try {
                            at = Instant.parse(String.valueOf(atObj));
                        } catch (DateTimeParseException ignored) {
                        }
                    }
                }
                case "per-player", "perplayer", "player" -> {
                    type = UnlockCondition.Type.PER_PLAYER;
                    seconds = asLong(unlock.get("seconds"), 0L);
                }
                case "global", "server" -> {
                    type = UnlockCondition.Type.GLOBAL;
                    seconds = asLong(unlock.get("seconds"), 0L);
                }
                default -> {
                    continue;
                }
            }

            String iconId = strOrNull(unlock.get("icon"));
            String displayName = strOrNull(unlock.get("name"));
            String description = strOrNull(unlock.get("description"));

            UnlockCondition condition = new UnlockCondition(type, seconds, at);
            dimensionLocks.add(new DimensionLockRule(env, condition, displayName, iconId, description));
        }
    }

    private static long asLong(Object o, long def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String strOrNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}

