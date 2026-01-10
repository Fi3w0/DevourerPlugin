# DevourerPlugin — **v1.0.5 (Stable)**

*The Rise of the Devourer — steal divine power. Break gods. Leave mortals.*

> "Power is nothing if you cannot take it… and I take it all." — The Devourer

---

This README is a complete reference for **DevourerPlugin v1.0.5**. Use it to install, configure, and extend the plugin. This version includes critical fixes for config persistence, crafting compatibility, and curse effect stacking.

---

## Table of Contents

* [What it does](#what-it-does)
* [What's new in 1.0.5 (CRITICAL FIXES)](#whats-new-in-105-critical-fixes)
* [Requirements](#requirements)
* [Quick install](#quick-install)
* [Commands & Permissions](#commands--permissions)
* [Configuration (explain & reference)](#configuration-explain--reference)
* [Heart of the Forsaken (curse removal item)](#heart-of-the-forsaken-curse-removal-item)
* [Runtime data & persistence](#runtime-data--persistence)
* [API / Integration points](#api--integration-points)
* [Design notes & behavior details](#design-notes--behavior-details)
* [Troubleshooting & common issues](#troubleshooting--common-issues)
* [Development & build](#development--build-for-devs)
* [Plugin compatibility](#plugin-compatibility)
* [Contributing & license](#contributing--license)
* [Changelog (v1.0.5)](#changelog-v105)
* [Credits & contact](#credits--contact)

---

## What it does

DevourerPlugin turns a chosen weapon into the **Devourer**: any player killed with that weapon becomes **cursed** and loses a "soul" — the killer becomes stronger. Cursed players receive periodic, server-enforced debuffs until they use a Heart of the Forsaken, an admin releases them, or the devourer dies.

**Key features:**
- **Persistent soul system**: Devourers gain power based on collected souls
- **Curse mechanics**: Cursed players suffer random debuffs (nausea, slowness, weakness, poison, blindness)
- **Visual feedback**: Particle auras, boss bars, title screens
- **Balance system**: Configurable scaling, caps, and recovery mechanics
- **Curse removal**: Craftable Heart of the Forsaken item breaks curses
- **Death mechanics**: When a devourer dies, all stolen souls return to victims

Perfect for: story-driven PvP servers, villain progression systems, and lore-heavy gameplay where one player grows by consuming others.

---

## What's new in 1.0.5 (CRITICAL FIXES)

### 🔥 **Major Bug Fixes**

1. **Config Overwrite System - COMPLETELY FIXED**
   - ✅ Config now only generates **once** on first install
   - ✅ Your edits are **100% safe** - no more resets on restart
   - ✅ Removed broken auto-update system that caused constant overwrites
   - ✅ All runtime data (cursed players, souls) properly persisted

2. **Crafting Compatibility - FIXED**
   - ✅ Better recipe registration with conflict detection
   - ✅ Works with plugins like: MythicCrucible, ItemsAdder, WorldEdit, Bukkit derivatives
   - ✅ New fallback command: `/devourer giveheart <player>` if crafting fails
   - ✅ Recipe now uses unique key to avoid conflicts

3. **Curse Effect Stacking Bug - FIXED**
   - ✅ Curse effects **always stay at level 1** (no more infinite stacking)
   - ✅ External potion effects from other plugins/items **don't interfere**
   - ✅ Custom swords with slowness/darkness no longer stack with curse
   - ✅ Prevents amplifier escalation from repeated applications

### 🆕 **New Features**

- **Manual Heart Distribution**: `/devourer giveheart <player>` command for admins
- **Better Error Handling**: Recipe registration failures are logged with helpful messages
- **Event Priority System**: Uses HIGHEST priority to prevent other plugins from interfering
- **Effect Isolation**: Curse effects are tracked separately from external sources

---

## Requirements

* **Server**: Paper 1.21.4+ (or Paper 1.21.11+)
* **Java**: JDK 21 to compile and run
* **Gradle**: Wrapper included (`gradlew`)
* **Optional**: Vault, LuckPerms for advanced permissions

**Tested with these plugins:**
- ✅ MythicCrucible
- ✅ MythicMobs
- ✅ ItemsAdder
- ✅ WorldEdit/FAWE
- ✅ LuckPerms
- ✅ Vault
- ✅ EssentialsX
- ✅ TAB

---

## Quick install

### Step 1: Build the JAR

```bash
./gradlew clean build
```

Output: `build/libs/Devourer-1.0.5.jar`

### Step 2: Install

```bash
cp build/libs/Devourer-1.0.5.jar /path/to/server/plugins/
```

### Step 3: Start server

The plugin will:
1. Create `plugins/Devourer/` folder
2. Generate `config.yml` (only once!)
3. Register Heart of the Forsaken recipe
4. Enable all features

### Step 4: Create a Devourer weapon

```bash
# Hold any weapon and run:
/devourer bind
```

Done! Your weapon is now a Devourer.

---

## Commands & Permissions

All commands use `/devourer` (alias: `/devour`)

### Permissions

```yaml
devourer.manage: true     # Admin commands (default: OP)
devourer.bind: true       # Bind devourer weapons (default: OP)
devourer.craft: true      # Craft Heart of Forsaken (default: true)
```

### Command Reference

| Command | Description | Permission |
|---------|-------------|------------|
| `/devourer help` | Show all commands | None |
| `/devourer bind` | Mark held item as Devourer weapon | devourer.bind |
| `/devourer giveheart <player>` | Give Heart of the Forsaken | devourer.manage |
| `/devourer curse <player>` | Manually curse a player | devourer.manage |
| `/devourer remove <player>` | Remove curse and return soul | devourer.manage |
| `/devourer toggle` | Enable/disable entire system | devourer.manage |
| `/devourer buffs <on\|off>` | Toggle devourer buff system | devourer.manage |
| `/devourer souls <player> give <#>` | Give souls to player | devourer.manage |
| `/devourer souls <player> remove <#>` | Remove souls from player | devourer.manage |
| `/devourer souls <player> set <#>` | Set exact soul count | devourer.manage |
| `/devourer bossbar <on\|off\|reload>` | Control boss bars | devourer.manage |
| `/devourer bossbar show/hide` | Show/hide for yourself | devourer.manage |
| `/devourer bossbar status` | Check bossbar status | devourer.manage |

### Command Examples

```bash
# Create a devourer weapon
/devourer bind

# Give someone the curse removal item
/devourer giveheart Steve

# Manage souls
/devourer souls Alex give 10
/devourer souls Notch set 5
/devourer souls Herobrine remove 3

# Control buffs
/devourer buffs off    # Disable all devourer buffs
/devourer buffs on     # Re-enable buffs

# Bossbar management
/devourer bossbar reload    # Reload with new config colors
/devourer bossbar status    # Check how many active
```

---

## Configuration — explain & reference

**IMPORTANT: v1.0.5 FIXED CONFIG SYSTEM**
- Config generates **once** on first install
- **Your edits are safe** - no more overwrites!
- Runtime data (cursed players, souls) saved separately
- Edit freely without fear of resets

### Core Settings

```yaml
# Master switches
feature-enabled: true    # Enable/disable entire system
buffs-enabled: true      # Enable/disable devourer buffs

# Curse timing
interval-seconds: 800                      # How often curse effects trigger
chance-to-trigger-per-interval: 0.5       # 0.0-1.0 chance per interval
```

### Devourer Scaling

```yaml
devourer:
  health-per-soul: 2.0           # HP gained per soul
  max-extra-health: 40.0         # Maximum bonus HP (prevents infinite stacking)
  
  souls-per-strength: 3          # Souls needed for Strength +1
  souls-per-speed: 4             # Souls needed for Speed +1  
  souls-per-haste: 5             # Souls needed for Haste +1
  
  max-strength-level: 5          # Maximum Strength level
  max-speed-level: 3             # Maximum Speed level
  max-haste-level: 2             # Maximum Haste level
  
  recovery-duration-seconds: 180 # Buff duration when devourer dies
```

**Example scaling:**
- 3 souls = +6 HP, Strength I
- 6 souls = +12 HP, Strength II, Speed I
- 9 souls = +18 HP, Strength III, Speed II
- 15 souls = +30 HP, Strength V, Speed III, Haste III

### Boss Bar Customization

```yaml
bossbar:
  enabled: true
  title: "&5Devourer - %name% &7[&d%souls% Souls&7]"
  color: "PURPLE"          # PINK, BLUE, RED, GREEN, YELLOW, WHITE
  style: "SOLID"           # SOLID, SEGMENTED_6, SEGMENTED_10, etc.
  visible-to-all: true     # false = only OPs see it
```

**Available placeholders:**
- `%name%` - Devourer player name
- `%souls%` - Current soul count
- `%health%` - Current health (rounded)
- `%maxhealth%` - Maximum health (rounded)

### Visual Effects

```yaml
aura-enabled: true
aura-particle: SOUL          # SOUL, END_ROD, FLAME, WITCH, etc.
aura-radius: 0.5
aura-count: 8
aura-interval-ticks: 30      # Lower = more frequent (more lag)
```

### Curse Effects

```yaml
effects:
  nausea-duration-seconds: 3
  slowness-duration-seconds: 5
  weakness-duration-seconds: 6
  poison-duration-seconds: 4
```

**Note:** Effects are **always level 1** in v1.0.5 (prevents stacking)

### Messages

All messages support `&` color codes:

```yaml
messages:
  cursed: "&cYou have been cursed by the Devourer!"
  uncursed: "&aThe curse has been lifted."
  broadcast-devour: "&5%killer% &dhas devoured %victim%&d!"
  recover: "&aYour stolen power returns!"
  devourer-death: "&5The Devourer %name% has fallen!"
  # ... and more
```

---

## Heart of the Forsaken (curse removal item)

### Crafting Recipe

```
[Golden Apple] [Golden Apple] [Skeleton Skull]
[Nether Star]  [Netherite]    [Pitcher Plant]
[Totem]        [Golden Apple] [Golden Apple]
```

**Materials needed:**
- 4x Golden Apple
- 1x Nether Star
- 1x Netherite Ingot
- 1x Pitcher Plant
- 1x Skeleton Skull
- 1x Totem of Undying

**Result:** Heart of the Sea (with custom NBT data)

### Usage

1. Right-click while cursed
2. Item is consumed (one-time use)
3. Curse is removed
4. Soul returned to devourer (who loses 1 soul)
5. Visual/sound effects play

### If Crafting Doesn't Work

Some plugin combinations prevent custom recipes. Use the fallback:

```bash
/devourer giveheart <player>
```

This manually gives the item with proper NBT data.

### Configuration

```yaml
heart-of-forsaken:
  enabled: true
  name: "&dHeart of the Forsaken"
  lore:
    - "&7A mystical artifact that can"
    - "&7recover god like powers."
    - ""
    - "&eRight-click to use"
  use-message: "&aThe Heart pulses with energy and breaks your curse!"
  not-cursed-message: "&cYou are not cursed!"
```

---

## Runtime data & persistence

### Data Storage

All persistent data is stored in `config.yml`:

```yaml
# Automatically managed by plugin
cursed-players: []    # List of cursed player UUIDs
soul-owners: {}       # victim UUID -> devourer UUID mapping
soul-counts: {}       # devourer UUID -> integer soul count
```

### How It Works

1. **When player is cursed:**
   - UUID added to `cursed-players`
   - Mapping added to `soul-owners`
   - Soul count incremented in `soul-counts`

2. **On server restart:**
   - All data loaded from config
   - Curses and souls restored
   - Buffs reapplied to online devourers

3. **When curse is removed:**
   - UUID removed from `cursed-players`
   - Mapping removed from `soul-owners`
   - Soul count decremented

4. **When devourer dies:**
   - All their soul mappings cleared
   - Soul count reset to 0
   - Victims receive recovery buffs

### Async Saving

All saves happen asynchronously to prevent server lag:
- No tick freezes when saving
- Safe for large player counts
- Automatic on shutdown (synchronous for safety)

---

## API / Integration points

Access the plugin instance:

```java
DevourerPlugin plugin = (DevourerPlugin) Bukkit.getPluginManager().getPlugin("Devourer");
```

### Public Methods (v1.0.5)

```java
// Item management
boolean isDevourerItem(ItemStack item)
void markItemAsDevourer(ItemStack item)

// Curse management
void addCurse(UUID victim, UUID byDevourer)
void removeCurse(UUID uuid)
boolean isCursed(UUID uuid)

// Soul management
int getSoulsOf(UUID devourer)
void setSouls(UUID devourer, int amount)
void returnSoul(UUID victim)
UUID getDevourerOf(UUID victim)

// Buff management
void reapplyAllDevourerBuffs()
void removeAllDevourerBuffs()

// Bossbar management
void createBossBarForDevourer(UUID dev)
void removeBossBar(UUID dev)
void recreateAllBossBars()

// Heart of the Forsaken
ItemStack createHeartOfForsaken()

// System control
void setFeatureEnabled(boolean enabled)
void onDevourerDeath(UUID devourer)
```

### Integration Examples

**MythicMobs custom drop:**
```yaml
myBoss:
  Drops:
    - devourerWeapon 1 1
    
devourerWeapon:
  Type: NETHERITE_SWORD
  Display: "&5Devourer of Gods"
  Options:
    ApplyToDevourer: true  # Use plugin API on drop
```

**PlaceholderAPI support (future):**
```
%devourer_souls% - Player's soul count
%devourer_cursed% - Is player cursed
%devourer_owner% - Who cursed this player
```

**Custom event handling:**
```java
@EventHandler
public void onCustomKill(CustomKillEvent event) {
    Player killer = event.getKiller();
    Player victim = event.getVictim();
    
    if (hasDevourerWeapon(killer)) {
        plugin.addCurse(victim.getUniqueId(), killer.getUniqueId());
    }
}
```

---

## Design notes & behavior details

### Curse Effect Protection

**v1.0.5 implements smart effect isolation:**

1. **Level locking**: Curse effects are always applied at level 1 (amplifier 0)
2. **External blocking**: Effects from other plugins/items don't stack with curses
3. **Priority handling**: Uses `EventPriority.HIGHEST` to override other plugins
4. **Tracking system**: Temporarily tracks when applying curse effects to allow them through

**Example scenario:**
- Player is cursed (has Slowness I from curse)
- Gets hit by custom sword that applies Slowness IV
- v1.0.4 and earlier: Slowness would stack to V, VI, VII...
- v1.0.5: External Slowness IV is blocked, curse stays at Slowness I

### Death Mechanics

**When a devourer dies:**
1. All soul mappings cleared (`soul-owners`)
2. Soul count reset to 0 (`soul-counts`)
3. Bossbar removed
4. Buffs removed (health, strength, speed, haste)
5. All victims uncursed
6. Victims receive 3-minute recovery buff:
   - Haste II
   - Speed II
   - Jump Boost II
   - Regeneration I
7. Server-wide broadcast message

### Bossbar Updates

- Updates every 0.5 seconds (10 ticks)
- Shows real-time health as progress bar
- Automatically removes when souls = 0
- Color and style changeable in config
- Supports `%placeholders%` in title

### Particle Aura

- Spawns around cursed players at chest height
- Configurable particle type, count, radius
- Default: SOUL particle (purple souls)
- Updates every 0.5 seconds (configurable)

---

## Troubleshooting & common issues

### Config keeps resetting (v1.0.3 issue - FIXED in v1.0.5)

**If you're still on v1.0.3 or v1.0.4:**
- Upgrade to v1.0.5 immediately
- Your config will generate once and never reset again

**If using v1.0.5 and config still resets:**
- Check file permissions (should be writable)
- Check for other plugins modifying configs
- Report as bug (this shouldn't happen)

### Heart of the Forsaken won't craft

**Common causes:**
1. Recipe conflicts with another plugin
2. MythicCrucible or ItemsAdder overriding recipes
3. Custom crafting plugin blocking

**Solutions:**
```bash
# Use fallback command
/devourer giveheart <player>

# Check server logs for:
"Failed to register Heart recipe - may conflict with another plugin"

# Temporarily disable conflicting plugins and test
```

### Curse effects keep stacking

**If you see Slowness X or Weakness X:**
- Make sure you're on v1.0.5 (this was fixed)
- Check `/version Devourer` - should show 1.0.5
- Report with `/timings paste` if still happening

### Devourers not getting buffs

**Check these:**
```bash
/devourer buffs          # Should show "ENABLED"
/devourer souls <player> give 3   # Test with 3 souls
```

**Expected results with 3 souls:**
- +6 HP (20 → 26 max health)
- Strength I
- Check with `/effect <player>` or F3

### Bossbars not visible

**Troubleshooting:**
```bash
/devourer bossbar status    # Check if enabled
/devourer bossbar show      # Force show for yourself
```

**Check config:**
```yaml
bossbar:
  enabled: true
  visible-to-all: true     # If false, only OPs see them
```

### Plugin conflicts

**Known compatible:**
- ✅ All Spigot/Paper plugins
- ✅ MythicMobs/MythicCrucible
- ✅ ItemsAdder
- ✅ WorldEdit/FAWE
- ✅ Citizens
- ✅ Vault/LuckPerms

**Potential issues:**
- ⚠️ Custom crafting plugins (use `/devourer giveheart` fallback)
- ⚠️ Effect management plugins (v1.0.5 should handle this)

---

## Development & build for devs

### Prerequisites

```bash
java -version    # Should show 21.x
```

### Build Steps

```bash
git clone <your-repo>
cd DevourerPlugin

# Build
./gradlew clean build

# Output
ls build/libs/Devourer-1.0.5.jar
```

### Project Structure

```
DevourPlugin/
├── src/main/
│   ├── java/com/fi3w0/devourer/
│   │   ├── DevourerPlugin.java      # Main class
│   │   └── DevourCommand.java       # Command handler
│   └── resources/
│       ├── plugin.yml               # Plugin metadata
│       └── config.yml               # Default config
├── build.gradle                     # Build configuration
└── settings.gradle                  # Project settings
```

### IDE Setup (IntelliJ IDEA)

1. Open as Gradle project
2. Set Project SDK to Java 21
3. Wait for dependencies to download
4. Run configurations: Use Gradle `build` task

### Testing

```bash
# Quick test server
mkdir test-server
cd test-server
wget https://papermc.io/api/v2/projects/paper/versions/1.21.4/builds/latest/downloads/paper-1.21.4-latest.jar
java -Xmx2G -jar paper-*.jar --nogui

# Copy plugin
cp ../build/libs/Devourer-1.0.5.jar plugins/

# Test commands in-game
/devourer help
/devourer bind
```

---

## Plugin compatibility

### Tested & Working

| Plugin | Version | Status | Notes |
|--------|---------|--------|-------|
| Paper | 1.21.4 | ✅ | Primary target |
| MythicCrucible | Latest | ✅ | Weapon integration works |
| MythicMobs | 5.x | ✅ | No conflicts |
| WorldEdit | Latest | ✅ | No conflicts |

Report compatibility issues on GitHub!

---

## Contributing & license

### How to Contribute

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

### Development Guidelines

- Follow existing code style
- Add comments for complex logic
- Test on Paper 1.21.4+
- Update README for new features
- Version bumps in `plugin.yml` and `build.gradle`

### License

**MIT License** - see LICENSE file

```
Copyright (c) 2025 fi3w0

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

---

## Changelog (v1.0.5)

### 🔥 Critical Fixes

- **FIXED**: Config overwrite system - config now only generates once, never overwrites
- **FIXED**: Crafting compatibility - works with MythicCrucible, ItemsAdder, and other plugins
- **FIXED**: Curse effect stacking - effects always stay at level 1, external sources blocked
- **FIXED**: Runtime data persistence - cursed players and souls properly saved/loaded

### 🆕 New Features

- Added `/devourer giveheart <player>` command for manual Heart distribution
- Added better recipe registration with conflict detection
- Added event priority system (HIGHEST) to prevent plugin interference
- Added effect isolation tracking system

### 🔧 Improvements

- Better error messages for recipe registration failures
- Improved async saving reliability
- Enhanced curse effect application logic
- Better handling of external potion effects

### 📝 Documentation

- Complete README overhaul
- Added troubleshooting section
- Added plugin compatibility table
- Added API documentation

---

## Credits & contact

### Author

**fi3w0** - Original developer and maintainer

### Built With

- Paper API 1.21.4
- Java 21
- Gradle 8.5

### Special Thanks

- Paper team for excellent API
- Community testers for bug reports
- MythicCrucible team for integration support

### Links

- **Issues**: Report bugs and request features
- **Wiki**: Extended documentation and guides
- **Discord**: Community support (if applicable)

---

## Final note (roleplay)

> *"In the beginning, there were gods. They were eternal, unkillable, divine.*  
> *Then came the Devourer.*  
> *Now there are only mortals… and the one who made them so."*

**Use Devourer responsibly.** This plugin creates a powerful asymmetric PvP dynamic. One player can become overwhelmingly strong if they collect enough souls. Balance carefully:

- Limit Devourer weapons (don't give to everyone)
- Make Heart of the Forsaken accessible but not free
- Consider soul decay over time (custom modification)
- Set up events where devourers can be challenged
- Use as a story arc, not permanent state

**Remember**: The best villain is one the heroes can eventually defeat. 🗡️

---

**Version**: 1.0.5 (Stable)  
**Release Date**: January 2025  
**Minecraft**: 1.21.4+  
**Java**: 21  
**License**: MIT

---

*"Power corrupts. Absolute power consumes absolutely."*
