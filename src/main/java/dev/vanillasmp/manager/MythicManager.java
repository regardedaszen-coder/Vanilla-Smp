package dev.vanillasmp.manager;

import dev.vanillasmp.item.MythicItemService;
import dev.vanillasmp.model.Mythic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MythicManager {
    private final MythicItemService items;

    public MythicManager(MythicItemService items) {
        this.items = items;
    }

    public ItemStack create(Mythic mythic) {
        return items.create(mythic);
    }

    public Mythic identify(ItemStack item) {
        return items.type(item);
    }

    public Mythic held(Player player) {
        Mythic main = identify(player.getInventory().getItemInMainHand());
        return main != null ? main : identify(player.getInventory().getItemInOffHand());
    }

    public ItemStack heldItem(Player player, Mythic mythic) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (identify(main) == mythic) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        return identify(off) == mythic ? off : null;
    }

    public boolean isAllowedHand(Player player, Mythic mythic) {
        boolean main = identify(player.getInventory().getItemInMainHand()) == mythic;
        boolean off = identify(player.getInventory().getItemInOffHand()) == mythic;
        return switch (mythic) {
            case AUTHORITY -> off && !main;
            case ONSLAUGHT, SKYFALL, EFFIGY, SCRIPTURE, RESONATOR -> main;
            default -> main || off;
        };
    }

    public void refreshLore(ItemStack item) {
        items.refreshLegacyLore(item);
    }

    public MythicItemService items() {
        return items;
    }
}
