package dev.vanillasmp.ability.combat;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class SkyfallAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.SKYFALL;
    }

    public String mechanic() {
        return "mace impact chaining";
    }
}
