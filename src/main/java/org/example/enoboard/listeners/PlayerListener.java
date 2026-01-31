package org.example.enoboard.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.example.enoboard.EnoBoard;

public class PlayerListener implements Listener {

    private final EnoBoard plugin;

    public PlayerListener(EnoBoard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Oyuncu giriş yaptığında scoreboard'u göster
        plugin.getScoreboardManager().createScoreboard(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Oyuncu çıkış yaptığında scoreboard'u temizle
        plugin.getScoreboardManager().removeScoreboard(event.getPlayer());
    }
}

