package dev.vanillasmp.model;

import org.bukkit.Material;

public enum Mythic {
    ONSLAUGHT(Material.NETHERITE_SWORD, "⚔ Onslaught"),
    EPOCH(Material.CLOCK, "🕰 Epoch"),
    SKYFALL(Material.MACE, "🔨 Skyfall"),
    STARFALL(Material.BOW, "⭐ Starfall"),
    DISARRAY(Material.REPEATER, "🔁 Disarray"),
    AUTHORITY(Material.COMMAND_BLOCK, "🟧 Authority"),
    TRANSPOSITION(Material.FISHING_ROD, "🟣 Transposition"),
    EFFIGY(Material.ARMOR_STAND, "🗿 Effigy"),
    SCRIPTURE(Material.WRITABLE_BOOK, "📖 Scripture"),
    EVENT_HORIZON(Material.ENDER_EYE, "👁 Event Horizon"),
    RESONATOR(Material.COMPARATOR, "⚡ Resonator"),
    PARALLAX(Material.SHIELD, "🛡 Parallax");
    public final Material material;
    public final String title;

    Mythic(Material material, String title) {
        this.material = material;
        this.title = title;
    }

    public static Mythic parse(String value) {
        try {
            return valueOf(value.toUpperCase().replace('-', '_'));
        } catch (Exception e) {
            return null;
        }
    }
}
