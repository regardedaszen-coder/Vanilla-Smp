package dev.vanillasmp.ability;

import dev.vanillasmp.VanillaSMP;
import dev.vanillasmp.item.MythicItemService;
import dev.vanillasmp.service.CooldownService;
import dev.vanillasmp.service.TargetingService;

public record AbilityContext(
        VanillaSMP plugin,
        MythicItemService items,
        CooldownService cooldowns,
        TargetingService targeting) {}
