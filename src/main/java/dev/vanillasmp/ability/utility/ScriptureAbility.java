package dev.vanillasmp.ability.utility;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class ScriptureAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.SCRIPTURE;
    }

    public String mechanic() {
        return "survival-resource inscriptions";
    }
}
