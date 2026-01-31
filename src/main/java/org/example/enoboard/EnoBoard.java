package org.example.enoboard;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.enoboard.commands.EnoBoardCommand;
import org.example.enoboard.listeners.PlayerListener;
import org.example.enoboard.scoreboard.ScoreboardManager;
import org.example.enoboard.web.WebServer;

public class EnoBoard extends JavaPlugin {

    private static EnoBoard instance;
    private ScoreboardManager scoreboardManager;
    private WebServer webServer;

    @Override
    public void onEnable() {
        instance = this;

        // Config dosyasını oluştur
        saveDefaultConfig();

        // Scoreboard manager'ı başlat
        scoreboardManager = new ScoreboardManager(this);
        scoreboardManager.loadConfig();
        scoreboardManager.startAnimation();

        // Web server'ı başlat
        int webPort = getConfig().getInt("web-port", 3131);
        webServer = new WebServer(this, webPort);
        webServer.start();

        // Listener'ları kaydet
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        // Komutları kaydet
        getCommand("enoboard").setExecutor(new EnoBoardCommand(this));

        getLogger().info("EnoBoard aktif! Web panel: http://localhost:" + webPort);
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
        }
        if (scoreboardManager != null) {
            scoreboardManager.stopAnimation();
        }
        getLogger().info("EnoBoard devre dışı!");
    }

    public static EnoBoard getInstance() {
        return instance;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public WebServer getWebServer() {
        return webServer;
    }
}

