package com.swag.swagjobs;

import com.swag.swagjobs.command.DevCommand;
import com.swag.swagjobs.command.GrantTagCommand;
import com.swag.swagjobs.command.JobsCommand;
import com.swag.swagjobs.command.JobsTabCompleter;
import com.swag.swagjobs.config.JobsConfig;
import com.swag.swagjobs.database.DatabaseManager;
import com.swag.swagjobs.integrations.AdvancedEnchantmentsIntegration;
import com.swag.swagjobs.listener.*;
import com.swag.swagjobs.manager.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class SwagJobsPlugin extends JavaPlugin {

    private static SwagJobsPlugin instance;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private JobsConfig jobsConfig;
    private JobManager jobManager;
    private BossBarManager bossBarManager;
    private PrestigeManager prestigeManager;
    private SmelterCapManager smelterCapManager;
    // MIGRATED: Vault economy hook replaced by SwagAPI IEconomyService (see hookSwagAPI() / ecoService field below)
    // private Economy economy;
    private Messages messages;
    private VanishManager vanishManager;
    private TagManager tagManager;
    private PrestigeShopManager prestigeShopManager;
    private PlaceBreakManager placeBreakManager;
    private AdvancedEnchantmentsIntegration advancedEnchantmentsIntegration;

    // ── SwagAPI service references ─────────────────────────────────────────────
    private com.SwagDev.SwagAPI.api.IDatabaseService   dbService;
    private com.SwagDev.SwagAPI.api.IEconomyService    ecoService;
    private com.SwagDev.SwagAPI.api.IPlayerDataService playerService;
    private com.SwagDev.SwagAPI.api.IEventBusService   busService;

    @Override
    public void onEnable() {
        try {
            instance = this;
            getLogger().info("Initializing SwagJobs...");
            saveDefaultConfig();

            // ── Step 1: Hook SwagAPI (must be first) ──────────────────────────────
            if (!hookSwagAPI()) {
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            this.messages = new Messages();
            this.databaseManager = new DatabaseManager(this, dbService);
            this.databaseManager.connect();

            this.playerDataManager = new PlayerDataManager(this);
            this.jobsConfig = new JobsConfig(this);
            this.jobManager = new JobManager(this);
            this.bossBarManager = new BossBarManager(this);
            this.prestigeManager = new PrestigeManager(this);
            this.vanishManager = new VanishManager(this);
            this.tagManager = new TagManager(this);
            this.prestigeShopManager = new PrestigeShopManager(this);
            this.prestigeShopManager.load();

            this.smelterCapManager = new SmelterCapManager(this);
            this.placeBreakManager = new PlaceBreakManager(this);
            this.advancedEnchantmentsIntegration = new AdvancedEnchantmentsIntegration(this);

            // MIGRATED: setupEconomy() replaced by SwagAPI IEconomyService, hooked in hookSwagAPI() above
            // setupEconomy();

            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            getServer().getPluginManager().registerEvents(new MinerListener(this), this);
            getServer().getPluginManager().registerEvents(new LumberjackListener(this), this);
            getServer().getPluginManager().registerEvents(new FarmerListener(this), this);
            getServer().getPluginManager().registerEvents(new FishingListener(this), this);
            getServer().getPluginManager().registerEvents(new SmelterListener(this), this);
            getServer().getPluginManager().registerEvents(new HunterListener(this), this);
            getServer().getPluginManager().registerEvents(new EnchanterListener(this), this);
            getServer().getPluginManager().registerEvents(new BrewerListener(this), this);
            getServer().getPluginManager().registerEvents(new CrafterListener(this), this);
            getServer().getPluginManager().registerEvents(new BuilderListener(this), this);
            getServer().getPluginManager().registerEvents(new SmelterCapListener(this), this);
            getServer().getPluginManager().registerEvents(new GUIListener(this), this);
            getServer().getPluginManager().registerEvents(new PrestigeShopListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
            getServer().getPluginManager().registerEvents(new SlimeFixListener(), this);

            // Marks player-placed blocks so job listeners can skip naturally-generated blocks
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onPlace(BlockPlaceEvent e) {
                    e.getBlock().setMetadata("placed", new FixedMetadataValue(SwagJobsPlugin.this, true));
                }
            }, this);

            // Delay by 1 tick to avoid Paper's async command builder concurrency issue
            getServer().getScheduler().runTaskLater(this, () -> {
                try {
                    JobsCommand jobsCmd = new JobsCommand(this);
                    JobsTabCompleter jobsTab = new JobsTabCompleter(this);
                    DevCommand devCmd = new DevCommand(this);
                    JobsTabCompleter devTab = new JobsTabCompleter(this);
                    GrantTagCommand grantTagCmd = new GrantTagCommand(this);

                    boolean jobsRegistered = false;
                    boolean devRegistered = false;

                    try {
                        if (getCommand("jobs") != null) {
                            getCommand("jobs").setExecutor(jobsCmd);
                            getCommand("jobs").setTabCompleter(jobsTab);
                            jobsRegistered = true;
                        } else {
                            getLogger().warning("Command 'jobs' not defined in plugin.yml");
                        }
                    } catch (Exception e) {
                        getLogger().warning("Standard registration for 'jobs' failed: " + e.getMessage());
                    }

                    try {
                        if (getCommand("SwagJobsdev") != null) {
                            getCommand("SwagJobsdev").setExecutor(devCmd);
                            getCommand("SwagJobsdev").setTabCompleter(devTab);
                            devRegistered = true;
                        } else {
                            getLogger().info("'SwagJobsdev' command not present (skipping).");
                        }
                    } catch (Exception e) {
                        getLogger().warning("Standard registration for 'SwagJobsdev' failed: " + e.getMessage());
                    }

                    if (!jobsRegistered) {
                        tryRegisterCommandForce("jobs", jobsCmd, jobsTab);
                        jobsRegistered = true;
                        getLogger().info("Force-registered 'jobs' into CommandMap via reflection.");
                    }
                    if (!devRegistered && getConfig().getBoolean("dev-command-enabled", true)) {
                        tryRegisterCommandForce("SwagJobsdev", devCmd, devTab);
                        devRegistered = true;
                        getLogger().info("Force-registered 'SwagJobsdev' into CommandMap via reflection.");
                    }

                    try {
                        if (getCommand("granttag") != null) {
                            getCommand("granttag").setExecutor(grantTagCmd);
                            getCommand("granttag").setTabCompleter(grantTagCmd);
                            getLogger().info("Registered 'granttag' command.");
                        }
                    } catch (Exception e) {
                        getLogger().warning("Failed to register 'granttag' command: " + e.getMessage());
                    }

                    getLogger().info("Commands registered successfully.");

                    for (Player player : getServer().getOnlinePlayers()) {
                        try {
                            playerDataManager.loadPlayer(player);
                        } catch (Exception ex) {
                            getLogger().warning("Failed to load player data for " + player.getName() + ": " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                } catch (Exception ex) {
                    getLogger().severe("Error registering commands or loading players: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }, 1L);

            getLogger().info("SwagJobs has been enabled!");
        } catch (Exception e) {
            getLogger().severe("CRITICAL ERROR DURING ENABLE:");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Force-registers a command into the server CommandMap via reflection when normal
     * plugin.yml registration fails (e.g. after a PlugMan reload leaving ghost entries).
     */
    private void tryRegisterCommandForce(String name, Object executorObj, TabCompleter tabCompleter) {
        try {
            CommandMap commandMap = getCommandMap();
            if (commandMap == null) {
                getLogger().warning("CommandMap not found; cannot force-register command: " + name);
                return;
            }

            org.bukkit.command.Command delegate = new org.bukkit.command.Command(name) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    try {
                        if (executorObj instanceof org.bukkit.command.CommandExecutor) {
                            return ((org.bukkit.command.CommandExecutor) executorObj).onCommand(sender, this, label, args);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    return false;
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                    try {
                        if (tabCompleter != null) {
                            List<String> res = tabCompleter.onTabComplete(sender, this, alias, args);
                            return res == null ? Collections.emptyList() : res;
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    return Collections.emptyList();
                }
            };

            commandMap.register(getDescription().getName().toLowerCase(), delegate);
        } catch (Exception ex) {
            getLogger().severe("Failed to force-register command '" + name + "': " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private CommandMap getCommandMap() {
        try {
            Object server = Bukkit.getServer();
            Field field = server.getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            Object cm = field.get(server);
            if (cm instanceof CommandMap) return (CommandMap) cm;
        } catch (NoSuchFieldException nsf) {
            // Try Bukkit.getServer().getPluginManager().getClass() fallback
            try {
                Field field = getServer().getPluginManager().getClass().getDeclaredField("commandMap");
                field.setAccessible(true);
                Object cm = field.get(getServer().getPluginManager());
                if (cm instanceof CommandMap) return (CommandMap) cm;
            } catch (Exception e) {
                // fall through
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            try {
                playerDataManager.saveAll();
                for (Player player : getServer().getOnlinePlayers()) {
                    playerDataManager.unloadPlayer(player);
                }
            } catch (Exception ex) {
                getLogger().severe("Error saving/unloading player data during shutdown: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        if (placeBreakManager != null) placeBreakManager.stopCleanupTask();
        // MIGRATED: connection pool is owned by SwagAPI — do not close it here
        // if (databaseManager != null) databaseManager.close();
        if (bossBarManager != null) bossBarManager.removeAll();
        getLogger().info("SwagJobs has been disabled!");
    }

    // MIGRATED: replaced by SwagAPI IEconomyService, hooked in hookSwagAPI() below
    // private void setupEconomy() {
    //     if (getServer().getPluginManager().getPlugin("Vault") == null) return;
    //     RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
    //     if (rsp == null) return;
    //     economy = rsp.getProvider();
    // }

    /**
     * Hooks all SwagAPI services this plugin needs. IDatabaseService is a hard
     * requirement — if it's missing, SwagAPI isn't loaded and the plugin disables itself.
     */
    private boolean hookSwagAPI() {
        org.bukkit.plugin.ServicesManager sm = getServer().getServicesManager();

        // Database — required
        org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IDatabaseService> dbProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IDatabaseService.class);
        if (dbProv == null) {
            getLogger().severe("SwagAPI IDatabaseService not found! Is SwagAPI loaded? Disabling.");
            return false;
        }
        dbService = dbProv.getProvider();
        getLogger().info("Hooked SwagAPI IDatabaseService.");

        // Economy
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

        // Player data — TODO: Consider registering job data as a PlayerDataModule with SwagAPI
        // IPlayerDataService in a future pass. For now we only hook the service reference;
        // PlayerDataManager continues to own its own SQLite-backed player data.
        org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IPlayerDataService> playerProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IPlayerDataService.class);
        if (playerProv != null) {
            playerService = playerProv.getProvider();
            getLogger().info("Hooked SwagAPI IPlayerDataService.");
        }

        // Event bus — used to publish swagjobs:level_up
        org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IEventBusService> busProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IEventBusService.class);
        if (busProv != null) {
            busService = busProv.getProvider();
            getLogger().info("Hooked SwagAPI IEventBusService.");
        }

        getLogger().info("SwagAPI hook complete.");
        return true;
    }

    public com.SwagDev.SwagAPI.api.IDatabaseService   getDbService()     { return dbService; }
    public com.SwagDev.SwagAPI.api.IEconomyService    getEcoService()    { return ecoService; }
    public com.SwagDev.SwagAPI.api.IPlayerDataService getPlayerService() { return playerService; }
    public com.SwagDev.SwagAPI.api.IEventBusService   getBusService()    { return busService; }

    public static SwagJobsPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public JobsConfig getJobsConfig() { return jobsConfig; }
    public JobManager getJobManager() { return jobManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public PrestigeManager getPrestigeManager() { return prestigeManager; }
    public Messages getMessages() { return messages; }
    public SmelterCapManager getSmelterCapManager() { return smelterCapManager; }
    public VanishManager getVanishManager() { return vanishManager; }
    public TagManager getTagManager() { return tagManager; }

    public int getCapForPlayer(Player player) {
        if (smelterCapManager == null) return 0;
        return smelterCapManager.getCapForPlayer(player);
    }

    public PrestigeShopManager getPrestigeShopManager() {
        return prestigeShopManager;
    }

    public PlaceBreakManager getPlaceBreakManager() {
        return placeBreakManager;
    }

    public AdvancedEnchantmentsIntegration getAdvancedEnchantmentsIntegration() {
        return advancedEnchantmentsIntegration;
    }

    public class Messages {
        public String prefix() {
            return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "&8[&aSwagJobs&8]&r "));
        }
    }
}