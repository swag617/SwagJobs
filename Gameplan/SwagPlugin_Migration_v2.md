# Swag Plugin Suite — Full Migration Prompt v2.0
## SwagAPI Services + Shared Web Server Integration

**Feed this document to the agent along with:**
1. `SwagAPI_Complete_Spec.md` — the full SwagAPI build spec (all services including update system)
2. `SwagAPI_WebService_Prompt.md` — the shared web server architecture spec
3. The **full source files** of the plugin being migrated — paste directly or attach as files

> **One plugin per agent session. Never attempt to migrate multiple plugins in one session.**

---

## Table of Contents

1. [The Golden Rules](#1-the-golden-rules)
2. [What SwagAPI Provides](#2-what-swagapi-provides)
3. [Universal Changes — Every Plugin](#3-universal-changes--every-plugin)
4. [Database Migration](#4-database-migration)
5. [Economy Migration](#5-economy-migration)
6. [Utility Class Migration](#6-utility-class-migration)
7. [Web Server Migration](#7-web-server-migration)
8. [Event Bus Migration](#8-event-bus-migration)
9. [GitHub Actions — Update Manifest](#9-github-actions--update-manifest)
10. [Per-Plugin Migration Scope](#10-per-plugin-migration-scope)
11. [Per-Plugin Specific Notes](#11-per-plugin-specific-notes)
12. [Post-Migration Checklist](#12-post-migration-checklist)
13. [What the Agent Must NEVER Do](#13-what-the-agent-must-never-do)

---

## 1. The Golden Rules

Read these before writing a single line of code. They apply to every plugin, every session, without exception.

**Rule 1 — Never delete any existing code.**
Only add new fields, methods, imports, and registrations. If something is being replaced (e.g. a local Vault hook replaced by `IEconomyService`), the old code gets **commented out** with a `// MIGRATED: replaced by SwagAPI IEconomyService` comment — it is never deleted. This preserves a complete rollback path.

**Rule 2 — Always output complete files.**
Never produce partial files, snippets with `// rest unchanged`, or diff-style outputs. Every file you touch must be output in its entirety, copy-pasteable and complete. This applies even to large files.

**Rule 3 — One plugin per session.**
Do not attempt to migrate multiple plugins simultaneously. Migrate one plugin completely, verify the deliverables checklist at the end of this document, then stop.

**Rule 4 — Never change handler logic.**
For web server migration: every inner handler class (`FishAPIHandler`, `AuthHandler`, `StaticFileHandler`, etc.) must remain 100% byte-for-byte identical. Only `WebServerManager.start()` and `WebServerManager.stop()` change. No handler inner class is touched.

**Rule 5 — Always include a standalone fallback.**
Every web server migration must include the original `HttpServer.create()` path as a fallback inside an `else` block. The plugin must be able to run standalone without SwagAPI if SwagAPI is absent.

**Rule 6 — Flag frontend URL changes.**
If a plugin has a web UI with JavaScript that makes API calls using absolute paths (e.g. `fetch('/api/fish')`), flag this to the developer at the end of the session. The frontend JS needs its API base URL updated to relative paths (`fetch('./api/fish')`) since the plugin is now served under a prefix path (e.g. `/swagfishing/`). This is a frontend task, out of scope for the Java migration.

**Rule 7 — If uncertain, do not change.**
If you are unsure whether something should be changed, do NOT change it. Add a `// TODO:` comment and flag it in the session summary for the developer to review.

**Rule 8 — Never add unsolicited features.**
Only perform the migration described in this document. Do not refactor, optimise, rename, or restructure anything beyond what is explicitly listed. The developer's existing code style and patterns must be preserved exactly.

---

## 2. What SwagAPI Provides

SwagAPI is a shared library plugin (`com.SwagDev.SwagAPI`) that all Swag plugins must declare as a hard dependency. It provides the following services, all retrieved via Bukkit's `ServicesManager`:

| Service Interface | Purpose |
|---|---|
| `IDatabaseService` | Shared HikariCP connection pool (MySQL or SQLite) |
| `IEconomyService` | Vault economy wrapper — replaces per-plugin Vault hooks |
| `IPlayerDataService` | Shared player profile cache with plugin data modules |
| `IMessagingService` | Color/Adventure text formatting and message delivery |
| `IEventBusService` | Cross-plugin publish/subscribe event bus |
| `IWebService` | Shared HTTP server — one port, all plugin web UIs mounted under prefix paths |
| `IUpdateService` | Version manifest fetching, update notifications, staged jar downloads |

All interfaces live in `com.SwagDev.SwagAPI.api.*`.

**Manifest URL (already live):**
```
https://raw.githubusercontent.com/swag617/swag-versions/main/versions.json
```

---

## 3. Universal Changes — Every Plugin

These changes apply to **every single plugin** being migrated, no exceptions.

### 3.0 Pre-Migration Git Hygiene — Do This First

Before making any code changes, ensure the repo is clean and no sensitive files will be accidentally committed.

#### 3.0.1 Verify / Update .gitignore

Check if a `.gitignore` exists in the project root. If it does not exist, create it. If it exists, ensure it contains at minimum all of the following entries — add any that are missing without removing existing entries:

```gitignore
# Maven build output — never commit compiled artifacts
target/
dependency-reduced-pom.xml

# IntelliJ IDEA project files
.idea/
*.iml
*.iws
*.ipr

# Claude AI settings — personal/local, never commit
.claude/

# Compiled classes and jars
*.class
*.jar

# Runtime files that may appear in project root
config.yml
plugin.yml
sqlite-jdbc.properties
META-INF/
org/

# OS files
.DS_Store
Thumbs.db
```

> **IMPORTANT:** The `.claude/` folder contains personal Claude AI settings and must never be committed to GitHub. If it has already been tracked in a previous commit, untrack it with `git rm -r --cached .claude/` before committing.

#### 3.0.2 Untrack Any Already-Committed Sensitive Files

Run the following to check if any sensitive files are currently tracked that should not be:

```bash
git ls-files .claude/ .idea/ target/
```

If any files appear in the output, untrack them without deleting the local copies:

```bash
git rm -r --cached .claude/
git rm -r --cached .idea/
git rm -r --cached target/
git rm --cached dependency-reduced-pom.xml
```

Then commit the `.gitignore` and the untracking together:

```bash
git add .gitignore
git commit -m "chore: add .gitignore, untrack sensitive and generated files"
```

#### 3.0.3 Commit and Push Confirmation Protocol

**The agent must ALWAYS follow this two-step protocol before any `git commit` or `git push`:**

**Step 1 — Before committing**, show the developer a summary of every file that will be included in the commit:

```
The following files will be committed:
  - src/main/java/com/swagserv/swagfishing/SwagFishing.java
  - src/main/java/com/swagserv/swagfishing/database/DatabaseManager.java
  - .github/workflows/update-manifest.yml
  - .gitignore
  - pom.xml

Do you want to proceed with this commit? (yes/no)
```

Only proceed after the developer explicitly confirms with "yes" or equivalent.

**Step 2 — Before pushing**, confirm again:

```
Ready to push to origin/master (or origin/main).
This will make the following commits visible on GitHub:
  - [commit hash] your commit message here

Do you want to push now? (yes/no)
```

Only push after the developer explicitly confirms.

> **Never auto-commit or auto-push.** Every git operation that writes to the remote must be explicitly approved by the developer. This prevents accidental exposure of sensitive files or unfinished code.

---

Add `SwagAPI` to the `depend:` list. If `depend:` does not exist, create it. Do not remove any existing entries. If Vault was in `softdepend:`, remove it — SwagAPI now handles Vault entirely.

```yaml
# Before:
softdepend: [Vault, PlaceholderAPI]

# After:
depend: [SwagAPI]
softdepend: [PlaceholderAPI]
# Vault removed from softdepend — SwagAPI IEconomyService handles it
```

### 3.2 pom.xml

Add SwagAPI as a `provided` scope dependency. Do not remove any existing dependencies.

```xml
<!-- Add after existing dependencies — do not remove anything -->
<dependency>
    <groupId>com.SwagDev</groupId>
    <artifactId>SwagAPI</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### 3.3 Main Plugin Class — Service Fields

Add these fields alongside any existing fields. **Only add the services the plugin actually uses** — see Section 10 for per-plugin scope.

```java
// ── SwagAPI service references ──────────────────────────────────────────────
// Add only the services this plugin needs (see per-plugin scope table)
private com.SwagDev.SwagAPI.api.IDatabaseService   dbService;
private com.SwagDev.SwagAPI.api.IEconomyService    ecoService;
private com.SwagDev.SwagAPI.api.IPlayerDataService playerService;
private com.SwagDev.SwagAPI.api.IMessagingService  msgService;
private com.SwagDev.SwagAPI.api.IEventBusService   busService;
private com.SwagDev.SwagAPI.api.IWebService        webService;
```

### 3.4 Main Plugin Class — hookSwagAPI() Method

Add this method to the main plugin class. Only include retrieval blocks for services the plugin actually uses.

```java
private boolean hookSwagAPI() {
    org.bukkit.plugin.ServicesManager sm = getServer().getServicesManager();

    // ── Database — hard required by every plugin ─────────────────────────────
    org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IDatabaseService> dbProv =
        sm.getRegistration(com.SwagDev.SwagAPI.api.IDatabaseService.class);
    if (dbProv == null) {
        getLogger().severe("SwagAPI IDatabaseService not found! Is SwagAPI loaded? Disabling.");
        return false;
    }
    dbService = dbProv.getProvider();
    getLogger().info("Hooked SwagAPI IDatabaseService.");

    // ── Economy — only if plugin uses economy ────────────────────────────────
    org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IEconomyService> ecoProv =
        sm.getRegistration(com.SwagDev.SwagAPI.api.IEconomyService.class);
    if (ecoProv != null) {
        ecoService = ecoProv.getProvider();
        if (ecoService.isEnabled()) {
            getLogger().info("Hooked SwagAPI IEconomyService (" + ecoService.getCurrencyName() + ").");
        } else {
            getLogger().warning("SwagAPI IEconomyService not available — economy features disabled.");
        }
    }

    // ── Player data — only if plugin uses shared player profiles ─────────────
    org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IPlayerDataService> playerProv =
        sm.getRegistration(com.SwagDev.SwagAPI.api.IPlayerDataService.class);
    if (playerProv != null) {
        playerService = playerProv.getProvider();
        getLogger().info("Hooked SwagAPI IPlayerDataService.");
    }

    // ── Messaging — only if plugin uses SwagAPI messaging ───────────────────
    org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IMessagingService> msgProv =
        sm.getRegistration(com.SwagDev.SwagAPI.api.IMessagingService.class);
    if (msgProv != null) {
        msgService = msgProv.getProvider();
    }

    // ── Event bus — only if plugin publishes or subscribes to events ─────────
    org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IEventBusService> busProv =
        sm.getRegistration(com.SwagDev.SwagAPI.api.IEventBusService.class);
    if (busProv != null) {
        busService = busProv.getProvider();
        getLogger().info("Hooked SwagAPI IEventBusService.");
    }

    // ── Web service — only if plugin has a web server ────────────────────────
    org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IWebService> webProv =
        sm.getRegistration(com.SwagDev.SwagAPI.api.IWebService.class);
    if (webProv != null) {
        webService = webProv.getProvider();
        getLogger().info("Hooked SwagAPI IWebService.");
    }

    getLogger().info("SwagAPI hook complete.");
    return true;
}
```

### 3.5 Main Plugin Class — onEnable() Order

`hookSwagAPI()` must be the **first substantive action** in `onEnable()`, immediately after `saveDefaultConfig()`. Any manager that needs the database or economy must be initialized after `hookSwagAPI()` returns `true`.

```java
@Override
public void onEnable() {
    instance = this;
    saveDefaultConfig();

    // Step 1 — Hook SwagAPI FIRST, before any manager initialization
    if (!hookSwagAPI()) {
        getServer().getPluginManager().disablePlugin(this);
        return;
    }

    // Step 2 — Now safe to initialize managers that need db/economy
    // Pass dbService into DatabaseManager constructor (see Section 4)
    this.databaseManager = new DatabaseManager(this, dbService);
    this.databaseManager.initialize();

    // Step 3 — Rest of existing onEnable() logic unchanged
    // ...
}
```

### 3.6 Main Plugin Class — Public Getters

Add public getters for each service field so manager classes can access them through the plugin instance:

```java
// Add only getters for services this plugin uses
public com.SwagDev.SwagAPI.api.IDatabaseService   getDbService()     { return dbService; }
public com.SwagDev.SwagAPI.api.IEconomyService    getEcoService()    { return ecoService; }
public com.SwagDev.SwagAPI.api.IPlayerDataService getPlayerService() { return playerService; }
public com.SwagDev.SwagAPI.api.IMessagingService  getMsgService()    { return msgService; }
public com.SwagDev.SwagAPI.api.IEventBusService   getBusService()    { return busService; }
public com.SwagDev.SwagAPI.api.IWebService        getWebService()    { return webService; }
```

---

## 4. Database Migration

### 4.1 What Changes vs What Stays Identical

**Stays 100% identical:**
- All `CREATE TABLE` SQL
- All `SELECT`, `INSERT`, `UPDATE`, `DELETE` query methods
- All `CompletableFuture` async patterns
- All `ON CONFLICT` / `INSERT OR REPLACE` upsert syntax
- All result set parsing logic

**What changes — and ONLY what changes:**
- Remove fields: `HikariDataSource`, `Connection connection`, `SQLiteDatabase`, `DriverManager` references
- Remove methods: `initialize()` pool setup, `connect()`, `close()`, `shutdown()` — the pool lifecycle belongs to SwagAPI now
- Add constructor parameter: `IDatabaseService dbService`
- Replace: `this.connection` / `dataSource.getConnection()` → `dbService.getConnection()`
- Remove: WAL mode `PRAGMA journal_mode=WAL` setup — SwagAPI's DatabaseService handles this

### 4.2 Before / After Pattern

**Before:**
```java
public class DatabaseManager {
    private final MyPlugin plugin;
    private Connection connection;              // ← owns the connection
    private final File databaseFile;

    public DatabaseManager(MyPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "plugin.db");
    }

    public void initialize() throws SQLException {
        Class.forName("org.sqlite.JDBC");
        this.connection = DriverManager.getConnection(
            "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL"); // ← SwagAPI handles this now
        }
        createTables();
    }

    public void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    private Connection getConnection() { return connection; }

    // ... all query methods — UNCHANGED
}
```

**After:**
```java
public class DatabaseManager {
    private final MyPlugin plugin;
    // MIGRATED: connection field removed — pool owned by SwagAPI IDatabaseService
    private final com.SwagDev.SwagAPI.api.IDatabaseService dbService;

    // Constructor updated to accept IDatabaseService
    public DatabaseManager(MyPlugin plugin,
                           com.SwagDev.SwagAPI.api.IDatabaseService dbService) {
        this.plugin    = plugin;
        this.dbService = dbService;
    }

    // initialize() now ONLY creates tables — no pool/connection management
    public void initialize() {
        try {
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create tables", e);
        }
    }

    // MIGRATED: close() removed — SwagAPI owns the pool lifecycle

    // getConnection() now delegates to SwagAPI
    private Connection getConnection() throws SQLException {
        return dbService.getConnection();
    }

    // ALL query methods below are COMPLETELY UNCHANGED
    // They already called getConnection() internally — they just work
}
```

### 4.3 Updating the Constructor Call in onEnable()

```java
// Before:
this.databaseManager = new DatabaseManager(this);
this.databaseManager.initialize();

// After:
this.databaseManager = new DatabaseManager(this, dbService);
this.databaseManager.initialize(); // still called — creates tables only
```

### 4.4 onDisable() Changes

Comment out (never delete) any DB pool close calls. Data-flush logic stays intact.

```java
// Before:
@Override
public void onDisable() {
    saveAllPlayers();
    databaseManager.close(); // ← COMMENT THIS OUT
}

// After:
@Override
public void onDisable() {
    saveAllPlayers();
    // MIGRATED: databaseManager.close() removed — pool owned by SwagAPI
}
```

---

## 5. Economy Migration

### 5.1 Pattern A — Plugin Has a Dedicated VaultIntegration Class (SwagFarming style)

**Do NOT delete `VaultIntegration.java`.** Comment out its instantiation and all usage. Add a migration header to the file.

```java
// In VaultIntegration.java — add at top of class:
// MIGRATED TO SWAGAPI: This class is no longer instantiated.
// Economy now routes through SwagAPI IEconomyService.
// File retained for rollback reference only.

// In the main plugin class — comment out, do not delete:
// MIGRATED: private VaultIntegration vaultIntegration;
// MIGRATED: this.vaultIntegration = new VaultIntegration(this);

// Replace all usage sites:
// Before: vaultIntegration.deposit(player, amount);
// After:
if (ecoService != null && ecoService.isEnabled()) {
    ecoService.deposit(player, amount);
}
```

### 5.2 Pattern B — Plugin Hooks Vault Inline (SwagFishing / SwagMenus style)

Comment out `setupEconomy()`, the `Economy` field, and the `vaultEnabled` boolean. Never delete them.

```java
// MIGRATED: Vault hook replaced by SwagAPI IEconomyService
// private Economy economy;
// private boolean vaultEnabled = false;

// MIGRATED: setupEconomy() commented out
// private boolean setupEconomy() {
//     if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
//     RegisteredServiceProvider<Economy> rsp =
//         getServer().getServicesManager().getRegistration(Economy.class);
//     if (rsp == null) return false;
//     this.economy = rsp.getProvider();
//     return economy != null;
// }

// Replace all call sites:
// Before: if (vaultEnabled) economy.depositPlayer(player, amount);
// After:
if (ecoService != null && ecoService.isEnabled()) {
    ecoService.deposit(player, amount);
}
```

### 5.3 Economy Method Mapping

| Old Vault call | New IEconomyService call |
|---|---|
| `economy.depositPlayer(player, amount)` | `ecoService.deposit(player, amount)` |
| `economy.withdrawPlayer(player, amount)` | `ecoService.withdraw(player, amount)` |
| `economy.getBalance(player)` | `ecoService.getBalance(player)` |
| `economy.has(player, amount)` | `ecoService.has(player, amount)` |
| `economy.format(amount)` | `ecoService.format(amount)` |
| `economy.currencyNameSingular()` | `ecoService.getCurrencyName()` |

> Always guard with `ecoService != null && ecoService.isEnabled()` before any economy call.

---

## 6. Utility Class Migration

### 6.1 ColorUtil

Any plugin with its own `ColorUtil` — update imports to use SwagAPI's version. Do NOT delete the local class. Add a migration comment to the local file.

```java
// Add to top of local ColorUtil.java:
// MIGRATED TO SWAGAPI: This class is superseded by com.SwagDev.SwagAPI.util.ColorUtil
// Retained for rollback reference. All imports updated to SwagAPI version.

// In all other classes — update import:
// Before: import com.swag.swagmenus.util.ColorUtil;
// After:  import com.SwagDev.SwagAPI.util.ColorUtil;
// Method signatures are identical — no other changes needed.
```

### 6.2 ItemBuilder

Same pattern as ColorUtil:

```java
// Add to top of local ItemBuilder.java:
// MIGRATED TO SWAGAPI: This class is superseded by com.SwagDev.SwagAPI.util.ItemBuilder
// Retained for rollback reference. All imports updated to SwagAPI version.

// In all other classes — update import:
// Before: import com.swag.swagmenus.util.ItemBuilder;
// After:  import com.SwagDev.SwagAPI.util.ItemBuilder;
```

### 6.3 MessageUtil

Only migrate if the plugin's local `MessageUtil` is a pure duplicate of SwagAPI's version. If the plugin has custom message keys or unique functionality not in SwagAPI's version, leave it completely alone and add a TODO:

```java
// TODO: Consider migrating to SwagAPI MessageUtil in a future pass
// Keep local MessageUtil for now — may contain plugin-specific keys
```

---

## 7. Web Server Migration

This section applies only to plugins with their own `HttpServer`. Based on codebase analysis:
- **SwagFishing** — `WebServerManager` with 13 routes + static file serving
- **DiscordUtils** — `LinkHttpServer` with OAuth2 callback handler(s)

All other plugins skip this section entirely.

### 7.1 The Core Principle

SwagAPI owns **one** `HttpServer` instance on a single configured port. Each plugin registers its routes with SwagAPI's server via `IWebService.registerModule(prefix, routes)`. SwagAPI's `PrefixStrippingHandler` strips the plugin prefix from the `HttpExchange` URI before dispatching, so every handler inner class receives paths identical to what it expected when it had its own server — **zero changes inside any handler.**

### 7.2 How Routes Map

| Plugin's original route | Mounted at on shared server | Handler receives |
|---|---|---|
| `/` | `/swagfishing/` | `/` |
| `/api/fish` | `/swagfishing/api/fish` | `/api/fish` |
| `/api/auth` | `/swagfishing/api/auth` | `/api/auth` |

All existing path parsing (e.g. `path.substring("/api/fish/".length())`) works unchanged because the prefix is stripped transparently.

### 7.3 SwagFishing — WebServerManager.start() Migration

Replace the entire method body. **Every handler inner class stays byte-for-byte identical.** Only these lines change.

```java
public void start() {
    if (!plugin.getConfig().getBoolean("web-editor.enabled", true)) {
        plugin.getLogger().info("Web editor is disabled in config");
        return;
    }

    // ── Try SwagAPI shared web server first ───────────────────────────────────
    com.SwagDev.SwagAPI.api.IWebService webService = plugin.getWebService();
    if (webService != null && webService.isRunning()) {

        Map<String, com.sun.net.httpserver.HttpHandler> routes = new java.util.LinkedHashMap<>();
        routes.put("/",                           new StaticFileHandler());
        routes.put("/api/fish",                   new FishAPIHandler());
        routes.put("/api/schemes",                new SchemesAPIHandler());
        routes.put("/api/totems",                 new TotemsAPIHandler());
        routes.put("/api/config/totems",          new TotemConfigAPIHandler());
        routes.put("/api/config/tournaments",     new TournamentConfigAPIHandler());
        routes.put("/api/config/deliveries",      new DeliveryConfigAPIHandler());
        routes.put("/api/auth",                   new AuthHandler());
        routes.put("/api/mythic/mobs",            new MythicMobsListHandler());
        routes.put("/api/modelengine/blueprints", new ModelEngineBlueprintsHandler());
        routes.put("/api/mythic/config",          new MythicConfigHandler());
        routes.put("/api/fishable-mobs/config",   new FishableMobConfigHandler());
        routes.put("/api/itemsadder/items",       new ItemsAdderItemsHandler());

        webService.registerModule("swagfishing", routes);

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Web Editor Started! (via SwagAPI shared server)");
        plugin.getLogger().info("URL: " + webService.getPluginUrl("swagfishing"));
        plugin.getLogger().info("Password: " + password);
        plugin.getLogger().info("Change password in config.yml!");
        plugin.getLogger().info("========================================");
        return;
    }

    // ── Fallback: start own server if SwagAPI web service is unavailable ──────
    // Original logic preserved exactly — do NOT modify anything below this line
    int port = plugin.getConfig().getInt("web-editor.port", 8081);
    String bindAddress = plugin.getConfig().getString("web-editor.bind-address", "0.0.0.0");

    try {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/fish", new FishAPIHandler());
        server.createContext("/api/schemes", new SchemesAPIHandler());
        server.createContext("/api/totems", new TotemsAPIHandler());
        server.createContext("/api/config/totems", new TotemConfigAPIHandler());
        server.createContext("/api/config/tournaments", new TournamentConfigAPIHandler());
        server.createContext("/api/config/deliveries", new DeliveryConfigAPIHandler());
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/mythic/mobs", new MythicMobsListHandler());
        server.createContext("/api/modelengine/blueprints", new ModelEngineBlueprintsHandler());
        server.createContext("/api/mythic/config", new MythicConfigHandler());
        server.createContext("/api/fishable-mobs/config", new FishableMobConfigHandler());
        server.createContext("/api/itemsadder/items", new ItemsAdderItemsHandler());

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Web Editor Started! (standalone — SwagAPI web unavailable)");
        plugin.getLogger().info("URL: http://" + getServerIP() + ":" + port);
        plugin.getLogger().info("Password: " + password);
        plugin.getLogger().info("Change password in config.yml!");
        plugin.getLogger().info("========================================");

    } catch (java.net.BindException e) {
        plugin.getLogger().severe("========================================");
        plugin.getLogger().severe("Web Editor FAILED to start!");
        plugin.getLogger().severe("Port " + port + " is already in use.");
        plugin.getLogger().severe("Either another process is using port " + port + ",");
        plugin.getLogger().severe("or the server was restarted before the OS released it.");
        plugin.getLogger().severe("Fix: change 'web-editor.port' in config.yml to a free port.");
        plugin.getLogger().severe("========================================");
    } catch (IOException e) {
        plugin.getLogger().log(Level.SEVERE, "Failed to start web server!", e);
    }
}
```

### 7.4 SwagFishing — WebServerManager.stop() Migration

Replace the entire method body. Original logic preserved as fallback.

```java
public void stop() {
    // ── If using SwagAPI shared server, unregister ────────────────────────────
    com.SwagDev.SwagAPI.api.IWebService webService = plugin.getWebService();
    if (webService != null) {
        webService.unregisterModule("swagfishing");
        plugin.getLogger().info("Web editor unregistered from SwagAPI shared server.");
        return;
    }

    // ── Fallback: stop own server (original logic — unchanged) ────────────────
    if (server != null) {
        server.stop(0);
        plugin.getLogger().info("Web editor stopped");
    }
}
```

### 7.5 SwagFishing — config.yml Update

Add a comment to `web-editor.port` explaining it is now a fallback only. Do NOT change the value or remove the key.

```yaml
web-editor:
  enabled: true
  password: "changeme"
  bind-address: "0.0.0.0"
  # NOTE: This port is only used when SwagAPI is NOT present (standalone fallback).
  # When SwagAPI is loaded, all web UIs share SwagAPI's configured web-server.port.
  port: 8081
```

### 7.6 DiscordUtils — LinkHttpServer Migration

Apply the same pattern. The prefix for DiscordUtils is `"discordutils"`. Map every `server.createContext()` call from the original `start()` into a `routes` map entry. Keep original `HttpServer.create()` as fallback.

```java
// In LinkHttpServer.start() — insert before the existing HttpServer.create() block:
com.SwagDev.SwagAPI.api.IWebService webService = plugin.getWebService();
if (webService != null && webService.isRunning()) {
    Map<String, com.sun.net.httpserver.HttpHandler> routes = new java.util.LinkedHashMap<>();
    // Map each createContext() from the original start() into the routes map:
    routes.put("/callback", /* existing handler reference */);
    routes.put("/",         /* existing handler reference */);
    webService.registerModule("discordutils", routes);
    plugin.getLogger().info("Discord link server registered at: "
        + webService.getPluginUrl("discordutils"));
    return;
}
// Original HttpServer.create() block follows unchanged as fallback...
```

> **AGENT NOTE:** Use the actual handler class references or method references from the existing `start()` implementation. Do not change the handler logic — only map the routes.

### 7.7 ⚠️ Frontend JavaScript Warning (SwagFishing)

> **Flag this to the developer at the end of the session.**
>
> After migration, the SwagFishing web UI is served at `http://IP:PORT/swagfishing/` instead of `http://IP:PORT/`. Any JavaScript in `web/index.html` using absolute API paths (e.g. `fetch('/api/fish', ...)`) will break — the browser resolves `/api/fish` to the server root, not `/swagfishing/api/fish`.
>
> **The fix is frontend-only:** Change all API call base URLs to relative paths — `fetch('./api/fish')` or `fetch('api/fish')`.
>
> This is out of scope for the Java migration and must be addressed separately by the developer.

---

## 8. Event Bus Migration

### 8.1 Publishing Events

```java
// When something notable happens in the plugin:
if (busService != null) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("uuid", player.getUniqueId().toString());
    payload.put("fishId", fish.getId());
    payload.put("rarity", fish.getRarity().name());

    busService.publish(new com.SwagDev.SwagAPI.events.SwagCrossPluginMessageEvent(
        "swagfishing:fish_caught",  // channel name
        "SwagFishing",              // source plugin name
        payload,
        player.getUniqueId()
    ));
}
```

### 8.2 Subscribing to Events

```java
// In onEnable(), after hookSwagAPI():
if (busService != null) {
    busService.subscribe("swagjobs:level_up", event -> {
        UUID uuid  = UUID.fromString((String) event.getData().get("uuid"));
        int level  = ((Number) event.getData().get("newLevel")).intValue();
        String job = (String) event.getData().get("job");
        // handle the event — replaces direct SwagJobsAPI call
    }, this);
}
```

### 8.3 Predefined Channel Convention

| Channel | Publisher | Payload Keys |
|---|---|---|
| `swagapi:player_loaded` | SwagAPI | `uuid` |
| `swagapi:player_unloaded` | SwagAPI | `uuid` |
| `swagfishing:fish_caught` | SwagFishing | `uuid`, `fishId`, `rarity`, `size` |
| `swagfishing:tournament_end` | SwagFishing | `tournamentId`, `winner`, `score` |
| `swagfarming:crop_harvested` | SwagFarming | `uuid`, `cropId`, `quality`, `amount` |
| `swagjobs:level_up` | SwagJobs | `uuid`, `job`, `newLevel` |
| `swagbounties:bounty_claimed` | SwagBounties | `uuid`, `targetUuid`, `reward` |
| `swagslayer:mob_slain` | SwagSlayer | `uuid`, `mobType`, `mobId` |
| `swagcore:player_punished` | SwagCore | `uuid`, `type`, `reason`, `duration` |
| `swagcore:rank_changed` | SwagCore | `uuid`, `oldRank`, `newRank` |

---

## 9. GitHub Actions — Update Manifest

Every private plugin repo must have the GitHub Actions workflow file that automatically updates `versions.json` in the public `swag617/swag-versions` repo when a GitHub Release is published.

### 9.1 What the Agent Does vs What the Developer Does

**The agent handles (via terminal/IntelliJ):**
- Creates `.github/workflows/` directory if it doesn't exist
- Writes `update-manifest.yml` with the content below
- Commits and pushes — GitHub registers the workflow automatically

**The developer must do manually on GitHub website (agent cannot do this):**
- Add `VERSIONS_REPO_TOKEN` secret: repo → Settings → Secrets and variables → Actions → New repository secret
- Add the plugin's initial entry to `swag-versions/versions.json` if not already present

### 9.2 Agent Terminal Commands

```bash
# Create the directory if it doesn't exist
mkdir -p .github/workflows

# Create the workflow file (agent writes content shown in 9.3 into this file)
touch .github/workflows/update-manifest.yml
```

### 9.3 Workflow File Location

```
.github/workflows/update-manifest.yml
```

### 9.4 Workflow File Content

> **IMPORTANT:** This is the proven working version for **private repos**. It uses the GitHub API asset endpoint with `GITHUB_TOKEN` authentication to download the jar — NOT the `browser_download_url` which returns 404 for private repo assets.

```yaml
name: Update Version Manifest

on:
  release:
    types: [published]

jobs:
  update-manifest:
    runs-on: ubuntu-latest

    steps:
      - name: Get release info
        run: |
          echo "TAG=${{ github.event.release.tag_name }}" >> $GITHUB_ENV
          echo "PLUGIN=${{ github.event.repository.name }}" >> $GITHUB_ENV

      - name: Download release jar
        run: |
          # Use asset ID + GitHub API — browser_download_url returns 404 for private repos
          ASSET_ID=$(echo '${{ toJson(github.event.release.assets) }}' \
            | jq -r '.[] | select(.name | endswith(".jar")) | .id' | head -1)

          if [ -z "$ASSET_ID" ]; then
            echo "ERROR: No .jar asset found in this release. Did you attach the jar?"
            exit 1
          fi

          JAR_NAME=$(echo '${{ toJson(github.event.release.assets) }}' \
            | jq -r '.[] | select(.name | endswith(".jar")) | .name' | head -1)

          JAR_URL="https://api.github.com/repos/${{ github.repository }}/releases/assets/$ASSET_ID"
          echo "JAR_URL=$JAR_URL" >> $GITHUB_ENV
          echo "Downloading asset ID: $ASSET_ID ($JAR_NAME)"

          curl -L -f -o plugin.jar \
            -H "Authorization: token ${{ secrets.GITHUB_TOKEN }}" \
            -H "Accept: application/octet-stream" \
            "$JAR_URL"

          echo "Downloaded: $(ls -lh plugin.jar | awk '{print $5}')"

      - name: Compute SHA-256 checksum
        run: |
          CHECKSUM=$(sha256sum plugin.jar | awk '{print $1}')
          echo "CHECKSUM=$CHECKSUM" >> $GITHUB_ENV
          echo "Checksum: $CHECKSUM"

      - name: Checkout swag-versions repo
        uses: actions/checkout@v4
        with:
          repository: swag617/swag-versions
          token: ${{ secrets.VERSIONS_REPO_TOKEN }}
          path: swag-versions

      - name: Update versions.json
        run: |
          cd swag-versions

          # Validate current versions.json before touching it
          jq empty versions.json || { echo "ERROR: versions.json is invalid JSON"; exit 1; }

          # Update only this plugin's entry — all other entries untouched
          jq --arg plugin "$PLUGIN" \
             --arg version "$TAG" \
             --arg url "$JAR_URL" \
             --arg checksum "$CHECKSUM" \
             '.plugins[$plugin] = {version: $version, download: $url, checksum: $checksum}' \
             versions.json > versions.tmp.json

          # Validate output before replacing
          jq empty versions.tmp.json || { echo "ERROR: jq produced invalid JSON"; exit 1; }

          mv versions.tmp.json versions.json

          echo "Updated $PLUGIN to $TAG"
          cat versions.json

      - name: Commit and push
        run: |
          cd swag-versions
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add versions.json

          # Only commit if something actually changed
          git diff --staged --quiet && echo "No changes to commit" && exit 0

          git commit -m "chore: update $PLUGIN to $TAG"
          git push
```

### 9.5 Key Notes

- **`GITHUB_TOKEN`** is a built-in GitHub secret — automatically provided to every Action run. You do NOT need to create or add it. The agent does not need to configure this.
- **`VERSIONS_REPO_TOKEN`** is the Personal Access Token — the developer must add this manually as a secret on the GitHub repo page. The agent cannot do this.
- The workflow triggers on `release: published` — only on the initial **Publish release** click. Editing a release after publishing does NOT re-trigger it.
- The plugin name in `versions.json` comes from `${{ github.event.repository.name }}` — the **GitHub repo name must match the `plugin.yml` name** exactly. If they differ, override: `echo "PLUGIN=SwagFishing" >> $GITHUB_ENV`
- Always attach the jar **before** clicking Publish release — not after.

### 9.6 Adding the Entry to versions.json

Before the first release, manually add the plugin entry to `swag-versions/versions.json`:

```json
"SwagFishing": {
  "version": "0.0.0",
  "download": "placeholder",
  "checksum": "placeholder"
}
```

The Action will overwrite this with real data on the first release.

---

## 10. Per-Plugin Migration Scope

Use this table to determine exactly which sections apply to each plugin. Only add services actually needed.

| Plugin | DB | Economy | PlayerData | EventBus | WebServer | ColorUtil | ItemBuilder | GitHub Action |
|---|---|---|---|---|---|---|---|---|
| **SwagFarming** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **SwagFishing** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| **SwagMenus** | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| **SwagAC** | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **SwagBounties** | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **SwagJobs** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **SwagSlayer** | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **SwagTags** | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **SwagTournaments** | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **SwagCore** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **StackPlus** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **DiscordUtils** | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |

---

## 11. Per-Plugin Specific Notes

### SwagFarming

- Has a dedicated **`VaultIntegration.java`** class — comment out per Section 5.1. Do NOT delete the file.
- Has a local **`MessageUtil`** — leave it in place. Add TODO comment for future migration pass.
- Has a **`JobsIntegrationManager`** that calls `SwagJobsAPI` directly — replace with `busService.subscribe("swagjobs:level_up", ...)` per Section 8.2.
- **Shades Gson under `com.swag.farming.libs.gson`** — do NOT touch this relocation. SwagAPI uses a separate relocation prefix. They must remain separate or classloader conflicts will occur.
- `DatabaseManager` uses a `SQLiteDatabase` wrapper class — when removing pool ownership, comment out the `SQLiteDatabase` field and instantiation (do not delete). The `getConnection()` call becomes `dbService.getConnection()`.
- Has an auto-save async timer in `onEnable()` — keep it exactly as-is.

### SwagFishing

- Has inline Vault setup (`setupEconomy()` method + `Economy` field + `isVaultEnabled` boolean) — comment all three out per Section 5.2.
- Has `isSwagJobsEnabled()` / `isFleaJobsEnabled()` boolean checks and direct `SwagJobsAPI` calls in `JobShopManager` — replace with event bus subscription to `swagjobs:level_up`. Cache the level locally in the plugin.
- `DatabaseManager` uses `DriverManager.getConnection()` directly — refactor per Section 4. The WAL PRAGMA is handled by SwagAPI's `DatabaseService`, so remove that line.
- **`WebServerManager`** — migrate `start()` and `stop()` per Sections 7.3 and 7.4. This is the most significant change for this plugin.
- Package is `com.swagserv.swagfishing` — do NOT change package naming.
- `WebServerManager$AuthHandler` contains a trust token system (`validateTrustToken`) — this stays completely inside `WebServerManager`. Do not touch it.
- `WebServerManager` has shared helpers (`sendJSON`, `readRequestBody`, `send404`, `send405`, `sendUnauthorized`, `getServerIP`) — do NOT touch any of these methods.
- ⚠️ **Frontend JS warning applies** — see Section 7.7.
- GitHub repo name is `SwagFishing` — matches `plugin.yml` name. No override needed in workflow.

### SwagMenus

- Has inline Vault setup (`setupEconomy()` + static `Economy` field) — comment out per Section 5.2.
- Has local `ColorUtil` and `ItemBuilder` under `com.swag.swagmenus.util` — update all imports across the plugin to `com.SwagDev.SwagAPI.util`. Add migration comment to the local class files. Do NOT delete local files.
- Has a `WebEditorServer` — inspect carefully. If it is a `com.sun.net.httpserver.HttpServer` instance, apply the web migration pattern with prefix `"swagmenus"`. If it is something else entirely (e.g. an in-game editor), leave it completely untouched.
- Has a `MenuFileWatcher` — leave completely unchanged.
- Has a `ChatInputManager` — leave completely unchanged.
- No database migration in scope for this plugin based on analysis.

### SwagAC

- Subscribe to `swagapi:player_loaded` and `swagapi:player_unloaded` on the event bus to sync player tracking with SwagAPI's profile lifecycle.
- Refactor `DatabaseManager` per Section 4 — infraction storage table structure unchanged.
- Publish `swagac:player_flagged` events when a player is flagged, so SwagCore and other plugins can react.

### SwagBounties

- Publish `swagbounties:bounty_claimed` event when a bounty is collected, so SwagTournaments can react without direct coupling.
- Refactor `DatabaseManager` per Section 4.
- Replace Vault hook per Section 5.

### SwagJobs

- **Must publish `swagjobs:level_up`** whenever a player levels up in any job. This is critical — SwagFishing and SwagFarming currently call SwagJobsAPI directly and will switch to subscribing to this event after their own migration.
- Payload must include at minimum: `uuid` (String), `job` (String), `newLevel` (int).
- Refactor `DatabaseManager` per Section 4.
- Replace Vault hook per Section 5.

### SwagSlayer

- Refactor `DatabaseManager` per Section 4.
- Replace Vault hook per Section 5.
- Publish `swagslayer:mob_slain` events for cross-plugin reactions.

### SwagTags

- Refactor `DatabaseManager` per Section 4.
- Consider registering player tag data as a `PlayerDataModule` with `IPlayerDataService` so tag data is accessible from the unified player profile. This is optional — only do it if the developer explicitly requests it.

### SwagTournaments

- Subscribe to `swagfishing:tournament_end` and `swagfarming:crop_harvested` on the event bus rather than calling SwagFishing or SwagFarming plugin instances directly.
- Refactor `DatabaseManager` per Section 4.
- Replace Vault hook per Section 5.

### SwagCore (existing plugin — distinct from SwagAPI)

- Minimal integration — add `depend: [SwagAPI]` to `plugin.yml` and hook `IDatabaseService` and `IEconomyService`.
- Do not add event bus or player data unless the plugin currently uses them.
- Do not confuse this plugin with SwagAPI — they are completely separate plugins.

### StackPlus

- Standard DB + economy migration per Sections 4 and 5.
- Plugin naming may change in future — the migration must NOT rename any packages, classes, or files.

### DiscordUtils

- Only `LinkHttpServer` is in scope. No database, economy, or event bus changes.
- Retrieve `IWebService` in the main plugin `onEnable()` and expose it via a getter.
- Migrate `LinkHttpServer.start()` and `stop()` per the pattern in Section 7.6.
- Prefix for DiscordUtils: `"discordutils"`.

---

## 12. Post-Migration Checklist

Verify every applicable item before ending the session. Do not end the session with unchecked items unless they are marked N/A for this plugin.

### Git Hygiene Checklist (every plugin — do before anything else)
- [ ] `.gitignore` exists and contains all entries from Section 3.0.1
- [ ] `.claude/` is listed in `.gitignore` and is NOT tracked by git
- [ ] `.idea/` is listed in `.gitignore` and is NOT tracked by git
- [ ] `target/` is listed in `.gitignore` and is NOT tracked by git
- [ ] `git ls-files .claude/ .idea/ target/` returns empty output
- [ ] Developer confirmed commit before `git commit` was run
- [ ] Developer confirmed push before `git push` was run

### Universal Checklist (every plugin)
- [ ] `plugin.yml` has `depend: [SwagAPI]` and Vault removed from softdepend
- [ ] `pom.xml` has SwagAPI as `provided` dependency, nothing removed
- [ ] Main class has service fields for all applicable services (Section 10)
- [ ] `hookSwagAPI()` method added and called first in `onEnable()`
- [ ] Main class has public getters for all service fields
- [ ] `onEnable()` order is correct — `hookSwagAPI()` before any manager initialization
- [ ] No existing code deleted — only commented out with migration notes
- [ ] All output files are complete — no `// rest unchanged` stubs anywhere
- [ ] `.github/workflows/` directory created and `update-manifest.yml` written by agent using Section 9.4 content
- [ ] Plugin name in GitHub repo matches `plugin.yml` `name:` field (or PLUGIN env var hardcoded in workflow)
- [ ] **Developer action required:** `VERSIONS_REPO_TOKEN` secret added on GitHub repo page (agent cannot do this)
- [ ] **Developer action required:** Plugin entry added to `swag-versions/versions.json` if not already present

### Database Checklist (plugins with DB column in Section 10)
- [ ] `DatabaseManager` constructor accepts `IDatabaseService` as second parameter
- [ ] `DatabaseManager.initialize()` only creates tables — no pool/connection setup
- [ ] `getConnection()` delegates to `dbService.getConnection()`
- [ ] WAL PRAGMA removed from `DatabaseManager` (SwagAPI handles it)
- [ ] `onDisable()` does NOT call any DB pool close method
- [ ] All query methods unchanged

### Economy Checklist (plugins with Economy column in Section 10)
- [ ] Local Vault hook / `setupEconomy()` / `Economy` field commented out (not deleted)
- [ ] All `economy.depositPlayer()` etc. replaced with `ecoService.deposit()` etc.
- [ ] All economy calls guarded with `ecoService != null && ecoService.isEnabled()`

### Utility Checklist (plugins with ColorUtil/ItemBuilder columns)
- [ ] Local class files have migration comment added at top
- [ ] All imports updated to `com.SwagDev.SwagAPI.util.*`
- [ ] Local class files NOT deleted

### Web Server Checklist (SwagFishing and DiscordUtils only)
- [ ] `WebServerManager.start()` migrated per Section 7.3 — standalone fallback included
- [ ] `WebServerManager.stop()` migrated per Section 7.4
- [ ] All handler inner classes completely unchanged
- [ ] All helper methods (`sendJSON`, etc.) completely unchanged
- [ ] `config.yml` `web-editor.port` has fallback-only comment added
- [ ] `webService` field and getter added to main plugin class
- [ ] Developer notified about frontend JS API path changes (Section 7.7)

### Event Bus Checklist (plugins with EventBus column in Section 10)
- [ ] `busService.publish()` calls added at appropriate game event points
- [ ] `busService.subscribe()` calls added in `onEnable()` for channels this plugin listens to
- [ ] Channel names match the convention table in Section 8.3

---

## 13. What the Agent Must NEVER Do

This list is absolute. If any of these exist in the plugin being migrated, they must remain completely untouched.

| Category | Specific Examples — Never Touch |
|---|---|
| Game listeners | `BlockBreakListener`, `FishingListener`, `PlayerJoinListener`, `TotemItemListener` |
| Manager business logic | `FishManager`, `TotemManager`, `CropManager`, `SkillManager`, `DeliveryManager`, `AttunementManager` |
| GUI classes | All inventory GUI classes — layouts, click handlers, page builders, icon configs |
| Config file content | `config.yml`, `messages.yml`, `fish.yml`, `totems.yml`, `baits.yml` etc. — content and structure unchanged |
| Resource files | All `src/main/resources/` files — unchanged unless specifically listed above |
| Custom model classes | `FishingProfile`, `Fish`, `Attunement`, `PlayerTotem`, `LoreScheme`, `FishConditionRule` |
| Command handlers | All `CommandExecutor` and `TabCompleter` implementations |
| Web handler inner classes | `FishAPIHandler`, `AuthHandler`, `StaticFileHandler`, `SchemesAPIHandler`, `TotemsAPIHandler`, `TotemConfigAPIHandler`, `TournamentConfigAPIHandler`, `DeliveryConfigAPIHandler`, `MythicMobsListHandler`, `ModelEngineBlueprintsHandler`, `MythicConfigHandler`, `FishableMobConfigHandler`, `ItemsAdderItemsHandler` |
| Web helper methods | `sendJSON()`, `readRequestBody()`, `send404()`, `send405()`, `sendUnauthorized()`, `getServerIP()`, `validateTrustToken()` |
| Third-party integrations | MythicMobs, ModelEngine, WorldGuard, ItemsAdder, Oraxen, FancyNPCs, PlaceholderAPI — all hooks unchanged |
| SQL table schemas | All `CREATE TABLE` SQL — never alter column definitions, table names, or index definitions |
| SQL query methods | All `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `REPLACE` methods — unchanged, only connection source changes |
| Async tasks / schedulers | All `BukkitRunnable`, `runTaskAsynchronously`, auto-save timers — unchanged |
| Plugin-specific shading | SwagFarming's `com.swag.farming.libs.gson` — never relocate or remove |
| Package declarations | Never change `package com.swagserv.swagfishing` etc. — package naming is out of scope |
| Class or file names | Never rename any existing class or file |
| Personal/local files | `.claude/` settings, `.idea/` project files — never commit, never modify |

> **Final reminder:** If you are uncertain whether something should be changed, do NOT change it. Add a `// TODO:` comment and flag it in the session summary. The developer reviews all TODOs.

---

*Swag Plugin Suite — Full Migration Prompt v2.1*
*Updated to reflect: SwagAPI_Complete_Spec.md (unified spec), proven GitHub Actions workflow for private repos (ASSET_ID method), live manifest URL at swag617/swag-versions, SwagFishing WebServerManager.java source analysis, per-plugin scope table with GitHub Actions column, pre-migration git hygiene protocol (.claude/ gitignore, commit/push confirmation).*
*Feed alongside: SwagAPI_Complete_Spec.md + SwagAPI_WebService_Prompt.md + plugin source files.*
