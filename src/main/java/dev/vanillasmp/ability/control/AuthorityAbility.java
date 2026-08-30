package dev.vanillasmp.ability.control;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class AuthorityAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.AUTHORITY;
    }

    public String mechanic() {
        return "deny, permit, and override rules";
    }
}
