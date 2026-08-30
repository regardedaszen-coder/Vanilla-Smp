package dev.vanillasmp.ability.spatial;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class EventHorizonAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.EVENT_HORIZON;
    }

    public String mechanic() {
        return "local spatial rejection and refraction";
    }
}
