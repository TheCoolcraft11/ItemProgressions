package de.thecoolcraft11.itemProgressions.logic;

import de.thecoolcraft11.itemProgressions.config.LockConfig;
import de.thecoolcraft11.itemProgressions.service.TimeTrackerService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public record LockEvaluator(LockConfig config, TimeTrackerService time) {
    public record Result(boolean allowed, long remainingSeconds) {
    }

    public Result canUse(Player player, Material material) {
        long worstRemaining = 0L;
        boolean blocked = false;
        for (LockConfig.LockRule rule : config.rules) {
            if (!rule.matches(material)) continue;
            long rem = remainingForRule(player, rule.condition());
            if (rem > 0) {
                blocked = true;
                if (rem > worstRemaining) worstRemaining = rem;
            }
        }
        if (!blocked) return new Result(true, 0);
        return new Result(false, worstRemaining);
    }

    public Result canEnterDimension(Player player, World.Environment dimension) {
        long worstRemaining = 0L;
        boolean blocked = false;
        for (LockConfig.DimensionLockRule rule : config.dimensionLocks) {
            if (!rule.dimension().equals(dimension)) continue;
            long rem = remainingForRule(player, rule.condition());
            if (rem > 0) {
                blocked = true;
                if (rem > worstRemaining) worstRemaining = rem;
            }
        }
        if (!blocked) return new Result(true, 0);
        return new Result(false, worstRemaining);
    }

    private long remainingForRule(Player player, LockConfig.UnlockCondition condition) {
        return switch (condition.type()) {
            case REALTIME -> remainingRealtime(condition);
            case PER_PLAYER -> remainingPerPlayer(player, condition);
            case GLOBAL -> remainingGlobal(condition);
        };
    }

    private long remainingForRule(Player player, LockConfig.LockRule rule) {
        return switch (rule.condition().type()) {
            case REALTIME -> remainingRealtime(rule.condition());
            case PER_PLAYER -> remainingPerPlayer(player, rule.condition());
            case GLOBAL -> remainingGlobal(rule.condition());
        };
    }

    private long remainingRealtime(LockConfig.UnlockCondition cond) {
        if (cond.at() == null) return 0L;
        long now = Instant.now().getEpochSecond();
        long target = cond.at().getEpochSecond();
        return Math.max(0, target - now);
    }

    private long remainingPerPlayer(Player p, LockConfig.UnlockCondition cond) {
        long have = time.getPlayerSeconds(p.getUniqueId());
        return Math.max(0, cond.seconds() - have);
    }

    private long remainingGlobal(LockConfig.UnlockCondition cond) {
        long have = time.getGlobalSeconds();
        return Math.max(0, cond.seconds() - have);
    }

    public static String humanDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long d = seconds / 86400;
        seconds %= 86400;
        long h = seconds / 3600;
        seconds %= 3600;
        long m = seconds / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append("s");
        return sb.toString().trim();
    }

    public Set<Material> getAllLockedMaterials() {
        Set<Material> result = new HashSet<>();
        for (Material mat : Material.values()) {
            for (LockConfig.LockRule rule : config.rules) {
                if (rule.matches(mat)) {
                    result.add(mat);
                    break;
                }
            }
        }
        return result;
    }

    public boolean isGloballyLocked(Material material) {
        for (LockConfig.LockRule rule : config.rules) {
            if (!rule.matches(material)) continue;
            LockConfig.UnlockCondition cond = rule.condition();
            if ((cond.type() == LockConfig.UnlockCondition.Type.GLOBAL && remainingGlobal(cond) > 0) || (cond.type() == LockConfig.UnlockCondition.Type.REALTIME || remainingRealtime(cond) > 0)) {
                return true;
            }
        }
        return false;
    }
}
