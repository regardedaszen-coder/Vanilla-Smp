package dev.vanillasmp.listener;

import dev.vanillasmp.manager.MythicManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MythicInventoryListener implements Listener {
    private final MythicManager mythics;

    public MythicInventoryListener(MythicManager mythics) {
        this.mythics = mythics;
    }

    @EventHandler
    public void refreshMovedMythic(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && mythics.identify(event.getCurrentItem()) != null) {
            mythics.refreshLore(event.getCurrentItem());
        }
    }

    @EventHandler
    public void refreshDraggedMythic(InventoryDragEvent event) {
        if (mythics.identify(event.getOldCursor()) != null)
            mythics.refreshLore(event.getOldCursor());
    }
}
