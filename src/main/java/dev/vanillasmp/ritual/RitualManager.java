package dev.vanillasmp.ritual;

import dev.vanillasmp.VanillaSMP;
import dev.vanillasmp.item.MythicItemService;
import dev.vanillasmp.model.Mythic;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

public final class RitualManager implements Listener {
    private static final int RITUAL_TICKS = 20 * 150;
    private static final int[] STAR_ORDER = {0, 2, 4, 1, 3, 0};
    private final VanillaSMP plugin;
    private final MythicItemService items;
    private final Map<Mythic, RecipeMaterials> recipes = new EnumMap<>(Mythic.class);
    private final Map<Mythic, NamespacedKey> recipeKeys = new EnumMap<>(Mythic.class);
    private final Set<Mythic> craftedMythics = java.util.EnumSet.noneOf(Mythic.class);
    private final Set<String> activeTables = new HashSet<>();
    private final Set<java.util.UUID> activeWorlds = new HashSet<>();
    private final Set<ItemDisplay> activeDisplays = new HashSet<>();
    private final Map<String, BossBar> activeBossBars = new java.util.HashMap<>();
    private final Map<java.util.UUID, PendingRelic> pendingRelics = new java.util.HashMap<>();
    private final Map<String, RitualSky> activeSkies = new java.util.HashMap<>();
    private final Set<java.util.UUID> themedSkyPlayers = new HashSet<>();

    public RitualManager(VanillaSMP plugin, MythicItemService items) {
        this.plugin = plugin;
        this.items = items;
        for (String value : plugin.getConfig().getStringList("crafted-mythics")) {
            Mythic mythic = Mythic.parse(value);
            if (mythic != null) craftedMythics.add(mythic);
        }
        defineRecipes();
    }

    public void registerRecipes() {
        for (Map.Entry<Mythic, RecipeMaterials> entry : recipes.entrySet()) {
            Mythic mythic = entry.getKey();
            RecipeMaterials materials = entry.getValue();
            NamespacedKey key = new NamespacedKey(plugin, "ritual_" + mythic.name().toLowerCase());
            recipeKeys.put(mythic, key);
            Bukkit.removeRecipe(key);
            if (craftedMythics.contains(mythic)) continue;
            ShapedRecipe recipe = new ShapedRecipe(key, items.create(mythic));
            recipe.shape("ABA", "CDC", "AEA");
            recipe.setIngredient('A', materials.corner);
            recipe.setIngredient('B', materials.north);
            recipe.setIngredient('C', materials.sides);
            recipe.setIngredient('D', materials.core);
            recipe.setIngredient('E', materials.south);
            Bukkit.addRecipe(recipe);
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) resetSky(player);
        for (ItemDisplay display : Set.copyOf(activeDisplays)) display.remove();
        for (BossBar bossBar : activeBossBars.values()) bossBar.removeAll();
        for (PendingRelic pending : pendingRelics.values()) {
            pending.display().remove();
            pending.interaction().remove();
        }
        activeDisplays.clear();
        activeTables.clear();
        activeWorlds.clear();
        activeBossBars.clear();
        pendingRelics.clear();
        activeSkies.clear();
        themedSkyPlayers.clear();
    }

    public String recipeSummary(Mythic mythic) {
        RecipeMaterials recipe = recipes.get(mythic);
        return pretty(recipe.corner)
                + " • "
                + pretty(recipe.north)
                + " • "
                + pretty(recipe.sides)
                + " • "
                + pretty(recipe.core)
                + " • "
                + pretty(recipe.south);
    }

    public boolean resetCraftedRecipes(Mythic mythic) {
        if (!activeTables.isEmpty()) return false;
        if (mythic == null) craftedMythics.clear();
        else craftedMythics.remove(mythic);
        plugin.getConfig()
                .set("crafted-mythics", craftedMythics.stream().map(Enum::name).sorted().toList());
        plugin.saveConfig();
        registerRecipes();
        return true;
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        Mythic mythic = items.type(event.getRecipe().getResult());
        if (mythic == null || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (craftedMythics.contains(mythic)) {
            player.sendMessage(
                    Component.text(
                            mythic.title + " has already been forged in this world.",
                            NamedTextColor.RED));
            return;
        }
        Location table = event.getInventory().getLocation();
        if (table == null) table = player.getLocation();
        table = table.getBlock().getLocation().add(.5, 1.05, .5);
        java.util.UUID worldId = table.getWorld().getUID();
        if (activeWorlds.contains(worldId)) {
            player.sendMessage(
                    Component.text(
                            "Only one Mythic ritual can be active in this world at a time.",
                            NamedTextColor.RED));
            return;
        }
        String tableId =
                table.getWorld().getUID()
                        + ":"
                        + table.getBlockX()
                        + ":"
                        + table.getBlockY()
                        + ":"
                        + table.getBlockZ();
        if (!activeTables.add(tableId)) {
            player.sendMessage(
                    Component.text(
                            "That table is already conducting a ritual.", NamedTextColor.RED));
            return;
        }
        activeWorlds.add(worldId);
        reserveMythic(mythic);
        consumeOneRecipe(event.getInventory());
        player.closeInventory();
        beginRitual(player, table, tableId, mythic);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (BossBar bossBar : activeBossBars.values()) bossBar.addPlayer(event.getPlayer());
        updateSkyViewers();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        themedSkyPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRelicClaim(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        PendingRelic pending = pendingRelics.remove(interaction.getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        interaction.remove();
        Location reward = pending.display().getLocation();
        pending.display().remove();
        activeDisplays.remove(pending.display());
        ItemStack item = items.create(pending.mythic());
        for (ItemStack overflow : event.getPlayer().getInventory().addItem(item).values())
            reward.getWorld().dropItemNaturally(reward, overflow);
        reward.getWorld().spawnParticle(Particle.FLASH, reward, 2);
        reward.getWorld().spawnParticle(Particle.END_ROD, reward, 60, .8, .8, .8, .08);
        reward.getWorld().playSound(reward, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1f);
        Bukkit.broadcast(
                Component.text(event.getPlayer().getName(), NamedTextColor.YELLOW)
                        .append(Component.text(" has received ", NamedTextColor.GRAY))
                        .append(Component.text(pending.mythic().title + "!", NamedTextColor.GOLD)));
    }

    private void consumeOneRecipe(CraftingInventory inventory) {
        ItemStack[] matrix = inventory.getMatrix();
        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack stack = matrix[slot];
            if (stack == null || stack.getType().isAir()) continue;
            if (stack.getAmount() == 1) matrix[slot] = null;
            else stack.setAmount(stack.getAmount() - 1);
        }
        inventory.setMatrix(matrix);
    }

    private void beginRitual(Player creator, Location center, String tableId, Mythic mythic) {
        Location ground = center.clone().subtract(0, 1.0, 0);
        List<ItemDisplay> relics = new ArrayList<>();
        for (int point = 0; point < 5; point++) {
            double angle = -Math.PI / 2 + point * Math.PI * 2 / 5;
            Location start = ground.clone().add(Math.cos(angle) * 2.7, .75, Math.sin(angle) * 2.7);
            ItemDisplay relic =
                    center.getWorld()
                            .spawn(
                                    start,
                                    ItemDisplay.class,
                                    display -> {
                                        display.setItemStack(items.create(mythic));
                                        display.setGlowing(true);
                                        display.setGlowColorOverride(omenColor(mythic));
                                        display.setViewRange(48f);
                                        display.setPersistent(false);
                                    });
            relics.add(relic);
            activeDisplays.add(relic);
        }
        int ritualX = center.getBlockX();
        int ritualY = center.getBlockY() - 1;
        int ritualZ = center.getBlockZ();
        String coordinates =
                center.getWorld().getName() + " • X " + ritualX + " Y " + ritualY + " Z " + ritualZ;
        BossBar bossBar =
                Bukkit.createBossBar(
                        mythic.title + " Ritual • 2:30 • " + coordinates,
                        BarColor.PURPLE,
                        BarStyle.SEGMENTED_20);
        bossBar.setProgress(0);
        for (Player online : Bukkit.getOnlinePlayers()) bossBar.addPlayer(online);
        activeBossBars.put(tableId, bossBar);
        activeSkies.put(tableId, new RitualSky(ground.clone(), mythic));
        updateSkyViewers();
        Bukkit.broadcast(
                Component.text("MYTHIC RITUAL: ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text(mythic.title, NamedTextColor.GOLD))
                        .append(
                                Component.text(
                                        " is being crafted by "
                                                + creator.getName()
                                                + " at "
                                                + coordinates,
                                        NamedTextColor.YELLOW)));
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                try {
                    tickRitual();
                } catch (RuntimeException exception) {
                    plugin.getLogger()
                            .log(
                                    java.util.logging.Level.WARNING,
                                    "A cosmetic effect failed during the "
                                            + mythic.name()
                                            + " ritual; the ritual will continue.",
                                    exception);
                    elapsed += 2;
                    if (elapsed >= RITUAL_TICKS) finish(true);
                }
            }

            private void tickRitual() {
                if (relics.stream().anyMatch(relic -> !relic.isValid())) {
                    finish(false);
                    return;
                }
                double progress = elapsed / (double) RITUAL_TICKS;
                int remaining = Math.max(0, (RITUAL_TICKS - elapsed + 19) / 20);
                bossBar.setProgress(Math.max(0, Math.min(1, progress)));
                bossBar.setTitle(
                        mythic.title + " Ritual • " + formatTime(remaining) + " • " + coordinates);
                double rotation = elapsed * .018;
                drawSpinningStar(ground, rotation, progress);
                drawRitualEffects(ground, elapsed, progress, mythic);
                if (elapsed % 20 == 0) updateSkyViewers();
                if (elapsed == 600 || elapsed == 1200 || elapsed == 1800 || elapsed == 2400)
                    safelySummonSkyOmen(ground, mythic);
                double convergence = Math.max(0, Math.min(1, (progress - .72) / .28));
                convergence = convergence * convergence * (3 - 2 * convergence);
                double orbitRadius = 2.7 * (1 - convergence);
                double height = .75 + Math.sin(elapsed * .04) * .14 + convergence * 7.25;
                for (int point = 0; point < relics.size(); point++) {
                    ItemDisplay relic = relics.get(point);
                    double angle = rotation - Math.PI / 2 + point * Math.PI * 2 / 5;
                    relic.setRotation(elapsed * 2.4f + point * 72, 0);
                    double scale = .52 + convergence * .38;
                    Transformation transformation = relic.getTransformation();
                    transformation.getScale().set((float) scale);
                    relic.setTransformation(transformation);
                    relic.teleport(
                            ground.clone()
                                    .add(
                                            Math.cos(angle) * orbitRadius,
                                            height,
                                            Math.sin(angle) * orbitRadius));
                }
                for (int point = 0; point < relics.size(); point++)
                    drawRelicCocoon(
                            relics.get(point).getLocation(),
                            ground,
                            elapsed,
                            progress,
                            mythic,
                            point == 0);
                elapsed += 2;
                if (elapsed >= RITUAL_TICKS) finish(true);
            }

            private void finish(boolean reward) {
                if (reward) completeRitual(creator, relics, center, mythic);
                else {
                    for (ItemDisplay relic : relics) relic.remove();
                    activeDisplays.removeAll(relics);
                }
                BossBar removed = activeBossBars.remove(tableId);
                if (removed != null) removed.removeAll();
                activeTables.remove(tableId);
                activeWorlds.remove(center.getWorld().getUID());
                activeSkies.remove(tableId);
                updateSkyViewers();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private String formatTime(int totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private void drawSpinningStar(Location center, double rotation, double progress) {
        Color color = progress > .85 ? Color.fromRGB(255, 210, 55) : Color.fromRGB(145, 55, 255);
        Particle.DustOptions dust = new Particle.DustOptions(color, progress > .85 ? 1.45f : 1.05f);
        Location[] vertices = new Location[5];
        double radius = 2.7 + Math.sin(progress * Math.PI) * .45;
        for (int vertex = 0; vertex < vertices.length; vertex++) {
            double angle = rotation - Math.PI / 2 + vertex * Math.PI * 2 / 5;
            vertices[vertex] =
                    center.clone().add(Math.cos(angle) * radius, .08, Math.sin(angle) * radius);
        }
        for (int edge = 0; edge < STAR_ORDER.length - 1; edge++)
            drawLine(vertices[STAR_ORDER[edge]], vertices[STAR_ORDER[edge + 1]], dust);
    }

    private void drawRelicCocoon(
            Location relic,
            Location ground,
            int elapsed,
            double progress,
            Mythic mythic,
            boolean drawTethers) {
        Color color = omenColor(mythic);
        Particle.DustOptions dust = new Particle.DustOptions(color, progress > .85 ? 1.3f : .9f);
        for (int ring = 0; ring < 2; ring++) {
            double radius = .65 + ring * .22 + progress * .3;
            double height = (ring - 1) * .48;
            for (int point = 0; point < 10; point++) {
                double angle = elapsed * (.025 + ring * .006) + point * Math.PI * 2 / 10;
                Location orbit =
                        relic.clone()
                                .add(
                                        Math.cos(angle) * radius,
                                        height + Math.sin(angle * 2 + ring) * .16,
                                        Math.sin(angle) * radius);
                relic.getWorld().spawnParticle(Particle.DUST, orbit, 1, 0, 0, 0, 0, dust);
            }
        }
        if (drawTethers && elapsed % 10 == 0) {
            for (int tether = 0; tether < 5; tether++) {
                double angle = elapsed * .018 - Math.PI / 2 + tether * Math.PI * 2 / 5;
                Location anchor =
                        ground.clone().add(Math.cos(angle) * 2.7, .1, Math.sin(angle) * 2.7);
                drawLine(
                        anchor, relic, new Particle.DustOptions(Color.fromRGB(185, 110, 255), .7f));
            }
        }
        if (drawTethers && elapsed % 400 == 0 && elapsed > 0) safelyDrawMilestone(relic, elapsed);
    }

    private void safelyDrawMilestone(Location relic, int elapsed) {
        try {
            relic.getWorld().spawnParticle(Particle.END_ROD, relic, 35, .8, .8, .8, .08);
            relic.getWorld().playSound(relic, Sound.BLOCK_BEACON_POWER_SELECT, .9f, 1.25f);
        } catch (RuntimeException exception) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Skipped ritual milestone effect at tick " + elapsed + ".",
                            exception);
        }
    }

    private void drawLine(Location start, Location end, Particle.DustOptions dust) {
        Vector delta = end.toVector().subtract(start.toVector());
        int points = Math.max(2, (int) (delta.length() * 3.5));
        Vector step = delta.multiply(1.0 / points);
        Location point = start.clone();
        for (int index = 0; index <= points; index++) {
            start.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            point.add(step);
        }
    }

    private void drawRitualEffects(Location center, int elapsed, double progress, Mythic mythic) {
        if (elapsed % 10 == 0)
            center.getWorld()
                    .spawnParticle(
                            Particle.DUST,
                            center.clone().add(0, 15, 0),
                            45,
                            13,
                            2.5,
                            13,
                            0,
                            new Particle.DustOptions(omenColor(mythic), 2.1f));
        if (elapsed % 20 == 0) {
            double radius = 1.2 + progress * 3.2;
            for (int point = 0; point < 32; point++) {
                double angle = point * Math.PI * 2 / 32 + elapsed * .01;
                Location ring =
                        center.clone().add(Math.cos(angle) * radius, .12, Math.sin(angle) * radius);
                center.getWorld().spawnParticle(Particle.ENCHANT, ring, 1, 0, .12, 0, 0);
            }
            center.getWorld()
                    .playSound(
                            center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .8f, .55f + (float) progress);
        }
        if (elapsed > RITUAL_TICKS / 2 && elapsed % 8 == 0)
            center.getWorld()
                    .spawnParticle(
                            Particle.END_ROD, center.clone().add(0, 1, 0), 5, 1.8, 1.5, 1.8, .015);
        if (elapsed > RITUAL_TICKS - 400 && elapsed % 4 == 0)
            center.getWorld()
                    .spawnParticle(
                            Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,
                            center.clone().add(0, 1.3, 0),
                            8,
                            2.2,
                            1.2,
                            2.2,
                            .02);
        if (elapsed > RITUAL_TICKS - 200 && elapsed % 4 == 0) {
            Location sky = center.clone().add(0, 18, 0);
            Particle.DustOptions beam = new Particle.DustOptions(omenColor(mythic), 1.5f);
            drawLine(sky, center.clone().add(0, .2, 0), beam);
        }
        if (elapsed == RITUAL_TICKS - 200)
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, .65f);
    }

    private void summonSkyOmen(Location center, Mythic mythic) {
        switch (mythic) {
            case ONSLAUGHT -> summonFallingOmen(center, mythic);
            case SKYFALL, EFFIGY -> summonFallingOmen(center, mythic);
            default -> summonDistinctOmen(center, mythic);
        }
    }

    private void safelySummonSkyOmen(Location center, Mythic mythic) {
        try {
            summonSkyOmen(center, mythic);
        } catch (RuntimeException exception) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Skipped a failed " + mythic.name() + " ritual sky omen.",
                            exception);
        }
    }

    private void summonFallingOmen(Location center, Mythic mythic) {
        Material symbol = omenMaterial(mythic);
        for (int index = 0; index < 5; index++) {
            int slot = index;
            double angle = index * Math.PI * 2 / 5;
            Location start =
                    center.clone()
                            .add(Math.cos(angle) * 3.3, 15 + index * .8, Math.sin(angle) * 3.3);
            ItemDisplay omen =
                    center.getWorld()
                            .spawn(
                                    start,
                                    ItemDisplay.class,
                                    display -> {
                                        display.setItemStack(new ItemStack(symbol));
                                        display.setGlowing(true);
                                        display.setGlowColorOverride(omenColor(mythic));
                                        display.setViewRange(48f);
                                        display.setPersistent(false);
                                        display.setRotation(slot * 72f, 90f);
                                    });
            activeDisplays.add(omen);
            animateSkyOmen(
                    omen,
                    center.clone().add(Math.cos(angle) * 3.3, .25, Math.sin(angle) * 3.3),
                    mythic);
        }
        center.getWorld()
                .playSound(
                        center.clone().add(0, 8, 0), Sound.ENTITY_ENDER_DRAGON_GROWL, .55f, 1.45f);
    }

    private void summonDistinctOmen(Location center, Mythic mythic) {
        Particle.DustOptions dust = new Particle.DustOptions(omenColor(mythic), 1.45f);
        center.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.1f, .7f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick > 70) {
                    center.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 5, 0), 1);
                    cancel();
                    return;
                }
                double phase = tick * .13;
                switch (mythic) {
                    case EPOCH -> drawClockOmen(center, phase, dust);
                    case STARFALL -> drawMeteorOmen(center, tick, dust);
                    case DISARRAY -> drawGlitchOmen(center, tick, dust);
                    case AUTHORITY -> drawAuthorityOmen(center, tick, dust);
                    case TRANSPOSITION -> drawRiftOmen(center, phase, dust);
                    case SCRIPTURE -> drawRuneOmen(center, phase, dust);
                    case EVENT_HORIZON -> drawVortexOmen(center, phase, dust);
                    case RESONATOR -> drawResonanceOmen(center, phase, dust);
                    case PARALLAX -> drawDomeOmen(center, phase, dust);
                    default -> {}
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void drawClockOmen(Location center, double phase, Particle.DustOptions dust) {
        Location face = center.clone().add(0, 7, 0);
        for (int point = 0; point < 20; point++) {
            double angle = point * Math.PI * 2 / 20;
            face.getWorld()
                    .spawnParticle(
                            Particle.DUST,
                            face.clone().add(Math.cos(angle) * 3, Math.sin(angle) * 3, 0),
                            1,
                            0,
                            0,
                            0,
                            0,
                            dust);
        }
        drawLine(face, face.clone().add(Math.cos(phase) * 2.4, Math.sin(phase) * 2.4, 0), dust);
    }

    private void drawMeteorOmen(Location center, int tick, Particle.DustOptions dust) {
        for (int meteor = 0; meteor < 3; meteor++) {
            double travel = (tick + meteor * 12) % 45;
            Location head = center.clone().add(9 - travel * .4, 14 - travel * .18, meteor * 3 - 3);
            drawLine(head, head.clone().add(3, 1.35, 0), dust);
            head.getWorld().spawnParticle(Particle.END_ROD, head, 3, .15, .15, .15, .02);
        }
    }

    private void drawGlitchOmen(Location center, int tick, Particle.DustOptions dust) {
        java.util.Random random = new java.util.Random(center.hashCode() * 31L + tick);
        for (int line = 0; line < 7; line++) {
            Location start =
                    center.clone()
                            .add(
                                    random.nextDouble(-7, 7),
                                    random.nextDouble(3, 12),
                                    random.nextDouble(-7, 7));
            drawLine(start, start.clone().add(random.nextDouble(-3, 3), 0, 0), dust);
        }
    }

    private void drawAuthorityOmen(Location center, int tick, Particle.DustOptions dust) {
        Location crown = center.clone().add(0, 10, 0);
        for (int ray = 0; ray < 6; ray++) {
            double angle = ray * Math.PI / 3;
            drawLine(crown, crown.clone().add(Math.cos(angle) * 4, -3, Math.sin(angle) * 4), dust);
        }
        if (tick % 20 == 0) center.getWorld().strikeLightningEffect(center.clone().add(0, 1, 0));
    }

    private void drawRiftOmen(Location center, double phase, Particle.DustOptions dust) {
        for (int side : new int[] {-1, 1})
            for (int point = 0; point < 18; point++) {
                double angle = phase + point * Math.PI * 2 / 18;
                Location rift =
                        center.clone()
                                .add(side * 5, 6 + Math.sin(angle) * 2.3, Math.cos(angle) * 2.3);
                center.getWorld().spawnParticle(Particle.DUST, rift, 1, 0, 0, 0, 0, dust);
            }
        drawLine(center.clone().add(-5, 6, 0), center.clone().add(5, 6, 0), dust);
    }

    private void drawRuneOmen(Location center, double phase, Particle.DustOptions dust) {
        Location rune = center.clone().add(0, 8, 0);
        for (int point = 0; point < 24; point++) {
            double angle = phase + point * Math.PI * 2 / 24;
            double radius = point % 2 == 0 ? 3.2 : 2.1;
            rune.getWorld()
                    .spawnParticle(
                            Particle.DUST,
                            rune.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                            1,
                            0,
                            0,
                            0,
                            0,
                            dust);
        }
    }

    private void drawVortexOmen(Location center, double phase, Particle.DustOptions dust) {
        Location voidCenter = center.clone().add(0, 7, 0);
        for (int arm = 0; arm < 5; arm++) {
            double angle = phase + arm * Math.PI * 2 / 5;
            for (double radius = .5; radius < 6; radius += .65) {
                angle += .11;
                voidCenter
                        .getWorld()
                        .spawnParticle(
                                Particle.DUST,
                                voidCenter
                                        .clone()
                                        .add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                                1,
                                0,
                                0,
                                0,
                                0,
                                dust);
            }
        }
    }

    private void drawResonanceOmen(Location center, double phase, Particle.DustOptions dust) {
        for (int ring = 0; ring < 4; ring++) {
            double radius = 1.3 + ring * 1.45 + Math.sin(phase) * .25;
            for (int point = 0; point < 20; point++) {
                double angle = point * Math.PI * 2 / 20;
                Location pulse =
                        center.clone()
                                .add(Math.cos(angle) * radius, 5 + ring, Math.sin(angle) * radius);
                center.getWorld().spawnParticle(Particle.DUST, pulse, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private void drawDomeOmen(Location center, double phase, Particle.DustOptions dust) {
        for (int latitude = 1; latitude <= 4; latitude++) {
            double vertical = latitude * Math.PI / 10;
            double radius = Math.sin(vertical) * 6;
            double height = Math.cos(vertical) * 6;
            for (int point = 0; point < 16; point++) {
                double angle = phase + point * Math.PI * 2 / 16;
                Location dome =
                        center.clone()
                                .add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                center.getWorld().spawnParticle(Particle.DUST, dome, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private void animateSkyOmen(ItemDisplay omen, Location destination, Mythic mythic) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!omen.isValid()) {
                    activeDisplays.remove(omen);
                    cancel();
                    return;
                }
                Location next = omen.getLocation().add(0, -.48, 0);
                next.setYaw(next.getYaw() + 16f);
                next.setPitch(90f);
                omen.teleport(next);
                omen.getWorld()
                        .spawnParticle(
                                Particle.DUST,
                                next,
                                3,
                                .12,
                                .12,
                                .12,
                                0,
                                new Particle.DustOptions(omenColor(mythic), 1.1f));
                if (next.getY() <= destination.getY() || tick++ > 50) {
                    Location impact = destination.clone();
                    omen.remove();
                    activeDisplays.remove(omen);
                    impact.getWorld()
                            .spawnParticle(omenImpact(mythic), impact, 24, .6, .35, .6, .08);
                    impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, .55f, 1.5f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Material omenMaterial(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT -> Material.NETHERITE_SWORD;
            case EPOCH -> Material.CLOCK;
            case SKYFALL -> Material.MACE;
            case STARFALL -> Material.SPECTRAL_ARROW;
            case DISARRAY -> Material.REPEATER;
            case AUTHORITY -> Material.LIGHTNING_ROD;
            case TRANSPOSITION -> Material.ENDER_PEARL;
            case EFFIGY -> Material.ARMOR_STAND;
            case SCRIPTURE -> Material.ENCHANTED_BOOK;
            case EVENT_HORIZON -> Material.ENDER_EYE;
            case RESONATOR -> Material.COMPARATOR;
            case PARALLAX -> Material.SHIELD;
        };
    }

    private Color omenColor(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT, RESONATOR -> Color.fromRGB(230, 15, 20);
            case EPOCH, AUTHORITY -> Color.fromRGB(255, 190, 35);
            case SKYFALL, PARALLAX -> Color.fromRGB(70, 205, 255);
            case STARFALL, TRANSPOSITION -> Color.fromRGB(175, 60, 255);
            case DISARRAY -> Color.fromRGB(65, 230, 85);
            case EFFIGY -> Color.fromRGB(145, 145, 145);
            case SCRIPTURE -> Color.fromRGB(190, 115, 45);
            case EVENT_HORIZON -> Color.fromRGB(55, 0, 100);
        };
    }

    private Particle omenImpact(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT -> Particle.CRIT;
            case EPOCH, SCRIPTURE -> Particle.ENCHANT;
            case SKYFALL -> Particle.CLOUD;
            case STARFALL -> Particle.END_ROD;
            case DISARRAY -> Particle.WITCH;
            case AUTHORITY, RESONATOR -> Particle.ELECTRIC_SPARK;
            case TRANSPOSITION -> Particle.PORTAL;
            case EFFIGY -> Particle.SOUL;
            case EVENT_HORIZON -> Particle.REVERSE_PORTAL;
            case PARALLAX -> Particle.ENCHANTED_HIT;
        };
    }

    private void updateSkyViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            RitualSky nearest =
                    activeSkies.values().stream()
                            .filter(sky -> sky.center().getWorld() == player.getWorld())
                            .filter(
                                    sky ->
                                            sky.center().distanceSquared(player.getLocation())
                                                    <= 96 * 96)
                            .min(
                                    java.util.Comparator.comparingDouble(
                                            sky ->
                                                    sky.center()
                                                            .distanceSquared(player.getLocation())))
                            .orElse(null);
            if (nearest == null) {
                if (themedSkyPlayers.remove(player.getUniqueId())) resetSky(player);
                continue;
            }
            SkyProfile profile = skyProfile(nearest.mythic());
            player.setPlayerTime(profile.time(), false);
            player.setPlayerWeather(profile.storm() ? WeatherType.DOWNFALL : WeatherType.CLEAR);
            themedSkyPlayers.add(player.getUniqueId());
        }
    }

    private void resetSky(Player player) {
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    private SkyProfile skyProfile(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT -> new SkyProfile(12500, true);
            case EPOCH -> new SkyProfile(6000, false);
            case SKYFALL -> new SkyProfile(23000, true);
            case STARFALL -> new SkyProfile(18000, false);
            case DISARRAY -> new SkyProfile(13500, true);
            case AUTHORITY -> new SkyProfile(12000, true);
            case TRANSPOSITION -> new SkyProfile(17000, false);
            case EFFIGY -> new SkyProfile(14500, true);
            case SCRIPTURE -> new SkyProfile(1000, false);
            case EVENT_HORIZON -> new SkyProfile(18000, true);
            case RESONATOR -> new SkyProfile(12500, true);
            case PARALLAX -> new SkyProfile(6000, false);
        };
    }

    private void completeRitual(
            Player creator, List<ItemDisplay> relics, Location center, Mythic mythic) {
        ItemDisplay descendingRelic = relics.get(0);
        for (int index = 1; index < relics.size(); index++) {
            ItemDisplay absorbed = relics.get(index);
            absorbed.getWorld().spawnParticle(Particle.FLASH, absorbed.getLocation(), 1);
            absorbed.remove();
            activeDisplays.remove(absorbed);
        }
        descendingRelic.getWorld().spawnParticle(Particle.FLASH, descendingRelic.getLocation(), 2);
        descendingRelic
                .getWorld()
                .playSound(descendingRelic.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.6f);
        Location restingPlace = center.clone().add(0, .35, 0);
        new BukkitRunnable() {
            int ticks;
            boolean landed;

            @Override
            public void run() {
                try {
                    animateStep();
                } catch (RuntimeException exception) {
                    plugin.getLogger()
                            .log(
                                    java.util.logging.Level.WARNING,
                                    "The ritual descent failed; placing the claimable relic safely.",
                                    exception);
                    land();
                }
            }

            private void animateStep() {
                if (landed) return;
                if (!descendingRelic.isValid() || ticks++ >= 100) {
                    land();
                    return;
                }
                Location current = descendingRelic.getLocation();
                current.setY(Math.max(restingPlace.getY(), current.getY() - .09));
                descendingRelic.teleport(current);
                descendingRelic.setRotation((ticks * 5f) % 360f, 0);
                current.getWorld()
                        .spawnParticle(
                                Particle.DUST,
                                current,
                                5,
                                .18,
                                .25,
                                .18,
                                0,
                                new Particle.DustOptions(omenColor(mythic), 1.6f));
                if (current.getY() > restingPlace.getY()) return;
                land();
            }

            private void land() {
                if (landed) return;
                landed = true;
                ItemDisplay display = descendingRelic;
                if (!display.isValid()) {
                    activeDisplays.remove(display);
                    display =
                            restingPlace
                                    .getWorld()
                                    .spawn(
                                            restingPlace,
                                            ItemDisplay.class,
                                            entity -> {
                                                entity.setItemStack(items.create(mythic));
                                                entity.setGlowing(true);
                                                entity.setGlowColorOverride(omenColor(mythic));
                                                entity.setPersistent(false);
                                            });
                    activeDisplays.add(display);
                } else display.teleport(restingPlace);
                Interaction interaction =
                        restingPlace
                                .getWorld()
                                .spawn(
                                        restingPlace.clone().add(0, .3, 0),
                                        Interaction.class,
                                        entity -> {
                                            entity.setInteractionWidth(1.4f);
                                            entity.setInteractionHeight(1.6f);
                                            entity.setResponsive(true);
                                            entity.setPersistent(false);
                                        });
                pendingRelics.put(
                        interaction.getUniqueId(), new PendingRelic(display, interaction, mythic));
                restingPlace
                        .getWorld()
                        .spawnParticle(Particle.END_ROD, restingPlace, 45, .7, .4, .7, .06);
                restingPlace
                        .getWorld()
                        .playSound(restingPlace, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, .8f);
                Bukkit.broadcast(
                        Component.text(mythic.title, NamedTextColor.GOLD)
                                .append(
                                        Component.text(
                                                " is resting on its crafting table. Right-click it to claim it.",
                                                NamedTextColor.YELLOW)));
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void defineRecipes() {
        put(
                Mythic.ONSLAUGHT,
                Material.ANCIENT_DEBRIS,
                Material.BLAZE_ROD,
                Material.WITHER_SKELETON_SKULL);
        put(Mythic.EPOCH, Material.GOLD_BLOCK, Material.ECHO_SHARD, Material.CRYING_OBSIDIAN);
        put(Mythic.SKYFALL, Material.BREEZE_ROD, Material.PHANTOM_MEMBRANE, Material.HEAVY_CORE);
        put(Mythic.STARFALL, Material.AMETHYST_BLOCK, Material.FIREWORK_STAR, Material.END_CRYSTAL);
        put(
                Mythic.DISARRAY,
                Material.REDSTONE_BLOCK,
                Material.FERMENTED_SPIDER_EYE,
                Material.ECHO_SHARD);
        put(Mythic.AUTHORITY, Material.GOLD_BLOCK, Material.OMINOUS_TRIAL_KEY, Material.ECHO_SHARD);
        put(
                Mythic.TRANSPOSITION,
                Material.ENDER_EYE,
                Material.HEART_OF_THE_SEA,
                Material.RECOVERY_COMPASS);
        put(Mythic.EFFIGY, Material.TUFF_BRICKS, Material.SOUL_SAND, Material.TOTEM_OF_UNDYING);
        put(
                Mythic.SCRIPTURE,
                Material.CHISELED_BOOKSHELF,
                Material.EXPERIENCE_BOTTLE,
                Material.ENCHANTED_GOLDEN_APPLE);
        put(Mythic.EVENT_HORIZON, Material.OBSIDIAN, Material.SCULK_CATALYST, Material.END_CRYSTAL);
        put(Mythic.RESONATOR, Material.REDSTONE_BLOCK, Material.LIGHTNING_ROD, Material.HEAVY_CORE);
        put(Mythic.PARALLAX, Material.IRON_BLOCK, Material.ECHO_SHARD, Material.TOTEM_OF_UNDYING);
    }

    private void put(Mythic mythic, Material north, Material sides, Material south) {
        Material vessel = mythic == Mythic.AUTHORITY ? Material.LIGHTNING_ROD : mythic.material;
        recipes.put(
                mythic, new RecipeMaterials(Material.NETHERITE_INGOT, north, sides, vessel, south));
    }

    private void reserveMythic(Mythic mythic) {
        craftedMythics.add(mythic);
        NamespacedKey key = recipeKeys.get(mythic);
        if (key != null) Bukkit.removeRecipe(key);
        plugin.getConfig()
                .set("crafted-mythics", craftedMythics.stream().map(Enum::name).sorted().toList());
        plugin.saveConfig();
    }

    private String pretty(Material material) {
        String value = material.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record RecipeMaterials(
            Material corner, Material north, Material sides, Material core, Material south) {}

    private record RitualSky(Location center, Mythic mythic) {}

    private record SkyProfile(long time, boolean storm) {}

    private record PendingRelic(ItemDisplay display, Interaction interaction, Mythic mythic) {}
}
