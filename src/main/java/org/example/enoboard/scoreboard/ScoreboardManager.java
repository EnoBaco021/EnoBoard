package org.example.enoboard.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.example.enoboard.EnoBoard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardManager {

    private final EnoBoard plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private BukkitTask animationTask;

    private List<String> titleFrames = new ArrayList<>();
    private List<String> lines = new ArrayList<>();
    private int currentTitleFrame = 0;
    private int updateInterval = 5; // ticks
    private boolean enabled = true;

    public ScoreboardManager(EnoBoard plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.reloadConfig();

        enabled = plugin.getConfig().getBoolean("scoreboard.enabled", true);
        updateInterval = plugin.getConfig().getInt("scoreboard.update-interval", 5);

        titleFrames.clear();
        titleFrames.addAll(plugin.getConfig().getStringList("scoreboard.title-frames"));
        if (titleFrames.isEmpty()) {
            titleFrames.add("&6&lEnoBoard");
        }

        lines.clear();
        lines.addAll(plugin.getConfig().getStringList("scoreboard.lines"));
        if (lines.isEmpty()) {
            lines.add("&7Hosgeldiniz!");
            lines.add("&eOyuncu: &f%player%");
            lines.add("&eSunucu: &f%online%/%max%");
        }

        // Tüm oyunculara yeniden uygula
        for (Player player : Bukkit.getOnlinePlayers()) {
            createScoreboard(player);
        }
    }

    public void startAnimation() {
        if (animationTask != null) {
            animationTask.cancel();
        }

        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled) return;

            currentTitleFrame = (currentTitleFrame + 1) % titleFrames.size();

            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
            }
        }, 0L, updateInterval);
    }

    public void stopAnimation() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }

        // Tüm scoreboardları temizle
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        playerScoreboards.clear();
    }

    public void createScoreboard(Player player) {
        if (!enabled) return;

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        String title = colorize(replacePlaceholders(titleFrames.get(currentTitleFrame), player));

        Objective objective = scoreboard.registerNewObjective("enoboard", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        updateLines(scoreboard, objective, player);

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }

    public void updateScoreboard(Player player) {
        if (!enabled) return;

        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            createScoreboard(player);
            return;
        }

        Objective objective = scoreboard.getObjective("enoboard");
        if (objective == null) {
            createScoreboard(player);
            return;
        }

        // Title güncelle
        String title = colorize(replacePlaceholders(titleFrames.get(currentTitleFrame), player));
        objective.setDisplayName(title);

        // Lines güncelle
        updateLines(scoreboard, objective, player);
    }

    private void updateLines(Scoreboard scoreboard, Objective objective, Player player) {
        // Eski satırları temizle
        for (String entry : new HashSet<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }

        int score = lines.size();
        Set<String> usedEntries = new HashSet<>();

        for (String line : lines) {
            String processedLine = colorize(replacePlaceholders(line, player));

            // Aynı satırları farklı yapmak için boşluk ekle
            while (usedEntries.contains(processedLine)) {
                processedLine += " ";
            }
            usedEntries.add(processedLine);

            // 40 karakter limiti
            if (processedLine.length() > 40) {
                processedLine = processedLine.substring(0, 40);
            }

            Score sc = objective.getScore(processedLine);
            sc.setScore(score--);
        }
    }

    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private String replacePlaceholders(String text, Player player) {
        return text
                .replace("%player%", player.getName())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%world%", player.getWorld().getName())
                .replace("%health%", String.valueOf((int) player.getHealth()))
                .replace("%food%", String.valueOf(player.getFoodLevel()))
                .replace("%level%", String.valueOf(player.getLevel()))
                .replace("%x%", String.valueOf(player.getLocation().getBlockX()))
                .replace("%y%", String.valueOf(player.getLocation().getBlockY()))
                .replace("%z%", String.valueOf(player.getLocation().getBlockZ()));
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // Getters and Setters for Web API
    public List<String> getTitleFrames() {
        return new ArrayList<>(titleFrames);
    }

    public void setTitleFrames(List<String> frames) {
        this.titleFrames = new ArrayList<>(frames);
        this.currentTitleFrame = 0;
        saveToConfig();
    }

    public List<String> getLines() {
        return new ArrayList<>(lines);
    }

    public void setLines(List<String> newLines) {
        this.lines = new ArrayList<>(newLines);
        saveToConfig();
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(int interval) {
        this.updateInterval = interval;
        saveToConfig();
        startAnimation(); // Restart with new interval
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveToConfig();
        if (!enabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeScoreboard(player);
            }
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                createScoreboard(player);
            }
        }
    }

    private void saveToConfig() {
        plugin.getConfig().set("scoreboard.enabled", enabled);
        plugin.getConfig().set("scoreboard.update-interval", updateInterval);
        plugin.getConfig().set("scoreboard.title-frames", titleFrames);
        plugin.getConfig().set("scoreboard.lines", lines);
        plugin.saveConfig();
    }
}

