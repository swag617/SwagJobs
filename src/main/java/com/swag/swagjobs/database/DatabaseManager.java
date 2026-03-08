package com.swag.swagjobs.database;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.JobProgress;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.Reward;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final SwagJobsPlugin plugin;
    private Connection connection;
    private final String url;

    public DatabaseManager(SwagJobsPlugin plugin) {
        this.plugin = plugin;
        File dbFile = new File(plugin.getDataFolder(), "SwagJobs.db");
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) return;

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            createTables();
            plugin.getLogger().info("Successfully connected to SQLite database.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database!", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection!", e);
        }
    }

    /**
     * Create tables and run lightweight migrations.
     * - Ensures player_rewards has a prestige column
     * - Dedupes existing player_rewards rows and creates a UNIQUE index on (uuid, job_name, level, prestige)
     */
    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // player_jobs (per-player per-job progress)
            statement.execute("CREATE TABLE IF NOT EXISTS player_jobs (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "job_name VARCHAR(32) NOT NULL," +
                    "level INTEGER DEFAULT 1," +
                    "xp DOUBLE DEFAULT 0.0," +
                    "prestige INTEGER DEFAULT 0," +
                    "job_points INTEGER DEFAULT 0," +
                    "PRIMARY KEY (uuid, job_name))");

            // player_rewards (persistent record of rewards, includes claimed flag + prestige)
            statement.execute("CREATE TABLE IF NOT EXISTS player_rewards (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "job_name VARCHAR(32) NOT NULL," +
                    "level INTEGER NOT NULL," +
                    "money DOUBLE NOT NULL," +
                    "claimed BOOLEAN DEFAULT 0," +
                    "prestige INTEGER DEFAULT 0)");

            // player_active_job
            statement.execute("CREATE TABLE IF NOT EXISTS player_active_job (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "active_job VARCHAR(32))");

            // player_smelter_blocks
            statement.execute("CREATE TABLE IF NOT EXISTS player_smelter_blocks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "world VARCHAR(64) NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "y INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "block_type VARCHAR(32) NOT NULL," +
                    "UNIQUE(world, x, y, z))");

            // Migration attempts (safe - ignore failures when columns already exist)
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

            // prestige_shop_purchases
            statement.execute("CREATE TABLE IF NOT EXISTS prestige_shop_purchases (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "item_id VARCHAR(64) NOT NULL," +
                    "cost INTEGER NOT NULL," +
                    "timestamp BIGINT NOT NULL)");
        }
    }

    /**
     * Load player data (job progress, rewards, active job, job_points)
     */
    public PlayerJobData loadPlayerData(UUID uuid) {
        PlayerJobData data = new PlayerJobData(uuid);
        try {
            // Load job progress & job_points
            try (PreparedStatement ps = connection.prepareStatement("SELECT job_name, level, xp, prestige, job_points FROM player_jobs WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
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
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load job progress for " + uuid, e);
            }

            // Load ALL rewards (claimed and unclaimed). Important: do NOT filter by claimed.
            try (PreparedStatement psRewards = connection.prepareStatement("SELECT * FROM player_rewards WHERE uuid = ?")) {
                psRewards.setString(1, uuid.toString());
                ResultSet rsRewards = psRewards.executeQuery();
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
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load rewards for " + uuid, e);
            }

            // Load active job
            try (PreparedStatement psActive = connection.prepareStatement("SELECT active_job FROM player_active_job WHERE uuid = ?")) {
                psActive.setString(1, uuid.toString());
                ResultSet rs2 = psActive.executeQuery();
                if (rs2.next()) {
                    String activeName = rs2.getString("active_job");
                    Job activeJob = Job.fromString(activeName);
                    if (activeJob != null) {
                        data.setActiveJob(activeJob);
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

    /**
     * Save the entire PlayerJobData. Uses transactions and avoids creating duplicate reward rows.
     */
    public void savePlayerData(PlayerJobData data) {
        try {
            connection.setAutoCommit(false);

            // 1) Upsert job progress (using ON CONFLICT)
            try (PreparedStatement psUpsertJob = connection.prepareStatement(
                    "INSERT INTO player_jobs (uuid, job_name, level, xp, prestige, job_points) VALUES (?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT(uuid, job_name) DO UPDATE SET level = excluded.level, xp = excluded.xp, prestige = excluded.prestige, job_points = excluded.job_points"
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

            // 2) Save rewards safely: UPDATE if exists, otherwise INSERT
            try (PreparedStatement psUpdate = connection.prepareStatement(
                    "UPDATE player_rewards SET money = ?, claimed = ? WHERE uuid = ? AND job_name = ? AND level = ? AND prestige = ?"
            );
                 PreparedStatement psInsert = connection.prepareStatement(
                         "INSERT INTO player_rewards (uuid, job_name, level, money, claimed, prestige) VALUES (?, ?, ?, ?, ?, ?)"
                 )) {

                for (Reward reward : data.getUnclaimedRewards()) {
                    // Note: unclaimedRewards list contains both claimed/unclaimed rewards (claimed flag embedded)
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

            // 3) Upsert active job
            try (PreparedStatement psActive = connection.prepareStatement(
                    "INSERT INTO player_active_job (uuid, active_job) VALUES (?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET active_job = excluded.active_job"
            )) {
                psActive.setString(1, data.getPlayerId().toString());
                psActive.setString(2, data.getActiveJob() != null ? data.getActiveJob().getName() : null);
                psActive.executeUpdate();
            }

            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + data.getPlayerId(), e);
        }
    }


    public boolean claimReward(UUID uuid, String jobName, int level, int prestige) {
        String updateSql = "UPDATE player_rewards SET claimed = 1 WHERE uuid = ? AND job_name = ? AND level = ? AND prestige = ?";
        String insertSql = "INSERT INTO player_rewards (uuid, job_name, level, money, claimed, prestige) VALUES (?, ?, ?, 0, 1, ?)";
        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
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

    /**
     * Delete all rewards for a specific job and prestige from the database.
     * This is called when a player prestiges to clean up old prestige rewards.
     */
    public void deletePrestigeRewards(UUID uuid, com.swag.swagjobs.model.Job job, int prestige) {
        String sql = "DELETE FROM player_rewards WHERE uuid = ? AND job_name = ? AND prestige = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job.getName());
            ps.setInt(3, prestige);
            int deleted = ps.executeUpdate();
            plugin.getLogger().info("Deleted " + deleted + " old prestige " + prestige + " rewards for " + job.getName());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete prestige rewards", e);
        }
    }

    // --- Smelter Block Methods ---

    public void addSmelterBlock(UUID uuid, String world, int x, int y, int z, String blockType) {
        try (PreparedStatement ps = connection.prepareStatement(
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

    public void removeSmelterBlock(String world, int x, int y, int z) {
        try (PreparedStatement ps = connection.prepareStatement(
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

    public int getSmelterBlockCount(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM player_smelter_blocks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get smelter block count", e);
        }
        return 0;
    }

    public UUID getSmelterOwnerUUID(String world, int x, int y, int z) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid FROM player_smelter_blocks WHERE world = ? AND x = ? AND y = ? AND z = ? LIMIT 1"
        )) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get smelter owner UUID", e);
        }
        return null;
    }

    // ---- Prestige Shop Purchase Methods ----

    public int getShopPurchaseCount(UUID uuid, String itemId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM prestige_shop_purchases WHERE uuid = ? AND item_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get shop purchase count", e);
        }
        return 0;
    }

    public void insertShopPurchase(UUID uuid, String playerName, String itemId, int cost, long timestamp) {
        try (PreparedStatement ps = connection.prepareStatement(
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

    /**
     * Returns true if the specified block coordinates are within the player's
     * first `cap` registered smelter blocks (ordered by insertion id ASC).
     */
    public boolean isSmelterBlockWithinCap(UUID uuid, String world, int x, int y, int z, int cap) {
        if (cap <= 0) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world, x, y, z FROM player_smelter_blocks WHERE uuid = ? ORDER BY id ASC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, cap);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String w = rs.getString("world");
                int rx = rs.getInt("x");
                int ry = rs.getInt("y");
                int rz = rs.getInt("z");
                if (w.equals(world) && rx == x && ry == y && rz == z) {
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check smelter block within cap", e);
        }
        return false;
    }
}