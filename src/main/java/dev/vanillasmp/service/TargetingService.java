package dev.vanillasmp.service;

import org.bukkit.entity.Player;

public final class TargetingService {
    public Player playerInSight(Player source, double range) {
        var result =
                source.getWorld()
                        .rayTraceEntities(
                                source.getEyeLocation(),
                                source.getEyeLocation().getDirection(),
                                range,
                                0.6,
                                entity -> entity instanceof Player && entity != source);
        return result != null && result.getHitEntity() instanceof Player target ? target : null;
    }
}
