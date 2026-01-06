package com.fi3w0.devourer;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Particle;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class DevourerPlugin extends JavaPlugin implements Listener {
    private NamespacedKey devourKey;

    // cursed players (UUID)
    final Set<UUID> cursed = Collections.synchronizedSet(new HashSet<>());

    // victim -> devourer
    final Map<UUID, UUID> soulOwner = Collections.synchronizedMap(new HashMap<>());

    // devourer -> soul count
    final Map<UUID, Integer> soulCounts = Collections.synchronizedMap(new HashMap<>());

    // bossbars for devourers (made public so commands can manage them)
    public final Map<UUID, BossBar> bossBars = Collections.synchronizedMap(new HashMap<>());

    FileConfiguration cfg;
    private volatile boolean enabledFeature = true;

    // tasks
    private BukkitRunnable periodicTask;
    private BukkitRunnable auraTask;
    private BukkitRunnable bossbarTask;

    @Override
    public void onEnable() {
        this.devourKey = new NamespacedKey(this, "devourer_weapon");

        // auto-update config if plugin version changed (overwrites old config)
        ensureDefaultConfigOnVersionMismatch();

        // read simple enabled flag
        cfg = getConfig();
        enabledFeature = cfg.getBoolean("feature-enabled", true);

        // load cursed list
        List<String> list = cfg.getStringList("cursed-players");
        for (String s : list) {
            try { cursed.add(UUID.fromString(s)); } catch (Exception ex) { getLogger().log(Level.WARNING, "Bad uuid in config: " + s); }
        }

        // load soul owners map
        ConfigurationSection owners = cfg.getConfigurationSection("soul-owners");
        if (owners != null) {
            for (String victim : owners.getKeys(false)) {
                String dev = owners.getString(victim);
                try {
                    soulOwner.put(UUID.fromString(victim), UUID.fromString(dev));
                } catch (Exception ex) {
                    getLogger().warning("Bad soul-owner entry: " + victim + " -> " + dev);
                }
            }
        }

        // load soul counts
        ConfigurationSection counts = cfg.getConfigurationSection("soul-counts");
        if (counts != null) {
            for (String dev : counts.getKeys(false)) {
                int v = counts.getInt(dev, 0);
                try { soulCounts.put(UUID.fromString(dev), v); } catch (Exception ex) { getLogger().warning("Bad soul-count entry: " + dev); }
            }
        }

        getServer().getPluginManager().registerEvents(this, this);

        // periodic task: apply random effects
        long intervalSecs = Math.max(1L, cfg.getLong("interval-seconds", 300L));
        long intervalTicks = intervalSecs * 20L;
        periodicTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabledFeature) return;
                applyPeriodicEffects();
            }
        };
        periodicTask.runTaskTimer(this, 20L, Math.max(20L, intervalTicks));

        // aura task: spawn particles around cursed players
        if (cfg.getBoolean("aura-enabled", true)) {
            auraTask = new BukkitRunnable() {
                @Override
                public void run() {
                    spawnAuras();
                }
            };
            auraTask.runTaskTimer(this, 5L, cfg.getLong("aura-interval-ticks", 10L));
        }

        // bossbar updater
        bossbarTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateBossBars();
            }
        };
        bossbarTask.runTaskTimer(this, 2L, 10L);

        // register command executor
        if (this.getCommand("devourer") != null) {
            this.getCommand("devourer").setExecutor(new DevourCommand(this));
        } else {
            getLogger().warning("Command 'devourer' is not defined in plugin.yml!");
        }

        // re-apply devourer buffs on enable for loaded devourers
        for (UUID dev : new HashSet<>(soulCounts.keySet())) {
            Player p = Bukkit.getPlayer(dev);
            if (p != null && p.isOnline()) applyDevourerBuffs(p);
            if (cfg.getBoolean("bossbar.enabled", true) && soulCounts.getOrDefault(dev,0) > 0) createBossBarForDevourer(dev);
        }

        getLogger().info("DevourerPlugin enabled.");
    }

    @Override
    public void onDisable() {
        // cancel tasks
        if (periodicTask != null) periodicTask.cancel();
        if (auraTask != null) auraTask.cancel();
        if (bossbarTask != null) bossbarTask.cancel();

        // remove bossbars
        for (BossBar b : bossBars.values()) {
            try { b.removeAll(); } catch (Throwable ignored) {}
        }
        bossBars.clear();

        // save cursed to config
        List<String> uuids = new ArrayList<>();
        for (UUID u : cursed) uuids.add(u.toString());
        cfg.set("cursed-players", uuids);

        // save soul-owners
        Map<String, String> owners = new HashMap<>();
        for (Map.Entry<UUID, UUID> e : soulOwner.entrySet()) owners.put(e.getKey().toString(), e.getValue().toString());
        cfg.set("soul-owners", owners);

        // save soul-counts
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : soulCounts.entrySet()) counts.put(e.getKey().toString(), e.getValue());
        cfg.set("soul-counts", counts);

        cfg.set("feature-enabled", enabledFeature);
        saveConfig();
    }

    /* -------------------------
       Public API
       ------------------------- */
    public boolean isDevourerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta im = item.getItemMeta();
        return im.getPersistentDataContainer().has(devourKey, PersistentDataType.BYTE);
    }

    // existing method (keeps original name)
    public void markItemAsDevour(ItemStack item) {
        if (item == null) return;
        if (!item.hasItemMeta()) {
            ItemMeta newMeta = Bukkit.getItemFactory().getItemMeta(item.getType());
            item.setItemMeta(newMeta);
        }
        ItemMeta im = item.getItemMeta();
        im.getPersistentDataContainer().set(devourKey, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(im);
    }

    // Alias to support alternative name used in commands (no functional change)
    public void markItemAsDevourer(ItemStack item) {
        markItemAsDevour(item);
    }

    public void addCurse(UUID uuid, UUID byDevourer) {
        if (uuid == null) return;
        cursed.add(uuid);
        soulOwner.put(uuid, byDevourer);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendMessage(cfg.getString("messages.cursed", "You have been cursed by the Devourer!"));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20*6, 0, false, false, true));
        }
        saveConfigAsync();
    }

    /**
     * Remove curse from a player (manual uncurse or when souls return)
     */
    public void removeCurse(UUID uuid) {
        if (uuid == null) return;
        cursed.remove(uuid);
        // remove any soulOwner mapping (if manual remove we release soul ownership too)
        soulOwner.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendMessage(cfg.getString("messages.uncursed", "The curse has been lifted."));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            // clear known curse effects
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.removePotionEffect(PotionEffectType.NAUSEA);
            p.removePotionEffect(PotionEffectType.POISON);
            p.removePotionEffect(PotionEffectType.WEAKNESS);
        }
        saveConfigAsync();
    }

    public boolean isCursed(UUID uuid) { return uuid != null && cursed.contains(uuid); }

    public void setFeatureEnabled(boolean b) { enabledFeature = b; saveConfigAsync(); }

    /* -------------------------
       Core mechanics
       ------------------------- */
    private void applyPeriodicEffects() {
        Random rnd = new Random();
        double globalChance = cfg.getDouble("chance-to-trigger-per-interval", 0.5);

        UUID[] copy;
        synchronized (cursed) { copy = cursed.toArray(new UUID[0]); }

        for (UUID u : copy) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            if (rnd.nextDouble() > globalChance) continue;

            double r = rnd.nextDouble();
            if (r < 0.45) {
                int dur = cfg.getInt("effects.nausea-duration-seconds", 3);
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, dur*20, 0, false, false, true));
                p.sendMessage(cfg.getString("messages.nausea", "You feel the world spin..."));
            } else if (r < 0.75) {
                int dur = cfg.getInt("effects.slowness-duration-seconds", 5);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur*20, 0, false, false, true));
                p.sendMessage(cfg.getString("messages.slowness", "Your limbs feel heavy..."));
            } else if (r < 0.9) {
                int dur = cfg.getInt("effects.weakness-duration-seconds", 6);
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur*20, 0, false, false, true));
                p.sendMessage(cfg.getString("messages.weakness", "You feel your strength fading..."));
            } else {
                int dur = cfg.getInt("effects.poison-duration-seconds", 4);
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, dur*20, 0, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.max(1, dur)*20, 0, false, false, true));
                p.sendMessage(cfg.getString("messages.rare", "The gods' remnants lash out at you..."));
            }
        }
    }

    private void spawnAuras() {
        if (!cfg.getBoolean("aura-enabled", true)) return;
        Particle particle;
        try {
            particle = Particle.valueOf(cfg.getString("aura-particle", "SOUL").toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            particle = Particle.SOUL;
        }
        double radius = cfg.getDouble("aura-radius", 1.2);
        int count = cfg.getInt("aura-count", 12);
        for (UUID u : new HashSet<>(cursed)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            Location loc = p.getLocation().add(0, 1.0, 0);
            p.getWorld().spawnParticle(particle, loc, count, radius, 0.4, radius, 0.02);
        }
    }

    /**
     * Called when a devourer kills a player: store soul mapping and scale devourer.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabledFeature) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (!isDevourerItem(hand)) return;

        // If victim already cursed by somebody, skip double-devour (optional)
        if (soulOwner.containsKey(victim.getUniqueId())) {
            // already devoured
        } else {
            // add curse
            addCurse(victim.getUniqueId(), killer.getUniqueId());

            // increase killer soul count
            soulCounts.put(killer.getUniqueId(), soulCounts.getOrDefault(killer.getUniqueId(), 0) + 1);
            applyDevourerBuffs(killer);
            saveConfigAsync();

            // create bossbar for killer if enabled
            if (cfg.getBoolean("bossbar.enabled", true)) createBossBarForDevourer(killer.getUniqueId());
        }

        String broadcast = cfg.getString("messages.broadcast-devour",
                "%killer% has devoured the divine essence of %victim%!");
        Bukkit.broadcastMessage(
                broadcast.replace("%killer%", killer.getName()).replace("%victim%", victim.getName())
        );

        killer.sendTitle(
                cfg.getString("messages.killer-title", "The Devourer"),
                cfg.getString("messages.killer-sub", "You consume forbidden power"),
                10, 60, 10
        );

        victim.sendTitle(
                cfg.getString("messages.victim-title", "Mortality"),
                cfg.getString("messages.victim-sub", "Your godhood is gone"),
                10, 60, 10
        );
    }

    /**
     * Prevent curse effects being removed for cursed players.
     * EntityPotionEffectEvent: cancel removals of our tracked types.
     */
    @EventHandler
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (!enabledFeature) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (!isCursed(p.getUniqueId())) return;

        PotionEffect oldEff = event.getOldEffect();
        PotionEffect newEff = event.getNewEffect();
        // if something is being removed (old exists and new is null) and it's one of our curse effects -> cancel
        if (oldEff != null && newEff == null) {
            PotionEffectType t = oldEff.getType();
            if (isCurseEffect(t)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isCurseEffect(PotionEffectType t) {
        return t == PotionEffectType.DARKNESS ||
               t == PotionEffectType.SLOWNESS ||
               t == PotionEffectType.POISON ||
               t == PotionEffectType.WEAKNESS ||
               t == PotionEffectType.BLINDNESS ||
               t == PotionEffectType.NAUSEA;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!enabledFeature) return;
        if (event.getItem() == null) return;
        if (!cursed.contains(event.getPlayer().getUniqueId())) return;

        // reapply quickly after drinking milk
        new BukkitRunnable() {
            @Override
            public void run() {
                applyPeriodicEffects(); // quick re-check (cheap)
            }
        }.runTaskLater(this, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!enabledFeature) return;
        Player p = event.getPlayer();
        if (isCursed(p.getUniqueId())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20*5, 0, false, false, true));
                }
            }.runTaskLater(this, 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // no-op; data saved on shutdown; but remove them as bossbar viewers
        Player p = event.getPlayer();
        // remove from any bossbars
        for (BossBar b : bossBars.values()) {
            try { b.removePlayer(p); } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // update bossbar immediately for live feel if damaged devourer is player and has bar
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            UUID id = p.getUniqueId();
            if (bossBars.containsKey(id)) {
                // will be updated by bossbarTask soon; but we can force update now
                updateSingleBossBar(id);
            }
        }
    }

    /* -------------------------
       Devourer buffing / soul mechanics
       ------------------------- */
    private void applyDevourerBuffs(Player dev) {
        int souls = soulCounts.getOrDefault(dev.getUniqueId(), 0);
        if (souls <= 0) {
            // remove permanent attribute changes and potion effects
            try {
                AttributeInstance inst = dev.getAttribute(Attribute.MAX_HEALTH);
                if (inst != null) {
                    inst.setBaseValue(20.0);
                }
            } catch (Throwable ignored) {}
            dev.removePotionEffect(PotionEffectType.STRENGTH);
            dev.removePotionEffect(PotionEffectType.SPEED);
            dev.removePotionEffect(PotionEffectType.JUMP_BOOST);
            dev.removePotionEffect(PotionEffectType.HASTE);
            return;
        }

        // scale values
        double perSoul = cfg.getDouble("devourer.health-per-soul", 2.0);
        double maxExtra = cfg.getDouble("devourer.max-extra-health", 40.0);
        double extra = Math.min(maxExtra, souls * perSoul);
        double base = 20.0;
        double newMax = base + extra;
        AttributeInstance attr = dev.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(newMax);
        }
        // ensure current health not above cap
        if (dev.getHealth() > newMax) dev.setHealth(newMax);

        // potion effects: make them strong based on souls
        int strLevel = Math.min(cfg.getInt("devourer.max-strength-level", 5), Math.max(0, souls / cfg.getInt("devourer.souls-per-strength", 3)));
        int speedLevel = Math.min(cfg.getInt("devourer.max-speed-level", 3), Math.max(0, souls / cfg.getInt("devourer.souls-per-speed", 4)));
        int hasteLevel = Math.min(cfg.getInt("devourer.max-haste-level", 2), Math.max(0, souls / cfg.getInt("devourer.souls-per-haste", 5)));
        // duration long (practical permanent while they keep souls)
        int duration = 20 * 60 * 60; // 1 hour in ticks
        // remove previous
        dev.removePotionEffect(PotionEffectType.STRENGTH);
        dev.removePotionEffect(PotionEffectType.SPEED);
        dev.removePotionEffect(PotionEffectType.HASTE);
        dev.removePotionEffect(PotionEffectType.JUMP_BOOST);
        // apply (amplifier is level-1)
        if (strLevel > 0) dev.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, Math.max(0, strLevel - 1), false, false, true));
        if (speedLevel > 0) dev.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, Math.max(0, speedLevel - 1), false, false, true));
        if (hasteLevel > 0) dev.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, Math.max(0, hasteLevel - 1), false, false, true));
        // small jump boost scaling with souls (optional)
        int jumpLevel = Math.min(2, souls / 6);
        if (jumpLevel > 0) dev.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, Math.max(0, jumpLevel - 1), false, false, true));

        // update bossbar title/progress if exists
        updateSingleBossBar(dev.getUniqueId());
    }

    public void createBossBarForDevourer(UUID devUuid) {
        if (!cfg.getBoolean("bossbar.enabled", true)) return;
        if (bossBars.containsKey(devUuid)) return;
        Player dev = Bukkit.getPlayer(devUuid);
        String title = cfg.getString("bossbar.title", "&5Devourer - %name%");
        String display = title.replace("%name%", (dev!=null ? dev.getName() : devUuid.toString()));
        // using Bukkit's BossBar API (keeps compatibility)
        BossBar bar = Bukkit.createBossBar(ChatColor.translateAlternateColorCodes('&', display), BarColor.PURPLE, BarStyle.SOLID);
        boolean visibleToAll = cfg.getBoolean("bossbar.visible-to-all", true);
        if (visibleToAll) {
            for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.isOp()) bar.addPlayer(p);
        }
        bossBars.put(devUuid, bar);
        updateSingleBossBar(devUuid);
    }

    public void removeBossBar(UUID devUuid) {
        BossBar b = bossBars.remove(devUuid);
        if (b != null) {
            try { b.removeAll(); } catch (Throwable ignored) {}
        }
    }

    private void updateBossBars() {
        for (UUID dev : new HashSet<>(bossBars.keySet())) updateSingleBossBar(dev);
    }

    private void updateSingleBossBar(UUID devUuid) {
        BossBar b = bossBars.get(devUuid);
        if (b == null) return;
        Player dev = Bukkit.getPlayer(devUuid);
        String title = cfg.getString("bossbar.title", "&5Devourer - %name%");
        String display = title.replace("%name%", (dev!=null ? dev.getName() : devUuid.toString()));
        double progress = 0.0;
        double cur = 0.0, max = 20.0;
        if (dev != null && dev.isOnline()) {
            healthSafeguard(dev);
            cur = dev.getHealth();
            AttributeInstance ai = dev.getAttribute(Attribute.MAX_HEALTH);
            if (ai != null) max = ai.getValue();
            else max = dev.getMaxHealth();
            if (max <= 0) max = 20;
            progress = Math.max(0.0, Math.min(1.0, cur / max));
            display = display + " §f(" + Math.round(cur) + " / " + Math.round(max) + ")";
        } else {
            progress = 0.0;
        }
        b.setColor(BarColor.PURPLE);
        b.setStyle(BarStyle.SOLID);
        b.setProgress((float)progress);
        b.setTitle(ChatColor.translateAlternateColorCodes('&', display));
        int souls = soulCounts.getOrDefault(devUuid, 0);
        if (souls <= 0) removeBossBar(devUuid);
    }

    private void healthSafeguard(Player p) {
        try {
            if (p.getHealth() <= 0.0 && p.isOnline()) {
                // nothing
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Called when a devourer dies: return souls to victims and give them short recovery buffs.
     * We detect devourer death in PlayerDeathEvent: if the killed player had souls > 0.
     * This method performs the restoration.
     */
    public void onDevourerDeath(UUID devUuid) {
        // find victims devoured by this devourer
        List<UUID> victims = soulOwner.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), devUuid))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // remove soul count for devourer
        soulCounts.remove(devUuid);
        removeBossBar(devUuid);
        saveConfigAsync();

        int recoverySec = cfg.getInt("devourer.recovery-duration-seconds", 180);
        for (UUID vic : victims) {
            // remove curse and apply small buff if online
            cursed.remove(vic);
            soulOwner.remove(vic);
            Player p = Bukkit.getPlayer(vic);
            if (p != null && p.isOnline()) {
                // small recovery buff: Haste, Speed, Jump Boost
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, recoverySec * 20, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, recoverySec * 20, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, recoverySec * 20, 1, false, false, true));
                p.sendMessage(cfg.getString("messages.recover", "Your stolen power returns and you feel invigorated."));
            }
        }
        saveConfigAsync();
    }

    /* -------------------------
       Utilities
       ------------------------- */
    public UUID getDevourerOf(UUID victim) { return soulOwner.get(victim); }

    public int getSoulsOf(UUID dev) { return soulCounts.getOrDefault(dev, 0); }

    /**
     * Return souls from victim -> dev mappings (used e.g. if admin forcibly removes curse).
     */
    public void returnSoul(UUID victim) {
        UUID dev = soulOwner.remove(victim);
        if (dev != null) {
            int prev = soulCounts.getOrDefault(dev, 0);
            soulCounts.put(dev, Math.max(0, prev - 1));
            Player p = Bukkit.getPlayer(dev);
            if (p != null && p.isOnline()) applyDevourerBuffs(p);
            saveConfigAsync();
        }
    }

    /**
     * Save config on a different thread to avoid tick lag.
     */
    private void saveConfigAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // update config maps
                List<String> uuids = new ArrayList<>();
                for (UUID u : cursed) uuids.add(u.toString());
                cfg.set("cursed-players", uuids);

                Map<String, String> owners = new HashMap<>();
                for (Map.Entry<UUID, UUID> e : soulOwner.entrySet()) owners.put(e.getKey().toString(), e.getValue().toString());
                cfg.set("soul-owners", owners);

                Map<String, Integer> counts = new HashMap<>();
                for (Map.Entry<UUID, Integer> e : soulCounts.entrySet()) counts.put(e.getKey().toString(), e.getValue());
                cfg.set("soul-counts", counts);

                cfg.set("feature-enabled", enabledFeature);
                saveConfig();
            }
        }.runTaskAsynchronously(this);
    }

    /* -------------------------
       CONFIG AUTO-UPDATE SYSTEM
       ------------------------- */
    private void ensureDefaultConfigOnVersionMismatch() {
        saveDefaultConfig();
        reloadConfig();
        cfg = getConfig();

        String diskCfgVersion = cfg.getString("config-version", "0");
        String pluginVersion = getDescription().getVersion();

        if (!pluginVersion.equals(diskCfgVersion)) {
            getLogger().info("Config version mismatch: disk=" + diskCfgVersion + " plugin=" + pluginVersion
                    + " — overwriting config.yml with bundled default.");

            try {
                // backup
                try {
                    File dataFolder = getDataFolder();
                    File cfgFile = new File(dataFolder, "config.yml");
                    if (cfgFile.exists()) {
                        String backupName = "config-backup-" + System.currentTimeMillis() + ".yml";
                        Files.copy(cfgFile.toPath(), new File(dataFolder, backupName).toPath());
                        getLogger().info("Backed up old config to " + backupName);
                    }
                } catch (Exception be) {
                    getLogger().warning("Failed to backup old config: " + be.getMessage());
                }

                // Overwrite config.yml from embedded resource (replace = true)
                saveResource("config.yml", true);

                // Reload into memory
                reloadConfig();
                cfg = getConfig();

                getLogger().info("Config successfully updated to version " + cfg.getString("config-version", pluginVersion));
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "Failed to overwrite config.yml", ex);
            }
        } else {
            getLogger().info("Config version up to date: " + diskCfgVersion);
        }
    }
}

