package com.fi3w0.devourer;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DevourCommand implements CommandExecutor {
    private final DevourerPlugin plugin;

    public DevourCommand(DevourerPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(s);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "bind": {
                if (!(s instanceof Player)) { s.sendMessage("Only players may bind items."); return true; }
                Player p = (Player) s;
                if (!p.hasPermission("devourer.bind")) { p.sendMessage("You lack permission."); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    p.sendMessage("You must hold a weapon.");
                    return true;
                }
                // both names supported by plugin (alias exists)
                plugin.markItemAsDevourer(hand);
                p.sendMessage("§5The weapon thirsts for divinity...");
                return true;
            }
            case "curse": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("You lack permission."); return true; }
                if (args.length < 2) { s.sendMessage("Usage: /devourer curse <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { s.sendMessage("Player not online."); return true; }
                UUID owner = (s instanceof Player) ? ((Player) s).getUniqueId() : null;
                plugin.addCurse(target.getUniqueId(), owner);
                s.sendMessage("Cursed " + target.getName());
                return true;
            }
            case "remove": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("You lack permission."); return true; }
                if (args.length < 2) { s.sendMessage("Usage: /devourer remove <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { s.sendMessage("Player not online."); return true; }
                plugin.returnSoul(target.getUniqueId());
                plugin.removeCurse(target.getUniqueId());
                s.sendMessage("Removed curse from " + target.getName());
                return true;
            }
            case "toggle": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("You lack permission."); return true; }
                boolean now = !plugin.cfg.getBoolean("feature-enabled", true);
                plugin.setFeatureEnabled(now);
                plugin.cfg.set("feature-enabled", now);
                plugin.saveConfig();
                s.sendMessage("Feature enabled: " + now);
                return true;
            }
            case "help": {
                sendHelp(s);
                return true;
            }
            case "bossbar": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("You lack permission."); return true; }
                if (args.length < 2) { s.sendMessage("Usage: /devourer bossbar <on|off|show|hide|status>"); return true; }
                String sub2 = args[1].toLowerCase();
                switch (sub2) {
                    case "on":
                        plugin.cfg.set("bossbar.enabled", true);
                        plugin.saveConfig();
                        s.sendMessage("Bossbar enabled.");
                        break;
                    case "off":
                        plugin.cfg.set("bossbar.enabled", false);
                        plugin.saveConfig();
                        // remove all bossbars
                        for (UUID u : plugin.bossBars.keySet()) plugin.removeBossBar(u);
                        s.sendMessage("Bossbar disabled and removed.");
                        break;
                    case "show":
                        if (s instanceof Player) {
                            Player p = (Player) s;
                            plugin.bossBars.values().forEach(b -> b.addPlayer(p));
                            s.sendMessage("Bossbars shown to you.");
                        } else s.sendMessage("Only players can use show/hide.");
                        break;
                    case "hide":
                        if (s instanceof Player) {
                            Player p = (Player) s;
                            plugin.bossBars.values().forEach(b -> b.removePlayer(p));
                            s.sendMessage("Bossbars hidden for you.");
                        } else s.sendMessage("Only players can use show/hide.");
                        break;
                    case "status":
                        s.sendMessage("Bossbar.enabled: " + plugin.cfg.getBoolean("bossbar.enabled", true));
                        s.sendMessage("Active bars: " + plugin.bossBars.size());
                        break;
                    default:
                        s.sendMessage("Unknown bossbar subcommand.");
                }
                return true;
            }
            default:
                sendHelp(s);
                return true;
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6=== Devourer Plugin Help ===");
        s.sendMessage("§e/devourer bind §7- Mark item in hand as Devourer weapon.");
        s.sendMessage("§e/devourer curse <player> §7- Manually curse a player.");
        s.sendMessage("§e/devourer remove <player> §7- Remove curse and return soul.");
        s.sendMessage("§e/devourer toggle §7- Enable/disable system.");
        s.sendMessage("§e/devourer bossbar <on|off|show|hide|status> §7- Bossbar control.");
        s.sendMessage("§e/devourer help §7- Show this help.");
    }
}

