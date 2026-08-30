package dev.vanillasmp.ability;

import dev.vanillasmp.ability.combat.*;
import dev.vanillasmp.ability.control.*;
import dev.vanillasmp.ability.spatial.*;
import dev.vanillasmp.ability.utility.*;
import dev.vanillasmp.model.Mythic;
import java.util.*;

public final class AbilityRegistry {
    private final Map<Mythic, MythicAbility> abilities = new EnumMap<>(Mythic.class);

    public AbilityRegistry() {
        register(new OnslaughtAbility());
        register(new SkyfallAbility());
        register(new ResonatorAbility());
        register(new EpochAbility());
        register(new DisarrayAbility());
        register(new AuthorityAbility());
        register(new EventHorizonAbility());
        register(new TranspositionAbility());
        register(new EffigyAbility());
        register(new StarfallAbility());
        register(new ScriptureAbility());
        register(new ParallaxAbility());
    }

    private void register(MythicAbility ability) {
        abilities.put(ability.mythic(), ability);
    }

    public MythicAbility get(Mythic mythic) {
        return abilities.get(mythic);
    }

    public Collection<MythicAbility> all() {
        return Collections.unmodifiableCollection(abilities.values());
    }
}
