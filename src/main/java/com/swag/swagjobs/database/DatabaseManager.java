package com.swag.swagjobs.database;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final SwagJobsPlugin plugin;
    // MIGRATED: this DatabaseManager no longer owns a raw JDBC connection or pool.
    // Connections are now borrowed from SwagAPI's shared HikariCP pool via dbService.getConnection()
    // and must be closed (returned to the pool) after each use — see getConnection() below.
    // private Connection connection;
    // private final String url;
    private final Object dbLock = new Object();

    // SwagAPI shared database service (injected) — replaces local pool ownership
    private final com.SwagDev.SwagAPI.api.IDatabaseService dbService;

    public DatabaseManager(SwagJobsPlugin plugin, com.SwagDev.SwagAPI.api.IDatabaseService dbService) {
        this.plugin = plugin;
        this.dbService = dbService;
        // MIGRATED: raw JDBC URL construction no longer needed — SwagAPI owns the connection pool.
        // File dbFile = new File(plugin.getDataFolder(), "SwagJobs.db");
        // this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    /**
     * initialize()/connect() now only creates tables — pool setup (open connection, WAL mode)
     * is owned entirely by SwagAPI's DatabaseService.
     */
    public void connect() {
        try {
            createTables();
            plugin.getLogger().info("Successfully connected to SwagAPI shared database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create SwagJobs tables on shared database!", e);
            return;
        }

        // One-time import of a legacy standalone database (old FleaJobs or pre-SwagAPI SwagJobs
        // install) if the admin dropped one into this plugin's data folder. See method below.
        importLegacyDatabaseIfPresent();

        // Fully automatic import straight from a still-present sibling FleaJobs install —
        // no admin file-copying required. See method below.
        importFromSiblingFleaJobsInstallIfPresent();
    }

    /**
     * Drag-and-drop migration path: if a legacy standalone SQLite file named "fleajobs.db"
     * or "SwagJobs.db" is found in this plugin's data folder, its rows are imported into
     * SwagAPI's shared database (SQLite or MySQL — both are supported) and the file is then
     * renamed to "<name>.imported" so it is never re-imported on a later restart.
     *
     * The legacy schema is identical to this plugin's schema (same table/column names), so
     * rows are copied as-is. Existing rows in the shared database are never overwritten
     * (INSERT ... IGNORE) so this is safe to run against a database that already has data.
     *
     * This is a fallback/explicit path — {@link #importFromSiblingFleaJobsInstallIfPresent()}
     * handles the fully automatic case where FleaJobs's own data folder is still present
     * alongside this plugin's, so most admins never need to use this manual drop-in path.
     */
    private void importLegacyDatabaseIfPresent() {
        File dataFolder = plugin.getDataFolder();
        String[] legacyNames = { "fleajobs.db", "SwagJobs.db" };

        for (String name : legacyNames) {
            File legacyFile = new File(dataFolder, name);
            if (!legacyFile.isFile()) continue;

            plugin.getLogger().info("Found legacy database '" + name + "' in SwagJobs's own data folder — importing into the SwagAPI shared database...");
            boolean ok = importLegacyDatabaseFile(legacyFile, name, false);

            if (ok) {
                File renamed = new File(dataFolder, name + ".imported");
                if (legacyFile.renameTo(renamed)) {
                    plugin.getLogger().info("Renamed '" + name + "' to '" + renamed.getName() + "' so it won't be imported again.");
                } else {
                    plugin.getLogger().warning("Import of '" + name + "' succeeded but the file could not be renamed. " +
                            "Please rename or remove it manually to prevent re-importing on next restart.");
                }
            }
        }
    }

    /**
     * Fully automatic, "hidden" migration path: if a sibling FleaJobs plugin data folder
     * ({@code plugins/FleaJobs/}) still exists next to this plugin's own data folder
     * ({@code plugins/SwagJobs/}) and contains a {@code fleajobs.db}, its rows are imported
     * into the SwagAPI shared database automatically — no admin action required at all.
     *
     * This is strictly READ-ONLY with respect to {@code plugins/FleaJobs/}: SwagJobs does not
     * own that directory (FleaJobs may still be installed, or simply left in place after being
     * uninstalled) so nothing inside it is ever renamed, moved, or deleted. The legacy SQLite
     * connection is opened in read-only mode so not even a WAL/journal side-file is written
     * there. "Already imported" is instead tracked with a marker file inside SwagJobs's own
     * data folder — safe to call on every startup.
     */
    private void importFromSiblingFleaJobsInstallIfPresent() {
        File dataFolder = plugin.getDataFolder();
        File marker = new File(dataFolder, ".fleajobs_sibling_import.done");
        if (marker.isFile()) {
            return; // already imported from the sibling FleaJobs install in a previous run
        }

        File siblingFolder = new File(dataFolder.getParentFile(), "FleaJobs");
        File siblingDb = new File(siblingFolder, "fleajobs.db");
        if (!siblingDb.isFile()) {
            // Nothing to import (yet). Deliberately do NOT write the marker here — if FleaJobs
            // is installed/populated later, or this runs before FleaJobs has created its file,
            // we want to retry the check again on the next restart.
            return;
        }

        plugin.getLogger().info("Found a sibling FleaJobs install at '" + siblingDb.getAbsolutePath() +
                "' — automatically importing its data into the SwagAPI shared database. " +
                "(Read-only: the FleaJobs folder itself will not be modified.)");

        boolean ok = importLegacyDatabaseFile(siblingDb, "sibling FleaJobs install", true);

        if (ok) {
            try {
                if (marker.createNewFile()) {
                    plugin.getLogger().info("Sibling FleaJobs import complete. Marked done at '" + marker.getAbsolutePath() +
                            "' so it will not be re-imported on future restarts.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Sibling FleaJobs import succeeded but the completion marker could not be " +
                        "written to '" + marker.getAbsolutePath() + "'. It may be re-imported on next restart " +
                        "(safe: duplicate rows are ignored either way).", e);
            }
        } else {
            plugin.getLogger().warning("Sibling FleaJobs import failed — will retry automatically on next restart.");
        }
    }

    /**
     * Opens a JDBC connection to {@code legacyFile} (a legacy fleajobs.db/SwagJobs.db) and
     * imports its five known tables into the SwagAPI shared database. Shared by both the
     * manual drop-in path and the automatic sibling-install path above.
     *
     * @param readOnly if true, the legacy connection is opened read-only (used for the sibling
     *                 FleaJobs install, which SwagJobs does not own and must not write to at all —
     *                 not even a WAL/journal file)
     * @return true if the import ran to completion without throwing
     */
    private boolean importLegacyDatabaseFile(File legacyFile, String sourceLabel, boolean readOnly) {
        try {
            Class.forName("org.sqlite.JDBC");

            String url = "jdbc:sqlite:" + legacyFile.getAbsolutePath();
            Connection legacy;
            if (readOnly) {
                org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
                config.setReadOnly(true);
                Properties props = config.toProperties();
                legacy = DriverManager.getConnection(url, props);
            } else {
                legacy = DriverManager.getConnection(url);
            }

            try (Connection legacyConn = legacy) {
                boolean mysql = dbService.isMySQL();

                int jobs = importLegacyTable(legacyConn,
                        "SELECT uuid, job_name, level, xp, prestige, job_points FROM player_jobs",
                        (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                                + "player_jobs (uuid, job_name, level, xp, prestige, job_points) VALUES (?, ?, ?, ?, ?, ?)",
                        (rs, ps) -> {
                            ps.setString(1, rs.getString("uuid"));
                            ps.setString(2, rs.getString("job_name"));
                            ps.setInt(3, rs.getInt("level"));
                            ps.setDouble(4, rs.getDouble("xp"));
                            ps.setInt(5, rs.getInt("prestige"));
                            ps.setInt(6, rs.getInt("job_points"));
                        });

                int rewards = importLegacyTable(legacyConn,
                        "SELECT uuid, job_name, level, money, claimed, prestige FROM player_rewards",
                        (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                                + "player_rewards (uuid, job_name, level, money, claimed, prestige) VALUES (?, ?, ?, ?, ?, ?)",
                        (rs, ps) -> {
                            ps.setString(1, rs.getString("uuid"));
                            ps.setString(2, rs.getString("job_name"));
                            ps.setInt(3, rs.getInt("level"));
                            ps.setDouble(4, rs.getDouble("money"));
                            ps.setBoolean(5, rs.getBoolean("claimed"));
                            ps.setInt(6, rs.getInt("prestige"));
                        });

                int activeJobs = importLegacyTable(legacyConn,
                        "SELECT uuid, active_job FROM player_active_job",
                        (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                                + "player_active_job (uuid, active_job) VALUES (?, ?)",
                        (rs, ps) -> {
                            ps.setString(1, rs.getString("uuid"));
                            ps.setString(2, rs.getString("active_job"));
                        });

                int smelterBlocks = importLegacyTable(legacyConn,
                        "SELECT uuid, world, x, y, z, block_type FROM player_smelter_blocks",
                        (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                                + "player_smelter_blocks (uuid, world, x, y, z, block_type) VALUES (?, ?, ?, ?, ?, ?)",
                        (rs, ps) -> {
                            ps.setString(1, rs.getString("uuid"));
                            ps.setString(2, rs.getString("world"));
                            ps.setInt(3, rs.getInt("x"));
                            ps.setInt(4, rs.getInt("y"));
                            ps.setInt(5, rs.getInt("z"));
                            ps.setString(6, rs.getString("block_type"));
                        });

                int shopPurchases = importLegacyTable(legacyConn,
                        "SELECT uuid, player_name, item_id, cost, timestamp FROM prestige_shop_purchases",
                        "INSERT INTO prestige_shop_purchases (uuid, player_name, item_id, cost, timestamp) VALUES (?, ?, ?, ?, ?)",
                        (rs, ps) -> {
                            ps.setString(1, rs.getString("uuid"));
                            ps.setString(2, rs.getString("player_name"));
                            ps.setString(3, rs.getString("item_id"));
                            ps.setInt(4, rs.getInt("cost"));
                            ps.setLong(5, rs.getLong("timestamp"));
                        });

                plugin.getLogger().info("Legacy import from '" + sourceLabel + "' complete: "
                        + jobs + " job rows, " + rewards + " reward rows, " + activeJobs + " active-job rows, "
                        + smelterBlocks + " smelter blocks, " + shopPurchases + " shop purchases.");
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to import legacy database '" + sourceLabel + "'. " +
                    "It has been left in place so you can retry after fixing the issue.", e);
            return false;
        }
    }

    /** Row-binder for copying a single legacy result-set row into a target INSERT statement. */
    private interface LegacyRowBinder {
        void bind(ResultSet rs, PreparedStatement ps) throws SQLException;
    }

    /**
     * Runs {@code selectSql} against the legacy connection and, for each row, binds it via
     * {@code binder} and executes {@code insertSql} against the shared SwagAPI database.
     * Returns the number of rows actually inserted (0 for rows ignored as duplicates).
     */
    private int importLegacyTable(Connection legacy, String selectSql, String insertSql, LegacyRowBinder binder) {
        int inserted = 0;
        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery(selectSql);
             Connection target = getConnection();
             PreparedStatement ps = target.prepareStatement(insertSql)) {
            while (rs.next()) {
                binder.bind(rs, ps);
                inserted += ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to import a table from the legacy database (query: " + selectSql + ")", e);
        }
        return inserted;
    }
    // MIGRATED: original connect() body (owned its own SQLite connection) retained for reference/rollback.
    // public void connect() {
    //     synchronized (dbLock) {
    //         try {
    //             if (connection != null && !connection.isClosed()) return;
    //
    //             Class.forName("org.sqlite.JDBC");
    //             connection = DriverManager.getConnection(url);
    //             createTables();
    //             plugin.getLogger().info("Successfully connected to SQLite database.");
    //         } catch (Exception e) {
    //             plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database!", e);
    //         }
    //     }
    // }

    /**
     * No-op — the connection pool is owned and closed by SwagAPI, not this plugin.
     * Method retained (not deleted) so existing call sites/rollback path still compile.
     */
    public void close() {
        // MIGRATED: pool is owned by SwagAPI — do not close it here.
        // synchronized (dbLock) {
        //     try {
        //         if (connection != null && !connection.isClosed()) {
        //             connection.close();
        //         }
        //     } catch (SQLException e) {
        //         plugin.getLogger().log(Level.SEVERE, "Error closing database connection!", e);
        //     }
        // }
    }

    /**
     * Borrows a connection from SwagAPI's shared pool. Callers are responsible for closing it
     * (try-with-resources) so it is returned to the pool — this replaces the old long-lived
     * single connection field.
     */
    private Connection getConnection() throws SQLException {
        return dbService.getConnection();
    }

    /**
     * Create tables and run lightweight migrations.
     * - Ensures player_rewards has a prestige column
     * - Dedupes existing player_rewards rows and creates a UNIQUE index on (uuid, job_name, level, prestige)
     */
    private void createTables() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS player_jobs (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "job_name VARCHAR(32) NOT NULL," +
                    "level INTEGER DEFAULT 1," +
                    "xp DOUBLE DEFAULT 0.0," +
                    "prestige INTEGER DEFAULT 0," +
                    "job_points INTEGER DEFAULT 0," +
                    "PRIMARY KEY (uuid, job_name))");

            statement.execute("CREATE TABLE IF NOT EXISTS player_rewards (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "job_name VARCHAR(32) NOT NULL," +
                    "level INTEGER NOT NULL," +
                    "money DOUBLE NOT NULL," +
                    "claimed BOOLEAN DEFAULT 0," +
                    "prestige INTEGER DEFAULT 0)");

            statement.execute("CREATE TABLE IF NOT EXISTS player_active_job (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "active_job VARCHAR(32))");

            statement.execute("CREATE TABLE IF NOT EXISTS player_smelter_blocks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "world VARCHAR(64) NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "y INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "block_type VARCHAR(32) NOT NULL," +
                    "UNIQUE(world, x, y, z))");

            // safe - ignore failures when columns already exist
            try {
                statement.execute("ALTER TABLE player_jobs ADD COLUMN job_points INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // already exists
            }

            try {
                statement.execute("ALTER TABLE player_rewards ADD COLUMN prestige INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // already exists
            }

            // Deduplicate existing player_rewards rows keeping earliest id per (uuid, job_name, level, prestige)
            try {
                statement.execute("DELETE FROM player_rewards WHERE id NOT IN (" +
                        "SELECT MIN(id) FROM player_rewards GROUP BY uuid, job_name, level, prestige)");
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not deduplicate player_rewards: " + e.getMessage());
            }

            // Create unique index to prevent future duplicates
            try {
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_player_rewards_unique ON player_rewards(uuid, job_name, level, prestige)");
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not create unique index on player_rewards: " + e.getMessage());
            }

            statement.execute("CREATE TABLE IF NOT EXISTS prestige_shop_purchases (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "item_id VARCHAR(64) NOT NULL," +
                    "cost INTEGER NOT NULL," +
                    "timestamp BIGINT NOT NULL)");

            statement.execute("CREATE TABLE IF NOT EXISTS player_daily_bonus (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "last_claim_date VARCHAR(10) NOT NULL)");
        }
    }

    public PlayerJobData loadPlayerData(UUID uuid) {
        synchronized (dbLock) {
            PlayerJobData data = new PlayerJobData(uuid);
            try (Connection connection = getConnection()) {
                try (PreparedStatement ps = connection.prepareStatement("SELECT job_name, level, xp, prestige, job_points FROM player_jobs WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String jobName = rs.getString("job_name");
                            Job job = Job.fromString(jobName);
                            if (job == null) continue;
                            JobProgress progress = data.getJobProgress(job);
                            progress.setLevel(rs.getInt("level"));
                            progress.setXp(rs.getDouble("xp"));
                            progress.setPrestige(rs.getInt("prestige"));
                            data.setJobPoints(rs.getInt("job_points"));
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load job progress for " + uuid, e);
                }

                try (PreparedStatement psRewards = connection.prepareStatement("SELECT * FROM player_rewards WHERE uuid = ?")) {
                    psRewards.setString(1, uuid.toString());
                    try (ResultSet rsRewards = psRewards.executeQuery()) {
                        while (rsRewards.next()) {
                            Job job = Job.fromString(rsRewards.getString("job_name"));
                            if (job != null) {
                                int level = rsRewards.getInt("level");
                                double money = rsRewards.getDouble("money");
                                boolean claimed = rsRewards.getBoolean("claimed");
                                int prestige = 0;
                                try {
                                    prestige = rsRewards.getInt("prestige");
                                } catch (SQLException ignore) {
                                    prestige = 0;
                                }
                                data.addReward(new Reward(job, level, prestige, money, claimed));
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load rewards for " + uuid, e);
                }

                try (PreparedStatement psActive = connection.prepareStatement("SELECT active_job FROM player_active_job WHERE uuid = ?")) {
                    psActive.setString(1, uuid.toString());
                    try (ResultSet rs2 = psActive.executeQuery()) {
                        if (rs2.next()) {
                            String activeName = rs2.getString("active_job");
                            Job activeJob = Job.fromString(activeName);
                            if (activeJob != null) {
                                data.setActiveJob(activeJob);
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load active job for " + uuid, e);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error while loading player data for " + uuid, e);
            }
            return data;
        }
    }

    public void savePlayerData(PlayerJobData data) {
        synchronized (dbLock) {
            Connection connection = null;
            try {
                connection = getConnection();
                connection.setAutoCommit(false);

                try (PreparedStatement psUpsertJob = connection.prepareStatement(
                        "INSERT INTO player_jobs (uuid, job_name, level, xp, prestige, job_points) VALUES (?, ?, ?, ?, ?, ?) " +
                                (dbService.isMySQL()
                                        ? "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp), prestige = VALUES(prestige), job_points = VALUES(job_points)"
                                        : "ON CONFLICT(uuid, job_name) DO UPDATE SET level = excluded.level, xp = excluded.xp, prestige = excluded.prestige, job_points = excluded.job_points")
                )) {
                    for (Job job : Job.values()) {
                        JobProgress progress = data.getJobProgress(job);
                        psUpsertJob.setString(1, data.getPlayerId().toString());
                        psUpsertJob.setString(2, job.getName());
                        psUpsertJob.setInt(3, progress.getLevel());
                        psUpsertJob.setDouble(4, progress.getXp());
                        psUpsertJob.setInt(5, progress.getPrestige());
                        psUpsertJob.setInt(6, data.getJobPoints());
                        psUpsertJob.addBatch();
                    }
                    psUpsertJob.executeBatch();
                }

                try (PreparedStatement psUpdate = connection.prepareStatement(
                        "UPDATE player_rewards SET money = ?, claimed = ? WHERE uuid = ? AND job_name = ? AND level = ? AND prestige = ?"
                );
                     PreparedStatement psInsert = connection.prepareStatement(
                             "INSERT INTO player_rewards (uuid, job_name, level, money, claimed, prestige) VALUES (?, ?, ?, ?, ?, ?)"
                     )) {

                    for (Reward reward : data.getUnclaimedRewards()) {
                        psUpdate.setDouble(1, reward.getMoney());
                        psUpdate.setBoolean(2, reward.isClaimed());
                        psUpdate.setString(3, data.getPlayerId().toString());
                        psUpdate.setString(4, reward.getJob().getName());
                        psUpdate.setInt(5, reward.getLevel());
                        psUpdate.setInt(6, reward.getPrestige());

                        int updated = psUpdate.executeUpdate();
                        if (updated == 0) {
                            psInsert.setString(1, data.getPlayerId().toString());
                            psInsert.setString(2, reward.getJob().getName());
                            psInsert.setInt(3, reward.getLevel());
                            psInsert.setDouble(4, reward.getMoney());
                            psInsert.setBoolean(5, reward.isClaimed());
                            psInsert.setInt(6, reward.getPrestige());
                            psInsert.executeUpdate();
                        }
                    }
                }

                try (PreparedStatement psActive = connection.prepareStatement(
                        "INSERT INTO player_active_job (uuid, active_job) VALUES (?, ?) " +
                                (dbService.isMySQL()
                                        ? "ON DUPLICATE KEY UPDATE active_job = VALUES(active_job)"
                                        : "ON CONFLICT(uuid) DO UPDATE SET active_job = excluded.active_job")
                )) {
                    psActive.setString(1, data.getPlayerId().toString());
                    psActive.setString(2, data.getActiveJob() != null ? data.getActiveJob().getName() : null);
                    psActive.executeUpdate();
                }

                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException ex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", ex);
                    }
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to restore autoCommit after rollback", ex);
                    }
                }
                plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + data.getPlayerId(), e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                        // return-to-pool failure — nothing more we can do
                    }
                }
            }
        }
    }


    public boolean claimReward(UUID uuid, String jobName, int level, int prestige) {
        synchronized (dbLock) {
            String updateSql = "UPDATE player_rewards SET claimed = 1 WHERE uuid = ? AND job_name = ? AND level = ? AND prestige = ?";
            String insertSql = "INSERT INTO player_rewards (uuid, job_name, level, money, claimed, prestige) VALUES (?, ?, ?, 0, 1, ?)";
            try (Connection connection = getConnection();
                 PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                updateStmt.setString(1, uuid.toString());
                updateStmt.setString(2, jobName);
                updateStmt.setInt(3, level);
                updateStmt.setInt(4, prestige);
                int updated = updateStmt.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                        insertStmt.setString(1, uuid.toString());
                        insertStmt.setString(2, jobName);
                        insertStmt.setInt(3, level);
                        insertStmt.setInt(4, prestige);
                        insertStmt.executeUpdate();
                        plugin.getLogger().info("Inserted missing reward row for claim: " + uuid + " job=" + jobName + " level=" + level + " prestige=" + prestige);
                        return true;
                    }
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Reward claim failed: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Delete all rewards for a specific job and prestige from the database.
     * This is called when a player prestiges to clean up old prestige rewards.
     */
    public void deletePrestigeRewards(UUID uuid, com.swag.swagjobs.model.Job job, int prestige) {
        synchronized (dbLock) {
            String sql = "DELETE FROM player_rewards WHERE uuid = ? AND job_name = ? AND prestige = ?";
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, job.getName());
                ps.setInt(3, prestige);
                int deleted = ps.executeUpdate();
                plugin.getLogger().info("Deleted " + deleted + " old prestige " + prestige + " rewards for " + job.getName());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete prestige rewards", e);
            }
        }
    }

    public void addSmelterBlock(UUID uuid, String world, int x, int y, int z, String blockType) {
        synchronized (dbLock) {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_smelter_blocks (uuid, world, x, y, z, block_type) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, world);
                ps.setInt(3, x);
                ps.setInt(4, y);
                ps.setInt(5, z);
                ps.setString(6, blockType);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to add smelter block", e);
            }
        }
    }

    public void removeSmelterBlock(String world, int x, int y, int z) {
        synchronized (dbLock) {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM player_smelter_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove smelter block", e);
            }
        }
    }

    public int getSmelterBlockCount(UUID uuid) {
        synchronized (dbLock) {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_smelter_blocks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get smelter block count", e);
            }
            return 0;
        }
    }

    public UUID getSmelterOwnerUUID(String world, int x, int y, int z) {
        synchronized (dbLock) {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM player_smelter_blocks WHERE world = ? AND x = ? AND y = ? AND z = ? LIMIT 1"
            )) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get smelter owner UUID", e);
            }
            return null;
        }
    }

    public int getShopPurchaseCount(UUID uuid, String itemId) {
        synchronized (dbLock) {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM prestige_shop_purchases WHERE uuid = ? AND item_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get shop purchase count", e);
            }
            return 0;
        }
    }

    public void insertShopPurchase(UUID uuid, String playerName, String itemId, int cost, long timestamp) {
        synchronized (dbLock) {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO prestige_shop_purchases (uuid, player_name, item_id, cost, timestamp) VALUES (?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, itemId);
                ps.setInt(4, cost);
                ps.setLong(5, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to insert shop purchase", e);
            }
        }
    }

    /**
     * Returns true if the specified block coordinates are within the player's
     * first `cap` registered smelter blocks (ordered by insertion id ASC).
     */
    public boolean isSmelterBlockWithinCap(UUID uuid, String world, int x, int y, int z, int cap) {
        synchronized (dbLock) {
            if (cap <= 0) return false;
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "SELECT world, x, y, z FROM player_smelter_blocks WHERE uuid = ? ORDER BY id ASC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, cap);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String w = rs.getString("world");
                        int rx = rs.getInt("x");
                        int ry = rs.getInt("y");
                        int rz = rs.getInt("z");
                        if (w.equals(world) && rx == x && ry == y && rz == z) {
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check smelter block within cap", e);
            }
            return false;
        }
    }

    /** A single leaderboard row for /jobs top. */
    public static class TopEntry {
        public final UUID uuid;
        public final int level;
        public final double xp;
        public final int prestige;

        public TopEntry(UUID uuid, int level, double xp, int prestige) {
            this.uuid = uuid;
            this.level = level;
            this.xp = xp;
            this.prestige = prestige;
        }
    }

    /** Returns the top players for a job, ordered by prestige, then level, then XP (all descending). */
    public List<TopEntry> getTopPlayers(com.swag.swagjobs.model.Job job, int limit) {
        synchronized (dbLock) {
            List<TopEntry> results = new ArrayList<>();
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, level, xp, prestige FROM player_jobs WHERE job_name = ? " +
                            "ORDER BY prestige DESC, level DESC, xp DESC LIMIT ?")) {
                ps.setString(1, job.getName());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new TopEntry(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getInt("level"),
                                rs.getDouble("xp"),
                                rs.getInt("prestige")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to fetch leaderboard for " + job.getName(), e);
            }
            return results;
        }
    }

    /**
     * Attempts to claim the daily job-points bonus for a player. Returns true (and records
     * today's date) only the first time this is called for a given player on a given day.
     */
    public boolean tryClaimDailyBonus(UUID uuid) {
        synchronized (dbLock) {
            String today = java.time.LocalDate.now().toString();
            try (Connection connection = getConnection()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT last_claim_date FROM player_daily_bonus WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && today.equals(rs.getString("last_claim_date"))) {
                            return false;
                        }
                    }
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO player_daily_bonus (uuid, last_claim_date) VALUES (?, ?) " +
                                (dbService.isMySQL()
                                        ? "ON DUPLICATE KEY UPDATE last_claim_date = VALUES(last_claim_date)"
                                        : "ON CONFLICT(uuid) DO UPDATE SET last_claim_date = excluded.last_claim_date"))) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, today);
                    ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check/claim daily bonus for " + uuid, e);
                return false;
            }
        }
    }
}
