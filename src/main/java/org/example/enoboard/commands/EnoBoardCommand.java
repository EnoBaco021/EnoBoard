package org.example.enoboard.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.example.enoboard.EnoBoard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EnoBoardCommand implements CommandExecutor, TabCompleter {

    private final EnoBoard plugin;

    public EnoBoardCommand(EnoBoard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enoboard.admin")) {
            sender.sendMessage(ChatColor.RED + "Bu komutu kullanma yetkiniz yok!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.getScoreboardManager().loadConfig();
                sender.sendMessage(ChatColor.GREEN + "EnoBoard yapılandırması yeniden yüklendi!");
                break;

            case "toggle":
                boolean newState = !plugin.getScoreboardManager().isEnabled();
                plugin.getScoreboardManager().setEnabled(newState);
                sender.sendMessage(ChatColor.GREEN + "Scoreboard " + (newState ? "aktif" : "devre dışı") + " edildi!");
                break;

            case "web":
                int port = plugin.getConfig().getInt("web-port", 3131);
                sender.sendMessage(ChatColor.AQUA + "Web Panel: " + ChatColor.WHITE + "http://localhost:" + port);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "═══════ " + ChatColor.WHITE + "EnoBoard Yardım" + ChatColor.AQUA + " ═══════");
        sender.sendMessage(ChatColor.YELLOW + "/enoboard reload " + ChatColor.GRAY + "- Yapılandırmayı yeniden yükle");
        sender.sendMessage(ChatColor.YELLOW + "/enoboard toggle " + ChatColor.GRAY + "- Scoreboard'u aç/kapat");
        sender.sendMessage(ChatColor.YELLOW + "/enoboard web " + ChatColor.GRAY + "- Web panel adresini göster");
        sender.sendMessage(ChatColor.AQUA + "════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "toggle", "web");
        }
        return new ArrayList<>();
    }
}

