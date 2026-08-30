package dev.vanillasmp.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class CooldownService {
    private final Map<UUID, Map<String, Long>> expiryByPlayer = new HashMap<>();

    public boolean start(Player player, String ability, long durationMillis) {
        if (remaining(player, ability) > 0) return false;
        expiryByPlayer
                .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + durationMillis);
        return true;
    }

    public long remaining(Player player, String ability) {
        Map<String, Long> cooldowns = expiryByPlayer.get(player.getUniqueId());
        if (cooldowns == null) return 0;
        return Math.max(0, cooldowns.getOrDefault(ability, 0L) - System.currentTimeMillis());
    }

    public String display(Player player, String ability) {
        long remaining = remaining(player, ability);
        if (remaining == 0) return "✓ READY";
        long seconds = (remaining + 999) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public void clear(Player player, String ability) {
        Map<String, Long> cooldowns = expiryByPlayer.get(player.getUniqueId());
        if (cooldowns != null) cooldowns.remove(ability);
    }

    public void removePlayer(UUID playerId) {
        expiryByPlayer.remove(playerId);
    }
}
