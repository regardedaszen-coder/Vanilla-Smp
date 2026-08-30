package dev.vanillasmp.ability.spatial;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class TranspositionAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.TRANSPOSITION;
    }

    public String mechanic() {
        return "exact two-position exchange";
    }
}
