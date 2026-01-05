package com.fi3w0.devourer;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DevourCommand implements CommandExecutor {
    private final DevourerPlugin plugin;

    public DevourCommand(DevourerPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            s.sendMessage("Usage: /devourer bind | curse <player> | remove <player> | toggle");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("bind")) {
            if (!(s instanceof Player)) { s.sendMessage("Only players may bind items."); return true; }
            Player p = (Player) s;
            if (!p.hasPermission("devourer.bind")) { p.sendMessage("You lack permission."); return true; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                p.sendMessage("You must hold a weapon.");
                return true;
            }
            // ensure item meta exists
            if (!hand.hasItemMeta()) {
                ItemMeta meta = hand.getItemMeta();
                hand.setItemMeta(meta);
            }
            plugin.markItemAsDevourer(hand);
            p.sendMessage("§5The weapon thirsts for divinity...");
            return true;
        } else if (sub.equals("curse") && args.length >= 2) {
            if (!s.hasPermission("devourer.manage")) { s.sendMessage("You lack permission."); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { s.sendMessage("Player not online."); return true; }
            plugin.addCurse(target.getUniqueId());
            s.sendMessage("Cursed " + target.getName());
            return true;
        } else if (sub.equals("remove") && args.length >= 2) {
            if (!s.hasPermission("devourer.manage")) { s.sendMessage("You lack permission."); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { s.sendMessage("Player not online."); return true; }
            plugin.removeCurse(target.getUniqueId());
            s.sendMessage("Removed curse from " + target.getName());
            return true;
        } else if (sub.equals("toggle")) {
            if (!s.hasPermission("devourer.manage")) { s.sendMessage("You lack permission."); return true; }
            boolean now = !plugin.cfg.getBoolean("feature-enabled", true);
            plugin.setFeatureEnabled(now);
            plugin.cfg.set("feature-enabled", now);
            plugin.saveConfig();
            s.sendMessage("Feature enabled: " + now);
            return true;
        }

        s.sendMessage("Unknown subcommand or missing permission.");
        return true;
    }
}
