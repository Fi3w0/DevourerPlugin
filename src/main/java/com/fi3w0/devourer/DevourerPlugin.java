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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Particle;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class DevourerPlugin extends JavaPlugin implements Listener {
    private NamespacedKey devourKey;
    private NamespacedKey heartKey;

    // cursed players (UUID)
    final Set<UUID> cursed = Collections.synchronizedSet(new HashSet<>());

    // victim -> devourer
    final Map<UUID, UUID> soulOwner = Collections.synchronizedMap(new HashMap<>());

    // devourer -> soul count
    final Map<UUID, Integer> soulCounts = Collections.synchronizedMap(new HashMap<>());

    // bossbars for devourers (made public so commands can manage them)
    public final Map<UUID, BossBar> bossBars = Collections.synchronizedMap(new HashMap<>());

    // Track which effects are from curse system (to prevent external stacking)
    private final Set<UUID> playersReceivingCurseEffects = Collections.synchronizedSet(new HashSet<>());

    FileConfiguration cfg;
    private volatile boolean enabledFeature = true;

    // tasks
    private BukkitRunnable periodicTask;
    private BukkitRunnable auraTask;
    private BukkitRunnable bossbarTask;

    @Override
    public void onEnable() {
        this.devourKey = new NamespacedKey(this, "devourer_weapon");
        this.heartKey = new NamespacedKey(this, "heart_of_forsaken");

        // FIXED: Simple config that NEVER overwrites
        saveDefaultConfig();
        cfg = getConfig();
        enabledFeature = cfg.getBoolean("feature-enabled", true);

        // load cursed list
        List<String> list = cfg.getStringList("cursed-players");
        for (String s : list) {
            try { cursed.add(UUID.fromString(s)); } catch (Exception ex) { 
                getLogger().log(Level.WARNING, "Bad uuid in config: " + s); 
            }
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
                try { soulCounts.put(UUID.fromString(dev), v); } catch (Exception ex) { 
                    getLogger().warning("Bad soul-count entry: " + dev); 
                }
            }
        }

        getServer().getPluginManager().registerEvents(this, this);

        // FIXED: Better recipe registration with conflict handling
        if (cfg.getBoolean("heart-of-forsaken.enabled", true)) {
            registerHeartRecipe();
        }

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
            if (cfg.getBoolean("bossbar.enabled", true) && soulCounts.getOrDefault(dev,0) > 0) {
                createBossBarForDevourer(dev);
            }
        }

        getLogger().info("DevourerPlugin v1.0.5 enabled! Fixes: config, crafting, curse stacking");
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

        // save state synchronously on shutdown
        saveAllState();
        
        getLogger().info("DevourerPlugin disabled.");
    }

    /* -------------------------
       Public API
       ------------------------- */
    public boolean isDevourerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta im = item.getItemMeta();
        return im.getPersistentDataContainer().has(devourKey, PersistentDataType.BYTE);
    }

    public void markItemAsDevourer(ItemStack item) {
        if (item == null) return;
        if (!item.hasItemMeta()) {
            ItemMeta newMeta = Bukkit.getItemFactory().getItemMeta(item.getType());
            item.setItemMeta(newMeta);
        }
        ItemMeta im = item.getItemMeta();
        im.getPersistentDataContainer().set(devourKey, PersistentDataType.BYTE, (byte)1);
        
        // Set display name
        String name = cfg.getString("devourer-item-name", "Devourer of Gods");
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&5" + name));
        
        item.setItemMeta(im);
    }

    public void addCurse(UUID uuid, UUID byDevourer) {
        if (uuid == null) return;
        cursed.add(uuid);
        soulOwner.put(uuid, byDevourer);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            String msg = cfg.getString("messages.cursed", "You have been cursed by the Devourer!");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            applySafeCurseEffect(p, PotionEffectType.WEAKNESS, 20*6);
        }
        saveConfigAsync();
    }

    /**
     * Remove curse from a player (manual uncurse or when souls return)
     */
    public void removeCurse(UUID uuid) {
        if (uuid == null) return;
        cursed.remove(uuid);
        soulOwner.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            String msg = cfg.getString("messages.uncursed", "The curse has been lifted.");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
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

    public int getSoulsOf(UUID dev) { return soulCounts.getOrDefault(dev, 0); }

    /**
     * Set soul count for a player and update buffs
     */
    public void setSouls(UUID dev, int amount) {
        amount = Math.max(0, amount);
        if (amount == 0) {
            soulCounts.remove(dev);
            removeBossBar(dev);
        } else {
            soulCounts.put(dev, amount);
            if (cfg.getBoolean("bossbar.enabled", true)) {
                createBossBarForDevourer(dev);
            }
        }
        Player p = Bukkit.getPlayer(dev);
        if (p != null && p.isOnline()) {
            applyDevourerBuffs(p);
        }
        saveConfigAsync();
    }

    /**
     * Reapply buffs to all devourers (used when buffs are toggled on)
     */
    public void reapplyAllDevourerBuffs() {
        for (UUID dev : new HashSet<>(soulCounts.keySet())) {
            Player p = Bukkit.getPlayer(dev);
            if (p != null && p.isOnline()) {
                applyDevourerBuffs(p);
            }
        }
    }

    /**
     * Remove buffs from all devourers (used when buffs are toggled off)
     */
    public void removeAllDevourerBuffs() {
        for (UUID dev : new HashSet<>(soulCounts.keySet())) {
            Player p = Bukkit.getPlayer(dev);
            if (p != null && p.isOnline()) {
                removeDevourerBuffs(p);
            }
        }
    }

    /**
     * Recreate all bossbars with current config settings
     */
    public void recreateAllBossBars() {
        // Remove all existing
        for (UUID u : new HashSet<>(bossBars.keySet())) {
            removeBossBar(u);
        }
        // Recreate for all devourers with souls
        if (cfg.getBoolean("bossbar.enabled", true)) {
            for (UUID dev : new HashSet<>(soulCounts.keySet())) {
                if (soulCounts.getOrDefault(dev, 0) > 0) {
                    createBossBarForDevourer(dev);
                }
            }
        }
    }

    /**
     * Create Heart of the Forsaken item
     */
    public ItemStack createHeartOfForsaken() {
        ItemStack heart = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = heart.getItemMeta();
        
        String name = cfg.getString("heart-of-forsaken.name", "&dHeart of the Forsaken");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        
        List<String> loreRaw = cfg.getStringList("heart-of-forsaken.lore");
        List<String> lore = new ArrayList<>();
        for (String line : loreRaw) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        
        // Mark with NBT
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.BYTE, (byte)1);
        
        heart.setItemMeta(meta);
        return heart;
    }

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
            String msg;
            
            // FIXED: Always apply level 0 (displays as level 1) to prevent stacking
            if (r < 0.45) {
                int dur = cfg.getInt("effects.nausea-duration-seconds", 3);
                applySafeCurseEffect(p, PotionEffectType.NAUSEA, dur * 20);
                msg = cfg.getString("messages.nausea", "You feel the world spin...");
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            } else if (r < 0.75) {
                int dur = cfg.getInt("effects.slowness-duration-seconds", 5);
                applySafeCurseEffect(p, PotionEffectType.SLOWNESS, dur * 20);
                msg = cfg.getString("messages.slowness", "Your limbs feel heavy...");
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            } else if (r < 0.9) {
                int dur = cfg.getInt("effects.weakness-duration-seconds", 6);
                applySafeCurseEffect(p, PotionEffectType.WEAKNESS, dur * 20);
                msg = cfg.getString("messages.weakness", "You feel your strength fading...");
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            } else {
                int dur = cfg.getInt("effects.poison-duration-seconds", 4);
                applySafeCurseEffect(p, PotionEffectType.POISON, dur * 20);
                applySafeCurseEffect(p, PotionEffectType.BLINDNESS, dur * 20);
                msg = cfg.getString("messages.rare", "The gods' remnants lash out at you...");
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            }
        }
    }

    /**
     * FIXED: Apply curse effect safely - always level 0 (displays as I) and prevents stacking
     */
    private void applySafeCurseEffect(Player p, PotionEffectType type, int durationTicks) {
        playersReceivingCurseEffects.add(p.getUniqueId());
        try {
            // Remove any existing effect first
            p.removePotionEffect(type);
            // Apply new effect at level 0 (displays as level I)
            p.addPotionEffect(new PotionEffect(type, durationTicks, 0, false, false, true));
        } finally {
            // Remove from tracking after a short delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    playersReceivingCurseEffects.remove(p.getUniqueId());
                }
            }.runTaskLater(this, 2L);
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
     * FIXED: Now properly handles devourer kills and devourer deaths
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabledFeature) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Check if a DEVOURER died (soul recovery)
        UUID victimId = victim.getUniqueId();
        if (soulCounts.containsKey(victimId) && soulCounts.get(victimId) > 0) {
            // A devourer died! Return all souls
            onDevourerDeath(victimId);
        }

        // Check if someone was killed BY a devourer
        if (killer == null) return;

        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (!isDevourerItem(hand)) return;

        // If victim already cursed by somebody, skip double-devour
        if (soulOwner.containsKey(victimId)) {
            return;
        }

        // Add curse
        addCurse(victimId, killer.getUniqueId());

        // Increase killer soul count
        UUID killerId = killer.getUniqueId();
        soulCounts.put(killerId, soulCounts.getOrDefault(killerId, 0) + 1);
        
        // Apply the buffs
        applyDevourerBuffs(killer);
        saveConfigAsync();

        // Create bossbar for killer if enabled
        if (cfg.getBoolean("bossbar.enabled", true)) {
            createBossBarForDevourer(killerId);
        }

        String broadcast = cfg.getString("messages.broadcast-devour",
                "%killer% has devoured the divine essence of %victim%!");
        broadcast = broadcast.replace("%killer%", killer.getName()).replace("%victim%", victim.getName());
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcast));

        String killerTitle = cfg.getString("messages.killer-title", "The Devourer");
        String killerSub = cfg.getString("messages.killer-sub", "You consume forbidden power");
        killer.sendTitle(
                ChatColor.translateAlternateColorCodes('&', killerTitle),
                ChatColor.translateAlternateColorCodes('&', killerSub),
                10, 60, 10
        );

        String victimTitle = cfg.getString("messages.victim-title", "Mortality");
        String victimSub = cfg.getString("messages.victim-sub", "Your godhood is gone");
        victim.sendTitle(
                ChatColor.translateAlternateColorCodes('&', victimTitle),
                ChatColor.translateAlternateColorCodes('&', victimSub),
                10, 60, 10
        );
    }

    /**
     * FIXED: Prevent curse effects from stacking with external sources
     * This runs at HIGHEST priority to catch effects from other plugins
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (!enabledFeature) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        UUID playerId = p.getUniqueId();
        
        if (!isCursed(playerId)) return;

        PotionEffect newEff = event.getNewEffect();
        
        // If this is our own curse effect being applied, allow it
        if (playersReceivingCurseEffects.contains(playerId)) {
            return;
        }

        // If an external source is trying to apply a curse effect type
        if (newEff != null && isCurseEffect(newEff.getType())) {
            // Cancel external curse effects to prevent stacking
            event.setCancelled(true);
            return;
        }

        // Prevent removal of curse effects
        PotionEffect oldEff = event.getOldEffect();
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
                applyPeriodicEffects();
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
                    if (p.isOnline()) {
                        applySafeCurseEffect(p, PotionEffectType.WEAKNESS, 20*5);
                    }
                }
            }.runTaskLater(this, 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        // remove from any bossbars
        for (BossBar b : bossBars.values()) {
            try { b.removePlayer(p); } catch (Throwable ignored) {}
        }
        // Clean up tracking
        playersReceivingCurseEffects.remove(p.getUniqueId());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // update bossbar immediately for live feel
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            UUID id = p.getUniqueId();
            if (bossBars.containsKey(id)) {
                updateSingleBossBar(id);
            }
        }
    }

    /* -------------------------
       Heart of the Forsaken
       ------------------------- */
    private void registerHeartRecipe() {
        try {
            ItemStack heart = createHeartOfForsaken();
            NamespacedKey recipeKey = new NamespacedKey(this, "heart_forsaken_v2");
            
            // Check if recipe already exists (from reload)
            try {
                Bukkit.removeRecipe(recipeKey);
            } catch (Exception ignored) {}
            
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, heart);
            
            // Pattern: 
            // A G H
            // S N I  
            // T A G
            recipe.shape("AGH", "SNI", "TAG");
            recipe.setIngredient('A', Material.GOLDEN_APPLE);
            recipe.setIngredient('G', Material.GOLDEN_APPLE);
            recipe.setIngredient('H', Material.SKELETON_SKULL);
            recipe.setIngredient('S', Material.NETHER_STAR);
            recipe.setIngredient('N', Material.NETHERITE_INGOT);
            recipe.setIngredient('I', Material.PITCHER_PLANT);
            recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
            
            boolean added = Bukkit.addRecipe(recipe);
            if (added) {
                getLogger().info("Heart of the Forsaken recipe registered successfully!");
            } else {
                getLogger().warning("Failed to register Heart recipe - may conflict with another plugin");
                getLogger().warning("Use /devourer giveheart <player> as alternative");
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error registering Heart recipe", e);
            getLogger().warning("Recipe registration failed - use /devourer giveheart <player> instead");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!cfg.getBoolean("heart-of-forsaken.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(heartKey, PersistentDataType.BYTE)) return;
        
        event.setCancelled(true);
        
        UUID playerId = p.getUniqueId();
        if (!isCursed(playerId)) {
            String msg = cfg.getString("heart-of-forsaken.not-cursed-message", "&cYou are not cursed!");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }
        
        // Remove curse
        returnSoul(playerId);
        removeCurse(playerId);
        
        // Consume item
        item.setAmount(item.getAmount() - 1);
        
        // Success message
        String msg = cfg.getString("heart-of-forsaken.use-message", "&aThe Heart pulses with energy and breaks your curse!");
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        
        // Effects
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.5f);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }

    /* -------------------------
       Devourer buffing / soul mechanics
       ------------------------- */
    private void applyDevourerBuffs(Player dev) {
        if (!cfg.getBoolean("buffs-enabled", true)) {
            removeDevourerBuffs(dev);
            return;
        }

        int souls = soulCounts.getOrDefault(dev.getUniqueId(), 0);
        if (souls <= 0) {
            removeDevourerBuffs(dev);
            return;
        }

        // Scale health
        double perSoul = cfg.getDouble("devourer.health-per-soul", 2.0);
        double maxExtra = cfg.getDouble("devourer.max-extra-health", 40.0);
        double extra = Math.min(maxExtra, souls * perSoul);
        double base = 20.0;
        double newMax = base + extra;
        
        AttributeInstance attr = dev.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(newMax);
        }
        
        // Heal player when they gain souls (feels rewarding)
        if (dev.getHealth() < newMax) {
            dev.setHealth(Math.min(newMax, dev.getHealth() + perSoul));
        }

        // Calculate potion effect levels
        int strLevel = Math.min(
            cfg.getInt("devourer.max-strength-level", 5), 
            souls / cfg.getInt("devourer.souls-per-strength", 3)
        );
        int speedLevel = Math.min(
            cfg.getInt("devourer.max-speed-level", 3), 
            souls / cfg.getInt("devourer.souls-per-speed", 4)
        );
        int hasteLevel = Math.min(
            cfg.getInt("devourer.max-haste-level", 2), 
            souls / cfg.getInt("devourer.souls-per-haste", 5)
        );
        
        // Long duration (effectively permanent while they have souls)
        int duration = 20 * 60 * 60; // 1 hour
        
        // Remove old effects
        dev.removePotionEffect(PotionEffectType.STRENGTH);
        dev.removePotionEffect(PotionEffectType.SPEED);
        dev.removePotionEffect(PotionEffectType.HASTE);
        dev.removePotionEffect(PotionEffectType.JUMP_BOOST);
        
        // Apply new effects (amplifier is level-1)
        if (strLevel > 0) {
            dev.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, strLevel - 1, false, false, true));
        }
        if (speedLevel > 0) {
            dev.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedLevel - 1, false, false, true));
        }
        if (hasteLevel > 0) {
            dev.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, hasteLevel - 1, false, false, true));
        }
        
        // Jump boost scaling
        int jumpLevel = Math.min(2, souls / 6);
        if (jumpLevel > 0) {
            dev.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, jumpLevel - 1, false, false, true));
        }

        updateSingleBossBar(dev.getUniqueId());
    }

    private void removeDevourerBuffs(Player dev) {
        // Reset health to default
        AttributeInstance attr = dev.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
        }
        if (dev.getHealth() > 20.0) {
            dev.setHealth(20.0);
        }
        
        // Remove effects
        dev.removePotionEffect(PotionEffectType.STRENGTH);
        dev.removePotionEffect(PotionEffectType.SPEED);
        dev.removePotionEffect(PotionEffectType.JUMP_BOOST);
        dev.removePotionEffect(PotionEffectType.HASTE);
    }

    public void createBossBarForDevourer(UUID devUuid) {
        if (!cfg.getBoolean("bossbar.enabled", true)) return;
        if (bossBars.containsKey(devUuid)) return;
        
        Player dev = Bukkit.getPlayer(devUuid);
        String title = cfg.getString("bossbar.title", "&5Devourer - %name%");
        String display = title.replace("%name%", (dev != null ? dev.getName() : devUuid.toString()));
        display = display.replace("%souls%", String.valueOf(soulCounts.getOrDefault(devUuid, 0)));
        
        // Parse color from config
        BarColor color;
        try {
            color = BarColor.valueOf(cfg.getString("bossbar.color", "PURPLE").toUpperCase());
        } catch (Exception e) {
            color = BarColor.PURPLE;
        }
        
        // Parse style from config
        BarStyle style;
        try {
            style = BarStyle.valueOf(cfg.getString("bossbar.style", "SOLID").toUpperCase());
        } catch (Exception e) {
            style = BarStyle.SOLID;
        }
        
        BossBar bar = Bukkit.createBossBar(
            ChatColor.translateAlternateColorCodes('&', display), 
            color, 
            style
        );
        
        boolean visibleToAll = cfg.getBoolean("bossbar.visible-to-all", true);
        if (visibleToAll) {
            for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) bar.addPlayer(p);
            }
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
        for (UUID dev : new HashSet<>(bossBars.keySet())) {
            updateSingleBossBar(dev);
        }
    }

    private void updateSingleBossBar(UUID devUuid) {
        BossBar b = bossBars.get(devUuid);
        if (b == null) return;
        
        Player dev = Bukkit.getPlayer(devUuid);
        int souls = soulCounts.getOrDefault(devUuid, 0);
        
        String title = cfg.getString("bossbar.title", "&5Devourer - %name%");
        String display = title.replace("%name%", (dev != null ? dev.getName() : devUuid.toString()));
        display = display.replace("%souls%", String.valueOf(souls));
        
        double progress = 0.0;
        if (dev != null && dev.isOnline()) {
            double cur = dev.getHealth();
            double max = dev.getMaxHealth();
            if (max <= 0) max = 20;
            progress = Math.max(0.0, Math.min(1.0, cur / max));
            
            // Add health to display if template contains %health% or %maxhealth%
            display = display.replace("%health%", String.valueOf(Math.round(cur)));
            display = display.replace("%maxhealth%", String.valueOf(Math.round(max)));
        } else {
            progress = 0.0;
        }
        
        // Parse color and style from config each update (allows live changes)
        BarColor color;
        try {
            color = BarColor.valueOf(cfg.getString("bossbar.color", "PURPLE").toUpperCase());
        } catch (Exception e) {
            color = BarColor.PURPLE;
        }
        
        BarStyle style;
        try {
            style = BarStyle.valueOf(cfg.getString("bossbar.style", "SOLID").toUpperCase());
        } catch (Exception e) {
            style = BarStyle.SOLID;
        }
        
        b.setColor(color);
        b.setStyle(style);
        b.setProgress(progress);
        b.setTitle(ChatColor.translateAlternateColorCodes('&', display));
        
        if (souls <= 0) {
            removeBossBar(devUuid);
        }
    }

    /**
     * Called when a devourer dies
     */
    public void onDevourerDeath(UUID devUuid) {
        // Find all victims devoured by this devourer
        List<UUID> victims = soulOwner.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), devUuid))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Remove soul count for devourer
        soulCounts.remove(devUuid);
        removeBossBar(devUuid);
        
        // Remove buffs from dead devourer
        Player deadDev = Bukkit.getPlayer(devUuid);
        if (deadDev != null && deadDev.isOnline()) {
            removeDevourerBuffs(deadDev);
        }

        int recoverySec = cfg.getInt("devourer.recovery-duration-seconds", 180);
        
        // Return souls to victims
        for (UUID vic : victims) {
            cursed.remove(vic);
            soulOwner.remove(vic);
            
            Player p = Bukkit.getPlayer(vic);
            if (p != null && p.isOnline()) {
                // Recovery buff
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, recoverySec * 20, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, recoverySec * 20, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, recoverySec * 20, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, recoverySec * 20, 0, false, false, true));
                
                String msg = cfg.getString("messages.recover", "Your stolen power returns and you feel invigorated.");
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
        
        // Broadcast devourer death
        String deathMsg = cfg.getString("messages.devourer-death", 
            "&5The Devourer %name% has fallen! &aAll stolen souls return to their owners!");
        deathMsg = deathMsg.replace("%name%", deadDev != null ? deadDev.getName() : "Unknown");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', deathMsg));
        
        saveConfigAsync();
    }

    /* -------------------------
       Utilities
       ------------------------- */
    public UUID getDevourerOf(UUID victim) { 
        return soulOwner.get(victim); 
    }

    /**
     * Return souls from victim -> dev mappings (manual removal)
     */
    public void returnSoul(UUID victim) {
        UUID dev = soulOwner.remove(victim);
        if (dev != null) {
            int prev = soulCounts.getOrDefault(dev, 0);
            soulCounts.put(dev, Math.max(0, prev - 1));
            Player p = Bukkit.getPlayer(dev);
            if (p != null && p.isOnline()) {
                applyDevourerBuffs(p);
            }
            saveConfigAsync();
        }
    }

    /**
     * Save all state (cursed players, souls, etc.)
     */
    private void saveAllState() {
        List<String> uuids = new ArrayList<>();
        for (UUID u : cursed) uuids.add(u.toString());
        cfg.set("cursed-players", uuids);

        Map<String, String> owners = new HashMap<>();
        for (Map.Entry<UUID, UUID> e : soulOwner.entrySet()) {
            owners.put(e.getKey().toString(), e.getValue().toString());
        }
        cfg.set("soul-owners", owners);

        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : soulCounts.entrySet()) {
            counts.put(e.getKey().toString(), e.getValue());
        }
        cfg.set("soul-counts", counts);

        cfg.set("feature-enabled", enabledFeature);
        saveConfig();
    }

    /**
     * Async save to prevent lag
     */
    private void saveConfigAsync() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllState();
            }
        }.runTaskAsynchronously(this);
    }
}
