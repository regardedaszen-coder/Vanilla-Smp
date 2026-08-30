package dev.vanillasmp.ability.utility;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class StarfallAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.STARFALL;
    }

    public String mechanic() {
        return "arrow-drawn constellation boundaries";
    }
}
