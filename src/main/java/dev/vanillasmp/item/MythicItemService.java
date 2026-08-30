package dev.vanillasmp.item;

import dev.vanillasmp.VanillaSMP;
import dev.vanillasmp.model.Mythic;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MythicItemService {
    private final NamespacedKey type, id, loreVersion;

    public MythicItemService(VanillaSMP plugin) {
        type = new NamespacedKey(plugin, "mythic");
        id = new NamespacedKey(plugin, "mythic_id");
        loreVersion = new NamespacedKey(plugin, "lore_version");
    }

    public ItemStack create(Mythic mythic) {
        ItemStack item = new ItemStack(mythic.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(mythic.title, NamedTextColor.GOLD));
        meta.getPersistentDataContainer().set(type, PersistentDataType.STRING, mythic.name());
        meta.getPersistentDataContainer()
                .set(id, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        status(item, defaultStatus(mythic));
        return item;
    }

    public Mythic type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String s =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(type, PersistentDataType.STRING);
        return s == null ? null : Mythic.parse(s);
    }

    public boolean is(ItemStack item, Mythic mythic) {
        return type(item) == mythic;
    }

    public String get(ItemStack item, String key) {
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(new NamespacedKey(type.getNamespace(), key), PersistentDataType.STRING);
    }

    public void set(ItemStack item, String key, String value) {
        ItemMeta m = item.getItemMeta();
        m.getPersistentDataContainer()
                .set(new NamespacedKey(type.getNamespace(), key), PersistentDataType.STRING, value);
        item.setItemMeta(m);
    }

    public int getInt(ItemStack item, String key, int fallback) {
        try {
            return Integer.parseInt(Objects.requireNonNullElse(get(item, key), ""));
        } catch (Exception e) {
            return fallback;
        }
    }

    public void setInt(ItemStack item, String key, int value) {
        set(item, key, Integer.toString(value));
    }

    public void status(ItemStack item, String... lines) {
        Mythic mythic = type(item);
        if (mythic == null) return;
        ItemMeta m = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        for (String line : lines) lore.add(Component.text(line, NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Component.text(description(mythic), NamedTextColor.GRAY));
        lore.add(Component.text(controls(mythic), NamedTextColor.YELLOW));
        lore.add(Component.text("Hand: " + handRule(mythic), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("✦ Genuine Mythic • Vanilla SMP", NamedTextColor.DARK_PURPLE));
        m.lore(lore);
        m.getPersistentDataContainer().set(loreVersion, PersistentDataType.INTEGER, 8);
        item.setItemMeta(m);
    }

    public void refreshLegacyLore(ItemStack item) {
        Mythic mythic = type(item);
        if (mythic == null) return;
        if (item.getType() != mythic.material) item.setType(mythic.material);
        Integer version =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(loreVersion, PersistentDataType.INTEGER);
        if (version == null || version < 8) status(item, defaultStatus(mythic));
    }

    private String defaultStatus(Mythic m) {
        return switch (m) {
            case ONSLAUGHT -> "Mode: BLOOD DASH • Combo: ×0";
            case SKYFALL -> "Impact: ◇◇◇";
            case STARFALL -> "Mode: TRIAD • Shots: 0 / 3";
            case TRANSPOSITION -> "Direct Swap • Range: 10 blocks";
            case RESONATOR -> "Beam: READY • Slam: READY";
            default -> "✓ READY";
        };
    }

    private String description(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT -> "Switch between a blood-red dash and a targeted storm of slashes.";
            case EPOCH ->
                    "Freeze nearby players, mobs, and projectiles; damage resolves afterward.";
            case SKYFALL -> "Earn Impact from real mace smashes and chain aerial attacks.";
            case STARFALL ->
                    "Fill projected stars with distinct shots to create a true-damage constellation field.";
            case DISARRAY -> "Randomize a targeted player's movable inventory slots.";
            case AUTHORITY -> "Deny, permit, override, or arm a true-damage lightning judgement.";
            case TRANSPOSITION -> "Aim at a nearby player and instantly exchange positions.";
            case EFFIGY -> "Deploy a nonlethal remote-damage proxy bound to another player.";
            case SCRIPTURE ->
                    "Sacrifice health, hunger, or levels to invoke survival inscriptions.";
            case EVENT_HORIZON -> "Reject incoming threats or refract nearby player positions.";
            case RESONATOR ->
                    "Charge an aimable redstone beam or ascend into a targeted redstone slam.";
            case PARALLAX ->
                    "Raise the shield to block attacks from every direction or bash a nearby player.";
        };
    }

    private String controls(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT -> "Sneak + Right Click: Cycle mode • Right Click: Activate";
            case EPOCH -> "Right Click: Time Stop";
            case SKYFALL -> "Right Click: Rebound • Sneak + Right Click: Hangtime";
            case STARFALL -> "Sneak + Right Click: Cycle shape • Shoot terrain to draw its outline";
            case DISARRAY -> "Aim + Right Click: Scramble";
            case AUTHORITY -> "Sneak + Right Click: Cycle • Judgement: Arm, then hit";
            case TRANSPOSITION -> "Aim + Right Click: Swap positions within 10 blocks";
            case EFFIGY -> "Aim + Right Click: Bind • Right Click Block: Deploy";
            case SCRIPTURE -> "Right Click: Read • /mythic cast <inscription>";
            case EVENT_HORIZON -> "Right Click: Zero • Sneak + Right Click: Refraction";
            case RESONATOR -> "Right Click: Charge Beam • Sneak + Right Click: Redstone Slam";
            case PARALLAX -> "Hold Right Click: 360° Guard • Sneak + Right Click: Bash Dash";
        };
    }

    private String handRule(Mythic mythic) {
        return switch (mythic) {
            case AUTHORITY -> "Offhand only";
            case ONSLAUGHT, SKYFALL, EFFIGY, SCRIPTURE, RESONATOR -> "Main hand";
            default -> "Main hand or offhand";
        };
    }
}
