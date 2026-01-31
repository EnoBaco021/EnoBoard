package org.example.enoboard.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.enoboard.EnoBoard;
import spark.Spark;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WebServer {

    private final EnoBoard plugin;
    private final int port;
    private final Gson gson = new Gson();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000; // 30 dakika

    public WebServer(EnoBoard plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    private String getAdminUsername() {
        return plugin.getConfig().getString("web-panel.username", "admin");
    }

    private String getAdminPassword() {
        return plugin.getConfig().getString("web-panel.password", "admin123");
    }

    private boolean isValidSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return false;
        Long expiry = sessions.get(sessionId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
    }

    private String createSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, System.currentTimeMillis() + SESSION_TIMEOUT);
        return sessionId;
    }

    private void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public void start() {
        Spark.port(port);

        // CORS
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        Spark.options("/*", (request, response) -> {
            return "OK";
        });

        // Login sayfası
        Spark.get("/", (request, response) -> {
            response.type("text/html");
            return getLoginPage();
        });

        // Panel sayfası
        Spark.get("/panel", (request, response) -> {
            response.type("text/html");
            return getPanelPage();
        });

        // API: Login
        Spark.post("/api/login", (request, response) -> {
            response.type("application/json");
            cleanExpiredSessions();

            try {
                JsonObject json = gson.fromJson(request.body(), JsonObject.class);
                String username = json.get("username").getAsString();
                String password = json.get("password").getAsString();

                if (username.equals(getAdminUsername()) && password.equals(getAdminPassword())) {
                    String sessionId = createSession();
                    return "{\"success\": true, \"sessionId\": \"" + sessionId + "\"}";
                } else {
                    response.status(401);
                    return "{\"success\": false, \"error\": \"Geçersiz kullanıcı adı veya şifre\"}";
                }
            } catch (Exception e) {
                response.status(400);
                return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
            }
        });

        // API: Logout
        Spark.post("/api/logout", (request, response) -> {
            response.type("application/json");
            String sessionId = request.headers("Authorization");
            if (sessionId != null) {
                sessions.remove(sessionId);
            }
            return "{\"success\": true}";
        });

        // API: Session doğrulama
        Spark.get("/api/verify", (request, response) -> {
            response.type("application/json");
            String sessionId = request.headers("Authorization");
            if (isValidSession(sessionId)) {
                return "{\"valid\": true}";
            } else {
                response.status(401);
                return "{\"valid\": false}";
            }
        });

        // API: Mevcut config'i getir (auth gerekli)
        Spark.get("/api/config", (request, response) -> {
            response.type("application/json");
            String sessionId = request.headers("Authorization");

            if (!isValidSession(sessionId)) {
                response.status(401);
                return "{\"success\": false, \"error\": \"Unauthorized\"}";
            }

            Map<String, Object> config = new HashMap<>();
            config.put("enabled", plugin.getScoreboardManager().isEnabled());
            config.put("updateInterval", plugin.getScoreboardManager().getUpdateInterval());
            config.put("titleFrames", plugin.getScoreboardManager().getTitleFrames());
            config.put("lines", plugin.getScoreboardManager().getLines());

            return gson.toJson(config);
        });

        // API: Config'i güncelle (auth gerekli)
        Spark.post("/api/config", (request, response) -> {
            response.type("application/json");
            String sessionId = request.headers("Authorization");

            if (!isValidSession(sessionId)) {
                response.status(401);
                return "{\"success\": false, \"error\": \"Unauthorized\"}";
            }

            try {
                JsonObject json = gson.fromJson(request.body(), JsonObject.class);

                if (json.has("enabled")) {
                    plugin.getScoreboardManager().setEnabled(json.get("enabled").getAsBoolean());
                }

                if (json.has("updateInterval")) {
                    plugin.getScoreboardManager().setUpdateInterval(json.get("updateInterval").getAsInt());
                }

                if (json.has("titleFrames")) {
                    List<String> frames = new ArrayList<>();
                    json.getAsJsonArray("titleFrames").forEach(e -> frames.add(e.getAsString()));
                    plugin.getScoreboardManager().setTitleFrames(frames);
                }

                if (json.has("lines")) {
                    List<String> lines = new ArrayList<>();
                    json.getAsJsonArray("lines").forEach(e -> lines.add(e.getAsString()));
                    plugin.getScoreboardManager().setLines(lines);
                }

                return "{\"success\": true}";
            } catch (Exception e) {
                response.status(400);
                return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
            }
        });

        // API: Hazır şablonları getir (auth gerekli)
        Spark.get("/api/templates", (request, response) -> {
            response.type("application/json");
            String sessionId = request.headers("Authorization");

            if (!isValidSession(sessionId)) {
                response.status(401);
                return "{\"success\": false, \"error\": \"Unauthorized\"}";
            }

            return gson.toJson(getPresetTemplates());
        });

        // API: Şablon uygula (auth gerekli)
        Spark.post("/api/templates/apply", (request, response) -> {
            response.type("application/json");
            String sessionId = request.headers("Authorization");

            if (!isValidSession(sessionId)) {
                response.status(401);
                return "{\"success\": false, \"error\": \"Unauthorized\"}";
            }

            try {
                JsonObject json = gson.fromJson(request.body(), JsonObject.class);
                String templateId = json.get("templateId").getAsString();

                Map<String, Object> template = getPresetTemplates().stream()
                        .filter(t -> t.get("id").equals(templateId))
                        .findFirst()
                        .orElse(null);

                if (template != null) {
                    plugin.getScoreboardManager().setTitleFrames((List<String>) template.get("titleFrames"));
                    plugin.getScoreboardManager().setLines((List<String>) template.get("lines"));
                    plugin.getScoreboardManager().setUpdateInterval((Integer) template.get("updateInterval"));
                    return "{\"success\": true}";
                } else {
                    response.status(404);
                    return "{\"success\": false, \"error\": \"Template not found\"}";
                }
            } catch (Exception e) {
                response.status(400);
                return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
            }
        });

        Spark.init();
    }

    public void stop() {
        Spark.stop();
    }

    private List<Map<String, Object>> getPresetTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();

        // Template 1: Classic
        Map<String, Object> classic = new HashMap<>();
        classic.put("id", "classic");
        classic.put("name", "Klasik");
        classic.put("description", "Basit ve şık bir scoreboard");
        classic.put("updateInterval", 10);
        classic.put("titleFrames", Arrays.asList(
                "&6&l✦ &e&lSunucu &6&l✦",
                "&e&l✦ &6&lSunucu &e&l✦"
        ));
        classic.put("lines", Arrays.asList(
                "&7&m----------------",
                "&f",
                "&e☀ &fOyuncu: &a%player%",
                "&e☀ &fOnline: &a%online%&7/&a%max%",
                "&f",
                "&e☀ &fDünya: &b%world%",
                "&e☀ &fKonum: &b%x%, %y%, %z%",
                "&f",
                "&7&m----------------"
        ));
        templates.add(classic);

        // Template 2: Survival
        Map<String, Object> survival = new HashMap<>();
        survival.put("id", "survival");
        survival.put("name", "Survival");
        survival.put("description", "Survival sunucuları için ideal");
        survival.put("updateInterval", 5);
        survival.put("titleFrames", Arrays.asList(
                "&2&l⚔ &a&lSURVIVAL &2&l⚔",
                "&a&l⚔ &2&lSURVIVAL &a&l⚔",
                "&2&l⚔ &a&lS&2&lURVIVAL &2&l⚔",
                "&2&l⚔ &a&lSU&2&lRVIVAL &2&l⚔",
                "&2&l⚔ &a&lSUR&2&lVIVAL &2&l⚔",
                "&2&l⚔ &a&lSURV&2&lIVAL &2&l⚔",
                "&2&l⚔ &a&lSURVI&2&lVAL &2&l⚔",
                "&2&l⚔ &a&lSURVIV&2&lAL &2&l⚔",
                "&2&l⚔ &a&lSURVIVA&2&lL &2&l⚔",
                "&2&l⚔ &a&lSURVIVAL &2&l⚔"
        ));
        survival.put("lines", Arrays.asList(
                "&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪",
                "",
                "&a❤ &fCan: &c%health%",
                "&a🍖 &fAçlık: &6%food%",
                "&a⭐ &fSeviye: &e%level%",
                "",
                "&a👤 &fOyuncu: &b%player%",
                "&a🌍 &fDünya: &b%world%",
                "",
                "&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪"
        ));
        templates.add(survival);

        // Template 3: PvP
        Map<String, Object> pvp = new HashMap<>();
        pvp.put("id", "pvp");
        pvp.put("name", "PvP Arena");
        pvp.put("description", "PvP sunucuları için agresif tasarım");
        pvp.put("updateInterval", 3);
        pvp.put("titleFrames", Arrays.asList(
                "&4&l⚔ &c&lPVP ARENA &4&l⚔",
                "&c&l⚔ &4&lPVP ARENA &c&l⚔",
                "&4&l✖ &c&lPVP ARENA &4&l✖",
                "&c&l✖ &4&lPVP ARENA &c&l✖"
        ));
        pvp.put("lines", Arrays.asList(
                "&4&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "",
                "&c⚔ &fSavaşçı: &e%player%",
                "&c❤ &fCan: &c%health%",
                "",
                "&c👥 &fArena: &a%online% &7oyuncu",
                "&c📍 &fKonum: &7%x%, %y%, %z%",
                "",
                "&4&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        templates.add(pvp);

        // Template 4: Skyblock
        Map<String, Object> skyblock = new HashMap<>();
        skyblock.put("id", "skyblock");
        skyblock.put("name", "Skyblock");
        skyblock.put("description", "Skyblock sunucuları için");
        skyblock.put("updateInterval", 10);
        skyblock.put("titleFrames", Arrays.asList(
                "&b&l☁ &f&lSKYBLOCK &b&l☁",
                "&f&l☁ &b&lSKYBLOCK &f&l☁",
                "&b&l✦ &f&lSKYBLOCK &b&l✦"
        ));
        skyblock.put("lines", Arrays.asList(
                "&b&m⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤",
                "",
                "&f☁ &bAda Sahibi:",
                "&f  &e%player%",
                "",
                "&f☁ &bOnline: &f%online%",
                "&f☁ &bSeviye: &f%level%",
                "",
                "&b&m⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤⏤"
        ));
        templates.add(skyblock);

        // Template 5: Rainbow
        Map<String, Object> rainbow = new HashMap<>();
        rainbow.put("id", "rainbow");
        rainbow.put("name", "Gökkuşağı");
        rainbow.put("description", "Renkli animasyonlu başlık");
        rainbow.put("updateInterval", 2);
        rainbow.put("titleFrames", Arrays.asList(
                "&c&lE&6&ln&e&lo&a&lB&b&lo&d&la&5&lr&c&ld",
                "&6&lE&e&ln&a&lo&b&lB&d&lo&5&la&c&lr&6&ld",
                "&e&lE&a&ln&b&lo&d&lB&5&lo&c&la&6&lr&e&ld",
                "&a&lE&b&ln&d&lo&5&lB&c&lo&6&la&e&lr&a&ld",
                "&b&lE&d&ln&5&lo&c&lB&6&lo&e&la&a&lr&b&ld",
                "&d&lE&5&ln&c&lo&6&lB&e&lo&a&la&b&lr&d&ld",
                "&5&lE&c&ln&6&lo&e&lB&a&lo&b&la&d&lr&5&ld"
        ));
        rainbow.put("lines", Arrays.asList(
                "&7✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦",
                "",
                "&c♦ &fOyuncu: &b%player%",
                "&6♦ &fOnline: &a%online%&7/&a%max%",
                "&e♦ &fDünya: &d%world%",
                "",
                "&a♦ &fKonum:",
                "&b  &7X: &f%x% &7Y: &f%y% &7Z: &f%z%",
                "",
                "&7✦✦✦✦✦✦✦✦✦✦✦✦✦✦✦"
        ));
        templates.add(rainbow);

        // Template 6: Minimalist
        Map<String, Object> minimal = new HashMap<>();
        minimal.put("id", "minimal");
        minimal.put("name", "Minimalist");
        minimal.put("description", "Sade ve temiz tasarım");
        minimal.put("updateInterval", 20);
        minimal.put("titleFrames", Arrays.asList(
                "&f&lSERVER"
        ));
        minimal.put("lines", Arrays.asList(
                "&8─────────────",
                "",
                "&7• &f%player%",
                "&7• &f%online% online",
                "",
                "&8─────────────"
        ));
        templates.add(minimal);

        return templates;
    }

    private String getLoginPage() {
        return """
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>EnoBoard - Giriş</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #fff;
        }
        
        .login-container {
            background: rgba(255, 255, 255, 0.05);
            border-radius: 20px;
            padding: 40px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            width: 100%;
            max-width: 400px;
            box-shadow: 0 25px 50px rgba(0, 0, 0, 0.3);
        }
        
        .login-header {
            text-align: center;
            margin-bottom: 35px;
        }
        
        .login-header h1 {
            font-size: 2.5em;
            background: linear-gradient(45deg, #00d9ff, #00ff88);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 10px;
        }
        
        .login-header p {
            color: #888;
            font-size: 0.95em;
        }
        
        .form-group {
            margin-bottom: 25px;
        }
        
        .form-group label {
            display: block;
            margin-bottom: 8px;
            color: #aaa;
            font-size: 0.9em;
            font-weight: 500;
        }
        
        .form-group input {
            width: 100%;
            padding: 15px;
            border: 2px solid rgba(255, 255, 255, 0.1);
            border-radius: 10px;
            background: rgba(0, 0, 0, 0.3);
            color: #fff;
            font-size: 1em;
            transition: all 0.3s;
        }
        
        .form-group input:focus {
            outline: none;
            border-color: #00d9ff;
            box-shadow: 0 0 20px rgba(0, 217, 255, 0.2);
        }
        
        .form-group input::placeholder {
            color: #666;
        }
        
        .btn-login {
            width: 100%;
            padding: 15px;
            border: none;
            border-radius: 10px;
            background: linear-gradient(45deg, #00d9ff, #00ff88);
            color: #000;
            font-size: 1.1em;
            font-weight: bold;
            cursor: pointer;
            transition: all 0.3s;
            margin-top: 10px;
        }
        
        .btn-login:hover {
            transform: translateY(-3px);
            box-shadow: 0 10px 30px rgba(0, 217, 255, 0.4);
        }
        
        .btn-login:active {
            transform: translateY(-1px);
        }
        
        .error-message {
            background: rgba(255, 68, 68, 0.2);
            border: 1px solid #ff4444;
            color: #ff6b6b;
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 20px;
            text-align: center;
            display: none;
            font-size: 0.9em;
        }
        
        .error-message.show {
            display: block;
            animation: shake 0.5s ease-in-out;
        }
        
        @keyframes shake {
            0%, 100% { transform: translateX(0); }
            25% { transform: translateX(-10px); }
            75% { transform: translateX(10px); }
        }
        
        .login-footer {
            text-align: center;
            margin-top: 25px;
            color: #666;
            font-size: 0.85em;
        }
        
        .lock-icon {
            font-size: 4em;
            margin-bottom: 15px;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="login-header">
            <div class="lock-icon">🔐</div>
            <h1>EnoBoard</h1>
            <p>Admin Paneline Giriş Yapın</p>
        </div>
        
        <div class="error-message" id="errorMessage">
            Geçersiz kullanıcı adı veya şifre!
        </div>
        
        <form id="loginForm" onsubmit="return handleLogin(event)">
            <div class="form-group">
                <label for="username">👤 Kullanıcı Adı</label>
                <input type="text" id="username" name="username" placeholder="Kullanıcı adınızı girin" required autocomplete="username">
            </div>
            
            <div class="form-group">
                <label for="password">🔑 Şifre</label>
                <input type="password" id="password" name="password" placeholder="Şifrenizi girin" required autocomplete="current-password">
            </div>
            
            <button type="submit" class="btn-login" id="loginBtn">
                Giriş Yap
            </button>
        </form>
        
        <div class="login-footer">
            <p>🛡️ Güvenli bağlantı ile korunmaktadır</p>
        </div>
    </div>
    
    <script>
        // Sayfa yüklendiğinde session kontrolü
        window.onload = async function() {
            const sessionId = localStorage.getItem('enoboard_session');
            if (sessionId) {
                try {
                    const response = await fetch('/api/verify', {
                        headers: { 'Authorization': sessionId }
                    });
                    const result = await response.json();
                    if (result.valid) {
                        window.location.href = '/panel';
                    }
                } catch (e) {
                    localStorage.removeItem('enoboard_session');
                }
            }
        };
        
        async function handleLogin(event) {
            event.preventDefault();
            
            const username = document.getElementById('username').value;
            const password = document.getElementById('password').value;
            const loginBtn = document.getElementById('loginBtn');
            const errorMsg = document.getElementById('errorMessage');
            
            loginBtn.textContent = 'Giriş yapılıyor...';
            loginBtn.disabled = true;
            errorMsg.classList.remove('show');
            
            try {
                const response = await fetch('/api/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password })
                });
                
                const result = await response.json();
                
                if (result.success) {
                    localStorage.setItem('enoboard_session', result.sessionId);
                    window.location.href = '/panel';
                } else {
                    errorMsg.textContent = result.error || 'Giriş başarısız!';
                    errorMsg.classList.add('show');
                    loginBtn.textContent = 'Giriş Yap';
                    loginBtn.disabled = false;
                }
            } catch (error) {
                errorMsg.textContent = 'Bağlantı hatası!';
                errorMsg.classList.add('show');
                loginBtn.textContent = 'Giriş Yap';
                loginBtn.disabled = false;
            }
            
            return false;
        }
    </script>
</body>
</html>
""";
    }

    private String getPanelPage() {
        return """
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>EnoBoard - Admin Panel</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            min-height: 100vh;
            color: #fff;
        }
        
        .navbar {
            background: rgba(0, 0, 0, 0.3);
            padding: 15px 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid rgba(255, 255, 255, 0.1);
        }
        
        .navbar-brand {
            font-size: 1.5em;
            font-weight: bold;
            background: linear-gradient(45deg, #00d9ff, #00ff88);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }
        
        .navbar-user {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        
        .navbar-user span {
            color: #aaa;
        }
        
        .btn-logout {
            padding: 8px 20px;
            border: 1px solid #ff4444;
            border-radius: 6px;
            background: transparent;
            color: #ff4444;
            cursor: pointer;
            transition: all 0.3s;
            font-weight: 500;
        }
        
        .btn-logout:hover {
            background: #ff4444;
            color: #fff;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        
        header {
            text-align: center;
            padding: 30px 0;
        }
        
        header h1 {
            font-size: 2.5em;
            background: linear-gradient(45deg, #00d9ff, #00ff88);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 10px;
        }
        
        header p {
            color: #888;
            font-size: 1.1em;
        }
        
        .main-content {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 30px;
        }
        
        @media (max-width: 900px) {
            .main-content {
                grid-template-columns: 1fr;
            }
        }
        
        .card {
            background: rgba(255, 255, 255, 0.05);
            border-radius: 15px;
            padding: 25px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        
        .card h2 {
            color: #00d9ff;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .form-group {
            margin-bottom: 20px;
        }
        
        .form-group label {
            display: block;
            margin-bottom: 8px;
            color: #aaa;
            font-size: 0.9em;
        }
        
        .form-group input,
        .form-group textarea {
            width: 100%;
            padding: 12px;
            border: 1px solid rgba(255, 255, 255, 0.2);
            border-radius: 8px;
            background: rgba(0, 0, 0, 0.3);
            color: #fff;
            font-size: 1em;
            transition: border-color 0.3s;
        }
        
        .form-group input:focus,
        .form-group textarea:focus {
            outline: none;
            border-color: #00d9ff;
        }
        
        .form-group textarea {
            min-height: 150px;
            font-family: 'Courier New', monospace;
            resize: vertical;
        }
        
        .switch-container {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        
        .switch {
            position: relative;
            width: 60px;
            height: 30px;
        }
        
        .switch input {
            opacity: 0;
            width: 0;
            height: 0;
        }
        
        .slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: #333;
            transition: 0.4s;
            border-radius: 30px;
        }
        
        .slider:before {
            position: absolute;
            content: "";
            height: 22px;
            width: 22px;
            left: 4px;
            bottom: 4px;
            background-color: #fff;
            transition: 0.4s;
            border-radius: 50%;
        }
        
        input:checked + .slider {
            background: linear-gradient(45deg, #00d9ff, #00ff88);
        }
        
        input:checked + .slider:before {
            transform: translateX(30px);
        }
        
        .btn {
            padding: 12px 25px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 1em;
            font-weight: bold;
            transition: all 0.3s;
        }
        
        .btn-primary {
            background: linear-gradient(45deg, #00d9ff, #00ff88);
            color: #000;
        }
        
        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 20px rgba(0, 217, 255, 0.4);
        }
        
        .btn-secondary {
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
            border: 1px solid rgba(255, 255, 255, 0.2);
        }
        
        .btn-secondary:hover {
            background: rgba(255, 255, 255, 0.2);
        }
        
        .templates-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
            gap: 15px;
            margin-top: 20px;
        }
        
        .template-card {
            background: rgba(0, 0, 0, 0.3);
            border-radius: 10px;
            padding: 20px;
            cursor: pointer;
            transition: all 0.3s;
            border: 2px solid transparent;
        }
        
        .template-card:hover {
            border-color: #00d9ff;
            transform: translateY(-3px);
        }
        
        .template-card.selected {
            border-color: #00ff88;
            background: rgba(0, 255, 136, 0.1);
        }
        
        .template-card h3 {
            color: #00d9ff;
            margin-bottom: 8px;
        }
        
        .template-card p {
            color: #888;
            font-size: 0.9em;
        }
        
        .preview-box {
            background: #000;
            border-radius: 10px;
            padding: 15px;
            margin-top: 20px;
            font-family: 'Courier New', monospace;
            min-height: 200px;
        }
        
        .preview-title {
            text-align: center;
            font-weight: bold;
            margin-bottom: 10px;
            font-size: 1.1em;
        }
        
        .preview-line {
            padding: 2px 0;
            font-size: 0.9em;
        }
        
        .status-message {
            position: fixed;
            bottom: 20px;
            right: 20px;
            padding: 15px 25px;
            border-radius: 10px;
            font-weight: bold;
            transform: translateX(150%);
            transition: transform 0.3s;
            z-index: 1000;
        }
        
        .status-message.show {
            transform: translateX(0);
        }
        
        .status-message.success {
            background: linear-gradient(45deg, #00ff88, #00d9ff);
            color: #000;
        }
        
        .status-message.error {
            background: linear-gradient(45deg, #ff4444, #ff8800);
            color: #fff;
        }
        
        .placeholders-info {
            background: rgba(0, 217, 255, 0.1);
            border-radius: 8px;
            padding: 15px;
            margin-top: 15px;
        }
        
        .placeholders-info h4 {
            color: #00d9ff;
            margin-bottom: 10px;
        }
        
        .placeholders-info code {
            background: rgba(0, 0, 0, 0.3);
            padding: 2px 6px;
            border-radius: 4px;
            margin: 2px;
            display: inline-block;
            font-size: 0.85em;
        }
        
        .button-group {
            display: flex;
            gap: 10px;
            margin-top: 20px;
        }
    </style>
</head>
<body>
    <nav class="navbar">
        <div class="navbar-brand">⚡ EnoBoard Admin</div>
        <div class="navbar-user">
            <span>👤 Admin</span>
            <button class="btn-logout" onclick="logout()">🚪 Çıkış</button>
        </div>
    </nav>

    <div class="container">
        <header>
            <h1>⚡ EnoBoard</h1>
            <p>Minecraft Animasyonlu Scoreboard Yönetim Paneli</p>
        </header>
        
        <div class="main-content">
            <div class="left-column">
                <div class="card">
                    <h2>⚙️ Genel Ayarlar</h2>
                    
                    <div class="form-group">
                        <div class="switch-container">
                            <label class="switch">
                                <input type="checkbox" id="enabled" checked>
                                <span class="slider"></span>
                            </label>
                            <span>Scoreboard Aktif</span>
                        </div>
                    </div>
                    
                    <div class="form-group">
                        <label>Güncelleme Aralığı (tick - 20 tick = 1 saniye)</label>
                        <input type="number" id="updateInterval" value="5" min="1" max="100">
                    </div>
                    
                    <div class="form-group">
                        <label>Başlık Frameleri (her satır bir frame)</label>
                        <textarea id="titleFrames" placeholder="&6&l✦ &e&lSunucu &6&l✦"></textarea>
                    </div>
                    
                    <div class="form-group">
                        <label>Scoreboard Satırları</label>
                        <textarea id="lines" placeholder="&7Hosgeldiniz!"></textarea>
                    </div>
                    
                    <div class="placeholders-info">
                        <h4>📝 Kullanılabilir Placeholderlar:</h4>
                        <code>%player%</code>
                        <code>%online%</code>
                        <code>%max%</code>
                        <code>%world%</code>
                        <code>%health%</code>
                        <code>%food%</code>
                        <code>%level%</code>
                        <code>%x%</code>
                        <code>%y%</code>
                        <code>%z%</code>
                    </div>
                    
                    <div class="button-group">
                        <button class="btn btn-primary" onclick="saveConfig()">💾 Kaydet</button>
                        <button class="btn btn-secondary" onclick="loadConfig()">🔄 Yenile</button>
                    </div>
                </div>
            </div>
            
            <div class="right-column">
                <div class="card">
                    <h2>📦 Hazır Şablonlar</h2>
                    <p style="color: #888; margin-bottom: 15px;">Bir şablona tıklayarak hızlıca uygulayın</p>
                    
                    <div class="templates-grid" id="templatesGrid">
                        <!-- Templates will be loaded here -->
                    </div>
                </div>
                
                <div class="card" style="margin-top: 20px;">
                    <h2>👁️ Önizleme</h2>
                    <div class="preview-box" id="previewBox">
                        <div class="preview-title" id="previewTitle">Başlık</div>
                        <div id="previewLines"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    
    <div class="status-message" id="statusMessage"></div>
    
    <script>
        const sessionId = localStorage.getItem('enoboard_session');
        
        // Session kontrolü
        window.onload = async function() {
            if (!sessionId) {
                window.location.href = '/';
                return;
            }
            
            try {
                const response = await fetch('/api/verify', {
                    headers: { 'Authorization': sessionId }
                });
                const result = await response.json();
                if (!result.valid) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
            } catch (e) {
                localStorage.removeItem('enoboard_session');
                window.location.href = '/';
                return;
            }
            
            loadConfig();
            loadTemplates();
        };
        
        async function logout() {
            try {
                await fetch('/api/logout', {
                    method: 'POST',
                    headers: { 'Authorization': sessionId }
                });
            } catch (e) {}
            localStorage.removeItem('enoboard_session');
            window.location.href = '/';
        }
        
        // Minecraft renk kodları
        const colorCodes = {
            '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
            '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
            '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF',
            'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'
        };
        
        function parseMinecraftColors(text) {
            let result = '';
            let currentColor = '#FFFFFF';
            let isBold = false;
            let isItalic = false;
            let isUnderline = false;
            let isStrike = false;
            
            for (let i = 0; i < text.length; i++) {
                if (text[i] === '&' && i + 1 < text.length) {
                    const code = text[i + 1].toLowerCase();
                    if (colorCodes[code]) {
                        currentColor = colorCodes[code];
                        i++;
                        continue;
                    } else if (code === 'l') {
                        isBold = true;
                        i++;
                        continue;
                    } else if (code === 'o') {
                        isItalic = true;
                        i++;
                        continue;
                    } else if (code === 'n') {
                        isUnderline = true;
                        i++;
                        continue;
                    } else if (code === 'm') {
                        isStrike = true;
                        i++;
                        continue;
                    } else if (code === 'r') {
                        currentColor = '#FFFFFF';
                        isBold = false;
                        isItalic = false;
                        isUnderline = false;
                        isStrike = false;
                        i++;
                        continue;
                    }
                }
                
                let style = `color: ${currentColor};`;
                if (isBold) style += 'font-weight: bold;';
                if (isItalic) style += 'font-style: italic;';
                if (isUnderline) style += 'text-decoration: underline;';
                if (isStrike) style += 'text-decoration: line-through;';
                
                result += `<span style="${style}">${text[i]}</span>`;
            }
            
            return result;
        }
        
        function showMessage(message, type) {
            const el = document.getElementById('statusMessage');
            el.textContent = message;
            el.className = `status-message ${type} show`;
            setTimeout(() => el.classList.remove('show'), 3000);
        }
        
        async function loadConfig() {
            try {
                const response = await fetch('/api/config', {
                    headers: { 'Authorization': sessionId }
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const config = await response.json();
                
                document.getElementById('enabled').checked = config.enabled;
                document.getElementById('updateInterval').value = config.updateInterval;
                document.getElementById('titleFrames').value = config.titleFrames.join('\\n');
                document.getElementById('lines').value = config.lines.join('\\n');
                
                updatePreview();
                showMessage('Ayarlar yüklendi!', 'success');
            } catch (error) {
                showMessage('Yükleme hatası: ' + error.message, 'error');
            }
        }
        
        async function saveConfig() {
            try {
                const config = {
                    enabled: document.getElementById('enabled').checked,
                    updateInterval: parseInt(document.getElementById('updateInterval').value),
                    titleFrames: document.getElementById('titleFrames').value.split('\\n').filter(l => l.trim()),
                    lines: document.getElementById('lines').value.split('\\n')
                };
                
                const response = await fetch('/api/config', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'Authorization': sessionId
                    },
                    body: JSON.stringify(config)
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const result = await response.json();
                if (result.success) {
                    showMessage('Ayarlar kaydedildi!', 'success');
                } else {
                    showMessage('Hata: ' + result.error, 'error');
                }
            } catch (error) {
                showMessage('Kaydetme hatası: ' + error.message, 'error');
            }
        }
        
        async function loadTemplates() {
            try {
                const response = await fetch('/api/templates', {
                    headers: { 'Authorization': sessionId }
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const templates = await response.json();
                
                const grid = document.getElementById('templatesGrid');
                grid.innerHTML = '';
                
                templates.forEach(template => {
                    const card = document.createElement('div');
                    card.className = 'template-card';
                    card.innerHTML = `
                        <h3>${template.name}</h3>
                        <p>${template.description}</p>
                    `;
                    card.onclick = () => applyTemplate(template.id);
                    grid.appendChild(card);
                });
            } catch (error) {
                console.error('Template yükleme hatası:', error);
            }
        }
        
        async function applyTemplate(templateId) {
            try {
                const response = await fetch('/api/templates/apply', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'Authorization': sessionId
                    },
                    body: JSON.stringify({ templateId })
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const result = await response.json();
                if (result.success) {
                    showMessage('Şablon uygulandı!', 'success');
                    loadConfig();
                } else {
                    showMessage('Hata: ' + result.error, 'error');
                }
            } catch (error) {
                showMessage('Şablon uygulama hatası: ' + error.message, 'error');
            }
        }
        
        function updatePreview() {
            const titleFrames = document.getElementById('titleFrames').value.split('\\n').filter(l => l.trim());
            const lines = document.getElementById('lines').value.split('\\n');
            
            const previewTitle = document.getElementById('previewTitle');
            const previewLines = document.getElementById('previewLines');
            
            if (titleFrames.length > 0) {
                previewTitle.innerHTML = parseMinecraftColors(titleFrames[0]);
            }
            
            previewLines.innerHTML = lines.map(line => 
                `<div class="preview-line">${parseMinecraftColors(line) || '&nbsp;'}</div>`
            ).join('');
        }
        
        // Event listeners
        document.getElementById('titleFrames').addEventListener('input', updatePreview);
        document.getElementById('lines').addEventListener('input', updatePreview);
        
        // Animate title preview
        let currentFrame = 0;
        setInterval(() => {
            const titleFrames = document.getElementById('titleFrames').value.split('\\n').filter(l => l.trim());
            if (titleFrames.length > 0) {
                currentFrame = (currentFrame + 1) % titleFrames.length;
                document.getElementById('previewTitle').innerHTML = parseMinecraftColors(titleFrames[currentFrame]);
            }
        }, 500);
    </script>
</body>
</html>
""";
    }

    private String getHtmlPage() {
        return """
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>EnoBoard - Web Panel</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            min-height: 100vh;
            color: #fff;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        
        header {
            text-align: center;
            padding: 40px 0;
        }
        
        header h1 {
            font-size: 3em;
            background: linear-gradient(45deg, #00d9ff, #00ff88);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 10px;
        }
        
        header p {
            color: #888;
            font-size: 1.1em;
        }
        
        .main-content {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 30px;
        }
        
        @media (max-width: 900px) {
            .main-content {
                grid-template-columns: 1fr;
            }
        }
        
        .card {
            background: rgba(255, 255, 255, 0.05);
            border-radius: 15px;
            padding: 25px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        
        .card h2 {
            color: #00d9ff;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .form-group {
            margin-bottom: 20px;
        }
        
        .form-group label {
            display: block;
            margin-bottom: 8px;
            color: #aaa;
            font-size: 0.9em;
        }
        
        .form-group input,
        .form-group textarea {
            width: 100%;
            padding: 12px;
            border: 1px solid rgba(255, 255, 255, 0.2);
            border-radius: 8px;
            background: rgba(0, 0, 0, 0.3);
            color: #fff;
            font-size: 1em;
            transition: border-color 0.3s;
        }
        
        .form-group input:focus,
        .form-group textarea:focus {
            outline: none;
            border-color: #00d9ff;
        }
        
        .form-group textarea {
            min-height: 150px;
            font-family: 'Courier New', monospace;
            resize: vertical;
        }
        
        .switch-container {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        
        .switch {
            position: relative;
            width: 60px;
            height: 30px;
        }
        
        .switch input {
            opacity: 0;
            width: 0;
            height: 0;
        }
        
        .slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: #333;
            transition: 0.4s;
            border-radius: 30px;
        }
        
        .slider:before {
            position: absolute;
            content: "";
            height: 22px;
            width: 22px;
            left: 4px;
            bottom: 4px;
            background-color: #fff;
            transition: 0.4s;
            border-radius: 50%;
        }
        
        input:checked + .slider {
            background: linear-gradient(45deg, #00d9ff, #00ff88);
        }
        
        input:checked + .slider:before {
            transform: translateX(30px);
        }
        
        .btn {
            padding: 12px 25px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 1em;
            font-weight: bold;
            transition: all 0.3s;
        }
        
        .btn-primary {
            background: linear-gradient(45deg, #00d9ff, #00ff88);
            color: #000;
        }
        
        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 20px rgba(0, 217, 255, 0.4);
        }
        
        .btn-secondary {
            background: rgba(255, 255, 255, 0.1);
            color: #fff;
            border: 1px solid rgba(255, 255, 255, 0.2);
        }
        
        .btn-secondary:hover {
            background: rgba(255, 255, 255, 0.2);
        }
        
        .templates-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
            gap: 15px;
            margin-top: 20px;
        }
        
        .template-card {
            background: rgba(0, 0, 0, 0.3);
            border-radius: 10px;
            padding: 20px;
            cursor: pointer;
            transition: all 0.3s;
            border: 2px solid transparent;
        }
        
        .template-card:hover {
            border-color: #00d9ff;
            transform: translateY(-3px);
        }
        
        .template-card.selected {
            border-color: #00ff88;
            background: rgba(0, 255, 136, 0.1);
        }
        
        .template-card h3 {
            color: #00d9ff;
            margin-bottom: 8px;
        }
        
        .template-card p {
            color: #888;
            font-size: 0.9em;
        }
        
        .preview-box {
            background: #000;
            border-radius: 10px;
            padding: 15px;
            margin-top: 20px;
            font-family: 'Courier New', monospace;
            min-height: 200px;
        }
        
        .preview-title {
            text-align: center;
            font-weight: bold;
            margin-bottom: 10px;
            font-size: 1.1em;
        }
        
        .preview-line {
            padding: 2px 0;
            font-size: 0.9em;
        }
        
        .status-message {
            position: fixed;
            bottom: 20px;
            right: 20px;
            padding: 15px 25px;
            border-radius: 10px;
            font-weight: bold;
            transform: translateX(150%);
            transition: transform 0.3s;
            z-index: 1000;
        }
        
        .status-message.show {
            transform: translateX(0);
        }
        
        .status-message.success {
            background: linear-gradient(45deg, #00ff88, #00d9ff);
            color: #000;
        }
        
        .status-message.error {
            background: linear-gradient(45deg, #ff4444, #ff8800);
            color: #fff;
        }
        
        .placeholders-info {
            background: rgba(0, 217, 255, 0.1);
            border-radius: 8px;
            padding: 15px;
            margin-top: 15px;
        }
        
        .placeholders-info h4 {
            color: #00d9ff;
            margin-bottom: 10px;
        }
        
        .placeholders-info code {
            background: rgba(0, 0, 0, 0.3);
            padding: 2px 6px;
            border-radius: 4px;
            margin: 2px;
            display: inline-block;
            font-size: 0.85em;
        }
        
        .button-group {
            display: flex;
            gap: 10px;
            margin-top: 20px;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>⚡ EnoBoard</h1>
            <p>Minecraft Animasyonlu Scoreboard Yönetim Paneli</p>
        </header>
        
        <div class="main-content">
            <div class="left-column">
                <div class="card">
                    <h2>⚙️ Genel Ayarlar</h2>
                    
                    <div class="form-group">
                        <div class="switch-container">
                            <label class="switch">
                                <input type="checkbox" id="enabled" checked>
                                <span class="slider"></span>
                            </label>
                            <span>Scoreboard Aktif</span>
                        </div>
                    </div>
                    
                    <div class="form-group">
                        <label>Güncelleme Aralığı (tick - 20 tick = 1 saniye)</label>
                        <input type="number" id="updateInterval" value="5" min="1" max="100">
                    </div>
                    
                    <div class="form-group">
                        <label>Başlık Frameleri (her satır bir frame)</label>
                        <textarea id="titleFrames" placeholder="&6&l✦ &e&lSunucu &6&l✦"></textarea>
                    </div>
                    
                    <div class="form-group">
                        <label>Scoreboard Satırları</label>
                        <textarea id="lines" placeholder="&7Hosgeldiniz!"></textarea>
                    </div>
                    
                    <div class="placeholders-info">
                        <h4>📝 Kullanılabilir Placeholderlar:</h4>
                        <code>%player%</code>
                        <code>%online%</code>
                        <code>%max%</code>
                        <code>%world%</code>
                        <code>%health%</code>
                        <code>%food%</code>
                        <code>%level%</code>
                        <code>%x%</code>
                        <code>%y%</code>
                        <code>%z%</code>
                    </div>
                    
                    <div class="button-group">
                        <button class="btn btn-primary" onclick="saveConfig()">💾 Kaydet</button>
                        <button class="btn btn-secondary" onclick="loadConfig()">🔄 Yenile</button>
                    </div>
                </div>
            </div>
            
            <div class="right-column">
                <div class="card">
                    <h2>📦 Hazır Şablonlar</h2>
                    <p style="color: #888; margin-bottom: 15px;">Bir şablona tıklayarak hızlıca uygulayın</p>
                    
                    <div class="templates-grid" id="templatesGrid">
                        <!-- Templates will be loaded here -->
                    </div>
                </div>
                
                <div class="card" style="margin-top: 20px;">
                    <h2>👁️ Önizleme</h2>
                    <div class="preview-box" id="previewBox">
                        <div class="preview-title" id="previewTitle">Başlık</div>
                        <div id="previewLines"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    
    <div class="status-message" id="statusMessage"></div>
    
    <script>
        const sessionId = localStorage.getItem('enoboard_session');
        
        // Session kontrolü
        window.onload = async function() {
            if (!sessionId) {
                window.location.href = '/';
                return;
            }
            
            try {
                const response = await fetch('/api/verify', {
                    headers: { 'Authorization': sessionId }
                });
                const result = await response.json();
                if (!result.valid) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
            } catch (e) {
                localStorage.removeItem('enoboard_session');
                window.location.href = '/';
                return;
            }
            
            loadConfig();
            loadTemplates();
        };
        
        async function logout() {
            try {
                await fetch('/api/logout', {
                    method: 'POST',
                    headers: { 'Authorization': sessionId }
                });
            } catch (e) {}
            localStorage.removeItem('enoboard_session');
            window.location.href = '/';
        }
        
        // Minecraft renk kodları
        const colorCodes = {
            '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
            '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
            '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF',
            'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'
        };
        
        function parseMinecraftColors(text) {
            let result = '';
            let currentColor = '#FFFFFF';
            let isBold = false;
            let isItalic = false;
            let isUnderline = false;
            let isStrike = false;
            
            for (let i = 0; i < text.length; i++) {
                if (text[i] === '&' && i + 1 < text.length) {
                    const code = text[i + 1].toLowerCase();
                    if (colorCodes[code]) {
                        currentColor = colorCodes[code];
                        i++;
                        continue;
                    } else if (code === 'l') {
                        isBold = true;
                        i++;
                        continue;
                    } else if (code === 'o') {
                        isItalic = true;
                        i++;
                        continue;
                    } else if (code === 'n') {
                        isUnderline = true;
                        i++;
                        continue;
                    } else if (code === 'm') {
                        isStrike = true;
                        i++;
                        continue;
                    } else if (code === 'r') {
                        currentColor = '#FFFFFF';
                        isBold = false;
                        isItalic = false;
                        isUnderline = false;
                        isStrike = false;
                        i++;
                        continue;
                    }
                }
                
                let style = `color: ${currentColor};`;
                if (isBold) style += 'font-weight: bold;';
                if (isItalic) style += 'font-style: italic;';
                if (isUnderline) style += 'text-decoration: underline;';
                if (isStrike) style += 'text-decoration: line-through;';
                
                result += `<span style="${style}">${text[i]}</span>`;
            }
            
            return result;
        }
        
        function showMessage(message, type) {
            const el = document.getElementById('statusMessage');
            el.textContent = message;
            el.className = `status-message ${type} show`;
            setTimeout(() => el.classList.remove('show'), 3000);
        }
        
        async function loadConfig() {
            try {
                const response = await fetch('/api/config', {
                    headers: { 'Authorization': sessionId }
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const config = await response.json();
                
                document.getElementById('enabled').checked = config.enabled;
                document.getElementById('updateInterval').value = config.updateInterval;
                document.getElementById('titleFrames').value = config.titleFrames.join('\\n');
                document.getElementById('lines').value = config.lines.join('\\n');
                
                updatePreview();
                showMessage('Ayarlar yüklendi!', 'success');
            } catch (error) {
                showMessage('Yükleme hatası: ' + error.message, 'error');
            }
        }
        
        async function saveConfig() {
            try {
                const config = {
                    enabled: document.getElementById('enabled').checked,
                    updateInterval: parseInt(document.getElementById('updateInterval').value),
                    titleFrames: document.getElementById('titleFrames').value.split('\\n').filter(l => l.trim()),
                    lines: document.getElementById('lines').value.split('\\n')
                };
                
                const response = await fetch('/api/config', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'Authorization': sessionId
                    },
                    body: JSON.stringify(config)
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const result = await response.json();
                if (result.success) {
                    showMessage('Ayarlar kaydedildi!', 'success');
                } else {
                    showMessage('Hata: ' + result.error, 'error');
                }
            } catch (error) {
                showMessage('Kaydetme hatası: ' + error.message, 'error');
            }
        }
        
        async function loadTemplates() {
            try {
                const response = await fetch('/api/templates', {
                    headers: { 'Authorization': sessionId }
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const templates = await response.json();
                
                const grid = document.getElementById('templatesGrid');
                grid.innerHTML = '';
                
                templates.forEach(template => {
                    const card = document.createElement('div');
                    card.className = 'template-card';
                    card.innerHTML = `
                        <h3>${template.name}</h3>
                        <p>${template.description}</p>
                    `;
                    card.onclick = () => applyTemplate(template.id);
                    grid.appendChild(card);
                });
            } catch (error) {
                console.error('Template yükleme hatası:', error);
            }
        }
        
        async function applyTemplate(templateId) {
            try {
                const response = await fetch('/api/templates/apply', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'Authorization': sessionId
                    },
                    body: JSON.stringify({ templateId })
                });
                
                if (response.status === 401) {
                    localStorage.removeItem('enoboard_session');
                    window.location.href = '/';
                    return;
                }
                
                const result = await response.json();
                if (result.success) {
                    showMessage('Şablon uygulandı!', 'success');
                    loadConfig();
                } else {
                    showMessage('Hata: ' + result.error, 'error');
                }
            } catch (error) {
                showMessage('Şablon uygulama hatası: ' + error.message, 'error');
            }
        }
        
        function updatePreview() {
            const titleFrames = document.getElementById('titleFrames').value.split('\\n').filter(l => l.trim());
            const lines = document.getElementById('lines').value.split('\\n');
            
            const previewTitle = document.getElementById('previewTitle');
            const previewLines = document.getElementById('previewLines');
            
            if (titleFrames.length > 0) {
                previewTitle.innerHTML = parseMinecraftColors(titleFrames[0]);
            }
            
            previewLines.innerHTML = lines.map(line => 
                `<div class="preview-line">${parseMinecraftColors(line) || '&nbsp;'}</div>`
            ).join('');
        }
        
        // Event listeners
        document.getElementById('titleFrames').addEventListener('input', updatePreview);
        document.getElementById('lines').addEventListener('input', updatePreview);
        
        // Animate title preview
        let currentFrame = 0;
        setInterval(() => {
            const titleFrames = document.getElementById('titleFrames').value.split('\\n').filter(l => l.trim());
            if (titleFrames.length > 0) {
                currentFrame = (currentFrame + 1) % titleFrames.length;
                document.getElementById('previewTitle').innerHTML = parseMinecraftColors(titleFrames[currentFrame]);
            }
        }, 500);
    </script>
</body>
</html>
""";
    }
}
