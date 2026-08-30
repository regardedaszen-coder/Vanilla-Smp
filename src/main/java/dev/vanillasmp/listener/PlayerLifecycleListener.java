package dev.vanillasmp.listener;

import dev.vanillasmp.service.CooldownService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {
    private final CooldownService cooldowns;

    public PlayerLifecycleListener(CooldownService cooldowns) {
        this.cooldowns = cooldowns;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.removePlayer(event.getPlayer().getUniqueId());
    }
}
