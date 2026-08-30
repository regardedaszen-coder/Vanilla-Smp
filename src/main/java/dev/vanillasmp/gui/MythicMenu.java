package dev.vanillasmp.gui;

import dev.vanillasmp.item.MythicItemService;
import dev.vanillasmp.model.Mythic;
import dev.vanillasmp.ritual.RitualManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** OP-only Mythic ritual guide. */
public final class MythicMenu implements Listener {
    private static final int[] SLOTS = {1, 3, 5, 7, 10, 12, 14, 16, 19, 21, 23, 25};
    private final MythicItemService items;
    private final RitualManager rituals;

    public MythicMenu(MythicItemService items, RitualManager rituals) {
        this.items = items;
        this.rituals = rituals;
    }

    public void open(Player player) {
        MenuHolder holder = new MenuHolder();
        Inventory inventory =
                Bukkit.createInventory(
                        holder,
                        27,
                        Component.text("Vanilla SMP • Mythics", NamedTextColor.DARK_PURPLE));
        holder.inventory = inventory;
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        Mythic[] mythics = Mythic.values();
        for (int i = 0; i < mythics.length; i++) {
            ItemStack icon = items.create(mythics[i]);
            ItemMeta iconMeta = icon.getItemMeta();
            List<Component> lore =
                    new ArrayList<>(iconMeta.lore() == null ? List.of() : iconMeta.lore());
            lore.add(Component.empty());
            lore.add(Component.text("Crafting Ritual", NamedTextColor.YELLOW));
            lore.add(Component.text(rituals.recipeSummary(mythics[i]), NamedTextColor.AQUA));
            lore.add(Component.text("Craft at a crafting table to begin", NamedTextColor.GREEN));
            lore.add(Component.text("Click to print ritual ingredients", NamedTextColor.DARK_GRAY));
            iconMeta.lore(lore);
            icon.setItemMeta(iconMeta);
            inventory.setItem(SLOTS[i], icon);
            holder.slots.put(SLOTS[i], mythics[i]);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder))
            return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        Mythic mythic = holder.slots.get(event.getRawSlot());
        if (mythic == null) return;
        player.sendMessage(
                Component.text(mythic.title + " ritual", NamedTextColor.GOLD)
                        .append(
                                Component.text(
                                        " — " + rituals.recipeSummary(mythic),
                                        NamedTextColor.AQUA)));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuHolder)
            event.setCancelled(true);
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Map<Integer, Mythic> slots = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
