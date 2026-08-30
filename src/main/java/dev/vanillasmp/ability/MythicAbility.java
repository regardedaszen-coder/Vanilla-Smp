package dev.vanillasmp.ability;

import dev.vanillasmp.model.Mythic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Stable metadata contract for each independently maintained Mythic mechanic. */
public interface MythicAbility {
    Mythic mythic();

    String mechanic();

    default void enable() {}

    default void disable() {}

    default String hud(Player player, ItemStack item) {
        return "✓ READY";
    }
}
