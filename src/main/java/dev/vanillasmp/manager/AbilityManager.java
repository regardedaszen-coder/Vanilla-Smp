package dev.vanillasmp.manager;

import dev.vanillasmp.ability.AbilityRegistry;
import dev.vanillasmp.ability.MythicAbility;
import dev.vanillasmp.model.Mythic;
import java.util.Collection;

public final class AbilityManager {
    private final AbilityRegistry registry;

    public AbilityManager(AbilityRegistry registry) {
        this.registry = registry;
    }

    public void enable() {
        registry.all().forEach(MythicAbility::enable);
    }

    public void disable() {
        registry.all().forEach(MythicAbility::disable);
    }

    public MythicAbility ability(Mythic mythic) {
        return registry.get(mythic);
    }

    public Collection<MythicAbility> abilities() {
        return registry.all();
    }
}
