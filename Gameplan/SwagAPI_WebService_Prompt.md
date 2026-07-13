# SwagAPI — Shared Web Service
## Self-Contained Implementation Prompt

---

## Context

You are working inside an existing Minecraft Paper plugin called **SwagAPI**
(`com.SwagDev.SwagAPI`). SwagAPI is a shared library plugin that is already
built and functional. You are adding a **shared HTTP web server** to SwagAPI so
that all Swag plugins can serve their web UIs through a single port instead of
each plugin starting its own server.

---

## Critical Rules — Read Before Writing Any Code

1. **NEVER delete any existing code.** Add new classes, new methods, new
   registrations. Do not remove or overwrite anything already present.
2. **Output complete files only.** Never produce partial snippets with
   `// rest unchanged` comments. Every file you touch must be output in full.
3. **Never import `com.sun.net.httpserver` in SwagAPI's service classes directly
   exposed to dependent plugins.** The `IWebService` interface must only use
   types from `java.*` or `com.sun.net.httpserver` (which is JDK-internal but
   universally available on Java 17 Paper servers). No third-party HTTP library
   is introduced — this entire feature uses only the JDK built-in
   `com.sun.net.httpserver.*` package.
4. **All routes registered by plugins retain their exact handler logic.**
   The `HttpHandler` implementations inside each plugin are not touched at all.
   SwagAPI only owns the `HttpServer` instance and the route dispatch table.
5. **Path prefix stripping must be transparent to handlers.** When SwagAPI
   mounts a plugin's handler at `/swagfishing/api/fish`, the `HttpExchange`
   passed to the handler must report `/api/fish` as its request URI path — not
   `/swagfishing/api/fish`. This preserves all internal path parsing in existing
   handler code (e.g. `path.substring("/api/fish/".length())`).
6. **Static file handlers must be remapped correctly.** A plugin's `/` static
   handler becomes `/<pluginPrefix>/` on the shared server. The handler receives
   a stripped path (e.g. `/index.html`) so `plugin.getResource("web" + path)`
   still works without modification.
7. **Auth is handled per-handler by each plugin's own password** — SwagAPI does
   not introduce a shared password or global auth layer. Each plugin's handlers
   continue to check their own `Authorization: Bearer <password>` header
   exactly as they do today.
8. **All new config keys go under `web-server:`** in SwagAPI's `config.yml`.
   Do not touch existing config keys.
9. **All new commands are subcommands of `/swagapi`** — the existing command.
10. **Package:** `com.SwagDev.SwagAPI` — match existing casing exactly.
11. **Java 17.** Use records, switch expressions, text blocks freely.

---

## Architecture Overview

```
SwagAPI HttpServer (one port, e.g. 8080)
│
├── /swagfishing/          → SwagFishing StaticFileHandler  (receives "/" stripped to "/")
├── /swagfishing/api/fish  → SwagFishing FishAPIHandler     (receives "/api/fish")
├── /swagfishing/api/auth  → SwagFishing AuthHandler        (receives "/api/auth")
├── /swagfishing/api/...   → (all other SwagFishing routes)
│
├── /discordutils/         → DiscordUtils OAuth handler     (receives "/" stripped)
├── /discordutils/callback → DiscordUtils callback handler
│
└── /swagapi/              → SwagAPI dashboard (built-in, owned by SwagAPI itself)
```

Each plugin calls `IWebService.registerModule(pluginPrefix, routes)` in its
`onEnable()` after retrieving the service. SwagAPI mounts every route from the
map at `/<pluginPrefix><route>` and strips the prefix before dispatching to the
handler.

---

## File 1 — `api/IWebService.java`

```java
package com.SwagDev.SwagAPI.api;

import com.sun.net.httpserver.HttpHandler;

import java.util.Map;

/**
 * Shared HTTP web server service.
 *
 * <p>Each Swag plugin registers its HTTP handlers here instead of starting its
 * own HttpServer. SwagAPI mounts them all under a plugin-specific prefix on a
 * single shared port.</p>
 *
 * <p>Handlers receive an HttpExchange whose request-URI path has the plugin
 * prefix already stripped, so existing handler path-parsing logic requires
 * no modification.</p>
 */
public interface IWebService {

    /**
     * Register all HTTP routes for a plugin.
     *
     * <p>Call this in the plugin's {@code onEnable()} after retrieving the
     * service from the ServicesManager.</p>
     *
     * @param pluginPrefix  Short identifier for this plugin, used as the URL
     *                      path prefix. Must be lowercase, no slashes.
     *                      Example: {@code "swagfishing"}, {@code "discordutils"}.
     * @param routes        Map of context path → handler. The path must start
     *                      with {@code "/"} and is relative to the plugin prefix.
     *                      Example: {@code "/api/fish"} becomes
     *                      {@code /swagfishing/api/fish} on the shared server.
     *                      Use {@code "/"} for the static file / root handler.
     */
    void registerModule(String pluginPrefix, Map<String, HttpHandler> routes);

    /**
     * Unregister all routes previously registered by a plugin.
     * Call this in the plugin's {@code onDisable()}.
     *
     * @param pluginPrefix  The same prefix used in {@link #registerModule}.
     */
    void unregisterModule(String pluginPrefix);

    /**
     * Returns the port the shared web server is listening on, or -1 if the
     * server is not running.
     */
    int getPort();

    /**
     * Returns the bind address the shared web server is listening on.
     */
    String getBindAddress();

    /**
     * Returns true if the shared web server is currently running.
     */
    boolean isRunning();

    /**
     * Returns the base URL for a given plugin's web UI.
     * Example: {@code "http://192.168.1.10:8080/swagfishing"}
     *
     * @param pluginPrefix  The plugin prefix.
     */
    String getPluginUrl(String pluginPrefix);

    /**
     * Returns the names of all currently registered plugin prefixes.
     */
    java.util.Set<String> getRegisteredModules();
}
```

---

## File 2 — `services/WebService.java`

This is the full implementation. Read every comment — they explain decisions
that must be preserved.

```java
package com.SwagDev.SwagAPI.services;

import com.SwagDev.SwagAPI.SwagAPI;
import com.SwagDev.SwagAPI.api.IWebService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class WebService implements IWebService {

    private final SwagAPI plugin;
    private HttpServer server;

    // pluginPrefix → list of context paths registered for that plugin
    // Used so unregisterModule() knows which contexts to remove.
    private final Map<String, List<String>> registeredContexts = new ConcurrentHashMap<>();

    // pluginPrefix → their routes (kept so we can log/inspect them)
    private final Map<String, Map<String, HttpHandler>> moduleRoutes = new ConcurrentHashMap<>();

    private int port = -1;
    private String bindAddress = "0.0.0.0";
    private boolean running = false;

    public WebService(SwagAPI plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void initialize() {
        if (!plugin.getConfig().getBoolean("web-server.enabled", true)) {
            plugin.getLogger().info("[WebService] Shared web server is disabled in config.");
            return;
        }

        port        = plugin.getConfig().getInt("web-server.port", 8080);
        bindAddress = plugin.getConfig().getString("web-server.bind-address", "0.0.0.0");

        try {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            server.setExecutor(Executors.newFixedThreadPool(
                plugin.getConfig().getInt("web-server.threads", 8)));

            // Register the SwagAPI dashboard routes (built-in)
            registerDashboardRoutes();

            server.start();
            running = true;

            plugin.getLogger().info("[WebService] Shared web server started on port " + port);

        } catch (java.net.BindException e) {
            plugin.getLogger().severe("[WebService] Port " + port + " is already in use. " +
                "Change 'web-server.port' in SwagAPI config.yml.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[WebService] Failed to start web server.", e);
        }
    }

    public void shutdown() {
        if (server != null) {
            server.stop(0);
            running = false;
            plugin.getLogger().info("[WebService] Shared web server stopped.");
        }
    }

    // ── IWebService implementation ────────────────────────────────────────────

    @Override
    public void registerModule(String pluginPrefix, Map<String, HttpHandler> routes) {
        if (server == null || !running) {
            plugin.getLogger().warning("[WebService] Cannot register module '" + pluginPrefix +
                "' — web server is not running.");
            return;
        }
        if (pluginPrefix == null || pluginPrefix.isBlank() || pluginPrefix.contains("/")) {
            plugin.getLogger().warning("[WebService] Invalid plugin prefix: '" + pluginPrefix + "'");
            return;
        }

        // Unregister any existing contexts for this prefix (safe re-registration)
        unregisterModule(pluginPrefix);

        List<String> contextPaths = new ArrayList<>();
        String prefix = "/" + pluginPrefix;

        for (Map.Entry<String, HttpHandler> entry : routes.entrySet()) {
            String route     = entry.getKey(); // e.g. "/api/fish"
            HttpHandler handler = entry.getValue();

            // Full context path on the shared server, e.g. /swagfishing/api/fish
            String contextPath = prefix + (route.equals("/") ? "" : route);
            if (contextPath.isBlank()) contextPath = "/";

            // Wrap the handler so it strips the prefix from the exchange URI
            // before passing it to the plugin's handler.
            HttpHandler wrapped = new PrefixStrippingHandler(prefix, handler);

            server.createContext(contextPath, wrapped);
            contextPaths.add(contextPath);
        }

        // Register the prefix root if not already registered — needed so
        // browsers hitting /swagfishing redirect correctly.
        String rootContext = prefix;
        if (!contextPaths.contains(rootContext) && !contextPaths.contains(prefix + "/")) {
            server.createContext(rootContext, exchange -> {
                exchange.getResponseHeaders().set("Location", prefix + "/");
                exchange.sendResponseHeaders(302, -1);
            });
            contextPaths.add(rootContext);
        }

        registeredContexts.put(pluginPrefix, contextPaths);
        moduleRoutes.put(pluginPrefix, new HashMap<>(routes));

        plugin.getLogger().info("[WebService] Registered module '" + pluginPrefix +
            "' with " + routes.size() + " route(s). URL: " + getPluginUrl(pluginPrefix));
    }

    @Override
    public void unregisterModule(String pluginPrefix) {
        List<String> contexts = registeredContexts.remove(pluginPrefix);
        if (contexts != null && server != null) {
            for (String ctx : contexts) {
                try {
                    server.removeContext(ctx);
                } catch (IllegalArgumentException ignored) {
                    // Context was already removed — safe to ignore
                }
            }
        }
        moduleRoutes.remove(pluginPrefix);
    }

    @Override
    public int getPort() {
        return running ? port : -1;
    }

    @Override
    public String getBindAddress() {
        return bindAddress;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getPluginUrl(String pluginPrefix) {
        String ip = resolveDisplayIp();
        return "http://" + ip + ":" + port + "/" + pluginPrefix;
    }

    @Override
    public Set<String> getRegisteredModules() {
        return Collections.unmodifiableSet(registeredContexts.keySet());
    }

    // ── Built-in SwagAPI dashboard routes ─────────────────────────────────────

    /**
     * Registers the /swagapi/ dashboard endpoints.
     * These are owned by SwagAPI itself — not a plugin module.
     */
    private void registerDashboardRoutes() {
        // /swagapi/ — simple status dashboard
        server.createContext("/swagapi/", exchange -> {
            String json = buildDashboardJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        // /swagapi/modules — list registered plugin modules
        server.createContext("/swagapi/modules", exchange -> {
            StringBuilder sb = new StringBuilder("{\"modules\":[");
            boolean first = true;
            for (Map.Entry<String, List<String>> e : registeredContexts.entrySet()) {
                if (!first) sb.append(",");
                sb.append("{\"prefix\":\"").append(e.getKey()).append("\",")
                  .append("\"url\":\"").append(getPluginUrl(e.getKey())).append("\",")
                  .append("\"routes\":").append(e.getValue().size()).append("}");
                first = false;
            }
            sb.append("]}");
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
    }

    private String buildDashboardJson() {
        return """
            {
              "status": "running",
              "port": %d,
              "modules": %d
            }
            """.formatted(port, registeredContexts.size());
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Resolves the best display IP for logging/URL generation.
     * Matches the logic in SwagFishing's WebServerManager.getServerIP().
     */
    private String resolveDisplayIp() {
        if (!bindAddress.equals("0.0.0.0")) return bindAddress;

        String serverIp = plugin.getServer().getIp();
        if (serverIp != null && !serverIp.isBlank() && !serverIp.equals("0.0.0.0")) {
            return serverIp;
        }
        try {
            String local = java.net.InetAddress.getLocalHost().getHostAddress();
            if (local != null && !local.isBlank()) return local;
        } catch (Exception ignored) {}

        return "YOUR_SERVER_IP";
    }

    // ── PrefixStrippingHandler ─────────────────────────────────────────────────

    /**
     * Wraps a plugin's HttpHandler and strips the plugin prefix from the
     * request URI before dispatching, so the plugin's handler sees paths
     * identical to what it expected when it had its own HttpServer.
     *
     * <p>Example: request to /swagfishing/api/fish → handler sees /api/fish</p>
     * <p>Example: request to /swagfishing/ → handler sees /</p>
     *
     * <p>This is the key mechanism that allows plugin handlers to require
     * zero modification.</p>
     */
    private static class PrefixStrippingHandler implements HttpHandler {

        private final String prefix;      // e.g. "/swagfishing"
        private final HttpHandler delegate;

        PrefixStrippingHandler(String prefix, HttpHandler delegate) {
            this.prefix   = prefix;
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Build the stripped URI — remove the prefix, keep everything after
            String originalPath = exchange.getRequestURI().getPath();
            String strippedPath = originalPath.startsWith(prefix)
                ? originalPath.substring(prefix.length())
                : originalPath;

            // Ensure strippedPath is never empty — handlers expect at least "/"
            if (strippedPath.isBlank()) strippedPath = "/";

            // Rebuild the URI with the stripped path
            java.net.URI originalUri  = exchange.getRequestURI();
            java.net.URI strippedUri;
            try {
                strippedUri = new java.net.URI(
                    originalUri.getScheme(),
                    originalUri.getUserInfo(),
                    originalUri.getHost(),
                    originalUri.getPort(),
                    strippedPath,
                    originalUri.getQuery(),
                    originalUri.getFragment()
                );
            } catch (java.net.URISyntaxException e) {
                // Fallback: deliver as-is if URI construction fails
                delegate.handle(exchange);
                return;
            }

            // Wrap the exchange to override getRequestURI()
            delegate.handle(new PrefixStrippedExchange(exchange, strippedUri));
        }
    }

    /**
     * HttpExchange wrapper that overrides getRequestURI() to return the
     * prefix-stripped URI. All other methods delegate to the real exchange.
     *
     * <p>This is necessary because HttpExchange is abstract with many methods —
     * we subclass it and delegate everything except getRequestURI().</p>
     */
    private static class PrefixStrippedExchange extends HttpExchange {

        private final HttpExchange delegate;
        private final java.net.URI strippedUri;

        PrefixStrippedExchange(HttpExchange delegate, java.net.URI strippedUri) {
            this.delegate   = delegate;
            this.strippedUri = strippedUri;
        }

        // ── The only override that matters ────────────────────────────────────
        @Override
        public java.net.URI getRequestURI() {
            return strippedUri;
        }

        // ── Everything else delegates to the real exchange ────────────────────
        @Override public com.sun.net.httpserver.Headers getRequestHeaders()  { return delegate.getRequestHeaders(); }
        @Override public com.sun.net.httpserver.Headers getResponseHeaders() { return delegate.getResponseHeaders(); }
        @Override public String getRequestMethod()                           { return delegate.getRequestMethod(); }
        @Override public com.sun.net.httpserver.HttpContext getHttpContext()  { return delegate.getHttpContext(); }
        @Override public void close()                                        { delegate.close(); }
        @Override public java.io.InputStream getRequestBody()                { return delegate.getRequestBody(); }
        @Override public java.io.OutputStream getResponseBody()              { return delegate.getResponseBody(); }
        @Override public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            delegate.sendResponseHeaders(rCode, responseLength);
        }
        @Override public java.net.InetSocketAddress getRemoteAddress()       { return delegate.getRemoteAddress(); }
        @Override public int getResponseCode()                               { return delegate.getResponseCode(); }
        @Override public java.net.InetSocketAddress getLocalAddress()        { return delegate.getLocalAddress(); }
        @Override public String getProtocol()                                { return delegate.getProtocol(); }
        @Override public Object getAttribute(String name)                    { return delegate.getAttribute(name); }
        @Override public void setAttribute(String name, Object value)        { delegate.setAttribute(name, value); }
        @Override public void setStreams(java.io.InputStream i, java.io.OutputStream o) {
            delegate.setStreams(i, o);
        }
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return delegate.getPrincipal(); }
    }
}
```

---

## Changes to Existing SwagAPI Files

### `SwagAPI.java` — Add WebService field, init, shutdown, and registration

Add field alongside the other service fields:
```java
private WebService webService;
```

In `onEnable()`, add after `eventBusService` is initialized and registered
(web service must be last so plugins that register on their own `onEnable()`
find it already running — but since they `depend: [SwagAPI]`, SwagAPI's
`onEnable()` finishes first, so they retrieve it during their own `onEnable()`):
```java
// 6. Web service — shared HTTP server
webService = new WebService(this);
webService.initialize();
sm.register(IWebService.class, webService, this, ServicePriority.Normal);
```

In `onDisable()`, add before `databaseService.shutdown()`:
```java
if (webService != null) webService.shutdown();
```

Add getter:
```java
public WebService getWebService() { return webService; }
```

---

### `plugin.yml` — Add permissions

Add under the existing `permissions:` block:
```yaml
  swagapi.web:
    description: Access the SwagAPI shared web dashboard
    default: op
```

---

### `config.yml` — Add web-server block

Add after the existing `debug: false` line:
```yaml
web-server:
  enabled: true
  port: 8080
  bind-address: "0.0.0.0"
  threads: 8            # HttpServer thread pool size
```

---

### `commands/SwagAPICommand.java` — Add webserver subcommands

Add inside the existing command executor alongside `reload`, `status`, `info`:

```java
// ── /swagapi web ──────────────────────────────────────────────────────────
case "web": {
    if (!sender.hasPermission("swagapi.admin")) {
        sender.sendMessage("§cNo permission.");
        return true;
    }
    WebService ws = SwagAPI.getInstance().getWebService();
    if (ws == null || !ws.isRunning()) {
        sender.sendMessage("§b[SwagAPI] §cWeb server is not running.");
        return true;
    }
    sender.sendMessage("§b[SwagAPI] §7Shared web server status:");
    sender.sendMessage("  §7Port: §f" + ws.getPort());
    sender.sendMessage("  §7Bind: §f" + ws.getBindAddress());
    sender.sendMessage("  §7Registered modules (§f" + ws.getRegisteredModules().size() + "§7):");
    for (String module : ws.getRegisteredModules()) {
        sender.sendMessage("    §7- §b" + module + " §7→ §f" + ws.getPluginUrl(module));
    }
    return true;
}
```

Also add `"web"` to the existing tab-completer list for `/swagapi` subcommands.

Also add the following to the existing `status` subcommand output:
```java
// Add to status output:
WebService ws = SwagAPI.getInstance().getWebService();
sender.sendMessage("§7Web Server: " + (ws != null && ws.isRunning() ? "§aRunning (port " + ws.getPort() + ")" : "§cStopped"));
if (ws != null && ws.isRunning()) {
    sender.sendMessage("§7Web Modules: §e" + ws.getRegisteredModules().size());
}
```

---

## How Dependent Plugins Integrate — The Exact Migration

This section shows precisely what changes in each plugin that has a web server.
Using SwagFishing as the reference (since its full `WebServerManager` source
was provided), the pattern applies identically to DiscordUtils and any future
plugins.

### Step 1 — Retrieve IWebService in `onEnable()`

In `SwagFishing.java` (the main plugin class), after `hookSwagAPI()` succeeds,
add:

```java
// Field declaration (alongside other service fields):
private com.SwagDev.SwagAPI.api.IWebService webService;

// In onEnable(), after hookSwagAPI():
RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IWebService> webProv =
    getServer().getServicesManager()
               .getRegistration(com.SwagDev.SwagAPI.api.IWebService.class);
if (webProv != null) {
    webService = webProv.getProvider();
}

// Getter:
public com.SwagDev.SwagAPI.api.IWebService getWebService() { return webService; }
```

### Step 2 — Modify `WebServerManager.start()` ONLY

The ONLY change inside `WebServerManager` is replacing the server creation and
`server.start()` call with a route registration call. **Every handler class,
every helper method, every line of handler logic stays 100% unchanged.**

**Before (`WebServerManager.start()`):**
```java
public void start() {
    if (!plugin.getConfig().getBoolean("web-editor.enabled", true)) {
        plugin.getLogger().info("Web editor is disabled in config");
        return;
    }

    int port = plugin.getConfig().getInt("web-editor.port", 8080);
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
        plugin.getLogger().info("Web Editor Started!");
        plugin.getLogger().info("URL: http://" + getServerIP() + ":" + port);
        plugin.getLogger().info("Password: " + password);
        plugin.getLogger().info("Change password in config.yml!");
        plugin.getLogger().info("========================================");

    } catch (java.net.BindException e) {
        // ... error handling
    } catch (IOException e) {
        plugin.getLogger().log(Level.SEVERE, "Failed to start web server!", e);
    }
}
```

**After (`WebServerManager.start()` — the ONLY change):**
```java
public void start() {
    if (!plugin.getConfig().getBoolean("web-editor.enabled", true)) {
        plugin.getLogger().info("Web editor is disabled in config");
        return;
    }

    // Check if SwagAPI shared web service is available
    com.SwagDev.SwagAPI.api.IWebService webService = plugin.getWebService();
    if (webService != null && webService.isRunning()) {
        // Register all routes with SwagAPI's shared web server
        Map<String, com.sun.net.httpserver.HttpHandler> routes = new java.util.LinkedHashMap<>();
        routes.put("/",                          new StaticFileHandler());
        routes.put("/api/fish",                  new FishAPIHandler());
        routes.put("/api/schemes",               new SchemesAPIHandler());
        routes.put("/api/totems",                new TotemsAPIHandler());
        routes.put("/api/config/totems",         new TotemConfigAPIHandler());
        routes.put("/api/config/tournaments",    new TournamentConfigAPIHandler());
        routes.put("/api/config/deliveries",     new DeliveryConfigAPIHandler());
        routes.put("/api/auth",                  new AuthHandler());
        routes.put("/api/mythic/mobs",           new MythicMobsListHandler());
        routes.put("/api/modelengine/blueprints",new ModelEngineBlueprintsHandler());
        routes.put("/api/mythic/config",         new MythicConfigHandler());
        routes.put("/api/fishable-mobs/config",  new FishableMobConfigHandler());
        routes.put("/api/itemsadder/items",      new ItemsAdderItemsHandler());

        webService.registerModule("swagfishing", routes);

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Web Editor Started (via SwagAPI shared server)!");
        plugin.getLogger().info("URL: " + webService.getPluginUrl("swagfishing"));
        plugin.getLogger().info("Password: " + password);
        plugin.getLogger().info("Change password in config.yml!");
        plugin.getLogger().info("========================================");

    } else {
        // Fallback: start own server if SwagAPI web service is unavailable
        // (preserves the original start() logic exactly for standalone operation)
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
            plugin.getLogger().info("Web Editor Started (standalone fallback)!");
            plugin.getLogger().info("URL: http://" + getServerIP() + ":" + port);
            plugin.getLogger().info("Password: " + password);
            plugin.getLogger().info("Change password in config.yml!");
            plugin.getLogger().info("========================================");

        } catch (java.net.BindException e) {
            plugin.getLogger().severe("Port " + port + " is already in use.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start web server!", e);
        }
    }
}
```

### Step 3 — Modify `WebServerManager.stop()` ONLY

```java
public void stop() {
    // If using SwagAPI shared server, unregister our module
    com.SwagDev.SwagAPI.api.IWebService webService = plugin.getWebService();
    if (webService != null) {
        webService.unregisterModule("swagfishing");
        plugin.getLogger().info("Web editor unregistered from shared server.");
        return;
    }

    // Fallback: stop own server (original logic — unchanged)
    if (server != null) {
        server.stop(0);
        plugin.getLogger().info("Web editor stopped");
    }
}
```

### Step 4 — Remove `web-editor.port` from SwagFishing's `config.yml`

The port is now owned by SwagAPI. The `web-editor.port` key in SwagFishing's
config is no longer used when SwagAPI is present (it is only used in the
standalone fallback path). Add a comment:

```yaml
web-editor:
  enabled: true
  # password is still per-plugin — each plugin keeps its own auth
  password: "changeme"
  bind-address: "0.0.0.0"
  # port is only used if SwagAPI is not present (standalone fallback mode)
  # When SwagAPI is loaded, all web UIs share SwagAPI's web-server.port
  port: 8081
```

---

## DiscordUtils Migration (LinkHttpServer)

DiscordUtils has a simpler single-purpose HTTP server (`LinkHttpServer`) used
for OAuth2 Discord account linking callbacks. The same pattern applies:

```java
// In LinkHttpServer.start() — replace HttpServer.create() and start():
com.SwagDev.SwagAPI.api.IWebService webService = plugin.getWebService();
if (webService != null && webService.isRunning()) {
    Map<String, com.sun.net.httpserver.HttpHandler> routes = new java.util.LinkedHashMap<>();
    routes.put("/callback", this::handleOAuthCallback); // or whatever the handler is
    routes.put("/",         this::handleRoot);
    webService.registerModule("discordutils", routes);
    plugin.getLogger().info("Discord link server registered at: "
        + webService.getPluginUrl("discordutils"));
} else {
    // Original standalone HttpServer.create() fallback — unchanged
}
```

---

## URL Changes for Admins

After migration, the web UI URLs change from:

| Before | After |
|---|---|
| `http://IP:8080` | `http://IP:8080/swagfishing` |
| `http://IP:PORT` (DiscordUtils) | `http://IP:8080/discordutils` |

The frontend JavaScript must have its API base URL updated to include the prefix.
In SwagFishing's `web/index.html`, any hardcoded `/api/` paths must become
relative paths (they already are if the HTML uses relative `fetch` calls, which
it does since it's served from the same origin) — no change needed.

> **IMPORTANT NOTE TO AGENT:** The frontend `index.html` inside SwagFishing's
> jar is served via `plugin.getResource("web" + path)`. When the server is at
> `/swagfishing/`, the browser loads `/swagfishing/index.html`. Relative API
> calls like `fetch('/api/fish')` from that page will resolve to
> `/api/fish` — NOT `/swagfishing/api/fish`. This means the existing JS in
> `index.html` will break unless it uses relative URLs (e.g. `fetch('api/fish')`
> without a leading slash, or `fetch('./api/fish')`).
>
> **The agent must flag this to the developer** and note that the SwagFishing
> frontend JS needs its API base URL updated to use relative paths or the full
> prefix path. This is a frontend-only change and is out of scope for this
> prompt — but the developer must be aware of it.

---

## Deliverables Checklist

- [ ] `api/IWebService.java` (interface — full file)
- [ ] `services/WebService.java` (implementation — full file)
- [ ] `SwagAPI.java` (main class — full file, webService added, nothing removed)
- [ ] `plugin.yml` (full file, new permission added, nothing removed)
- [ ] `config.yml` (full file, web-server block added, nothing removed)
- [ ] `commands/SwagAPICommand.java` (full file, `web` subcommand added, nothing removed)

## What the Agent Does NOT Produce

- Changes to SwagFishing's `WebServerManager` handler inner classes (zero changes)
- Changes to SwagFishing's `index.html` or any JS (frontend — out of scope, flagged above)
- Changes to DiscordUtils' OAuth handler logic (zero changes to handler logic)
- Any third-party HTTP library — everything uses `com.sun.net.httpserver` only

---

## Key Technical Points for the Agent to Verify

1. `HttpExchange` is abstract — `PrefixStrippedExchange` must implement ALL
   abstract methods. The implementation above covers all methods in the
   `com.sun.net.httpserver.HttpExchange` abstract class as of Java 17. Verify
   this compiles cleanly.

2. `server.removeContext(String path)` throws `IllegalArgumentException` if the
   context doesn't exist — the `unregisterModule()` implementation catches this.

3. `HttpServer.createContext()` uses prefix matching — `/swagfishing` matches
   `/swagfishing/api/fish` AND `/swagfishing/`. The more specific context
   always wins. This is correct behavior and requires no special handling.

4. The thread pool size of 8 in `web-server.threads` replaces the 4 threads
   SwagFishing previously used alone — size upward since multiple plugins share it.

5. The `PrefixStrippingHandler` and `PrefixStrippedExchange` classes are
   package-private static inner classes of `WebService`. They do not need to
   be in the `api/` package — plugins never reference them directly.

---

*SwagAPI Shared Web Service Prompt — Self-contained, paste directly into agent CLI.*
