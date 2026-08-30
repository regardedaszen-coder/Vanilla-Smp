package dev.vanillasmp.gui;

import dev.vanillasmp.item.MythicItemService;
import dev.vanillasmp.model.Mythic;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GatewayMenu implements Listener {
    private final MythicItemService items;
    private final Map<World.Environment, Integer> costs = new EnumMap<>(World.Environment.class);

    public GatewayMenu(MythicItemService items) {
        this.items = items;
        costs.put(World.Environment.NORMAL, 10);
        costs.put(World.Environment.NETHER, 20);
        costs.put(World.Environment.THE_END, 30);
    }

    public void open(Player player) {
        GatewayHolder holder = new GatewayHolder();
        Inventory menu =
                Bukkit.createInventory(
                        holder,
                        27,
                        Component.text("Scripture • Gateway", NamedTextColor.DARK_PURPLE));
        holder.inventory = menu;
        holder.inventory = menu;
        add(menu, 11, Material.GRASS_BLOCK, "[ OVERWORLD ]", World.Environment.NORMAL);
        add(menu, 13, Material.NETHERRACK, "[ NETHER ]", World.Environment.NETHER);
        add(menu, 15, Material.END_STONE, "[ THE END ]", World.Environment.THE_END);
        player.openInventory(menu);
    }

    private void add(
            Inventory menu,
            int slot,
            Material material,
            String name,
            World.Environment environment) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        int cost = costs.get(environment);
        meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE));
        meta.lore(
                List.of(
                        Component.text(
                                "Cost: " + cost + " experience levels", NamedTextColor.YELLOW),
                        Component.text("Click to cross the Gateway", NamedTextColor.GRAY)));
        icon.setItemMeta(meta);
        menu.setItem(slot, icon);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof GatewayHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        World.Environment environment =
                switch (event.getRawSlot()) {
                    case 11 -> World.Environment.NORMAL;
                    case 13 -> World.Environment.NETHER;
                    case 15 -> World.Environment.THE_END;
                    default -> null;
                };
        if (environment == null) return;
        if (!items.is(player.getInventory().getItemInMainHand(), Mythic.SCRIPTURE)) {
            player.closeInventory();
            player.sendMessage(
                    Component.text("You must hold Scripture to cross.", NamedTextColor.RED));
            return;
        }
        int cost = costs.get(environment);
        if (player.getLevel() < cost) {
            player.sendMessage(
                    Component.text(
                            "The Gateway requires " + cost + " levels.", NamedTextColor.RED));
            return;
        }
        World destination =
                Bukkit.getWorlds().stream()
                        .filter(world -> world.getEnvironment() == environment)
                        .findFirst()
                        .orElse(null);
        if (destination == null) {
            player.sendMessage(
                    Component.text("That dimension is unavailable.", NamedTextColor.RED));
            return;
        }
        player.setLevel(player.getLevel() - cost);
        Location spawn = destination.getSpawnLocation().clone().add(.5, 1, .5);
        player.closeInventory();
        player.teleportAsync(spawn);
        player.sendMessage(
                Component.text(
                        "The Gateway carries you to " + destination.getName() + ".",
                        NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof GatewayHolder)
            event.setCancelled(true);
    }

    private static final class GatewayHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
