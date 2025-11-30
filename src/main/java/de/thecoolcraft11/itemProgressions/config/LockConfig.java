package de.thecoolcraft11.itemProgressions.config;

import org.bukkit.Material;
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

    public record LockRule(List<Pattern> itemPatterns, UnlockCondition condition) {

        public boolean matches(Material mat) {
            String name = mat.name();
            for (Pattern p : itemPatterns) {
                if (p.matcher(name).matches()) return true;
            }
            return false;
        }
    }

    public final List<LockRule> rules = new ArrayList<>();
    public final String blockedMessage;
    public final int messageCooldownSeconds;

    public LockConfig(FileConfiguration cfg) {
        this.blockedMessage = cfg.getString("message", "&cYou can't use %item% yet. Unlocks in %remaining%.");
        this.messageCooldownSeconds = cfg.getInt("messageCooldownSeconds", 1);

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

            UnlockCondition condition = new UnlockCondition(type, seconds, at);
            rules.add(new LockRule(patterns, condition));
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
}
