package dev.vanillasmp.ability.control;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class EpochAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.EPOCH;
    }

    public String mechanic() {
        return "local time stop and queued damage";
    }
}
