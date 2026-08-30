package dev.vanillasmp.ability.control;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class DisarrayAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.DISARRAY;
    }

    public String mechanic() {
        return "protected inventory scrambling";
    }
}
