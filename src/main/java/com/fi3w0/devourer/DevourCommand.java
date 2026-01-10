package com.fi3w0.devourer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
                if (!(s instanceof Player)) { s.sendMessage("§cOnly players may bind items."); return true; }
                Player p = (Player) s;
                if (!p.hasPermission("devourer.bind")) { p.sendMessage("§cYou lack permission."); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    p.sendMessage("§cYou must hold a weapon.");
                    return true;
                }
                plugin.markItemAsDevourer(hand);
                p.sendMessage("§5The weapon thirsts for divinity...");
                return true;
            }
            case "giveheart": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("§cYou lack permission."); return true; }
                if (args.length < 2) { 
                    s.sendMessage("§cUsage: /devourer giveheart <player>"); 
                    s.sendMessage("§7Gives Heart of the Forsaken item");
                    return true; 
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { s.sendMessage("§cPlayer not online."); return true; }
                
                ItemStack heart = plugin.createHeartOfForsaken();
                target.getInventory().addItem(heart);
                target.sendMessage("§dYou have received the Heart of the Forsaken!");
                s.sendMessage("§aGave Heart of the Forsaken to " + target.getName());
                return true;
            }
            case "curse": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("§cYou lack permission."); return true; }
                if (args.length < 2) { s.sendMessage("§cUsage: /devourer curse <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { s.sendMessage("§cPlayer not online."); return true; }
                UUID owner = (s instanceof Player) ? ((Player) s).getUniqueId() : null;
                plugin.addCurse(target.getUniqueId(), owner);
                s.sendMessage("§aCursed " + target.getName());
                return true;
            }
            case "remove": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("§cYou lack permission."); return true; }
                if (args.length < 2) { s.sendMessage("§cUsage: /devourer remove <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { s.sendMessage("§cPlayer not online."); return true; }
                plugin.returnSoul(target.getUniqueId());
                plugin.removeCurse(target.getUniqueId());
                s.sendMessage("§aRemoved curse from " + target.getName());
                return true;
            }
            case "toggle": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("§cYou lack permission."); return true; }
                boolean now = !plugin.cfg.getBoolean("feature-enabled", true);
                plugin.setFeatureEnabled(now);
                plugin.cfg.set("feature-enabled", now);
                plugin.saveConfig();
                s.sendMessage("§aFeature enabled: " + now);
                return true;
            }
            case "buffs": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("§cYou lack permission."); return true; }
                if (args.length < 2) { 
                    boolean current = plugin.cfg.getBoolean("buffs-enabled", true);
                    s.sendMessage("§eBuffs are currently: " + (current ? "§aENABLED" : "§cDISABLED"));
                    s.sendMessage("§7Usage: /devourer buffs <on|off>");
                    return true; 
                }
                String action = args[1].toLowerCase();
                if (action.equals("on") || action.equals("enable") || action.equals("true")) {
                    plugin.cfg.set("buffs-enabled", true);
                    plugin.saveConfig();
                    plugin.reapplyAllDevourerBuffs();
                    s.sendMessage("§aBuffs ENABLED and reapplied to all devourers.");
                } else if (action.equals("off") || action.equals("disable") || action.equals("false")) {
                    plugin.cfg.set("buffs-enabled", false);
                    plugin.saveConfig();
                    plugin.removeAllDevourerBuffs();
                    s.sendMessage("§cBuffs DISABLED and removed from all devourers.");
                } else {
                    s.sendMessage("§cUsage: /devourer buffs <on|off>");
                }
                return true;
            }
            case "souls": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("§cYou lack permission."); return true; }
                if (args.length < 4) { 
                    s.sendMessage("§cUsage: /devourer souls <player> <give|remove|set> <number>"); 
                    return true; 
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { s.sendMessage("§cPlayer not online."); return true; }
                
                String action = args[2].toLowerCase();
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    s.sendMessage("§cInvalid number: " + args[3]);
                    return true;
                }
                
                UUID targetId = target.getUniqueId();
                int current = plugin.getSoulsOf(targetId);
                
                switch (action) {
                    case "give":
                    case "add":
                        plugin.setSouls(targetId, current + amount);
                        s.sendMessage("§aGave " + amount + " souls to " + target.getName() + " (now has " + (current + amount) + ")");
                        break;
                    case "remove":
                    case "take":
                        int newAmount = Math.max(0, current - amount);
                        plugin.setSouls(targetId, newAmount);
                        s.sendMessage("§aRemoved " + amount + " souls from " + target.getName() + " (now has " + newAmount + ")");
                        break;
                    case "set":
                        plugin.setSouls(targetId, Math.max(0, amount));
                        s.sendMessage("§aSet " + target.getName() + "'s souls to " + amount);
                        break;
                    default:
                        s.sendMessage("§cUsage: /devourer souls <player> <give|remove|set> <number>");
                        return true;
                }
                return true;
            }
            case "help": {
                sendHelp(s);
                return true;
            }
            case "bossbar": {
                if (!s.hasPermission("devourer.manage")) { s.sendMessage("§cYou lack permission."); return true; }
                if (args.length < 2) { 
                    s.sendMessage("§cUsage: /devourer bossbar <on|off|show|hide|status|reload>"); 
                    return true; 
                }
                String sub2 = args[1].toLowerCase();
                switch (sub2) {
                    case "on":
                        plugin.cfg.set("bossbar.enabled", true);
                        plugin.saveConfig();
                        plugin.recreateAllBossBars();
                        s.sendMessage("§aBossbar enabled and recreated.");
                        break;
                    case "off":
                        plugin.cfg.set("bossbar.enabled", false);
                        plugin.saveConfig();
                        for (UUID u : plugin.bossBars.keySet()) plugin.removeBossBar(u);
                        s.sendMessage("§aBossbar disabled and removed.");
                        break;
                    case "show":
                        if (s instanceof Player) {
                            Player p = (Player) s;
                            plugin.bossBars.values().forEach(b -> b.addPlayer(p));
                            s.sendMessage("§aBossbars shown to you.");
                        } else s.sendMessage("§cOnly players can use show/hide.");
                        break;
                    case "hide":
                        if (s instanceof Player) {
                            Player p = (Player) s;
                            plugin.bossBars.values().forEach(b -> b.removePlayer(p));
                            s.sendMessage("§aBossbars hidden for you.");
                        } else s.sendMessage("§cOnly players can use show/hide.");
                        break;
                    case "reload":
                        plugin.recreateAllBossBars();
                        s.sendMessage("§aAll bossbars reloaded with new config settings.");
                        break;
                    case "status":
                        s.sendMessage("§eBossbar.enabled: " + plugin.cfg.getBoolean("bossbar.enabled", true));
                        s.sendMessage("§eActive bars: " + plugin.bossBars.size());
                        break;
                    default:
                        s.sendMessage("§cUnknown bossbar subcommand.");
                }
                return true;
            }
            default:
                sendHelp(s);
                return true;
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6§l=== Devourer Plugin Help ===");
        s.sendMessage("§e/devourer bind §7- Mark item in hand as Devourer weapon.");
        s.sendMessage("§e/devourer giveheart <player> §7- Give Heart of the Forsaken item.");
        s.sendMessage("§e/devourer curse <player> §7- Manually curse a player.");
        s.sendMessage("§e/devourer remove <player> §7- Remove curse and return soul.");
        s.sendMessage("§e/devourer toggle §7- Enable/disable system.");
        s.sendMessage("§e/devourer buffs <on|off> §7- Toggle buff application.");
        s.sendMessage("§e/devourer souls <player> <give|remove|set> <number> §7- Manage souls.");
        s.sendMessage("§e/devourer bossbar <on|off|show|hide|status|reload> §7- Bossbar control.");
        s.sendMessage("§e/devourer help §7- Show this help.");
    }
}
