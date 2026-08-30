package dev.vanillasmp.ability.combat;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class OnslaughtAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.ONSLAUGHT;
    }

    public String mechanic() {
        return "same-target sword combo";
    }
}
