package dev.vanillasmp;

import dev.vanillasmp.ability.AbilityRegistry;
import dev.vanillasmp.command.MythicCommand;
import dev.vanillasmp.gui.GatewayMenu;
import dev.vanillasmp.gui.MythicMenu;
import dev.vanillasmp.item.MythicItemService;
import dev.vanillasmp.listener.MythicInventoryListener;
import dev.vanillasmp.listener.PlayerLifecycleListener;
import dev.vanillasmp.manager.AbilityManager;
import dev.vanillasmp.manager.MythicManager;
import dev.vanillasmp.model.Mythic;
import dev.vanillasmp.ritual.RitualManager;
import dev.vanillasmp.service.CooldownService;
import java.io.File;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class VanillaSMP extends JavaPlugin implements Listener {
    MythicItemService items;
    private MythicMenu mythicMenu;
    private GatewayMenu gatewayMenu;
    private RitualManager ritualManager;
    private MythicManager mythicManager;
    private AbilityManager abilityManager;
    private CooldownService cooldownService;
    private final Map<UUID, Combo> combos = new HashMap<>();
    private final Map<UUID, OnslaughtMode> onslaughtModes = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>(),
            silenced = new HashMap<>(),
            wards = new HashMap<>();
    private final Map<UUID, TimeStop> timeStops = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, FrozenPlayerState> frozenPlayerStates = new HashMap<>();
    private final Map<UUID, List<Location>> stars = new HashMap<>();
    private final Map<UUID, StarMode> starModes = new HashMap<>();
    private final Map<UUID, StarGuide> starGuides = new HashMap<>();
    private final Map<UUID, List<Location>> starShots = new HashMap<>();
    private final Map<String, Integer> starImpacts = new HashMap<>();
    private final List<Constellation> constellations = new ArrayList<>();
    private final Map<UUID, EffigyLink> effigies = new HashMap<>();
    private final Map<UUID, Integer> impact = new HashMap<>();
    private final Map<UUID, Long> zero = new HashMap<>(),
            refraction = new HashMap<>(),
            authorityOverride = new HashMap<>();
    private final Map<UUID, Long> authorityJudgement = new HashMap<>();
    private final Map<UUID, ScripturePocket> scripturePockets = new HashMap<>();
    private final Map<UUID, String> pendingEffigies = new HashMap<>();
    private final Map<UUID, Long> hudPausedUntil = new HashMap<>();
    private final Set<UUID> hudVisible = new HashSet<>();
    private final Set<UUID> resonatorCharging = new HashSet<>();
    private final Set<UUID> resonatorFiring = new HashSet<>();
    private final Set<BlockDisplay> resonatorDisplays = new HashSet<>();
    private final Map<UUID, Long> resonatorFallProtection = new HashMap<>();
    private final Map<Mythic, Team> badgeTeams = new EnumMap<>(Mythic.class);
    private final Map<UUID, Mythic> displayedBadges = new HashMap<>();
    private NamespacedKey effigyOwner, effigyTarget, starArrow;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File legacyTempleData = new File(getDataFolder(), "temples.yml");
        if (legacyTempleData.exists() && !legacyTempleData.delete())
            getLogger().warning("Could not remove the obsolete temples.yml file.");
        items = new MythicItemService(this);
        mythicManager = new MythicManager(items);
        cooldownService = new CooldownService();
        abilityManager = new AbilityManager(new AbilityRegistry());
        abilityManager.enable();
        setupBadgeTeams();
        effigyOwner = new NamespacedKey(this, "effigy_owner");
        effigyTarget = new NamespacedKey(this, "effigy_target");
        starArrow = new NamespacedKey(this, "star_arrow");
        getServer().getPluginManager().registerEvents(this, this);
        getServer()
                .getPluginManager()
                .registerEvents(new MythicInventoryListener(mythicManager), this);
        getServer()
                .getPluginManager()
                .registerEvents(new PlayerLifecycleListener(cooldownService), this);
        ritualManager = new RitualManager(this, items);
        ritualManager.registerRecipes();
        getServer().getPluginManager().registerEvents(ritualManager, this);
        mythicMenu = new MythicMenu(items, ritualManager);
        getServer().getPluginManager().registerEvents(mythicMenu, this);
        gatewayMenu = new GatewayMenu(items);
        getServer().getPluginManager().registerEvents(gatewayMenu, this);
        MythicCommand handler = new MythicCommand(this);
        Objects.requireNonNull(getCommand("mythic")).setExecutor(handler);
        Objects.requireNonNull(getCommand("mythic")).setTabCompleter(handler);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
        getServer().getScheduler().runTaskTimer(this, this::updateActionBars, 10, 10);
        getLogger().info("Vanilla SMP: 12 Mythics enabled.");
    }

    @Override
    public void onDisable() {
        for (ScripturePocket pocket : new ArrayList<>(scripturePockets.values()))
            releaseScripturePocket(pocket);
        if (abilityManager != null) abilityManager.disable();
        if (ritualManager != null) ritualManager.shutdown();
        for (BlockDisplay display : new ArrayList<>(resonatorDisplays)) display.remove();
        resonatorDisplays.clear();
        clearBadgeTeams();
        endTimeStop();
        for (EffigyLink e : new ArrayList<>(effigies.values())) sever(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        Mythic m = items.type(item);
        if (m == null) return;
        boolean right = e.getAction().isRightClick();
        if (!right) return;
        Player p = e.getPlayer();
        // Starfall is a real bow: allow drawing/firing while ProjectileLaunch applies its mechanic.
        if (m == Mythic.STARFALL) {
            if (p.isSneaking()) {
                e.setCancelled(true);
                if (!allowedHand(p, item, m)) {
                    msg(p, "That Mythic cannot activate from this hand.", NamedTextColor.RED);
                    return;
                }
                if (isSilenced(p)) {
                    msg(p, "Your Mythics are silenced.", NamedTextColor.DARK_RED);
                    return;
                }
                switchStarfallMode(p, item);
            }
            return;
        }
        if (m != Mythic.EFFIGY && m != Mythic.PARALLAX) e.setCancelled(true);
        if (!allowedHand(p, item, m)) {
            msg(p, "That Mythic cannot activate from this hand.", NamedTextColor.RED);
            return;
        }
        if (isSilenced(p)) {
            msg(p, "Your Mythics are silenced.", NamedTextColor.DARK_RED);
            return;
        }
        boolean shift = p.isSneaking();
        switch (m) {
            case ONSLAUGHT -> {
                if (shift) switchOnslaughtMode(p, item);
                else activateOnslaught(p, item);
            }
            case EPOCH -> epoch(p, item);
            case SKYFALL -> skyfall(p, item, shift);
            case DISARRAY -> disarray(p, item);
            case AUTHORITY -> authority(p, item, shift);
            case TRANSPOSITION -> transpose(p, item);
            case EFFIGY -> bindEffigy(p, item, e);
            case SCRIPTURE -> scripture(p, item);
            case EVENT_HORIZON -> horizon(p, item, shift);
            case RESONATOR -> resonator(p, item, shift);
            case PARALLAX -> {
                if (shift) {
                    e.setCancelled(true);
                    parallaxBash(p, item);
                }
            }
            default -> {}
        }
    }

    @EventHandler
    public void onPocketViewerJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        for (ScripturePocket pocket : scripturePockets.values()) {
            Player victim = Bukkit.getPlayer(pocket.victim());
            if (victim == null) continue;
            viewer.hidePlayer(this, victim);
            victim.hidePlayer(this, viewer);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        Mythic m = items.type(e.getItemInHand());
        if (m != null && m != Mythic.EFFIGY) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void fish(PlayerFishEvent e) {
        if (items.is(e.getPlayer().getInventory().getItemInMainHand(), Mythic.TRANSPOSITION)
                || items.is(
                        e.getPlayer().getInventory().getItemInOffHand(), Mythic.TRANSPOSITION)) {
            e.setCancelled(true);
            if (e.getHook() != null) e.getHook().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void projectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (e.getEntity() instanceof EnderSignal && hasEither(p, Mythic.EVENT_HORIZON))
            e.setCancelled(true);
        if (e.getEntity() instanceof Arrow a && hasEither(p, Mythic.STARFALL) && !isSilenced(p)) {
            a.getPersistentDataContainer()
                    .set(starArrow, PersistentDataType.STRING, p.getUniqueId().toString());
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler
    public void projectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a)) return;
        String raw = a.getPersistentDataContainer().get(starArrow, PersistentDataType.STRING);
        if (raw == null) return;
        UUID owner = UUID.fromString(raw);
        if (e.getHitEntity() instanceof Player target) {
            String key = owner + ":" + target.getUniqueId();
            int hits = starImpacts.merge(key, 1, Integer::sum);
            if (hits >= 3) {
                starImpacts.remove(key);
                Player shooter = Bukkit.getPlayer(owner);
                target.getWorld()
                        .spawnParticle(
                                Particle.FIREWORK,
                                target.getLocation().add(0, 1, 0),
                                35,
                                .8,
                                .8,
                                .8,
                                .08);
                target.getWorld()
                        .spawnParticle(
                                Particle.END_ROD,
                                target.getLocation().add(0, 1, 0),
                                20,
                                1,
                                1,
                                1,
                                .04);
                target.damage(7.0, shooter);
                target.setVelocity(target.getVelocity().add(new Vector(0, .45, 0)));
                particleStar(target, shooter);
                if (shooter != null) msg(shooter, "STAR IMPACT!", NamedTextColor.LIGHT_PURPLE);
            }
            a.remove();
            return;
        }
        if (e.getHitBlock() == null) return;
        Location l = a.getLocation();
        a.remove();
        addStar(owner, l);
    }

    private void particleStar(Player target, Player shooter) {
        Vector facing =
                shooter == null
                        ? target.getLocation().getDirection().setY(0).normalize()
                        : shooter.getLocation()
                                .toVector()
                                .subtract(target.getLocation().toVector())
                                .setY(0)
                                .normalize();
        if (facing.lengthSquared() < .01) facing = new Vector(0, 0, 1);
        Vector right = new Vector(-facing.getZ(), 0, facing.getX()).normalize();
        Vector up = new Vector(0, 1, 0);
        int[] order = {0, 2, 4, 1, 3, 0};
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 20, 15), 1.5f);
        for (int frame = 0; frame < 10; frame++) {
            getServer()
                    .getScheduler()
                    .runTaskLater(
                            this,
                            () -> {
                                if (!target.isOnline()) return;
                                Location center = target.getLocation().add(0, 1.0, 0);
                                for (int segment = 0; segment < order.length - 1; segment++) {
                                    Location from =
                                            starVertex(center, right, up, order[segment], 1.12);
                                    Location to =
                                            starVertex(center, right, up, order[segment + 1], 1.12);
                                    particleLine(from, to, red, null);
                                }
                            },
                            frame);
        }
    }

    private Location starVertex(
            Location center, Vector right, Vector up, int vertex, double radius) {
        double angle = -Math.PI / 2 + vertex * Math.PI * 2 / 5;
        return center.clone()
                .add(right.clone().multiply(Math.cos(angle) * radius))
                .add(up.clone().multiply(Math.sin(angle) * radius));
    }

    private void particleLine(
            Location start, Location end, Particle.DustOptions dust, Particle accent) {
        Vector delta = end.toVector().subtract(start.toVector());
        int points = Math.max(2, (int) (delta.length() * 9));
        Vector step = delta.multiply(1.0 / points);
        Location point = start.clone();
        for (int i = 0; i <= points; i++) {
            start.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            if (accent != null && i % 6 == 0)
                start.getWorld().spawnParticle(accent, point, 1, 0, 0, 0, 0);
            point.add(step);
        }
    }

    private void switchOnslaughtMode(Player player, ItemStack sword) {
        if (!ready(player, "onslaught-mode-swap", 2)) return;
        OnslaughtMode mode =
                onslaughtModes.getOrDefault(player.getUniqueId(), OnslaughtMode.BLOOD_DASH).next();
        onslaughtModes.put(player.getUniqueId(), mode);
        Combo combo = combos.get(player.getUniqueId());
        int hits = combo == null ? 0 : combo.hits;
        items.status(sword, "Mode: " + mode.display, "Combo: ×" + hits);
        msg(player, "Onslaught Mode: " + mode.display, NamedTextColor.RED);
    }

    private void switchStarfallMode(Player player, ItemStack bow) {
        StarMode mode = starModes.getOrDefault(player.getUniqueId(), StarMode.TRIAD).next();
        starModes.put(player.getUniqueId(), mode);
        stars.remove(player.getUniqueId());
        starShots.remove(player.getUniqueId());
        items.status(bow, "Mode: " + mode.display, "Shots: 0 / " + mode.points);
        starGuides.put(player.getUniqueId(), createStarGuide(player, mode));
        msg(player, "Starfall Mode: " + mode.display, NamedTextColor.LIGHT_PURPLE);
    }

    private StarGuide createStarGuide(Player player, StarMode mode) {
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < .01) forward = new Vector(0, 0, 1);
        forward.normalize();
        Player target = target(player, 30);
        Location center =
                target == null
                        ? player.getLocation().add(forward.multiply(11))
                        : target.getLocation();
        center.setY(
                player.getWorld().getHighestBlockYAt(center, HeightMap.MOTION_BLOCKING_NO_LEAVES)
                        + .18);
        List<Location> nodes = new ArrayList<>();
        for (int point = 0; point < mode.points; point++) {
            double angle = -Math.PI / 2 + point * Math.PI * 2 / mode.points;
            double radius = mode == StarMode.STAR && point % 2 == 1 ? 2.5 : 5.0;
            if (mode == StarMode.TRIAD) radius = 4.5;
            if (mode == StarMode.ORBIT) radius = 4.7;
            Location node =
                    center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            node.setY(
                    player.getWorld().getHighestBlockYAt(node, HeightMap.MOTION_BLOCKING_NO_LEAVES)
                            + .18);
            nodes.add(node);
        }
        return new StarGuide(nodes, System.currentTimeMillis() + 20000L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player victim) {
            if (e instanceof EntityDamageByEntityEvent
                    && victim.isBlocking()
                    && hasEither(victim, Mythic.PARALLAX)) {
                e.setCancelled(true);
                victim.getWorld()
                        .spawnParticle(
                                Particle.ENCHANTED_HIT,
                                victim.getLocation().add(0, 1, 0),
                                18,
                                .7,
                                .8,
                                .7,
                                .12);
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, .8f);
                return;
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                    && System.currentTimeMillis()
                            < resonatorFallProtection.getOrDefault(victim.getUniqueId(), 0L)) {
                e.setCancelled(true);
                victim.setFallDistance(0);
                return;
            }
            if (System.currentTimeMillis() < wards.getOrDefault(victim.getUniqueId(), 0L)
                    && e.getDamage() >= 3) {
                e.setDamage(e.getDamage() * .25);
                wards.remove(victim.getUniqueId());
                msg(victim, "WARD absorbed the attack.", NamedTextColor.AQUA);
            }
            if (System.currentTimeMillis() < zero.getOrDefault(victim.getUniqueId(), 0L)) {
                if (e instanceof EntityDamageByEntityEvent de
                        && de.getDamager() instanceof LivingEntity attacker) {
                    e.setCancelled(true);
                    repel(attacker, victim.getLocation(), 1.5);
                } else if (e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                        || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
                    e.setDamage(e.getDamage() * .25);
            }
        }
        if (e instanceof EntityDamageByEntityEvent de) combat(de);
    }

    private void combat(EntityDamageByEntityEvent e) {
        Player attacker = attacker(e.getDamager());
        if (attacker != null) {
            if (timeFrozen(e.getEntity())) {
                e.setCancelled(true);
                TimeStop s = containingStop(e.getEntity().getLocation());
                if (s != null && e.getEntity() instanceof LivingEntity le)
                    s.queued.merge(le.getUniqueId(), Math.min(e.getFinalDamage(), 6), Double::sum);
                return;
            }
            if (items.is(attacker.getInventory().getItemInMainHand(), Mythic.ONSLAUGHT)
                    && e.getEntity() instanceof Player target) onslaught(attacker, target, e);
            if (e.getEntity() instanceof Player target
                    && hasEither(attacker, Mythic.AUTHORITY)
                    && authorityJudgement.getOrDefault(attacker.getUniqueId(), 0L)
                            > System.currentTimeMillis()) {
                authorityJudgement.remove(attacker.getUniqueId());
                strikeJudgement(attacker, target);
            }
            if (items.is(attacker.getInventory().getItemInMainHand(), Mythic.SKYFALL)
                    && attacker.getFallDistance() > 1.5) {
                int v = Math.min(3, impact.getOrDefault(attacker.getUniqueId(), 0) + 1);
                impact.put(attacker.getUniqueId(), v);
                items.status(
                        attacker.getInventory().getItemInMainHand(),
                        "Impact: " + "◆".repeat(v) + "◇".repeat(3 - v));
                if (items.getInt(attacker.getInventory().getItemInMainHand(), "rebound", 0) == 1) {
                    attacker.setVelocity(attacker.getVelocity().setY(1.55));
                    attacker.setFallDistance(0);
                    items.setInt(attacker.getInventory().getItemInMainHand(), "rebound", 0);
                    msg(attacker, "REBOUND!", NamedTextColor.AQUA);
                }
            }
        }
        if (e.getEntity() instanceof ArmorStand stand) {
            String target =
                    stand.getPersistentDataContainer().get(effigyTarget, PersistentDataType.STRING);
            if (target != null) {
                e.setCancelled(true);
                Player p = Bukkit.getPlayer(UUID.fromString(target));
                if (p != null) {
                    double d =
                            Math.min(
                                    getConfig().getDouble("effigy.max-transfer-damage"),
                                    e.getFinalDamage()
                                            * getConfig().getDouble("effigy.transfer-multiplier"));
                    p.setHealth(Math.max(1, p.getHealth() - d));
                    p.playHurtAnimation(0);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, .85f);
                    p.sendActionBar(
                            Component.text(
                                    "EFFIGY DAMAGE  -" + String.format("%.1f", d),
                                    NamedTextColor.DARK_RED));
                    p.getWorld()
                            .spawnParticle(
                                    Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 8);
                }
            }
        }
    }

    @EventHandler
    public void death(PlayerDeathEvent e) {
        combos.remove(e.getPlayer().getUniqueId());
        interruptTimeStopFor(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void frozenMove(PlayerMoveEvent e) {
        if (!frozenPlayers.contains(e.getPlayer().getUniqueId())) return;
        if (e.hasChangedPosition()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void timeStopTeleport(PlayerTeleportEvent event) {
        interruptTimeStopFor(event.getPlayer());
    }

    @EventHandler
    public void timeStopWorldChange(PlayerChangedWorldEvent event) {
        interruptTimeStopFor(event.getPlayer());
    }

    @EventHandler
    public void timeStopQuit(PlayerQuitEvent event) {
        interruptTimeStopFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void inventory(InventoryClickEvent e) {
        if (e.getCurrentItem() != null
                && items.type(e.getCurrentItem()) != null
                && e.getClick().isKeyboardClick()) {}
    }

    private void epoch(Player p, ItemStack item) {
        if (!ready(p, "epoch", getConfig().getInt("epoch.cooldown-seconds"))) return;
        endTimeStop();
        double radius = getConfig().getDouble("epoch.radius");
        TimeStop stop =
                new TimeStop(
                        UUID.randomUUID(),
                        p.getUniqueId(),
                        p.getLocation().clone(),
                        radius,
                        System.currentTimeMillis()
                                + getConfig().getLong("epoch.duration-seconds") * 1000L);
        timeStops.put(p.getUniqueId(), stop);
        try {
            for (Entity entity :
                    p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
                if (entity == p) continue;
                if (entity instanceof Player player) freezePlayer(player, stop);
                else freezeEntity(entity, stop);
            }
            long ticks = getConfig().getLong("epoch.duration-seconds") * 20;
            stop.task =
                    getServer().getScheduler().runTaskLater(this, () -> endTimeStop(stop), ticks);
        } catch (RuntimeException exception) {
            endTimeStop(stop);
            throw exception;
        }
        msg(p, "TIME STOP", NamedTextColor.GOLD);
    }

    private void freezePlayer(Player player, TimeStop stop) {
        UUID id = player.getUniqueId();
        if (!frozenPlayers.add(id)) return;
        frozenPlayerStates.put(
                id,
                new FrozenPlayerState(
                        player.getVelocity().clone(),
                        player.hasGravity(),
                        player.getWalkSpeed(),
                        player.getFlySpeed(),
                        player.getAllowFlight(),
                        player.isFlying(),
                        player.isCollidable(),
                        player.getFallDistance()));
        stop.entities.add(id);
        player.setVelocity(new Vector());
        player.setGravity(false);
    }

    private void freezeEntity(Entity entity, TimeStop stop) {
        Boolean originalAi = entity instanceof Mob mob ? mob.hasAI() : null;
        stop.nonPlayers.put(
                entity.getUniqueId(),
                new FrozenEntityState(
                        entity.getVelocity().clone(), entity.hasGravity(), originalAi));
        entity.setVelocity(new Vector());
        entity.setGravity(false);
        if (entity instanceof Mob mob) mob.setAI(false);
    }

    private boolean isTimeStopped(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    private void unfreezePlayer(Player player) {
        UUID id = player.getUniqueId();
        frozenPlayers.remove(id);
        FrozenPlayerState state = frozenPlayerStates.remove(id);
        for (TimeStop stop : timeStops.values()) {
            stop.entities.remove(id);
            stop.queued.remove(id);
        }
        if (state == null) return;
        player.setGravity(state.gravity());
        player.setWalkSpeed(state.walkSpeed());
        player.setFlySpeed(state.flySpeed());
        player.setAllowFlight(state.allowFlight());
        if (state.allowFlight()) player.setFlying(state.flying());
        player.setCollidable(state.collidable());
        player.setFallDistance(state.fallDistance());
        player.setVelocity(state.velocity().clone());
    }

    private void endTimeStop() {
        for (TimeStop stop : new ArrayList<>(timeStops.values())) endTimeStop(stop);
        for (UUID id : new HashSet<>(frozenPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) unfreezePlayer(player);
            else {
                frozenPlayers.remove(id);
                frozenPlayerStates.remove(id);
            }
        }
        timeStops.clear();
    }

    private void endTimeStop(TimeStop stop) {
        if (stop == null || stop.ending) return;
        TimeStop current = timeStops.get(stop.owner);
        if (current != stop || !current.id.equals(stop.id)) return;
        stop.ending = true;
        timeStops.remove(stop.owner, stop);
        if (stop.task != null && !stop.task.isCancelled()) stop.task.cancel();
        Map<UUID, Double> queuedDamage = new HashMap<>(stop.queued);
        for (UUID id : new HashSet<>(stop.entities)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                try {
                    unfreezePlayer(player);
                } catch (RuntimeException exception) {
                    getLogger()
                            .log(
                                    Level.WARNING,
                                    "Could not fully restore " + player.getName(),
                                    exception);
                    frozenPlayers.remove(id);
                    frozenPlayerStates.remove(id);
                }
            } else {
                frozenPlayers.remove(id);
                frozenPlayerStates.remove(id);
            }
        }
        for (Map.Entry<UUID, FrozenEntityState> entry : stop.nonPlayers.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null) continue;
            try {
                FrozenEntityState state = entry.getValue();
                entity.setGravity(state.gravity());
                entity.setVelocity(state.velocity().clone());
                if (entity instanceof Mob mob && state.ai() != null) mob.setAI(state.ai());
            } catch (RuntimeException exception) {
                getLogger()
                        .log(
                                Level.WARNING,
                                "Could not fully restore a time-stopped entity.",
                                exception);
            }
        }
        stop.entities.clear();
        stop.nonPlayers.clear();
        stop.queued.clear();
        double cap = getConfig().getDouble("epoch.damage-cap");
        for (var q : queuedDamage.entrySet()) {
            Entity en = Bukkit.getEntity(q.getKey());
            if (en instanceof LivingEntity le)
                le.damage(Math.min(cap, q.getValue()), Bukkit.getPlayer(stop.owner));
        }
        Player p = Bukkit.getPlayer(stop.owner);
        if (p != null) msg(p, "TIME RESUMES", NamedTextColor.YELLOW);
    }

    private void interruptTimeStopFor(Player player) {
        TimeStop owned = timeStops.get(player.getUniqueId());
        if (owned != null) endTimeStop(owned);
        if (isTimeStopped(player)) unfreezePlayer(player);
    }

    private void skyfall(Player p, ItemStack item, boolean shift) {
        int v = impact.getOrDefault(p.getUniqueId(), 0), cost = shift ? 2 : 1;
        if (v < cost) {
            msg(p, "Not enough Impact.", NamedTextColor.RED);
            return;
        }
        impact.put(p.getUniqueId(), v - cost);
        if (shift) {
            p.setVelocity(p.getVelocity().setY(.85));
            getServer()
                    .getScheduler()
                    .runTaskLater(
                            this,
                            () -> {
                                if (p.isOnline() && p.getVelocity().getY() < .15)
                                    p.setVelocity(p.getVelocity().setY(0));
                            },
                            8);
            msg(p, "HANGTIME armed.", NamedTextColor.AQUA);
        } else {
            items.setInt(item, "rebound", 1);
            msg(p, "REBOUND armed.", NamedTextColor.AQUA);
        }
        items.status(item, "Impact: " + "◆".repeat(v - cost) + "◇".repeat(3 - v + cost));
    }

    private void disarray(Player p, ItemStack item) {
        if (!ready(p, "disarray", getConfig().getInt("disarray.cooldown-seconds"))) return;
        Player t = target(p, getConfig().getDouble("disarray.range"));
        if (t == null) {
            refund(p, "disarray");
            msg(p, "No target in range.", NamedTextColor.RED);
            return;
        }
        if (System.currentTimeMillis() < authorityOverride.getOrDefault(t.getUniqueId(), 0L)) {
            authorityOverride.remove(t.getUniqueId());
            msg(t, "OVERRIDDEN.", NamedTextColor.GOLD);
            return;
        }
        PlayerInventory inv = t.getInventory();
        List<ItemStack> movable = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack x = inv.getItem(i);
            if (x != null && items.type(x) == null) {
                movable.add(x.clone());
                slots.add(i);
                inv.setItem(i, null);
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() != Material.AIR && items.type(off) == null) {
            movable.add(off.clone());
            slots.add(40);
            inv.setItemInOffHand(null);
        }
        Collections.shuffle(slots);
        for (int i = 0; i < movable.size(); i++) inv.setItem(slots.get(i), movable.get(i));
        msg(t, "Your inventory was thrown into DISARRAY.", NamedTextColor.RED);
    }

    private void transpose(Player p, ItemStack item) {
        Player target = target(p, 10);
        if (target == null || target == p) {
            msg(p, "Aim at a player within 10 blocks.", NamedTextColor.RED);
            return;
        }
        if (!ready(p, "transposition", getConfig().getInt("transposition.cooldown-seconds")))
            return;
        Location playerLocation = p.getLocation().clone();
        Location targetLocation = target.getLocation().clone();
        p.teleportAsync(targetLocation);
        target.teleportAsync(playerLocation);
        p.getWorld()
                .spawnParticle(
                        Particle.PORTAL, targetLocation.clone().add(0, 1, 0), 35, .6, 1, .6, .15);
        target.getWorld()
                .spawnParticle(
                        Particle.PORTAL, playerLocation.clone().add(0, 1, 0), 35, .6, 1, .6, .15);
        items.status(item, "Swap: " + target.getName(), "Range: 10 blocks");
        msg(p, "TRANSPOSED WITH " + target.getName(), NamedTextColor.LIGHT_PURPLE);
    }

    private void bindEffigy(Player p, ItemStack item, PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && items.get(item, "bound") != null) {
            pendingEffigies.put(p.getUniqueId(), items.get(item, "bound"));
            return;
        }
        Player t = target(p, 15);
        if (t == null) {
            event.setCancelled(true);
            msg(p, "Look at a player to bind Effigy.", NamedTextColor.RED);
            return;
        }
        event.setCancelled(true);
        items.set(item, "bound", t.getUniqueId().toString());
        items.status(item, "Bound: " + t.getName());
        msg(t, "YOUR EFFIGY HAS BEEN BOUND", NamedTextColor.DARK_RED);
    }

    @EventHandler(ignoreCancelled = true)
    public void armorStand(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getPersistentDataContainer().has(effigyTarget))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void entityPlace(EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ArmorStand stand) || !(e.getPlayer() instanceof Player p))
            return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        String target =
                items.is(hand, Mythic.EFFIGY)
                        ? items.get(hand, "bound")
                        : pendingEffigies.remove(p.getUniqueId());
        if (target == null) {
            e.setCancelled(true);
            return;
        }
        stand.getPersistentDataContainer()
                .set(effigyOwner, PersistentDataType.STRING, p.getUniqueId().toString());
        stand.getPersistentDataContainer().set(effigyTarget, PersistentDataType.STRING, target);
        Player t = Bukkit.getPlayer(UUID.fromString(target));
        if (t != null) stand.getEquipment().setArmorContents(t.getInventory().getArmorContents());
        EffigyLink link = new EffigyLink(stand, UUID.fromString(target), p.getUniqueId());
        effigies.put(stand.getUniqueId(), link);
        getServer()
                .getScheduler()
                .runTaskLater(
                        this,
                        () -> sever(link),
                        getConfig().getLong("effigy.duration-seconds") * 20);
    }

    private void sever(EffigyLink link) {
        if (effigies.remove(link.stand.getUniqueId()) == null) return;
        link.stand.remove();
        Player owner = Bukkit.getPlayer(link.owner);
        if (owner != null) {
            Map<Integer, ItemStack> overflow =
                    owner.getInventory().addItem(items.create(Mythic.EFFIGY));
            overflow.values()
                    .forEach(item -> owner.getWorld().dropItemNaturally(owner.getLocation(), item));
            msg(owner, "Effigy returned.", NamedTextColor.GRAY);
        }
        Player t = Bukkit.getPlayer(link.target);
        if (t != null) msg(t, "LINK SEVERED.", NamedTextColor.GRAY);
    }

    private void scripture(Player p, ItemStack item) {
        ItemStack book = item.clone();
        book.setType(Material.WRITTEN_BOOK);
        BookMeta m = (BookMeta) book.getItemMeta();
        m.title(Component.text("The Scripture", NamedTextColor.DARK_PURPLE));
        m.author(Component.text("The Nameless Scribe", NamedTextColor.DARK_GRAY));
        m.pages(
                List.of(
                        scriptureCover(),
                        bodyPageOne(),
                        bodyPageTwo(),
                        dominionPageOne(),
                        dominionPageTwo(),
                        gatewayPage(),
                        corruptedPage()));
        book.setItemMeta(m);
        p.openBook(book);
        msg(p, "Click a colored inscription to invoke it.", NamedTextColor.LIGHT_PURPLE);
    }

    private Component scriptureCover() {
        return bookHeading("[ SCRIPTURE ]", NamedTextColor.DARK_PURPLE)
                .append(Component.text("\n\nA covenant written in\n", NamedTextColor.DARK_GRAY))
                .append(Component.text("blood, hunger, and memory.\n\n", NamedTextColor.GRAY))
                .append(Component.text("[ Body ]\n", NamedTextColor.DARK_RED))
                .append(Component.text("[ Dominion ]\n", NamedTextColor.DARK_BLUE))
                .append(Component.text("[ Gateway ]\n\n", NamedTextColor.DARK_PURPLE))
                .append(
                        Component.text(
                                "Colored inscriptions\ncan be clicked.", NamedTextColor.DARK_GRAY));
    }

    private Component bodyPageOne() {
        return bookHeading("[ BODY I ]", NamedTextColor.DARK_RED)
                .append(Component.text("\n\n"))
                .append(
                        spell(
                                "VITALITY",
                                "vitality",
                                NamedTextColor.RED,
                                "Spend 5 levels to restore 4 hearts."))
                .append(
                        spell(
                                "KNOWLEDGE",
                                "knowledge",
                                NamedTextColor.GOLD,
                                "Gain 15 levels, but fall to half a heart and one hunger."))
                .append(
                        spell(
                                "MOMENTUM",
                                "momentum",
                                NamedTextColor.GREEN,
                                "Spend 8 hunger for an upward launch and slow falling."));
    }

    private Component bodyPageTwo() {
        return bookHeading("[ BODY II ]", NamedTextColor.DARK_RED)
                .append(Component.text("\n\n"))
                .append(
                        spell(
                                "WARD",
                                "ward",
                                NamedTextColor.AQUA,
                                "Sacrifice 3 hearts to reduce the next major hit."))
                .append(
                        spell(
                                "PURIFY",
                                "purify",
                                NamedTextColor.BLUE,
                                "Spend 4 levels to cleanse harmful effects."))
                .append(
                        Component.text(
                                "\n\nThe body remembers\nevery price it pays.",
                                NamedTextColor.DARK_GRAY));
    }

    private Component dominionPageOne() {
        return bookHeading("[ DOMINION I ]", NamedTextColor.DARK_BLUE)
                .append(Component.text("\n\n"))
                .append(
                        spell(
                                "RUPTURE",
                                "rupture",
                                NamedTextColor.DARK_RED,
                                "Sacrifice 4 hearts to damage nearby enemies."))
                .append(
                        spell(
                                "SILENCE",
                                "silence",
                                NamedTextColor.DARK_PURPLE,
                                "Spend health and levels to suppress a target's Mythics."))
                .append(
                        spell(
                                "SUMMON",
                                "summon",
                                NamedTextColor.BLUE,
                                "Sacrifice 6 hearts to pull the targeted player to you."));
    }

    private Component dominionPageTwo() {
        return bookHeading("[ DOMINION II ]", NamedTextColor.DARK_BLUE)
                .append(Component.text("\n\n"))
                .append(
                        spell(
                                "DECAY",
                                "decay",
                                NamedTextColor.DARK_GREEN,
                                "Spend 5 levels to weaken a target's recovery."))
                .append(
                        spell(
                                "EXTRACT",
                                "extract",
                                NamedTextColor.DARK_AQUA,
                                "Spend 6 hunger to steal a positive effect."))
                .append(
                        Component.text(
                                "\n\nPower taken is never\npower freely given.",
                                NamedTextColor.DARK_GRAY));
    }

    private Component gatewayPage() {
        Component bars = Component.text(">▌▌▌▌▌▌▌▌<\n", NamedTextColor.DARK_PURPLE);
        return bookHeading("[ GATEWAY ]", NamedTextColor.DARK_PURPLE)
                .append(Component.text("\n\n"))
                .append(bars)
                .append(bars)
                .append(bars)
                .append(bars)
                .append(bars)
                .append(
                        Component.text("\n[ OPEN GATEWAY ]", NamedTextColor.LIGHT_PURPLE)
                                .decorate(TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/mythic gateway"))
                                .hoverEvent(
                                        HoverEvent.showText(
                                                Component.text(
                                                        "Choose a dimension. Experience levels are required.",
                                                        NamedTextColor.YELLOW))));
    }

    private Component corruptedPage() {
        Component corruption =
                Component.text(
                                "THE VEIL REMEMBERS\n▓▒░ ███ ░▒▓\nTHE NAME BELOW\n████ ▒▒ ████\n",
                                NamedTextColor.BLACK)
                        .decorate(TextDecoration.OBFUSCATED);
        return bookHeading("[ PAGE III ]", NamedTextColor.BLACK)
                .append(Component.text("\n\n"))
                .append(corruption)
                .append(
                        Component.text("\n[ UNKNOWN RUPTURE ]", NamedTextColor.DARK_PURPLE)
                                .decorate(TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/mythic cast unknown_rupture"))
                                .hoverEvent(
                                        HoverEvent.showText(
                                                Component.text(
                                                        "The cost is hidden.",
                                                        NamedTextColor.RED))));
    }

    private Component bookHeading(String text, NamedTextColor color) {
        return Component.text("      " + text, color).decorate(TextDecoration.BOLD);
    }

    private Component spell(String label, String command, NamedTextColor color, String hover) {
        return Component.text("[ " + label + " ]\n\n", color)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/mythic cast " + command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));
    }

    private void horizon(Player p, ItemStack item, boolean shift) {
        String key = shift ? "refraction" : "zero";
        if (!ready(p, key, getConfig().getInt("event-horizon.cooldown-seconds"))) return;
        long until =
                System.currentTimeMillis()
                        + getConfig().getLong("event-horizon.duration-seconds") * 1000;
        if (shift) {
            refraction.put(p.getUniqueId(), until);
            msg(p, "REFRACTION", NamedTextColor.AQUA);
        } else {
            zero.put(p.getUniqueId(), until);
            msg(p, "ZERO", NamedTextColor.DARK_PURPLE);
        }
    }

    private void resonator(Player player, ItemStack item, boolean slam) {
        if (slam) resonatorSlam(player, item);
        else chargeResonatorBeam(player, item);
    }

    private void chargeResonatorBeam(Player player, ItemStack item) {
        if (resonatorCharging.contains(player.getUniqueId())) return;
        if (!ready(player, "resonator-beam", getConfig().getInt("resonator.beam-cooldown-seconds")))
            return;
        resonatorCharging.add(player.getUniqueId());
        List<BlockDisplay> formation = new ArrayList<>();
        for (int block = 0; block < 4; block++) {
            BlockDisplay display = createResonatorBlock(player.getEyeLocation());
            formation.add(display);
            resonatorDisplays.add(display);
        }
        items.status(item, "BEAM: CHARGING");
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline()
                        || player.isDead()
                        || items.type(player.getInventory().getItemInMainHand())
                                != Mythic.RESONATOR) {
                    formation.forEach(VanillaSMP.this::removeResonatorDisplay);
                    resonatorCharging.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                Vector forward = player.getEyeLocation().getDirection().normalize();
                Vector right = new Vector(forward.getZ(), 0, -forward.getX());
                if (right.lengthSquared() < .01) right = new Vector(1, 0, 0);
                right.normalize();
                Vector screenUp = forward.clone().crossProduct(right).normalize();
                Location center = player.getEyeLocation().add(forward.multiply(3.0));
                double rotation = tick * .18;
                for (int index = 0; index < formation.size(); index++) {
                    double angle = rotation + index * Math.PI / 2;
                    Vector offset =
                            right.clone()
                                    .multiply(Math.cos(angle) * 1.18)
                                    .add(screenUp.clone().multiply(Math.sin(angle) * 1.18));
                    Location position = center.clone().add(offset).add(-.5, -.5, -.5);
                    position.setYaw(tick * 20f + index * 72f);
                    position.setPitch(tick * 13f + index * 25f);
                    formation.get(index).teleport(position);
                }
                player.getWorld()
                        .spawnParticle(
                                Particle.DUST,
                                center,
                                7,
                                .17,
                                .17,
                                .17,
                                0,
                                new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.55f));
                if (tick % 5 == 0)
                    player.getWorld()
                            .playSound(
                                    center, Sound.BLOCK_COMPARATOR_CLICK, .55f, .7f + tick / 45f);
                if (++tick >= getConfig().getInt("resonator.beam-charge-ticks")) {
                    Location release = center.clone();
                    formation.forEach(VanillaSMP.this::removeResonatorDisplay);
                    player.getWorld()
                            .spawnParticle(
                                    Particle.DUST,
                                    release,
                                    65,
                                    .9,
                                    .9,
                                    .9,
                                    .05,
                                    new Particle.DustOptions(Color.fromRGB(255, 15, 5), 1.45f));
                    player.getWorld()
                            .spawnParticle(Particle.ELECTRIC_SPARK, release, 25, .7, .7, .7, .12);
                    resonatorCharging.remove(player.getUniqueId());
                    fireResonatorBeam(player, item);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private BlockDisplay createResonatorBlock(Location location) {
        return location.getWorld()
                .spawn(
                        location,
                        BlockDisplay.class,
                        display -> {
                            display.setBlock(Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                            display.setGlowing(true);
                            display.setGlowColorOverride(Color.RED);
                            display.setTeleportDuration(1);
                        });
    }

    private void fireResonatorBeam(Player player, ItemStack item) {
        resonatorFiring.add(player.getUniqueId());
        items.status(item, "BEAM: FIRING • 3.5s");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1.1f, .7f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                int duration = getConfig().getInt("resonator.beam-duration-ticks");
                if (!player.isOnline()
                        || player.isDead()
                        || tick >= duration
                        || items.type(player.getInventory().getItemInMainHand())
                                != Mythic.RESONATOR) {
                    resonatorFiring.remove(player.getUniqueId());
                    if (player.isOnline()) items.status(item, "BEAM: DISCHARGED");
                    cancel();
                    return;
                }
                renderResonatorBeam(player, tick++);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void renderResonatorBeam(Player player, int tick) {
        Location start =
                player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(.65));
        Vector direction = start.getDirection().normalize();
        double range = getConfig().getDouble("resonator.beam-range");
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(start, direction, range);
        double length =
                blockHit == null
                        ? range
                        : Math.max(.5, blockHit.getHitPosition().distance(start.toVector()));
        RayTraceResult entityHit =
                player.getWorld()
                        .rayTraceEntities(
                                start,
                                direction,
                                length,
                                .7,
                                entity -> entity instanceof LivingEntity && entity != player);
        if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity victim) {
            length = entityHit.getHitPosition().distance(start.toVector());
            int interval = getConfig().getInt("resonator.beam-damage-interval-ticks");
            if (tick % Math.max(1, interval) == 0) {
                victim.damage(getConfig().getDouble("resonator.beam-damage-per-pulse"), player);
                victim.setVelocity(direction.clone().multiply(.24).setY(.08));
            }
        }
        drawSignalBeam(start, direction, length);
        if (tick % 10 == 0)
            player.getWorld().playSound(start, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .7f, 1.6f);
    }

    private void drawSignalBeam(Location start, Vector direction, double length) {
        Particle.DustOptions outer = new Particle.DustOptions(Color.fromRGB(130, 0, 0), 1.8f);
        Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(255, 25, 10), 1.05f);
        for (double distance = 0; distance <= length; distance += .32) {
            Location point = start.clone().add(direction.clone().multiply(distance));
            start.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, outer);
            start.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, core);
            if (((int) (distance * 10)) % 16 == 0)
                start.getWorld()
                        .spawnParticle(Particle.ELECTRIC_SPARK, point, 2, .08, .08, .08, .01);
        }
    }

    private void resonatorSlam(Player player, ItemStack item) {
        if (!ready(player, "resonator-slam", getConfig().getInt("resonator.slam-cooldown-seconds")))
            return;
        resonatorFallProtection.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
        player.setFallDistance(0);
        player.setVelocity(
                new Vector(0, getConfig().getDouble("resonator.slam-launch-velocity"), 0));
        items.status(item, "REDSTONE SLAM: ACTIVE");
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || tick > 100) {
                    cancel();
                    return;
                }
                player.setFallDistance(0);
                player.getWorld()
                        .spawnParticle(
                                Particle.DUST,
                                player.getLocation().add(0, .5, 0),
                                10,
                                .35,
                                .5,
                                .35,
                                0,
                                new Particle.DustOptions(Color.RED, 1.1f));
                if (tick == 18) player.setVelocity(new Vector(0, -2.35, 0));
                if (tick > 20 && player.isOnGround()) {
                    resonatorImpact(player);
                    cancel();
                    return;
                }
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void resonatorImpact(Player owner) {
        Location impact = owner.getLocation();
        owner.setFallDistance(0);
        resonatorFallProtection.put(owner.getUniqueId(), System.currentTimeMillis() + 3000L);
        owner.getWorld().spawnParticle(Particle.EXPLOSION, impact, 3, 1.2, .2, 1.2, 0);
        owner.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, .65f);
        double radius = getConfig().getDouble("resonator.slam-radius");
        for (Player target : owner.getWorld().getNearbyPlayers(impact, radius))
            if (target != owner && !target.isDead())
                eruptRedstone(target.getLocation(), owner, target);
    }

    private void eruptRedstone(Location targetLocation, Player owner, Player target) {
        Location ground = targetLocation.clone();
        for (int depth = 0;
                depth < 6 && !ground.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
                depth++) ground.subtract(0, 1, 0);
        ground.setY(Math.floor(ground.getY()) - 1.2);
        BlockDisplay spike =
                ground.getWorld()
                        .spawn(
                                ground,
                                BlockDisplay.class,
                                display -> {
                                    display.setBlock(
                                            Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                                    display.setGlowing(true);
                                    display.setGlowColorOverride(Color.RED);
                                    display.setTeleportDuration(2);
                                });
        resonatorDisplays.add(spike);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!spike.isValid() || tick > 14) {
                    removeResonatorDisplay(spike);
                    cancel();
                    return;
                }
                Location next = ground.clone().add(0, Math.min(2.2, tick * .28), 0);
                next.setYaw(tick * 22f);
                spike.teleport(next);
                if (tick == 5 && target.isOnline() && !target.isDead()) {
                    target.damage(getConfig().getDouble("resonator.slam-damage"), owner);
                    target.setVelocity(target.getVelocity().setY(.85));
                }
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void removeResonatorDisplay(BlockDisplay display) {
        resonatorDisplays.remove(display);
        display.remove();
    }

    private void parallaxBash(Player player, ItemStack shield) {
        if (!ready(player, "parallax-bash", 9)) return;
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector dash = direction.clone().multiply(1.45);
        dash.setY(Math.max(.12, Math.min(.3, dash.getY())));
        player.setVelocity(dash);
        player.setFallDistance(0);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, .7f);
        items.status(shield, "360° GUARD", "Shield Bash: USED");
        Set<UUID> struck = new HashSet<>();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || tick++ >= 8) {
                    cancel();
                    return;
                }
                Location center = player.getLocation().add(0, .9, 0);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 3, .45, .35, .45, 0);
                player.getWorld().spawnParticle(Particle.CLOUD, center, 7, .35, .25, .35, .03);
                for (Player target :
                        player.getWorld().getNearbyPlayers(player.getLocation(), 2.1)) {
                    if (target == player || target.isDead() || !struck.add(target.getUniqueId()))
                        continue;
                    Vector outward =
                            target.getLocation()
                                    .toVector()
                                    .subtract(player.getLocation().toVector());
                    if (outward.lengthSquared() < .01) outward = direction.clone();
                    Vector knockback =
                            direction
                                    .clone()
                                    .multiply(1.15)
                                    .add(outward.normalize().multiply(.55))
                                    .setY(.48);
                    target.damage(2.0, player);
                    target.setVelocity(knockback);
                    target.getWorld()
                            .spawnParticle(
                                    Particle.CRIT,
                                    target.getLocation().add(0, 1, 0),
                                    20,
                                    .5,
                                    .6,
                                    .5,
                                    .12);
                    target.getWorld()
                            .playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.1f, .55f);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void authority(Player p, ItemStack item, boolean shift) {
        int mode = items.getInt(item, "mode", 0);
        if (shift) {
            mode = (mode + 1) % 4;
            items.setInt(item, "mode", mode);
            items.status(
                    item,
                    "Mode: " + new String[] {"DENY", "PERMIT", "OVERRIDE", "JUDGEMENT"}[mode]);
            return;
        }
        if (mode == 3) {
            if (!ready(p, "authority-judgement", 75)) return;
            authorityJudgement.put(p.getUniqueId(), System.currentTimeMillis() + 8000L);
            items.status(item, "Mode: JUDGEMENT", "Next hit: ARMED");
            msg(p, "JUDGEMENT: your next hit calls the storm.", NamedTextColor.GOLD);
        } else if (mode == 2) {
            if (!ready(p, "authority", 180)) return;
            authorityOverride.put(p.getUniqueId(), System.currentTimeMillis() + 5000);
            msg(p, "OVERRIDE: your next Mythic conflict wins.", NamedTextColor.GOLD);
        } else if (mode == 0) {
            if (!ready(p, "authority-deny", 45)) return;
            for (Player near : p.getWorld().getNearbyPlayers(p.getLocation(), 12))
                if (near != p) silenced.put(near.getUniqueId(), System.currentTimeMillis() + 4000);
            msg(p, "DENY: MYTHIC ACTIVATION", NamedTextColor.RED);
        } else {
            silenced.remove(p.getUniqueId());
            msg(p, "PERMIT: restriction bypass granted.", NamedTextColor.GREEN);
        }
    }

    private void strikeJudgement(Player attacker, Player target) {
        msg(attacker, "JUDGEMENT DESCENDS", NamedTextColor.YELLOW);
        for (int strike = 0; strike < 4; strike++) {
            getServer()
                    .getScheduler()
                    .runTaskLater(
                            this,
                            () -> {
                                if (!target.isOnline() || target.isDead()) return;
                                target.getWorld().strikeLightningEffect(target.getLocation());
                                target.getWorld()
                                        .spawnParticle(
                                                Particle.FLASH,
                                                target.getLocation().add(0, 1, 0),
                                                1);
                                target.setHealth(Math.max(0, target.getHealth() - 4.0));
                            },
                            strike * 10L);
        }
    }

    private void onslaught(Player p, Player target, EntityDamageByEntityEvent e) {
        long now = System.currentTimeMillis();
        Combo c = combos.get(p.getUniqueId());
        if (c == null || !c.target.equals(target.getUniqueId()) || now - c.last > 3000)
            c = new Combo(target.getUniqueId(), 0, now);
        c.hits++;
        c.last = now;
        combos.put(p.getUniqueId(), c);
        if (c.hits == 3) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 35, 0));
        if (c.hits == 5) e.setDamage(e.getDamage() + 2);
        if (c.hits >= 10) {
            e.setDamage(e.getDamage() + 5);
            p.getWorld()
                    .spawnParticle(
                            Particle.SWEEP_ATTACK,
                            target.getLocation().add(0, 1, 0),
                            18,
                            .7,
                            .5,
                            .7,
                            0);
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, 1));
            c.hits = 0;
        }
        OnslaughtMode mode = onslaughtModes.getOrDefault(p.getUniqueId(), OnslaughtMode.BLOOD_DASH);
        items.status(
                p.getInventory().getItemInMainHand(), "Mode: " + mode.display, "Combo: ×" + c.hits);
    }

    private void activateOnslaught(Player player, ItemStack sword) {
        OnslaughtMode mode =
                onslaughtModes.getOrDefault(player.getUniqueId(), OnslaughtMode.BLOOD_DASH);
        if (mode == OnslaughtMode.BLOOD_DASH) bloodDash(player, sword);
        else cleave(player, sword);
    }

    private void bloodDash(Player player, ItemStack sword) {
        if (!ready(player, "onslaught-blood-dash", 8)) return;
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector velocity = direction.multiply(1.55);
        velocity.setY(Math.max(.12, Math.min(.45, velocity.getY())));
        player.setVelocity(velocity);
        player.setFallDistance(0);
        Particle.DustOptions blood = new Particle.DustOptions(Color.fromRGB(150, 0, 20), 1.35f);
        for (int tick = 0; tick < 8; tick++) {
            getServer()
                    .getScheduler()
                    .runTaskLater(
                            this,
                            () -> {
                                if (!player.isOnline()) return;
                                Location trail = player.getLocation().add(0, .8, 0);
                                player.getWorld()
                                        .spawnParticle(
                                                Particle.DUST, trail, 12, .35, .35, .35, 0, blood);
                                player.getWorld()
                                        .spawnParticle(
                                                Particle.SWEEP_ATTACK, trail, 2, .3, .2, .3, 0);
                            },
                            tick);
        }
        items.status(sword, "Mode: BLOOD DASH", "Dash: USED");
        msg(player, "BLOOD DASH", NamedTextColor.DARK_RED);
    }

    private void cleave(Player player, ItemStack sword) {
        Player victim = target(player, 9);
        if (victim == null || victim == player) {
            msg(player, "Aim at a player within 9 blocks.", NamedTextColor.RED);
            return;
        }
        if (!ready(player, "onslaught-cleave", 12)) return;
        items.status(sword, "Mode: CLEAVE", "Target: " + victim.getName());
        msg(player, "CLEAVE: " + victim.getName(), NamedTextColor.RED);
        for (int slash = 0; slash < 6; slash++) {
            int step = slash;
            getServer()
                    .getScheduler()
                    .runTaskLater(this, () -> performCleaveSlash(player, victim, step), slash * 3L);
        }
    }

    private void performCleaveSlash(Player attacker, Player victim, int step) {
        if (!attacker.isOnline()
                || !victim.isOnline()
                || victim.isDead()
                || attacker.getWorld() != victim.getWorld()
                || attacker.getLocation().distanceSquared(victim.getLocation()) > 144) return;
        Location center = victim.getLocation().add(0, 1, 0);
        double angle = step * Math.PI / 3.0;
        Vector offset = new Vector(Math.cos(angle), (step % 2 == 0 ? .35 : -.25), Math.sin(angle));
        Location start = center.clone().add(offset);
        Location end = center.clone().subtract(offset);
        lineSlash(start, end);
        victim.damage(1.0, attacker);
        victim.getWorld()
                .playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, .55f, 1.15f + step * .06f);
    }

    private void lineSlash(Location start, Location end) {
        Vector step = end.toVector().subtract(start.toVector()).multiply(1.0 / 10.0);
        Location point = start.clone();
        for (int i = 0; i <= 10; i++) {
            start.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
            if (i % 5 == 0)
                start.getWorld().spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
            point.add(step);
        }
    }

    private void addStar(UUID owner, Location l) {
        List<Location> points = stars.computeIfAbsent(owner, k -> new ArrayList<>());
        StarMode mode = starModes.getOrDefault(owner, StarMode.TRIAD);
        StarGuide guide = starGuides.get(owner);
        if (guide != null && guide.until > System.currentTimeMillis()) {
            Location shotLocation = l;
            List<Location> shots = starShots.computeIfAbsent(owner, ignored -> new ArrayList<>());
            double minimumDistance =
                    getConfig().getDouble("starfall.minimum-shot-separation-blocks");
            double maximumDistance =
                    getConfig().getDouble("starfall.maximum-shot-separation-blocks");
            if (shots.stream()
                    .anyMatch(
                            shot ->
                                    shot.getWorld() == shotLocation.getWorld()
                                            && shot.distanceSquared(shotLocation)
                                                    < minimumDistance * minimumDistance)) {
                Player player = Bukkit.getPlayer(owner);
                if (player != null)
                    msg(player, "Shoot a different spot to add the next star.", NamedTextColor.RED);
                return;
            }
            if (shots.stream()
                    .anyMatch(
                            shot ->
                                    shot.getWorld() != shotLocation.getWorld()
                                            || shot.distanceSquared(shotLocation)
                                                    > maximumDistance * maximumDistance)) {
                Player player = Bukkit.getPlayer(owner);
                if (player != null)
                    msg(
                            player,
                            "Keep every constellation shot within " + maximumDistance + " blocks.",
                            NamedTextColor.RED);
                return;
            }
            shots.add(shotLocation.clone());
            l = guide.nodes.get(Math.min(points.size(), guide.nodes.size() - 1)).clone();
        }
        if (guide == null && points.size() >= 3 && points.getFirst().distance(l) < 3) {
            List<Location> shape = new ArrayList<>(points);
            constellations.add(new Constellation(owner, shape, constellationExpiry()));
            stars.remove(owner);
            starGuides.remove(owner);
            starShots.remove(owner);
            Player p = Bukkit.getPlayer(owner);
            if (p != null) msg(p, "CONSTELLATION CREATED", NamedTextColor.YELLOW);
            return;
        }
        if (points.size() >= 8) points.clear();
        points.add(l);
        if (points.size() >= mode.points) {
            constellations.add(
                    new Constellation(owner, new ArrayList<>(points), constellationExpiry()));
            stars.remove(owner);
            starGuides.remove(owner);
            starShots.remove(owner);
            Player creator = Bukkit.getPlayer(owner);
            if (creator != null)
                msg(creator, mode.display + " CONSTELLATION CREATED", NamedTextColor.YELLOW);
            return;
        }
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            ItemStack star = find(p, Mythic.STARFALL);
            if (star != null)
                items.status(
                        star,
                        "Mode: " + mode.display,
                        "Shots: " + points.size() + " / " + mode.points);
        }
    }

    private long constellationExpiry() {
        return System.currentTimeMillis()
                + (long) (getConfig().getDouble("starfall.constellation-duration-seconds") * 1000);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        silenced.entrySet().removeIf(x -> x.getValue() < now);
        zero.entrySet().removeIf(x -> x.getValue() < now);
        refraction.entrySet().removeIf(x -> x.getValue() < now);
        authorityOverride.entrySet().removeIf(x -> x.getValue() < now);
        authorityJudgement.entrySet().removeIf(x -> x.getValue() < now);
        starGuides
                .entrySet()
                .removeIf(
                        entry -> {
                            boolean expired = entry.getValue().until < now;
                            if (expired) {
                                starShots.remove(entry.getKey());
                                stars.remove(entry.getKey());
                            }
                            return expired;
                        });
        constellations.removeIf(c -> c.until < now);
        for (TimeStop stop : new ArrayList<>(timeStops.values())) {
            if (now >= stop.endsAt) {
                endTimeStop(stop);
                continue;
            }
            for (UUID id : new HashSet<>(stop.entities)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && isTimeStopped(player)) {
                    player.setVelocity(new Vector());
                    player.setGravity(false);
                }
            }
            for (UUID id : stop.nonPlayers.keySet()) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.setVelocity(new Vector());
                    entity.setGravity(false);
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (now < zero.getOrDefault(p.getUniqueId(), 0L)) {
                p.getWorld()
                        .spawnParticle(
                                Particle.PORTAL, p.getLocation().add(0, 1, 0), 5, 2, 1, 2, 0);
                for (Entity en : p.getNearbyEntities(4, 3, 4))
                    if (en instanceof Projectile) repel(en, p.getLocation(), .35);
            }
            if (now < refraction.getOrDefault(p.getUniqueId(), 0L))
                for (Player near : p.getWorld().getNearbyPlayers(p.getLocation(), 2.5))
                    if (near != p) {
                        double a = Math.random() * Math.PI * 2;
                        Location dest = p.getLocation().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
                        dest.setY(p.getWorld().getHighestBlockYAt(dest) + 1);
                        near.teleportAsync(dest);
                    }
        }
        drawStars();
    }

    private void drawStars() {
        Particle.DustOptions guideDust =
                new Particle.DustOptions(Color.fromRGB(75, 210, 255), .75f);
        if (Bukkit.getCurrentTick() % 4 == 0) {
            for (Map.Entry<UUID, StarGuide> entry : starGuides.entrySet()) {
                StarGuide guide = entry.getValue();
                int filled = stars.getOrDefault(entry.getKey(), List.of()).size();
                for (int i = 0; i < guide.nodes.size(); i++) {
                    Location node = guide.nodes.get(i);
                    particleLine(
                            node,
                            guide.nodes.get((i + 1) % guide.nodes.size()),
                            guideDust,
                            Particle.WAX_ON);
                    node.getWorld()
                            .spawnParticle(
                                    i < filled ? Particle.ENCHANT : Particle.FIREWORK,
                                    node,
                                    2,
                                    .12,
                                    .08,
                                    .12,
                                    .01);
                }
            }
        }
        for (List<Location> pts : stars.values())
            for (int i = 1; i < pts.size(); i++) line(pts.get(i - 1), pts.get(i));
        for (Constellation c : constellations) {
            for (int i = 0; i < c.points.size(); i++)
                line(c.points.get(i), c.points.get((i + 1) % c.points.size()));
            for (Player p : c.points.getFirst().getWorld().getPlayers())
                if (!p.getUniqueId().equals(c.owner) && inside(p.getLocation(), c.points)) {
                    double trueDamage =
                            getConfig().getDouble("starfall.constellation-true-damage-per-tick");
                    p.setHealth(Math.max(0, p.getHealth() - trueDamage));
                    if (Bukkit.getCurrentTick() % 2 == 0) {
                        p.playHurtAnimation(0);
                        p.getWorld()
                                .spawnParticle(
                                        Particle.DUST,
                                        p.getLocation().add(0, 1, 0),
                                        5,
                                        .3,
                                        .65,
                                        .3,
                                        0,
                                        new Particle.DustOptions(Color.fromRGB(255, 15, 10), 1.1f));
                    }
                    Vector center = center(c.points).toVector();
                    p.setVelocity(
                            center.subtract(p.getLocation().toVector()).normalize().multiply(.12));
                }
        }
    }

    private void line(Location a, Location b) {
        if (a.getWorld() != b.getWorld()) return;
        Vector d = b.toVector().subtract(a.toVector());
        int n = Math.max(1, (int) (d.length() * 4));
        d.multiply(1d / n);
        Location x = a.clone();
        Particle.DustOptions starlight =
                new Particle.DustOptions(Color.fromRGB(120, 95, 255), 1.05f);
        for (int i = 0; i < n; i++) {
            Location ground = x.clone().add(0, .12, 0);
            a.getWorld().spawnParticle(Particle.DUST, ground, 1, 0, 0, 0, 0, starlight);
            if (i % 8 == 0) a.getWorld().spawnParticle(Particle.END_ROD, ground, 1, 0, 0, 0, 0);
            x.add(d);
        }
    }

    private boolean inside(Location l, List<Location> p) {
        if (l.getWorld() != p.getFirst().getWorld()) return false;
        boolean in = false;
        for (int i = 0, j = p.size() - 1; i < p.size(); j = i++) {
            double xi = p.get(i).getX(),
                    zi = p.get(i).getZ(),
                    xj = p.get(j).getX(),
                    zj = p.get(j).getZ();
            if (((zi > l.getZ()) != (zj > l.getZ()))
                    && (l.getX() < (xj - xi) * (l.getZ() - zi) / (zj - zi) + xi)) in = !in;
        }
        return in;
    }

    private boolean nearBoundary(Location l, List<Location> p) {
        for (int i = 0; i < p.size(); i++)
            if (distanceSegment(l, p.get(i), p.get((i + 1) % p.size())) < .8) return true;
        return false;
    }

    private double distanceSegment(Location q, Location a, Location b) {
        Vector ab = b.toVector().subtract(a.toVector()), aq = q.toVector().subtract(a.toVector());
        double t = Math.max(0, Math.min(1, aq.dot(ab) / Math.max(.001, ab.lengthSquared())));
        return a.toVector().add(ab.multiply(t)).distance(q.toVector());
    }

    private Location center(List<Location> ps) {
        Location c = ps.getFirst().clone().zero();
        for (Location l : ps) c.add(l);
        return c.multiply(1d / ps.size());
    }

    public boolean cast(Player p, String spell) {
        if (!items.is(p.getInventory().getItemInMainHand(), Mythic.SCRIPTURE)) return false;
        Player t = target(p, 15);
        switch (spell.toLowerCase()) {
            case "vitality" -> {
                if (p.getLevel() < 5) return false;
                p.setLevel(p.getLevel() - 5);
                p.setHealth(Math.min(maxHealth(p), p.getHealth() + 8));
            }
            case "knowledge" -> {
                p.setHealth(1.0);
                p.setFoodLevel(1);
                p.setSaturation(0);
                p.giveExpLevels(15);
            }
            case "momentum" -> {
                if (p.getFoodLevel() < 8) return false;
                p.setFoodLevel(p.getFoodLevel() - 8);
                p.setVelocity(p.getVelocity().setY(1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
            }
            case "ward" -> {
                if (p.getHealth() <= 6) return false;
                p.setHealth(p.getHealth() - 6);
                wards.put(p.getUniqueId(), System.currentTimeMillis() + 10000);
            }
            case "purify" -> {
                if (p.getLevel() < 4) return false;
                p.setLevel(p.getLevel() - 4);
                p.getActivePotionEffects().stream()
                        .filter(
                                x ->
                                        x.getType().getCategory()
                                                != PotionEffectTypeCategory.BENEFICIAL)
                        .map(PotionEffect::getType)
                        .toList()
                        .forEach(p::removePotionEffect);
            }
            case "rupture" -> {
                if (p.getHealth() <= 8) return false;
                p.setHealth(p.getHealth() - 8);
                for (Entity e : p.getNearbyEntities(5, 3, 5))
                    if (e instanceof LivingEntity le) le.damage(6, p);
            }
            case "unknown_rupture", "unknown-rupture" -> {
                if (t == null
                        || t == p
                        || scripturePockets.containsKey(t.getUniqueId())
                        || p.getHealth() <= 16
                        || p.getLevel() < 25) return false;
                if (!ready(p, "scripture-unknown-rupture", 90)) return false;
                p.setHealth(p.getHealth() - 16);
                p.setLevel(p.getLevel() - 25);
                imprisonInScripturePocket(p, t);
            }
            case "silence" -> {
                if (t == null || p.getHealth() <= 6 || p.getLevel() < 5) return false;
                p.setHealth(p.getHealth() - 6);
                p.setLevel(p.getLevel() - 5);
                silenced.put(
                        t.getUniqueId(),
                        System.currentTimeMillis()
                                + getConfig().getLong("scripture.silence-seconds") * 1000);
            }
            case "summon" -> {
                if (t == null || p.getHealth() <= 12) return false;
                p.setHealth(p.getHealth() - 12);
                t.teleportAsync(p.getLocation());
            }
            case "decay" -> {
                if (t == null || p.getLevel() < 5) return false;
                p.setLevel(p.getLevel() - 5);
                t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 1));
            }
            case "extract" -> {
                if (t == null || p.getFoodLevel() < 6) return false;
                Optional<PotionEffect> effect =
                        t.getActivePotionEffects().stream()
                                .filter(
                                        x ->
                                                x.getType().getCategory()
                                                        == PotionEffectTypeCategory.BENEFICIAL)
                                .findFirst();
                if (effect.isEmpty()) return false;
                p.setFoodLevel(p.getFoodLevel() - 6);
                t.removePotionEffect(effect.get().getType());
                p.addPotionEffect(
                        new PotionEffect(
                                effect.get().getType(),
                                Math.min(200, effect.get().getDuration()),
                                effect.get().getAmplifier()));
            }
            default -> {
                return false;
            }
        }
        msg(p, spell.toUpperCase() + " inscribed.", NamedTextColor.LIGHT_PURPLE);
        return true;
    }

    public boolean handleCommand(
            CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) && args.length == 0) return false;
        if (args.length > 0
                && args[0].equalsIgnoreCase("gateway")
                && sender instanceof Player gatewayPlayer) {
            if (!items.is(gatewayPlayer.getInventory().getItemInMainHand(), Mythic.SCRIPTURE)) {
                msg(gatewayPlayer, "Hold Scripture to open the Gateway.", NamedTextColor.RED);
                return true;
            }
            gatewayMenu.open(gatewayPlayer);
            return true;
        }
        if (args.length > 0
                && args[0].equalsIgnoreCase("menu")
                && sender instanceof Player menuPlayer) {
            if (!menuPlayer.isOp()) {
                menuPlayer.sendMessage(
                        Component.text(
                                "Only server operators can open the Mythic ritual guide.",
                                NamedTextColor.RED));
                return true;
            }
            mythicMenu.open(menuPlayer);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cast") && sender instanceof Player p) {
            if (args.length < 2 || !cast(p, args[1]))
                msg(p, "The inscription failed.", NamedTextColor.RED);
            return true;
        }
        if (!sender.hasPermission("vanillasmp.admin")) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(
                    "Mythics: "
                            + String.join(
                                    ", ",
                                    Arrays.stream(Mythic.values())
                                            .map(x -> x.name().toLowerCase())
                                            .toList()));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("VanillaSMP reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("resetrecipes")) {
            Mythic mythic = null;
            if (args.length > 1 && !args[1].equalsIgnoreCase("all")) {
                mythic = Mythic.parse(args[1]);
                if (mythic == null) {
                    sender.sendMessage("Unknown Mythic. Use a Mythic name or 'all'.");
                    return true;
                }
            }
            if (!ritualManager.resetCraftedRecipes(mythic)) {
                sender.sendMessage(
                        "Wait for the active ritual to finish before resetting recipes.");
                return true;
            }
            sender.sendMessage(
                    mythic == null
                            ? "All one-time Mythic recipes have been reset and registered."
                            : mythic.title + " can now be crafted once again.");
            return true;
        }
        if (args[0].equalsIgnoreCase("giveall")) {
            Player t = args.length > 1 ? Bukkit.getPlayer(args[1]) : (Player) sender;
            if (t == null) return true;
            for (Mythic m : Mythic.values()) t.getInventory().addItem(items.create(m));
            return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 2) {
            Player t = args.length >= 3 ? Bukkit.getPlayer(args[1]) : (Player) sender;
            Mythic m = Mythic.parse(args.length >= 3 ? args[2] : args[1]);
            if (t != null && m != null) t.getInventory().addItem(items.create(m));
            else sender.sendMessage("Unknown player or Mythic.");
            return true;
        }
        return false;
    }

    public List<String> tabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1)
            return List.of("menu", "give", "giveall", "list", "reload", "resetrecipes", "cast");
        if (a.length == 2 && a[0].equalsIgnoreCase("resetrecipes"))
            return java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("all"),
                            Arrays.stream(Mythic.values()).map(x -> x.name().toLowerCase()))
                    .toList();
        if (a.length >= 2 && a[0].equalsIgnoreCase("cast"))
            return List.of(
                    "vitality",
                    "knowledge",
                    "momentum",
                    "ward",
                    "purify",
                    "rupture",
                    "unknown_rupture",
                    "silence",
                    "summon",
                    "decay",
                    "extract");
        if (a.length >= 2)
            return Arrays.stream(Mythic.values()).map(x -> x.name().toLowerCase()).toList();
        return List.of();
    }

    private void imprisonInScripturePocket(Player caster, Player victim) {
        ScripturePocket pocket =
                new ScripturePocket(
                        victim.getUniqueId(),
                        victim.getLocation().clone(),
                        victim.isInvulnerable(),
                        victim.isCollidable(),
                        victim.hasGravity(),
                        victim.getWalkSpeed(),
                        victim.getFlySpeed());
        scripturePockets.put(victim.getUniqueId(), pocket);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == victim) continue;
            viewer.hidePlayer(this, victim);
            victim.hidePlayer(this, viewer);
        }
        victim.setInvulnerable(true);
        victim.setCollidable(false);
        victim.setGravity(false);
        victim.setWalkSpeed(0);
        victim.setFlySpeed(0);
        victim.setVelocity(new Vector());
        int durationTicks = Math.max(1, getConfig().getInt("scripture.pocket-seconds")) * 20;
        victim.addPotionEffect(
                new PotionEffect(PotionEffectType.DARKNESS, durationTicks + 10, 0, false, false));
        victim.addPotionEffect(
                new PotionEffect(PotionEffectType.BLINDNESS, durationTicks + 10, 0, false, false));
        msg(victim, "THE UNKNOWN RUPTURE HAS CLAIMED YOU.", NamedTextColor.DARK_PURPLE);
        msg(caster, victim.getName() + " was sealed beyond the veil.", NamedTextColor.DARK_PURPLE);
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                ScripturePocket active = scripturePockets.get(victim.getUniqueId());
                if (active != pocket || !victim.isOnline() || elapsed >= durationTicks) {
                    if (active == pocket) releaseScripturePocket(pocket);
                    cancel();
                    return;
                }
                victim.teleport(pocket.anchor());
                victim.setVelocity(new Vector());
                victim.spawnParticle(
                        Particle.DUST,
                        victim.getEyeLocation(),
                        80,
                        2.5,
                        1.8,
                        2.5,
                        0,
                        new Particle.DustOptions(Color.fromRGB(0, 0, 0), 2.5f));
                victim.sendActionBar(
                        Component.text(
                                "UNKNOWN RUPTURE  •  "
                                        + Math.max(0, (durationTicks - elapsed + 19) / 20)
                                        + "s",
                                NamedTextColor.DARK_PURPLE));
                elapsed += 2;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void releaseScripturePocket(ScripturePocket pocket) {
        if (!scripturePockets.remove(pocket.victim(), pocket)) return;
        Player victim = Bukkit.getPlayer(pocket.victim());
        if (victim == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == victim) continue;
            viewer.showPlayer(this, victim);
            victim.showPlayer(this, viewer);
        }
        victim.setInvulnerable(pocket.invulnerable());
        victim.setCollidable(pocket.collidable());
        victim.setGravity(pocket.gravity());
        victim.setWalkSpeed(pocket.walkSpeed());
        victim.setFlySpeed(pocket.flySpeed());
        victim.removePotionEffect(PotionEffectType.DARKNESS);
        victim.removePotionEffect(PotionEffectType.BLINDNESS);
        victim.teleport(pocket.anchor());
        victim.sendActionBar(Component.empty());
        msg(victim, "The veil releases you.", NamedTextColor.GRAY);
    }

    private void playUseAnimation(Player player, EquipmentSlot hand, int ticks) {
        getServer()
                .getScheduler()
                .runTask(
                        this,
                        () -> {
                            if (!player.isOnline()) return;
                            player.startUsingItem(hand);
                            getServer()
                                    .getScheduler()
                                    .runTaskLater(
                                            this,
                                            () -> {
                                                if (player.isOnline()
                                                        && items.type(player.getActiveItem())
                                                                == Mythic.RESONATOR) {
                                                    player.clearActiveItem();
                                                }
                                            },
                                            ticks);
                        });
    }

    private void setupBadgeTeams() {
        Scoreboard scoreboard =
                Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        for (Mythic mythic : Mythic.values()) {
            String teamName = "vsmp_m_" + mythic.ordinal();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) team = scoreboard.registerNewTeam(teamName);
            team.suffix(Component.text("  " + mythicIcon(mythic), mythicBadgeColor(mythic)));
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            badgeTeams.put(mythic, team);
        }
    }

    private void updateMythicBadges() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Mythic badge = possessedMythic(player);
            if (displayedBadges.get(player.getUniqueId()) == badge) continue;
            for (Team team : badgeTeams.values()) team.removeEntry(player.getName());
            if (badge == null) {
                displayedBadges.remove(player.getUniqueId());
                player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
                continue;
            }
            Team team = badgeTeams.get(badge);
            if (team != null) team.addEntry(player.getName());
            player.playerListName(
                    Component.text(player.getName(), NamedTextColor.WHITE)
                            .append(
                                    Component.text(
                                            "  " + mythicIcon(badge), mythicBadgeColor(badge))));
            displayedBadges.put(player.getUniqueId(), badge);
        }
        displayedBadges.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private Mythic possessedMythic(Player player) {
        Mythic held = items.type(player.getInventory().getItemInMainHand());
        if (held != null) return held;
        held = items.type(player.getInventory().getItemInOffHand());
        if (held != null) return held;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            Mythic mythic = items.type(item);
            if (mythic != null) return mythic;
        }
        return null;
    }

    private String mythicIcon(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT -> "⚔";
            case EPOCH -> "⌛";
            case SKYFALL -> "🔨";
            case STARFALL -> "🏹";
            case DISARRAY -> "🔀";
            case AUTHORITY -> "◆";
            case TRANSPOSITION -> "🎣";
            case EFFIGY -> "♟";
            case SCRIPTURE -> "📖";
            case EVENT_HORIZON -> "◉";
            case RESONATOR -> "🔴";
            case PARALLAX -> "🛡";
        };
    }

    private NamedTextColor mythicBadgeColor(Mythic mythic) {
        return switch (mythic) {
            case ONSLAUGHT, RESONATOR -> NamedTextColor.RED;
            case EPOCH, SCRIPTURE -> NamedTextColor.GOLD;
            case SKYFALL, PARALLAX -> NamedTextColor.AQUA;
            case STARFALL -> NamedTextColor.LIGHT_PURPLE;
            case DISARRAY -> NamedTextColor.GREEN;
            case AUTHORITY -> NamedTextColor.YELLOW;
            case TRANSPOSITION, EVENT_HORIZON -> NamedTextColor.DARK_PURPLE;
            case EFFIGY -> NamedTextColor.GRAY;
        };
    }

    private void clearBadgeTeams() {
        for (Player player : Bukkit.getOnlinePlayers())
            player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
        for (Team team : new ArrayList<>(badgeTeams.values())) team.unregister();
        badgeTeams.clear();
        displayedBadges.clear();
    }

    private void updateActionBars() {
        updateMythicBadges();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hudPausedUntil.getOrDefault(player.getUniqueId(), 0L) > now) continue;
            ItemStack held = player.getInventory().getItemInMainHand();
            Mythic mythic = items.type(held);
            if (mythic == null) {
                held = player.getInventory().getItemInOffHand();
                mythic = items.type(held);
            }
            if (mythic == null) {
                if (hudVisible.remove(player.getUniqueId()))
                    player.sendActionBar(Component.empty());
                continue;
            }
            items.refreshLegacyLore(held);
            hudVisible.add(player.getUniqueId());
            player.sendActionBar(
                    Component.text(mythic.title, NamedTextColor.GOLD)
                            .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                            .append(
                                    Component.text(
                                            hudStatus(player, held, mythic, now),
                                            NamedTextColor.AQUA)));
        }
    }

    private String hudStatus(Player player, ItemStack item, Mythic mythic, long now) {
        return switch (mythic) {
            case ONSLAUGHT -> {
                Combo combo = combos.get(player.getUniqueId());
                int hits = combo != null && now - combo.last <= 3000 ? combo.hits : 0;
                OnslaughtMode mode =
                        onslaughtModes.getOrDefault(player.getUniqueId(), OnslaughtMode.BLOOD_DASH);
                String key =
                        mode == OnslaughtMode.BLOOD_DASH
                                ? "onslaught-blood-dash"
                                : "onslaught-cleave";
                yield "Mode: "
                        + mode.display
                        + "  •  Combo ×"
                        + hits
                        + "  •  "
                        + cooldownText(player, key);
            }
            case EPOCH -> "Time Stop: " + cooldownText(player, "epoch");
            case SKYFALL -> {
                int charge = impact.getOrDefault(player.getUniqueId(), 0);
                String diamonds = "◆".repeat(charge) + "◇".repeat(3 - charge);
                yield "Impact "
                        + diamonds
                        + (items.getInt(item, "rebound", 0) == 1 ? "  •  Rebound ARMED" : "");
            }
            case STARFALL -> {
                int points = stars.getOrDefault(player.getUniqueId(), List.of()).size();
                StarMode mode = starModes.getOrDefault(player.getUniqueId(), StarMode.TRIAD);
                boolean active =
                        constellations.stream().anyMatch(c -> c.owner.equals(player.getUniqueId()));
                yield "Mode: "
                        + mode.display
                        + "  •  Shots: "
                        + points
                        + " / "
                        + mode.points
                        + "  •  Remaining: "
                        + Math.max(0, mode.points - points)
                        + "  •  "
                        + (active ? "ACTIVE" : "OPEN");
            }
            case DISARRAY -> "Scramble: " + cooldownText(player, "disarray");
            case AUTHORITY -> {
                int mode = items.getInt(item, "mode", 0);
                String modeName = new String[] {"DENY", "PERMIT", "OVERRIDE", "JUDGEMENT"}[mode];
                String cooldownKey =
                        mode == 3
                                ? "authority-judgement"
                                : mode == 2 ? "authority" : "authority-deny";
                boolean armed =
                        mode == 3
                                && authorityJudgement.getOrDefault(player.getUniqueId(), 0L) > now;
                yield "Mode: "
                        + modeName
                        + "  •  "
                        + (armed ? "NEXT HIT ARMED" : cooldownText(player, cooldownKey));
            }
            case TRANSPOSITION -> {
                yield "Direct Swap  •  Range: 10m  •  " + cooldownText(player, "transposition");
            }
            case EFFIGY -> {
                String bound = items.get(item, "bound");
                if (bound == null) yield "Bound: None";
                try {
                    yield "Bound: " + name(UUID.fromString(bound));
                } catch (IllegalArgumentException ignored) {
                    yield "Bound: Unknown";
                }
            }
            case SCRIPTURE ->
                    isSilenced(player) ? "SILENCED" : "Open Scripture to view inscriptions";
            case EVENT_HORIZON -> {
                boolean zeroActive = zero.getOrDefault(player.getUniqueId(), 0L) > now;
                boolean refractionActive = refraction.getOrDefault(player.getUniqueId(), 0L) > now;
                yield "Zero: "
                        + (zeroActive ? "ACTIVE" : cooldownText(player, "zero"))
                        + "  •  Refraction: "
                        + (refractionActive ? "ACTIVE" : cooldownText(player, "refraction"));
            }
            case RESONATOR -> {
                yield (resonatorFiring.contains(player.getUniqueId())
                                ? "BEAM ACTIVE  •  "
                                : resonatorCharging.contains(player.getUniqueId())
                                        ? "CHARGING  •  "
                                        : "Beam ")
                        + cooldownText(player, "resonator-beam")
                        + "  •  Slam "
                        + cooldownText(player, "resonator-slam");
            }
            case PARALLAX -> {
                yield (player.isBlocking() ? "360° GUARD ACTIVE" : "Raise shield to guard")
                        + "  •  Bash "
                        + cooldownText(player, "parallax-bash");
            }
        };
    }

    private String cooldownText(Player player, String key) {
        long remaining = cooldownRemaining(player, key);
        if (remaining <= 0) return "✓ READY";
        long seconds = (remaining + 999) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private long cooldownRemaining(Player player, String key) {
        UUID id = UUID.nameUUIDFromBytes((player.getUniqueId() + ":" + key).getBytes());
        return Math.max(0, cooldowns.getOrDefault(id, 0L) - System.currentTimeMillis());
    }

    private boolean ready(Player p, String key, int seconds) {
        String k = p.getUniqueId() + ":" + key;
        UUID id = UUID.nameUUIDFromBytes(k.getBytes());
        long now = System.currentTimeMillis(), until = cooldowns.getOrDefault(id, 0L);
        if (until > now) {
            msg(p, "Cooldown: " + ((until - now + 999) / 1000) + "s", NamedTextColor.RED);
            return false;
        }
        cooldowns.put(id, now + seconds * 1000L);
        return true;
    }

    private void refund(Player p, String key) {
        cooldowns.remove(UUID.nameUUIDFromBytes((p.getUniqueId() + ":" + key).getBytes()));
    }

    private boolean allowedHand(Player p, ItemStack clicked, Mythic m) {
        boolean main = items.type(p.getInventory().getItemInMainHand()) == m,
                off = items.type(p.getInventory().getItemInOffHand()) == m;
        return switch (m) {
            case AUTHORITY -> off && !main;
            case ONSLAUGHT, SKYFALL, EFFIGY, SCRIPTURE, RESONATOR -> main;
            default -> main || off;
        };
    }

    private boolean hasEither(Player p, Mythic m) {
        return items.is(p.getInventory().getItemInMainHand(), m)
                || items.is(p.getInventory().getItemInOffHand(), m);
    }

    private ItemStack find(Player p, Mythic m) {
        if (items.is(p.getInventory().getItemInMainHand(), m))
            return p.getInventory().getItemInMainHand();
        if (items.is(p.getInventory().getItemInOffHand(), m))
            return p.getInventory().getItemInOffHand();
        return null;
    }

    private Player target(Player p, double range) {
        var ray =
                p.getWorld()
                        .rayTraceEntities(
                                p.getEyeLocation(),
                                p.getEyeLocation().getDirection(),
                                range,
                                .6,
                                e -> e instanceof Player && e != p);
        return ray != null && ray.getHitEntity() instanceof Player t ? t : null;
    }

    private Player attacker(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private void repel(Entity e, Location from, double power) {
        Vector v = e.getLocation().toVector().subtract(from.toVector());
        if (v.lengthSquared() < .01) v = new Vector(1, 0, 0);
        e.setVelocity(v.normalize().multiply(power).setY(Math.max(.2, v.getY())));
    }

    private boolean isSilenced(Player p) {
        return silenced.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private boolean timeFrozen(Entity e) {
        if (e instanceof Player player) return isTimeStopped(player);
        return timeStops.values().stream()
                .anyMatch(stop -> stop.nonPlayers.containsKey(e.getUniqueId()));
    }

    private TimeStop containingStop(Location l) {
        for (TimeStop s : timeStops.values())
            if (s.center.getWorld() == l.getWorld()
                    && s.center.distanceSquared(l) <= s.radius * s.radius) return s;
        return null;
    }

    private String name(UUID u) {
        Player p = u == null ? null : Bukkit.getPlayer(u);
        return p == null ? "None" : p.getName();
    }

    private double maxHealth(Player p) {
        var a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private void msg(Player p, String s, NamedTextColor c) {
        hudPausedUntil.put(p.getUniqueId(), System.currentTimeMillis() + 1500L);
        p.sendActionBar(Component.text(s, c));
    }

    private static final class Combo {
        final UUID target;
        int hits;
        long last;

        Combo(UUID t, int h, long l) {
            target = t;
            hits = h;
            last = l;
        }
    }

    private record FrozenEntityState(Vector velocity, boolean gravity, Boolean ai) {}

    private record FrozenPlayerState(
            Vector velocity,
            boolean gravity,
            float walkSpeed,
            float flySpeed,
            boolean allowFlight,
            boolean flying,
            boolean collidable,
            float fallDistance) {}

    private static final class TimeStop {
        final UUID id;
        final UUID owner;
        final Location center;
        final double radius;
        final long endsAt;
        final Set<UUID> entities = new HashSet<>();
        final Map<UUID, FrozenEntityState> nonPlayers = new HashMap<>();
        final Map<UUID, Double> queued = new HashMap<>();
        BukkitTask task;
        boolean ending;

        TimeStop(UUID id, UUID o, Location c, double r, long endsAt) {
            this.id = id;
            owner = o;
            center = c;
            radius = r;
            this.endsAt = endsAt;
        }
    }

    private record Constellation(UUID owner, List<Location> points, long until) {}

    private record StarGuide(List<Location> nodes, long until) {}

    private record EffigyLink(ArmorStand stand, UUID target, UUID owner) {}

    private record ScripturePocket(
            UUID victim,
            Location anchor,
            boolean invulnerable,
            boolean collidable,
            boolean gravity,
            float walkSpeed,
            float flySpeed) {}

    private enum StarMode {
        TRIAD("TRIAD", 3),
        ORBIT("ORBIT", 6),
        STAR("STAR", 8);

        private final String display;
        private final int points;

        StarMode(String display, int points) {
            this.display = display;
            this.points = points;
        }

        private StarMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private enum OnslaughtMode {
        BLOOD_DASH("BLOOD DASH"),
        CLEAVE("CLEAVE");

        private final String display;

        OnslaughtMode(String display) {
            this.display = display;
        }

        private OnslaughtMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
