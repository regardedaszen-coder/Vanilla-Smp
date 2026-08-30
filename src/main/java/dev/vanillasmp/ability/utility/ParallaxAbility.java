package dev.vanillasmp.ability.utility;

import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;

public final class ParallaxAbility implements MythicAbility {
    public Mythic mythic() {
        return Mythic.PARALLAX;
    }

    public String mechanic() {
        return "360-degree shield guard and close-range shield bash";
    }
}
