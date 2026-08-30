package dev.vanillasmp.ability.combat;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class ResonatorAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.RESONATOR;
    }

    public String mechanic() {
        return "impact converted to kinetic force";
    }
}
