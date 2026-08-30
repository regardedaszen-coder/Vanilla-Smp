package dev.vanillasmp.ability.spatial;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class EffigyAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.EFFIGY;
    }

    public String mechanic() {
        return "temporary nonlethal remote damage proxy";
    }
}
