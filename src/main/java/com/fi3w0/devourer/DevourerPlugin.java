package com.fi3w0.devourer;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

import java.util.*;
import java.util.logging.Level;

public final class DevourerPlugin extends JavaPlugin implements Listener {
    private NamespacedKey devourKey;
    final Set<UUID> cursed = Collections.synchronizedSet(new HashSet<>());
    FileConfiguration cfg;
    private volatile boolean enabledFeature = true;

    @Override
    public void onEnable() {
        this.devourKey = new NamespacedKey(this, "devourer_weapon");
        saveDefaultConfig();
        cfg = getConfig();
        enabledFeature = cfg.getBoolean("feature-enabled", true);

        // load cursed list
        List<String> list = cfg.getStringList("cursed-players");
        for (String s : list) {
            try { cursed.add(UUID.fromString(s)); } catch (Exception ex) { getLogger().log(Level.WARNING, "Bad uuid in config: " + s); }
        }

        getServer().getPluginManager().registerEvents(this, this);

        long intervalSecs = Math.max(1L, cfg.getLong("interval-seconds", 300L));
        long intervalTicks = intervalSecs * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabledFeature) return;
                applyPeriodicEffects();
            }
        }.runTaskTimer(this, 20L, Math.max(20L, intervalTicks));

        // register command executor if declared in plugin.yml
        if (this.getCommand("devourer") != null) {
            this.getCommand("devourer").setExecutor(new DevourCommand(this));
        } else {
            getLogger().warning("Command 'devourer' is not defined in plugin.yml!");
        }

        getLogger().info("DevourerPlugin enabled.");
    }

    @Override
    public void onDisable() {
        // save cursed to config
        List<String> uuids = new ArrayList<>();
        for (UUID u : cursed) uuids.add(u.toString());
        cfg.set("cursed-players", uuids);
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

    public void markItemAsDevourer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta im = item.getItemMeta();
        im.getPersistentDataContainer().set(devourKey, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(im);
    }

    public void addCurse(UUID uuid) {
        if (uuid == null) return;
        cursed.add(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendMessage(cfg.getString("messages.cursed", "You have been cursed by the Devourer!"));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20*6, 0, false, false, true));
        }
    }

    public void removeCurse(UUID uuid) {
        if (uuid == null) return;
        cursed.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.sendMessage(cfg.getString("messages.uncursed", "The curse has been lifted."));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            // remove common curse effects
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.removePotionEffect(PotionEffectType.POISON);
            p.removePotionEffect(PotionEffectType.WEAKNESS);
        }
    }

    public boolean isCursed(UUID uuid) { return uuid != null && cursed.contains(uuid); }

    public void setFeatureEnabled(boolean b) { enabledFeature = b; }

    /* -------------------------
       Core mechanics
       ------------------------- */
    private void applyPeriodicEffects() {
        Random rnd = new Random();
        double globalChance = cfg.getDouble("chance-to-trigger-per-interval", 0.5);
        // iterate a copy to avoid CME
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

    /* -------------------------
       Events
       ------------------------- */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabledFeature) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (!isDevourerItem(hand)) return;

        addCurse(victim.getUniqueId());

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

    @EventHandler
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (!enabledFeature) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (!isCursed(p.getUniqueId())) return;

        // Cancel removal of our curse effects
        if (event.getOldEffect() != null) {
            PotionEffectType t = event.getOldEffect().getType();
            if (t == PotionEffectType.DARKNESS ||
                t == PotionEffectType.SLOWNESS ||
                t == PotionEffectType.POISON ||
                t == PotionEffectType.WEAKNESS ||
                t == PotionEffectType.BLINDNESS ||
                t == PotionEffectType.NAUSEA) {
                event.setCancelled(true);
            }
        }
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
                    if (p.isOnline()) p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20*5, 0, false, false, true));
                }
            }.runTaskLater(this, 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // no-op; data saved on shutdown. If you want frequent saving add saves here.
    }
}
