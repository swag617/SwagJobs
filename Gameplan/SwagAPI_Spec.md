# SwagAPI — Inter-Plugin Communication Library
**Build Specification for AI Agent**

`com.SwagDev.SwagAPI` | Paper 1.21+ | Java 17+ | Maven | Version 1.0.0

---

## Table of Contents

1. [Purpose & Background](#1-purpose--background)
2. [Project Setup](#2-project-setup)
3. [Package Structure](#3-package-structure)
4. [SwagAPI Main Class](#4-swagapi-main-class)
5. [Service Interfaces](#5-service-interfaces-api-package)
6. [Database Service Implementation](#6-database-service-implementation)
7. [SwagPlayerProfile](#7-swagplayerprofile-shared-player-object)
8. [Event Bus](#8-event-bus)
9. [Shared Utility Classes](#9-shared-utility-classes)
10. [How Dependent Plugins Integrate](#10-how-dependent-plugins-integrate)
11. [Full Plugin Ecosystem & Dependencies](#11-full-plugin-ecosystem--dependencies)
12. [Full config.yml](#12-full-configyml)
13. [Commands](#13-commands)
14. [Coding Conventions](#14-coding-conventions)
15. [Deliverables Checklist](#15-deliverables-checklist)
16. [Notes for the Agent](#16-notes-for-the-agent)

---

## 1. Purpose & Background

SwagAPI is a shared library plugin that acts as the single source of truth and communication backbone for the entire Swag plugin ecosystem. Currently each plugin manages its own database connection, its own Vault hook, its own utility classes, and communicates with other plugins via direct plugin instance lookups. SwagAPI eliminates that duplication by centralising every shared concern.

> **NOTE:** SwagAPI is named to avoid conflict with the developer's existing `SwagCore` plugin, which is a separate, unrelated plugin in the same server ecosystem.

### 1.1 Problems Being Solved

- Every plugin opens its own SQLite/MySQL connection — connection pools multiply with each plugin.
- Vault is hooked independently in SwagFarming, SwagFishing, SwagMenus, and presumably every other plugin.
- Utility code (`ColorUtil`, `ItemBuilder`, `MessageUtil`) is duplicated across plugins.
- Cross-plugin calls require direct `JavaPlugin` instance lookups — tight coupling, fragile on load-order issues.
- No shared event bus — plugins cannot fire or listen to domain events from sibling plugins without direct coupling.

### 1.2 Solution Architecture

SwagAPI uses Bukkit's **ServicesManager** as its inter-plugin bus. SwagAPI registers service interfaces on startup. Each dependent plugin retrieves those services via `Bukkit.getServicesManager().getRegistration(ServiceInterface.class)`. No direct class coupling is needed in dependent plugins beyond the service interface lookup.

In addition, SwagAPI provides a lightweight **SwagEventBus** — a synchronous pub/sub system built on top of Bukkit's event system using custom events. Plugins fire domain events through SwagAPI; sibling plugins register listeners on those events without knowing which plugin fired them.

---

## 2. Project Setup

### 2.1 Metadata

| Key | Value |
|---|---|
| Plugin Name | SwagAPI |
| Main Class | `com.SwagDev.SwagAPI.SwagAPI` |
| Base Package | `com.SwagDev.SwagAPI` |
| Maven Group ID | `com.SwagDev` |
| Maven Artifact ID | `SwagAPI` |
| Version | `1.0.0` |
| API Version | `1.21` |
| Server Platform | Paper 1.21+ |
| Java Version | 17+ |
| Build System | Maven |
| Author | Swag617 / SwagDev |

### 2.2 plugin.yml

```yaml
name: SwagAPI
version: 1.0.0
api-version: "1.21"
main: com.SwagDev.SwagAPI.SwagAPI
author: Swag617
description: Shared API and communication backbone for all Swag plugins.
prefix: SwagAPI

commands:
  swagapi:
    description: SwagAPI admin command
    usage: /swagapi <reload|status|info>
    permission: swagapi.admin
    aliases: [sapi]

permissions:
  swagapi.admin:
    description: Access SwagAPI admin commands
    default: op
```

> **NOTE:** SwagAPI has NO `depend` or `softdepend` entries. It is the root of the dependency tree. All other plugins depend ON it — it depends on nothing Swag-specific.

### 2.3 pom.xml Key Dependencies

```xml
<dependencies>
    <!-- Paper API -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.21-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>

    <!-- HikariCP — shared connection pool -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>5.1.0</version>
        <scope>compile</scope>
    </dependency>

    <!-- MySQL connector -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>8.3.0</version>
        <scope>compile</scope>
    </dependency>

    <!-- SQLite JDBC -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.45.3.0</version>
        <scope>compile</scope>
    </dependency>

    <!-- Gson (shade — farming uses com.swag.farming.libs.gson internally) -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.11.0</version>
        <scope>compile</scope>
    </dependency>

    <!-- Vault (soft dependency for economy service) -->
    <dependency>
        <groupId>com.github.MilkBowl</groupId>
        <artifactId>VaultAPI</artifactId>
        <version>1.7</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

> **IMPORTANT:** HikariCP, Gson, and the JDBC drivers MUST be shaded/relocated into the SwagAPI jar using the Maven Shade Plugin. Configure relocations so they do not conflict with any drivers already on the classpath. Suggested relocation prefix: `com.SwagDev.SwagAPI.libs`

---

## 3. Package Structure

```
com.SwagDev.SwagAPI
├── SwagAPI.java                        ← Main plugin class (JavaPlugin)
│
├── api/                                ← Public service interfaces (the contract)
│   ├── IDatabaseService.java
│   ├── IEconomyService.java
│   ├── IPlayerDataService.java
│   ├── IMessagingService.java
│   └── IEventBusService.java
│
├── services/                           ← Concrete implementations
│   ├── DatabaseService.java
│   ├── EconomyService.java
│   ├── PlayerDataService.java
│   ├── MessagingService.java
│   └── EventBusService.java
│
├── database/                           ← Low-level DB layer
│   ├── DatabaseManager.java            ← HikariCP pool owner
│   ├── MySQLDatabase.java
│   └── SQLiteDatabase.java
│
├── events/                             ← Custom Bukkit events (the event bus)
│   ├── SwagPlayerDataLoadEvent.java
│   ├── SwagPlayerDataSaveEvent.java
│   ├── SwagEconomyTransactionEvent.java
│   └── SwagCrossPluginMessageEvent.java
│
├── model/                              ← Shared data models
│   └── SwagPlayerProfile.java          ← Central player profile object
│
├── util/                               ← Shared utility classes
│   ├── ColorUtil.java                  ← Migrated from SwagMenus
│   ├── ItemBuilder.java                ← Migrated from SwagMenus
│   ├── MessageUtil.java                ← Migrated from SwagFarming
│   └── SchedulerUtil.java              ← Async/sync task helpers
│
├── commands/
│   └── SwagAPICommand.java             ← /swagapi reload|status|info
│
└── listeners/
    └── PlayerSessionListener.java      ← Handles join/quit for profile cache
```

### 3.1 Design Rules

- The `api/` package contains **ONLY interfaces** — no implementation code whatsoever.
- Dependent plugins should only ever import from `com.SwagDev.SwagAPI.api.*` and `com.SwagDev.SwagAPI.events.*` — never from `services/`, `database/`, or `util/` directly.
- The `util/` classes are intentionally accessible to dependent plugins since they are stateless helpers. Import them directly.
- All service implementations are registered to `Bukkit.getServicesManager()` in `SwagAPI.onEnable()` and unregistered in `onDisable()`.

---

## 4. SwagAPI Main Class

```java
public final class SwagAPI extends JavaPlugin {

    private static SwagAPI instance;

    // Service implementations (held as fields for shutdown)
    private DatabaseService databaseService;
    private EconomyService economyService;
    private PlayerDataService playerDataService;
    private MessagingService messagingService;
    private EventBusService eventBusService;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();
        getLogger().info("[SwagAPI] Initializing...");

        saveDefaultConfig();

        // 1. Database (must be first — everything else may depend on it)
        databaseService = new DatabaseService(this);
        databaseService.initialize();

        // 2. Economy (soft-depends on Vault)
        economyService = new EconomyService(this);
        economyService.initialize();

        // 3. Player data cache
        playerDataService = new PlayerDataService(this, databaseService);
        playerDataService.initialize();

        // 4. Messaging service
        messagingService = new MessagingService(this);

        // 5. Event bus
        eventBusService = new EventBusService(this);

        // Register all services to Bukkit ServicesManager
        ServicesManager sm = getServer().getServicesManager();
        sm.register(IDatabaseService.class,   databaseService,   this, ServicePriority.Normal);
        sm.register(IEconomyService.class,    economyService,    this, ServicePriority.Normal);
        sm.register(IPlayerDataService.class, playerDataService, this, ServicePriority.Normal);
        sm.register(IMessagingService.class,  messagingService,  this, ServicePriority.Normal);
        sm.register(IEventBusService.class,   eventBusService,   this, ServicePriority.Normal);

        // Listeners & commands
        getServer().getPluginManager().registerEvents(
            new PlayerSessionListener(this), this);
        getCommand("swagapi").setExecutor(new SwagAPICommand(this));

        getLogger().info("[SwagAPI] Ready in " +
            (System.currentTimeMillis() - start) + "ms.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[SwagAPI] Shutting down...");
        if (playerDataService != null) playerDataService.saveAll();
        if (databaseService   != null) databaseService.shutdown();
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("[SwagAPI] Disabled.");
    }

    public static SwagAPI getInstance() { return instance; }

    // Direct getters for internal use within SwagAPI itself
    public DatabaseService   getDatabaseService()    { return databaseService; }
    public EconomyService    getEconomyService()     { return economyService; }
    public PlayerDataService getPlayerDataService()  { return playerDataService; }
    public MessagingService  getMessagingService()   { return messagingService; }
    public EventBusService   getEventBusService()    { return eventBusService; }
}
```

---

## 5. Service Interfaces (api/ package)

These interfaces are the public contract. Dependent plugins compile against these. **Never change method signatures without versioning.**

### 5.1 IDatabaseService

```java
public interface IDatabaseService {
    Connection getConnection() throws SQLException;
    HikariDataSource getDataSource();
    boolean isMySQL();
    boolean isSQLite();
    void executeAsync(Runnable task);
    <T> CompletableFuture<T> queryAsync(Callable<T> query);
}
```

Provides raw `Connection` access and async query helpers. Dependent plugins use `getConnection()` to run their own plugin-specific queries against the shared pool.

### 5.2 IEconomyService

```java
public interface IEconomyService {
    boolean isEnabled();
    double getBalance(OfflinePlayer player);
    boolean has(OfflinePlayer player, double amount);
    boolean withdraw(OfflinePlayer player, double amount);
    boolean deposit(OfflinePlayer player, double amount);
    String format(double amount);
    String getCurrencyName();
}
```

Wraps Vault Economy. Plugins call this instead of hooking Vault themselves. If Vault is absent, `isEnabled()` returns false and all money operations are no-ops returning `true`/`0` as appropriate — plugins do not need to null-check.

### 5.3 IPlayerDataService

```java
public interface IPlayerDataService {
    // Profile management
    SwagPlayerProfile getProfile(UUID uuid);
    SwagPlayerProfile getProfile(Player player);
    boolean isLoaded(UUID uuid);
    CompletableFuture<SwagPlayerProfile> loadProfile(UUID uuid);
    CompletableFuture<Void> saveProfile(UUID uuid);
    void saveAll();

    // Plugin data modules — each plugin registers its own data namespace
    void registerModule(String pluginKey, PlayerDataModule module);
    <T> T getModuleData(UUID uuid, String pluginKey, Class<T> type);
    void setModuleData(UUID uuid, String pluginKey, Object data);
}
```

### 5.4 IMessagingService

```java
public interface IMessagingService {
    void send(Player player, String message);
    void send(Player player, String message, Map<String, String> placeholders);
    void broadcast(String message);
    void broadcastPermission(String message, String permission);
    void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);
    void sendActionBar(Player player, String message);
    String colorize(String input);
    Component toComponent(String input);
}
```

### 5.5 IEventBusService

```java
public interface IEventBusService {
    void publish(SwagCrossPluginMessageEvent event);
    void subscribe(String channel, Consumer<SwagCrossPluginMessageEvent> handler, Plugin owner);
    void unsubscribeAll(Plugin owner);
}
```

> **NOTE:** The event bus supplements — not replaces — standard Bukkit events. Use Bukkit events for game-level events. Use the SwagAPI event bus for plugin-to-plugin messages that have no Bukkit equivalent (e.g. "player completed a fishing tournament", "player ranked up in SwagJobs").

---

## 6. Database Service Implementation

SwagAPI owns a single HikariCP connection pool. All dependent plugins request connections from this pool — they do **NOT** create their own pools or `DriverManager` connections.

### 6.1 config.yml — Database Section

```yaml
database:
  type: sqlite           # 'sqlite' or 'mysql'

  sqlite:
    file: swagapi.db     # relative to SwagAPI data folder

  mysql:
    host: localhost
    port: 3306
    database: swagapi
    username: root
    password: ""
    pool-size: 10
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
    use-ssl: false
```

### 6.2 DatabaseService Implementation Notes

- On `initialize()`, read config, build `HikariConfig`, open the pool.
- For SQLite, use WAL journal mode and set `PRAGMA synchronous=NORMAL` after each new connection (via HikariCP `connectionInitSql` or a connection listener).
- For MySQL, configure the full JDBC URL with `useSSL`, `allowPublicKeyRetrieval`, and `serverTimezone`.
- Expose `executeAsync(Runnable)` and `queryAsync(Callable<T>)` that run tasks on Bukkit's async scheduler and return `CompletableFuture` — this is the same pattern used in SwagFarming's existing `DatabaseManager` and should be preserved.
- On `shutdown()`, call `HikariDataSource.close()` after all pending async tasks complete.

### 6.3 How Dependent Plugins Use the Database

```java
// In any dependent plugin's own manager class:
RegisteredServiceProvider<IDatabaseService> prov =
    Bukkit.getServicesManager().getRegistration(IDatabaseService.class);

if (prov == null) {
    getLogger().severe("SwagAPI not found! Disabling.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}

IDatabaseService db = prov.getProvider();

// Run a plugin-specific query
db.queryAsync(() -> {
    try (Connection conn = db.getConnection();
         PreparedStatement ps = conn.prepareStatement(
             "SELECT * FROM swagfishing_players WHERE uuid = ?")) {
        ps.setString(1, uuid.toString());
        return ps.executeQuery();
    }
}).thenAccept(rs -> { /* handle result */ });
```

> **IMPORTANT:** Each dependent plugin is responsible for creating and managing its own tables. SwagAPI only creates the shared tables it owns (see below). Plugin-specific tables are created by each plugin in its own `onEnable()`.

### 6.4 Tables Owned by SwagAPI

| Table | Purpose |
|---|---|
| `swagapi_players` | Central player registry: uuid, username, first_join, last_seen |
| `swagapi_modules` | Key-value store for plugin data modules (pluginKey, uuid, json_data) |

---

## 7. SwagPlayerProfile (Shared Player Object)

`SwagPlayerProfile` is a central player data object cached in memory for all online players. Each plugin can attach its own data module to it via a string key. This avoids 10+ separate player caches while giving each plugin its own isolated data namespace.

### 7.1 SwagPlayerProfile Fields

```java
public class SwagPlayerProfile {
    private final UUID uuid;
    private String username;
    private long firstJoin;
    private long lastSeen;

    // Plugin data modules — each plugin stores its own object here
    private final Map<String, Object> moduleData = new ConcurrentHashMap<>();

    // getters/setters for core fields
    // getModuleData(String key) / setModuleData(String key, Object data)
}
```

### 7.2 Module Registration Pattern

```java
// In SwagFishing's onEnable(), after retrieving IPlayerDataService:
playerDataService.registerModule("swagfishing", new SwagFishingDataModule());

// SwagFishingDataModule tells SwagAPI how to load/save fishing-specific data:
public class SwagFishingDataModule implements PlayerDataModule {
    @Override
    public CompletableFuture<Object> load(UUID uuid, IDatabaseService db) {
        // Load FishingProfile from swagfishing tables via db.getConnection()
    }

    @Override
    public CompletableFuture<Void> save(UUID uuid, Object data, IDatabaseService db) {
        // Save FishingProfile back to swagfishing tables
    }
}

// Reading the data anywhere in SwagFishing:
FishingProfile profile = (FishingProfile) playerDataService
    .getModuleData(player.getUniqueId(), "swagfishing", FishingProfile.class);
```

> **NOTE:** Plugins that do not need the module system can ignore it entirely and manage their player data directly using `IDatabaseService.getConnection()`. The module system is opt-in.

---

## 8. Event Bus

The SwagAPI event bus is built on top of Bukkit's event system. `SwagCrossPluginMessageEvent` is a custom Bukkit event. The `EventBusService` manages a registry of channel-keyed consumers and fires them when a matching event is published.

### 8.1 SwagCrossPluginMessageEvent

```java
public class SwagCrossPluginMessageEvent extends Event {
    private final String channel;       // e.g. "swagfishing:tournament_end"
    private final String sourcePlugin;  // e.g. "SwagFishing"
    private final Map<String, Object> data;  // arbitrary payload
    private final UUID playerUuid;      // nullable — may be a server-wide event

    // Standard Bukkit event constructor, getters, HandlerList...
}
```

### 8.2 Publishing an Event (from SwagFishing)

```java
IEventBusService bus = SwagAPI.getInstance().getEventBusService();

Map<String, Object> payload = new HashMap<>();
payload.put("tournamentId", tournament.getId());
payload.put("winner", player.getUniqueId().toString());
payload.put("score", tournament.getTopScore());

bus.publish(new SwagCrossPluginMessageEvent(
    "swagfishing:tournament_end",
    "SwagFishing",
    payload,
    player.getUniqueId()
));
```

### 8.3 Subscribing (from SwagTournaments or SwagJobs)

```java
// In SwagTournaments onEnable():
IEventBusService bus = ...; // retrieved from ServicesManager

bus.subscribe("swagfishing:tournament_end", event -> {
    UUID winner = UUID.fromString((String) event.getData().get("winner"));
    double score = (double) event.getData().get("score");
    tournamentManager.recordFishingTournamentWin(winner, score);
}, this);
```

### 8.4 Predefined Channel Convention

| Channel | Fired by | Payload keys |
|---|---|---|
| `swagapi:player_loaded` | SwagAPI | uuid |
| `swagapi:player_unloaded` | SwagAPI | uuid |
| `swagfishing:tournament_end` | SwagFishing | tournamentId, winner, score |
| `swagfishing:fish_caught` | SwagFishing | uuid, fishId, rarity, size |
| `swagfarming:crop_harvested` | SwagFarming | uuid, cropId, quality, amount |
| `swagjobs:level_up` | SwagJobs | uuid, job, newLevel |
| `swagbounties:bounty_claimed` | SwagBounties | uuid, targetUuid, reward |
| `<plugin>:<event>` | Any plugin | Arbitrary — document as added |

> **NOTE:** Channel names are by convention only — the bus does not validate or enforce them. Document all new channels in this table when adding them.

---

## 9. Shared Utility Classes

These classes are migrated from existing plugins into SwagAPI. Existing plugins should replace their local copies with imports from SwagAPI once SwagAPI is on the classpath.

### 9.1 ColorUtil (`com.SwagDev.SwagAPI.util.ColorUtil`)

Migrated verbatim from SwagMenus. Handles `&` legacy color codes and `&#RRGGBB` hex color codes. Converts to Adventure `Component` objects.

```java
// Key methods (preserve exactly from SwagMenus):
public static String colorize(String input)            // & and &#hex → §-codes
public static Component toComponent(String input)      // → Adventure Component
public static List<Component> toComponents(List<String> input)
public static String strip(String input)               // strip all color codes
```

### 9.2 ItemBuilder (`com.SwagDev.SwagAPI.util.ItemBuilder`)

Migrated from SwagMenus. Fluent builder for `ItemStack` objects using Adventure Components. Supports skull textures via base64 (Paper's `PlayerProfile` API).

```java
// Key methods (preserve exactly from SwagMenus):
new ItemBuilder(Material material)
new ItemBuilder(ItemStack base)
.amount(int)
.name(String legacyName)           // uses ColorUtil.toComponent internally
.name(Component)
.lore(List<Component>)
.loreStrings(List<String>)         // uses ColorUtil.toComponents internally
.glow(boolean)
.flags(ItemFlag...)
.skullTexture(String base64)
.build()  →  ItemStack
```

### 9.3 MessageUtil (`com.SwagDev.SwagAPI.util.MessageUtil`)

Generalised from SwagFarming's `MessageUtil`. Loads message templates from a `messages.yml` inside SwagAPI's data folder. Supports prefix injection and placeholder substitution.

```java
public class MessageUtil {
    public String get(String key)
    public String get(String key, Map<String, String> placeholders)
    public void send(Player player, String key)
    public void send(Player player, String key, Map<String, String> placeholders)
    public void reload()
}
```

### 9.4 SchedulerUtil (`com.SwagDev.SwagAPI.util.SchedulerUtil`)

Thin wrappers around Bukkit scheduler to reduce boilerplate.

```java
public class SchedulerUtil {
    public static void async(Plugin plugin, Runnable task)
    public static void sync(Plugin plugin, Runnable task)
    public static void asyncDelayed(Plugin plugin, Runnable task, long delayTicks)
    public static void syncDelayed(Plugin plugin, Runnable task, long delayTicks)
    public static BukkitTask asyncTimer(Plugin plugin, Runnable task, long delay, long period)
    public static BukkitTask syncTimer(Plugin plugin, Runnable task, long delay, long period)
}
```

---

## 10. How Dependent Plugins Integrate

### 10.1 plugin.yml Change

```yaml
# Add SwagAPI as a hard dependency in every Swag plugin:
depend: [SwagAPI]

# If the plugin also has other hard dependencies, append:
depend: [SwagAPI, Vault]
```

### 10.2 pom.xml Change

```xml
<dependency>
    <groupId>com.SwagDev</groupId>
    <artifactId>SwagAPI</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### 10.3 Service Lookup Boilerplate

Place this in each plugin's `onEnable()`, immediately after `saveDefaultConfig()`:

```java
private IDatabaseService   dbService;
private IEconomyService    ecoService;
private IPlayerDataService playerService;
private IMessagingService  msgService;
private IEventBusService   busService;

private boolean hookSwagAPI() {
    ServicesManager sm = getServer().getServicesManager();

    RegisteredServiceProvider<IDatabaseService> dbProv =
        sm.getRegistration(IDatabaseService.class);
    if (dbProv == null) {
        getLogger().severe("SwagAPI IDatabaseService not found! Disabling.");
        return false;
    }
    dbService     = dbProv.getProvider();
    ecoService    = sm.getRegistration(IEconomyService.class).getProvider();
    playerService = sm.getRegistration(IPlayerDataService.class).getProvider();
    msgService    = sm.getRegistration(IMessagingService.class).getProvider();
    busService    = sm.getRegistration(IEventBusService.class).getProvider();
    return true;
}

// In onEnable():
if (!hookSwagAPI()) {
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

### 10.4 Removing Redundant Code

| Plugin | Classes to Remove / Replace |
|---|---|
| SwagFarming | `VaultIntegration` → `IEconomyService`; `MessageUtil` → SwagAPI `MessageUtil`; remove DB pool ownership from `DatabaseManager` |
| SwagFishing | `setupEconomy()` + `Economy` field → `IEconomyService`; `DatabaseManager` connection ownership → `IDatabaseService` |
| SwagMenus | `setupEconomy()` + `Economy` field → `IEconomyService`; `ColorUtil` → SwagAPI `ColorUtil`; `ItemBuilder` → SwagAPI `ItemBuilder` |
| All others | Any local `VaultIntegration` / Economy hook; any duplicate `ColorUtil`, `ItemBuilder`, `MessageUtil` |

> **IMPORTANT:** Do NOT delete plugin-specific Manager classes, listener classes, or table-creation logic. Only remove the connection pool ownership and duplicated utility/economy classes. Each plugin's own `DatabaseManager` can be simplified to take an `IDatabaseService` and call `getConnection()` rather than managing its own pool.

---

## 11. Full Plugin Ecosystem & Dependencies

| Plugin | Needs from SwagAPI |
|---|---|
| SwagAC | `IDatabaseService`, `IEventBusService` |
| SwagBounties | `IDatabaseService`, `IEconomyService`, `IEventBusService` |
| SwagMenus | `IEconomyService`, `IMessagingService`, `ColorUtil`, `ItemBuilder` |
| SwagFishing | `IDatabaseService`, `IEconomyService`, `IPlayerDataService`, `IEventBusService` |
| SwagFarming | `IDatabaseService`, `IEconomyService`, `IPlayerDataService`, `IEventBusService`, `MessageUtil` |
| SwagJobs | `IDatabaseService`, `IEconomyService`, `IPlayerDataService`, `IEventBusService` |
| SwagSlayer | `IDatabaseService`, `IEconomyService`, `IEventBusService` |
| SwagTags | `IDatabaseService`, `IPlayerDataService` |
| SwagTournaments | `IDatabaseService`, `IEconomyService`, `IEventBusService` |
| SwagCore | `IDatabaseService`, `IEconomyService` |
| StackPlus | `IDatabaseService`, `IEconomyService` |

> **NOTE:** SwagAPI must load before ALL of these. Because they all declare `depend: [SwagAPI]`, Bukkit's plugin loader guarantees this automatically.

---

## 12. Full config.yml

```yaml
# SwagAPI Configuration
# ─────────────────────────────────────────────

database:
  type: sqlite           # 'sqlite' or 'mysql'

  sqlite:
    file: swagapi.db

  mysql:
    host: localhost
    port: 3306
    database: swagapi
    username: root
    password: ""
    pool-size: 10
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
    use-ssl: false

economy:
  enabled: true          # Set false to disable Vault hook entirely

messaging:
  prefix: "&8[&bSwagAPI&8] &r"
  date-format: "MM/dd/yyyy HH:mm"

player-data:
  cache-expiry-minutes: 10
  auto-save-interval-minutes: 5

event-bus:
  log-events: false      # Set true for debug logging of all published events

debug: false
```

---

## 13. Commands

**`/swagapi`** (alias: `/sapi`) — Permission: `swagapi.admin` — default: op

| Subcommand | Description |
|---|---|
| `reload` | Reloads `config.yml` and `messages.yml`. Does NOT restart the database pool or re-register services. |
| `status` | Prints database type and pool stats, Vault status, number of loaded player profiles, registered event bus subscriptions. |
| `info` | Prints SwagAPI version, Paper version, registered services, and loaded dependent plugins using SwagAPI services. |

---

## 14. Coding Conventions

### 14.1 Observed Patterns to Replicate

- **Static `getInstance()`:** Every plugin main class exposes a static `getInstance()`. SwagAPI must follow the same pattern.
- **`CompletableFuture` async pattern:** All database operations return `CompletableFuture`. Use `Bukkit.getScheduler().runTaskAsynchronously()` internally — NOT Java's `ForkJoinPool`. This is the exact pattern in SwagFarming's `DatabaseManager`.
- **`ON CONFLICT` upsert:** All INSERT SQL uses `ON CONFLICT(...) DO UPDATE SET` (SQLite upsert syntax). MySQL equivalent is `INSERT ... ON DUPLICATE KEY UPDATE`. The `DatabaseService` should provide a helper or plugins must branch based on `isMySQL()`/`isSQLite()`.
- **Logger over System.out:** Always use `getLogger()` or `plugin.getLogger()`. Never `System.out.println()`.
- **Null-safe integration checks:** Every integration (Vault, PlaceholderAPI, etc.) checks for null before use and exposes an `isEnabled()` boolean, matching the `VaultIntegration` pattern in SwagFarming.
- **Startup timing log:** Log startup duration in ms: `"Enabled in Xms."`.
- **`saveDefaultConfig()` first:** Always called first in `onEnable()` before any config reads.
- **Shutdown order:** Save player data → close DB pool → unregister services. Always in that order.

### 14.2 Package Naming

Use `com.SwagDev.SwagAPI` (capital S, capital D, capital API) exactly as specified. Do not alter casing.

### 14.3 Java Version

Target Java 17. Use records, sealed classes, switch expressions, and text blocks freely. Do not use preview features.

### 14.4 Adventure API

Use Paper's native Adventure API (`net.kyori.adventure`) for all text. Do not use legacy `ChatColor` for new code. `ColorUtil` handles the translation layer for config strings.

---

## 15. Deliverables Checklist

### Source Files

- [ ] `SwagAPI.java` (main class — full implementation)
- [ ] `plugin.yml`
- [ ] `pom.xml` (with shade plugin configured for HikariCP + JDBC drivers + Gson)
- [ ] `config.yml`
- [ ] `messages.yml` (default messages for MessageUtil)
- [ ] `api/IDatabaseService.java`
- [ ] `api/IEconomyService.java`
- [ ] `api/IPlayerDataService.java`
- [ ] `api/IMessagingService.java`
- [ ] `api/IEventBusService.java`
- [ ] `services/DatabaseService.java` (HikariCP, MySQL + SQLite)
- [ ] `services/EconomyService.java` (Vault wrapper)
- [ ] `services/PlayerDataService.java` (profile cache + module system)
- [ ] `services/MessagingService.java` (color, send, title, actionbar)
- [ ] `services/EventBusService.java` (channel registry + publish/subscribe)
- [ ] `database/DatabaseManager.java` (low-level HikariCP setup)
- [ ] `model/SwagPlayerProfile.java`
- [ ] `model/PlayerDataModule.java` (interface for plugin data modules)
- [ ] `events/SwagCrossPluginMessageEvent.java`
- [ ] `events/SwagPlayerDataLoadEvent.java`
- [ ] `events/SwagPlayerDataSaveEvent.java`
- [ ] `util/ColorUtil.java` (migrated from SwagMenus — preserve exactly)
- [ ] `util/ItemBuilder.java` (migrated from SwagMenus — preserve exactly)
- [ ] `util/MessageUtil.java`
- [ ] `util/SchedulerUtil.java`
- [ ] `commands/SwagAPICommand.java` (`/swagapi reload|status|info`)
- [ ] `listeners/PlayerSessionListener.java` (load/save profiles on join/quit)

### Must Compile Clean Against

- Paper 1.21 API
- Java 17
- Vault API 1.7
- HikariCP 5.1.0 (shaded)
- sqlite-jdbc 3.45.3.0 (shaded)
- mysql-connector-j 8.3.0 (shaded)

> **IMPORTANT:** All shaded dependencies must be relocated to avoid classpath conflicts. Relocation prefix: `com.SwagDev.SwagAPI.libs`

---

## 16. Notes for the Agent

> **NOTE:** This spec was generated by analysing decompiled bytecode from SwagFarming 1.0.0, SwagFishing 1.0.0-SNAPSHOT, and SwagMenus 1.1.0. The existing plugins are functional and production-ready. SwagAPI must not break them — it only provides new shared services that they will opt into over time.

### Key Observations from Decompiled Code

- **SwagFarming** shades its own Gson under `com.swag.farming.libs.gson`. SwagAPI must shade Gson under `com.SwagDev.SwagAPI.libs.gson` to avoid collision.
- **SwagFishing** uses SQLite WAL mode (`PRAGMA journal_mode=WAL`) — the shared `DatabaseService` must also enable WAL on SQLite connections.
- **SwagFishing** auto-detects SwagJobs or FleaJobs for levelling — SwagAPI's event bus should publish `swagjobs:level_up` events so SwagFishing can subscribe instead of calling `SwagJobsAPI` directly.
- **SwagMenus** exposes a `WebEditorServer` — this is unrelated to SwagAPI and should not be touched.
- **Both SwagFarming and SwagFishing** have their own `DatabaseManager` classes. These do NOT need to be deleted immediately. In the first integration pass, they can be refactored to accept `IDatabaseService` and call `getConnection()` rather than owning their own pools. Full migration is a separate step.
- Package naming is inconsistent across existing plugins: SwagFarming uses `com.swag.farming`, SwagFishing uses `com.swagserv.swagfishing`, SwagMenus uses `com.swag.swagmenus`. SwagAPI uses `com.SwagDev.SwagAPI`. Do not attempt to normalise existing plugin packages — that is out of scope.

### Load Order Guarantee

Because all plugins declare `depend: [SwagAPI]`, Bukkit guarantees `SwagAPI.onEnable()` completes before any dependent plugin's `onEnable()` begins. There is no race condition to handle. The `ServicesManager` lookups in dependent plugins will always succeed if SwagAPI loaded correctly.

### What is Out of Scope for This Task

- Migrating existing plugin `DatabaseManager`s to use `IDatabaseService` (do later, per plugin).
- Creating a SwagAPI API jar separate from the plugin jar (not needed for ServicesManager pattern).
- Any changes to existing plugin source code (SwagAPI is a new, standalone plugin).
- PlaceholderAPI expansion registration (each plugin handles its own PAPI expansion).

---

*SwagAPI Spec v1.0.0 — Last updated by analysis of SwagFarming 1.0.0, SwagFishing 1.0.0-SNAPSHOT, SwagMenus 1.1.0*
