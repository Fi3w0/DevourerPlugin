# DevourerPlugin — **v1.0.3 (Beta)**

*The Rise of the Devourer — steal divine power. Break gods. Leave mortals.*

> “Power is nothing if you cannot take it… and I take it all.” — The Devourer

---

This README is a single, complete reference for **DevourerPlugin v1.0.3**. Use it to install, configure and extend the plugin. It is updated to match the v1.0.3 code (bossbars, soul persistence, auto config updates, aura particles, devourer scaling, and improved commands).

---

## Table of Contents

- [What it does](#what-it-does)  
- [Highlights / What's new in 1.0.3](#highlights--whats-new-in-103)  
- [Requirements](#requirements)  
- [Quick install](#quick-install)  
- [Commands & Permissions](#commands--permissions)  
- [Configuration (explain & reference)](#configuration-explain--reference)  
- [Runtime data & persistence](#runtime-data--persistence)  
- [API / Integration points (Crucible / MythicMobs / other plugins)](#api--integration-points-crucible--mythicmobs--other-plugins)  
- [Design notes & behavior details](#design-notes--behavior-details)  
- [Troubleshooting & common issues](#troubleshooting--common-issues)  
- [Development & build (for devs)](#development--build-for-devs)  
- [Contributing & license](#contributing--license)  
- [Changelog (v1.0.3)](#changelog-v103)  
- [Credits & contact](#credits--contact)

---

## What it does

DevourerPlugin turns a chosen weapon into the **Devourer**: any player killed with that weapon becomes **cursed** and loses a “soul” — the killer becomes stronger. Cursed players receive periodic, server-enforced debuffs until an admin (or game event) releases them. Souls are persisted and affect the killer’s stats and visual presence.

Key idea: powerful, persistent, lore-first PvP progression — great for story arcs where one villain grows by consuming gods.

---

## Highlights / What's new in 1.0.3

- **Soul persistence:** victim → devourer mappings and devourer soul counts are saved to disk (config) and restored on restart.
- **Auto config upgrade:** bundled `config.yml` has a `config-version`. When plugin `version` ≠ `config-version`, the plugin backs up the old config and overwrites it with the new bundled config (quick iteration mode).
- **BossBar:** per-devourer boss bar showing current HP and name. Configurable visibility.
- **Aura particles:** cursed players show a configurable particle aura.
- **Power scaling:** Devourer gains max HP and potion buffs based on souls, with configurable caps and per-soul values.
- **Return mechanic:** if Devourer dies, stolen souls are returned to their owners and those victims get short recovery buffs.
- **Commands:** improved `/devourer help`, `/devourer bind`, `/devourer curse`, `/devourer remove`, `/devourer toggle`, `/devourer bossbar ...`.
- **Async save:** runtime data is saved asynchronously to avoid tick lag.

---

## Requirements

- Paper server: **1.21.11** (recommended)
- Java: **JDK 21** to compile (plugin targets Java 21). Server runtime must match Paper requirements.
- Gradle wrapper included (`gradlew`) for building.
- Optional: Crucible / MythicMobs to create custom items — not required.

---

## Quick install

1. Build the JAR:
   ```bash
   ./gradlew clean build
   ```
   Output: `build/libs/DevourerPlugin-1.0.3.jar`

2. Put the JAR in `paper-server/plugins/`.

3. Start the server once to generate the plugin folder and `config.yml`.

4. Edit `plugins/DevourerPlugin/config.yml` if desired — note: **1.0.3 auto-updates the config on version mismatch** (see Config section).

---

## Commands & Permissions

All commands are under `/devourer`.

> By default commands are OP-only. Hook a permissions plugin and assign the following permission nodes if needed:

- `devourer.bind` — allowed to bind item in-hand as Devourer weapon  
- `devourer.manage` — admin management (curse/remove/toggle/bossbar)

### Command list

- `/devourer help`  
  Show help and usage.

- `/devourer bind`  
  Mark the item in your main hand as the Devourer weapon (PDC tag). Use while holding the desired weapon.

- `/devourer curse <player>`  
  Manually apply the curse to `<player>` (admin).

- `/devourer remove <player>`  
  Remove curse and return the soul (admin). Also reduces the devourer’s soul count if applicable.

- `/devourer toggle`  
  Toggle the system on/off (`feature-enabled`) — saved.

- `/devourer bossbar <on|off|show|hide|status>`  
  Manage bossbar global enable, show/hide for caller, or report status.

---

## Configuration — explain & reference

**Important:** `config-version` is used by the plugin to detect config format changes. If the plugin version in `plugin.yml` differs from `config-version`, the on-disk config will be backed up and overwritten by the bundled default. This is intentional for 1.0.3 (fast iteration). Backup files are created automatically.

Below is an explained reference of the important config keys. Put these in `src/main/resources/config.yml` when packaging, or edit `plugins/DevourerPlugin/config.yml`.

```yaml
# config-version triggers the auto-update logic
config-version: "1.0.3"

# Global master switch
feature-enabled: true

# How often the plugin checks cursed players (seconds)
interval-seconds: 300

# Chance (0.0 - 1.0) each cursed player triggers a random debuff at the interval
chance-to-trigger-per-interval: 0.5

# Devourer scaling and behavior
devourer:
  # Each soul adds this many HP to the devourer (float)
  health-per-soul: 2.0

  # Maximum extra HP that souls can grant (prevents infinite stacking)
  max-extra-health: 40.0

  # How many souls required to increase Strength by +1
  souls-per-strength: 3

  # Souls to increase Speed by +1
  souls-per-speed: 4

  # Souls to increase Haste by +1
  souls-per-haste: 5

  # Upper caps for potion levels applied to Devourer
  max-strength-level: 5
  max-speed-level: 3
  max-haste-level: 2

  # How long (seconds) recovery buff lasts when Devourer dies and souls return
  recovery-duration-seconds: 180

# Bossbar settings
bossbar:
  enabled: true
  # Bossbar title template. %name% will be replaced by devourer player name
  title: "&5Devourer - %name%"
  # true = everyone sees bossbars; false = only OPs
  visible-to-all: true

# Visual aura around cursed players (particle enum name)
aura-enabled: true
aura-particle: SOUL
aura-radius: 1.2
aura-count: 12
# How often (ticks) aura spawns. 20 ticks = 1s.
aura-interval-ticks: 10

# Name used as friendly display for devourer items (optional)
devourer-item-name: "Devourer of Gods"

# Messages shown to players. Use & color codes; plugin translates them before display.
messages:
  cursed: "You have been cursed by the Devourer!"
  uncursed: "The curse has been lifted."
  broadcast-devour: "%killer% has devoured the divine essence of %victim%!"
  killer-title: "The Devourer"
  killer-sub: "You consume forbidden power"
  victim-title: "Mortality"
  victim-sub: "Your godhood is gone"
  nausea: "You feel the world spin..."
  slowness: "Your limbs feel heavy..."
  weakness: "You feel your strength fading..."
  rare: "The gods' remnants lash out at you..."
  recover: "You feel the stolen power return, granting you brief vigor."

# Periodic effect durations (seconds)
effects:
  nausea-duration-seconds: 3
  slowness-duration-seconds: 5
  weakness-duration-seconds: 6
  poison-duration-seconds: 4

# Runtime persisted data (plugin will write here)
cursed-players: []
soul-owners: {}
soul-counts: {}
```

**Tuning tips**
- For testing: set `interval-seconds` to 30–60 and `chance-to-trigger-per-interval` to 0.7–1.0 so you see results quickly.
- Balance HP per soul and max-extra-health to avoid Devourers becoming unstoppable. Example: 2 HP per soul and 40 max gives up to +40 HP (base 20 → 60 HP).

---

## Runtime data & persistence

- **cursed-players** — persisted list of UUIDs that are currently cursed.
- **soul-owners** — map `victimUUID -> devourerUUID` so souls are tied to a devourer.
- **soul-counts** — map `devourerUUID -> integer` soul totals for scaling.

Data is saved asynchronously (no tick lag) and restored on plugin enable. On version differences, the config is backed up and replaced with the default — runtime maps will be re-populated as players are cursed again or restored via commands.

---

## API & Integration points (Crucible / MythicMobs / other plugins)

The plugin exposes a small, convenient API for external integrations or custom code. These methods are accessible on the DevourerPlugin instance:

Example: DevourerPlugin plugin = JavaPlugin.getPlugin(DevourerPlugin.class);

Public helpers (v1.0.3):

- boolean isDevourerItem(ItemStack item) — check PDC tag.
- void markItemAsDevour(ItemStack item) — mark item with the Devourer PDC tag.  
  Alias: void markItemAsDevourer(ItemStack item) also exists for convenience.
- void addCurse(UUID victim, UUID byDevourer) — apply a curse and bind victim to devourer (call this if you trigger devour from custom logic).
- void removeCurse(UUID uuid) — remove curse from a player (admin/manual release).
- void returnSoul(UUID victim) — remove a victim → dev mapping and decrement devourer soul count.
- UUID getDevourerOf(UUID victim) — lookup owner of a soul.
- int getSoulsOf(UUID devourer) — read soul count.
- void createBossBarForDevourer(UUID dev) — create bossbar for a devourer (if enabled).
- void removeBossBar(UUID dev) — remove a devourer’s bossbar.
- void onDevourerDeath(UUID dev) — (helper) return all souls of a devourer and apply recovery buffs (used internally on death; call externally if needed).

Crucible / MythicMobs
- Set the same PDC key (devourer_plugin:devourer_weapon) or simply let admins /devourer bind the weapon after giving it.
- MythicMobs can give the item — you can mark it with PDC in MythicMobs scripts or match the display name in config (devourer-item-name).

---

## Design notes & behavior details

- Hidden PDC tag: marking uses Bukkit PersistentDataContainer — robust vs renames/repairs.
- Effect enforcement: the plugin cancels/removes attempts to clear potion effects that are part of the curse and re-applies effects when necessary (after milk/resurrect).
- Bossbars: created per-devourer and updated periodically; shows HP and name — visible to all or OPs only depending on config.
- Death / return logic: when a devourer dies, all their stolen souls are returned to victims and victims receive a short buff (configurable). Devourer loses soul counts and max HP is reset to base.
- Auto-config overwrite: intentionally overwrites config when bundled config-version differs from saved config-version — plugin makes a timestamped backup first. This is a deliberate behavior in 1.0.3 for easier iteration. We will add a migration/merge system in future releases.

---

## Troubleshooting & common issues

Build fails with wrong Java version
Install JDK 21 and make sure java -version returns a 21.x build before running ./gradlew build.

Command missing / plugin not enabling
Check console logs for stacktraces. Common causes:
- plugin.yml main not pointing to com.fi3w0.devourer.DevourerPlugin.
- Compiled against wrong API (double-check Paper API version in build.gradle).
- Java runtime mismatch.

Config was overwritten unexpectedly
Remember: v1.0.3 auto-updates config on version mismatch. The plugin always creates a backup file named like config-backup-<ms>.yml before overwriting. Restore from that backup if you want to recover old values.

Bossbars not visible
- If bossbar.visible-to-all: false only OPs see them.
- Some clients or plugin combinations can hide bossbars — test with a basic Paper server.

---

## Development & build (for devs)

1. Ensure JDK 21 is installed and java -version shows it.
2. Clone the repo:
   git clone https://github.com/fi3w0/DevourerPlugin.git
   cd DevourerPlugin
3. Build:
   ./gradlew clean build
   JAR: build/libs/DevourerPlugin-1.0.3.jar

IDE: Open as Gradle project in IntelliJ IDEA. Set Project SDK to Java 21.

Notes about dependencies / Paper API: target Paper 1.21.11; keep Paper API dependency consistent with that version.

---

## Contributing & license

Contributions welcome.

- Fork → branch → PR
- Keep PRs focused and documented
- Add tests where possible

License: MIT — include LICENSE file in repo.

---

## Changelog (v1.0.3)

- Added: soul persistence (soul-owners + soul-counts saved)
- Added: auto config upgrade with backups (config-version check)
- Added: bossbar per-devourer (HP + name)
- Added: aura particle visual for cursed players
- Added: devourer max-HP scaling & potion buffs based on soul counts
- Added: onDevourerDeath return mechanic + victim recovery buff
- Improved: /devourer command set (help, bind, curse, remove, toggle, bossbar)
- Improved: async saving of runtime data
- Fixed: potion effect names & Paper 1.21 API compatibility
- Note: v1.0.2 skipped (internal/discarded)

---

## Credits & contact

- Author / maintainer: fi3w0
- Built for Paper 1.21.11 (Java 21)
- Want custom features (MythicMobs skill hooks, GUI, Vault integration)? Open an issue or PR.

---

## Final (roleplay) note

> *Power is temporary. Souls remember.*  
> Use Devourer wisely — it will make the world smaller, louder, and far more dangerous.

---
