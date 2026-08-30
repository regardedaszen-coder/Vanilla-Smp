# Vanilla SMP

A Paper **1.21.11** plugin implementing twelve PDC-authenticated Mythic artifacts. Requires **Java 21**.

## Project layout

```text
src/main/java/dev/vanillasmp/
├── VanillaSMP.java                 plugin lifecycle and event engine
├── command/
│   └── MythicCommand.java          command routing and tab completion
├── item/
│   └── MythicItemService.java      creation, PDC identity, lore/state
├── model/
│   └── Mythic.java                 canonical Mythic/item definitions
└── ability/
    ├── MythicAbility.java          common ability contract
    ├── AbilityRegistry.java        registers all twelve mechanics
    ├── combat/                     Onslaught, Skyfall, Resonator
    ├── control/                    Epoch, Disarray, Authority
    ├── spatial/                    Event Horizon, Transposition, Effigy
    └── utility/                    Starfall, Scripture, Parallax

src/main/resources/
├── plugin.yml                      Paper/Bukkit entry point and commands
└── config.yml                      server balance settings
```

The package boundaries are intentional: item authentication never belongs in an
ability class, command parsing stays outside item mechanics, and every Mythic has
its own discoverable definition file for continued development.

## Build / IntelliJ

1. In IntelliJ IDEA, choose **Open** and select this `vanilla-smp` folder.
2. Let IntelliJ import `pom.xml` as a Maven project and select a Java 21 SDK.
3. Run Maven `package`, or use `mvn package` in the terminal.
4. Copy `target/vanilla-smp-2.7.1.jar` into the Paper server's `plugins` folder.

## Commands

- `/mythic list`
- `/mythic menu` — OP-only ritual guide showing every Mythic and its crafting ingredients.
- `/mythic give <mythic>`
- `/mythic give <player> <mythic>`
- `/mythic giveall [player]`
- `/mythic reload`
- `/mythic cast <inscription>` while holding Scripture

Commands require `vanillasmp.admin` (OP by default), except casting Scripture.

## Implemented systems

- Unique PDC type and UUID on every genuine Mythic; renamed vanilla items do nothing.
- Starfall is a Bow and keeps normal bow draw/fire behavior while converting its arrows into
  constellation points. Transposition is a Fishing Rod whose cast/reel behavior is suppressed
  and replaced with player selection and position exchange.
- Parallax is a Shield with 360-degree raised guarding and a crouching shield bash.
- Scripture opens as a seven-page interactive written book with colored bracketed inscriptions,
  hoverable resource costs, clickable casting, gateway bars, and a corrupted final page. Players
  never need to type spell commands, while management commands remain admin-protected.
- Vanilla use/place cancellation and the requested main/offhand activation rules.
- Onslaught combos and 3/5/10-hit thresholds.
- Epoch radius freeze, restored motion/AI, queued capped damage, and cleanup on shutdown.
- Skyfall Impact, Rebound, and Hangtime.
- Starfall arrow points, line drawing, closed polygon zones, boundary damage/knockback, and expiry.
- Disarray inventory shuffle that preserves armor and Mythics.
- Authority modes, local denial, permit, and one-conflict override.
- Transposition A/B selection and exact location swaps.
- Effigy binding, armor copy, capped nonlethal remote damage, and expiry.
- Scripture's ten named resource-cost inscriptions and interactive reference book.
- Event Horizon Zero/Refraction fields.
- Comparator Resonator with an aimable charged beam and no-fall-damage redstone eruption slam.
- Automatic Mythic ownership icons beside player nametags and tab-list names.
- Parallax 360-degree guarding and knockback shield bash.
- Configurable major durations, ranges, cooldowns, and damage limits in `config.yml`.
- Twelve distinct shaped crafting recipes. Completing one consumes a single ingredient set and
  starts an item-colored 80-tick crafting-table ritual with orbiting runes, rising relic preview,
  staged sounds, a final particle release, a physical Mythic reward, and a server announcement.

This is a playable first implementation of the design. The intentionally interpretation-heavy mechanics—recognizing a hand-drawn five-point star, cross-Mythic Authority priority, exact mace smash qualification, and the invisible Page III pocket—are isolated areas for deeper server-specific iteration and balance testing.
