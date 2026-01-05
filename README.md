# DevourerPlugin — **v1.0.0**

*The Rise of the Devourer — steal divine power. Break gods. Leave mortals.*

> “Power is nothing if you cannot take it… and I take it all.” — The Devourer

---

Welcome to **DevourerPlugin**, the lore-first Paper plugin that turns your server into a stage for apocalypse-grade roleplay. This README tells you everything — from a cinematic intro to installation, configuration, commands, integration tips (Crucible / MythicMobs), troubleshooting, and development notes. Read it, deploy it, and watch your story unfold.

---

## Table of Contents

* [What it does](#what-it-does)
* [Features](#features)
* [Requirements](#requirements)
* [Installation (quick)](#installation-quick)
* [Usage & Commands](#usage--commands)
* [Configuration (explain and example)](#configuration-explain-and-example)
* [Crucible & MythicMobs integration](#crucible--mythicmobs-integration)
* [Design notes & behavior details](#design-notes--behavior-details)
* [Troubleshooting & common issues](#troubleshooting--common-issues)
* [Development & build (for devs)](#development--build-for-devs)
* [Contributing](#contributing)
* [License](#license)
* [Changelog (v1.0.0)](#changelog-v100)
* [Credits & Contact](#credits--contact)

---

## What it does

DevourerPlugin gives you a narrative tool: an admin-controlled “Devourer” weapon that **marks** players as cursed when they are killed with it. Cursed players:

* become mortal (your lore: their godhood is stolen),
* receive **persistent** server-enforced debuffs at configurable intervals (nausea, slowness, weakness, poison, blindness),
* cannot remove those debuffs by milk, death, relog, `/effect clear` or restart,
* can only be released by an admin command (or a story event you implement).

Perfect for arcs where the villain grows stronger by consuming gods and players undergo dramatic consequences.

---

## Features

* ✅ Mark **any** weapon in hand as the Devourer (PDC tag) via `/devourer bind`.
* ✅ Killing a player with that weapon applies a **persistent curse** (stored by UUID).
* ✅ Periodic random debuffs with configurable durations and chance.
* ✅ Blocks removal of the curse effects (reapplies / cancels removals).
* ✅ Commands for admins: manual curse, uncurse, toggle system.
* ✅ Works nicely with Crucible / MythicMobs items (PDC or name fallback).
* ✅ Config-driven messages, durations, interval, chances.
* ✅ Minimal, tested on Paper 1.21.11 (Java 21).

---

## Requirements

* Paper MC server: **1.21.11** (recommended).
* Java: **JDK 21** (compile) / runtime must match server runtime (Paper builds for 1.21.11 target Java 21).
* Gradle wrapper (project ships `gradlew`) if you want to rebuild locally.
* Optional: Crucible / MythicMobs to create lore items, but not required.

---

## Installation (quick)

1. Drop the compiled `Devourer-1.0.0.jar` into your server `plugins/` folder.
2. Start the server once to generate `plugins/Devourer/config.yml`.
3. Adjust `config.yml` if desired, then reload/restart.

---

## Usage & Commands

Permissions default to OP unless you hook a permissions plugin.

* `/devourer bind`
  Marks the item in your main hand as the Devourer weapon (adds hidden PDC). Use while holding the desired weapon.

* `/devourer curse <player>`
  Manually applies the curse to `<player>`.

* `/devourer remove <player>`
  Remove the curse from `<player>`.

* `/devourer toggle`
  Enable / disable the whole system (saved to config).

**Example flow (roleplay):**

1. Admin holds a themed sword. `/devourer bind`.
2. Admin kills target player with that sword. The victim receives titles/chat and becomes cursed.
3. Every configured interval, the victim randomly receives short debuffs (nausea/slowness/etc.) until removed.

---

## Configuration (explain and example)

`plugins/Devourer/config.yml` controls behavior. Example (abridged):

```yaml
feature-enabled: true
interval-seconds: 300               # how often the plugin runs checks (in seconds)
chance-to-trigger-per-interval: 0.5 # per cursed player chance to get a debuff each interval

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

effects:
  nausea-duration-seconds: 3
  slowness-duration-seconds: 5
  weakness-duration-seconds: 6
  poison-duration-seconds: 4

cursed-players: []
```

**Tweak tips**

* Lower `interval-seconds` for more frequent flavor events (be careful with server load).
* Use `chance-to-trigger-per-interval` to make events rare or constant.
* Edit message strings to add color codes (`§`) or localize.

---

## Crucible & MythicMobs integration

* **Crucible**: when defining an item, add the same PDC key (`devourer_plugin:devourer_weapon`) or ensure the display name matches `devourer-item-name` in config. Then `/devourer bind` works or you can let the admin bind the handed item after giving it to themselves.
* **MythicMobs**: have a skill drop/give the item; either set the PDC tag or make sure the item's display name matches. You can also trigger a MythicMobs skill on death, but the plugin triggers on `PlayerDeathEvent` using the killer's main hand item.

---

## Design notes & behavior details

* **Persistence**: cursed players are stored by UUID in the config (saved on disable). You can extend this to a database if you expect many entries.
* **Durability & exploits**: the PDC marking makes the Devourer tag hidden and robust — players cannot remove it through renaming.
* **Effect enforcement**: the plugin cancels certain potion removal actions and re-applies effects after drinking milk, respawn, etc. That makes the curse feel permanent to the player until an admin un-curses them.

---

## Troubleshooting & common issues

**Build fails complaining about Java version**

* You need **JDK 21** to compile against Paper 1.21.11. Install Temurin 21 (or other JDK 21) and ensure `java -version` shows 21 during build.

**Compiler errors referencing PotionEffectType.SLOW or CONFUSION**

* Use the Paper 1.21+ names (e.g. `SLOWNESS` and `DARKNESS`) — the plugin is already updated for those.

**`tag name can't be blank` when creating GitHub release**

* Create and push a tag first:

  ```bash
  git tag -a v1.0.0 -m "Initial release"
  git push origin v1.0.0
  ```

**Plugin not enabling / missing command**

* Check `plugin.yml` `main` path matches package and class.
* Check server console errors; common reason: wrong API target or missing Java version.

---

## Development & build (for devs)

1. Clone repo.
2. Ensure JDK 21 on your machine.
3. Build:

```bash
./gradlew build
# or on Windows
.\gradlew.bat build
```

Artifact will be in `build/libs/Devourer-1.0.0.jar`.

**IDE tips**: open as Gradle project in IntelliJ IDEA; set Project SDK to Java 21.

---

## Contributing

Love pull requests. If you contribute:

* Fork the repo
* Implement features in a branch
* Add tests if applicable
* Keep changes atomic and documented
* Submit a PR with a clear description and testing steps

If you create lore assets (messages, title flows, particle effects), include configuration options so server owners can enable/disable the cosmetics.

---

## License

**MIT License** — see `LICENSE` file.
Short: use it, modify it, distribute it, just keep the copyright notice and don’t blame me if your server burns.

---

## Changelog (v1.0.0)

**Initial release**

* Devourer weapon binding command
* Persistent curse system (UUID storage)
* Periodic randomized debuffs (nausea, slowness, weakness, poison, blindness)
* Admin commands: bind, curse, remove, toggle
* Configurable messages and durations
* Compatibility: Paper 1.21.11 (Java 21)

---

## Credits & contact

* Author / maintainer: **fi3w0**
* Built for: Paper 1.21.11
* Inspired by: RPG/MMO servers and dark-lore campaigns

Want a custom integration (Vault group stripping, bossbar corruption gauge, MythicMobs skill to auto-bind on drop)? Open an issue or drop a PR and I’ll help implement.

---

## Final note (a little roleplay closing)

> The Devourer ascends not by chance but by design — your server is the stage, your players are the actors, and DevourerPlugin is the director’s hand. Use it responsibly. Make the story memorable.
