package com.swag.swagjobs.model;

import java.util.*;

/**
 * Represents all job data for a single player
 */
public class PlayerJobData {
    private final UUID playerId;
    private final Map<Job, JobProgress> jobProgress;
    private final List<Reward> unclaimedRewards;
    private Job activeJob;

    // Spendable currency for prestige shop, etc.
    private int jobPoints = 0;

    public PlayerJobData(UUID playerId) {
        this.playerId = playerId;
        this.jobProgress = new HashMap<>();
        this.unclaimedRewards = new ArrayList<>();
        this.activeJob = null;

        for (Job job : Job.values()) {
            jobProgress.put(job, new JobProgress(job));
        }
    }

    public int getJobPoints() {
        return jobPoints;
    }

    public void setJobPoints(int points) {
        this.jobPoints = Math.max(0, points);
    }

    public void addJobPoints(int amount) {
        if (amount <= 0) return;
        this.jobPoints += amount;
    }

    public boolean spendJobPoints(int amount) {
        if (amount <= 0) return true;
        if (jobPoints < amount) return false;
        jobPoints -= amount;
        return true;
    }

    @Deprecated
    public int getPrestigePoints() {
        return getJobPoints();
    }

    @Deprecated
    public void setPrestigePoints(int points) {
        setJobPoints(points);
    }

    @Deprecated
    public void addPrestigePoints(int amount) {
        addJobPoints(amount);
    }

    @Deprecated
    public boolean spendPrestigePoints(int amount) {
        return spendJobPoints(amount);
    }

    public void clearUnclaimedRewards(Job job) {
        for (Reward reward : unclaimedRewards) {
            if (reward.getJob() == job && !reward.isClaimed()) {
                reward.claim();
            }
        }
    }

    /**
     * Removes all reward entries (claimed or unclaimed) for the given job and prestige level.
     * Use this during prestige to cleanly purge old prestige rewards from the in-memory list.
     */
    public void clearRewardsForPrestige(Job job, int prestige) {
        unclaimedRewards.removeIf(r -> r.getJob() == job && r.getPrestige() == prestige);
    }

    public List<Reward> getRewards() {
        return Collections.unmodifiableList(unclaimedRewards);
    }

    /**
     * Atomically removes an unclaimed reward entry and re-inserts it as claimed.
     * Use this instead of calling getRewards().removeIf(...) directly (that would throw
     * UnsupportedOperationException since getRewards() returns an unmodifiable view).
     */
    public void markRewardClaimed(Job job, int level, int prestige, double amount) {
        unclaimedRewards.removeIf(r ->
                r.getJob() == job && r.getLevel() == level && r.getPrestige() == prestige && !r.isClaimed());
        addReward(new Reward(job, level, prestige, amount, true));
    }

    public void resetMilestoneRewards(Job job) {
        // Rewards are tracked per-prestige; the GUI re-checks on next open.
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Job getActiveJob() {
        return activeJob;
    }

    public void setActiveJob(Job job) {
        this.activeJob = job;
    }

    public JobProgress getJobProgress(Job job) {
        return jobProgress.get(job);
    }

    public Map<Job, JobProgress> getAllJobProgress() {
        return Collections.unmodifiableMap(jobProgress);
    }

    public List<Reward> getUnclaimedRewards() {
        return unclaimedRewards;
    }

    /** Filters by current prestige so old prestige rewards don't bleed through. */
    public List<Reward> getUnclaimedRewards(Job job) {
        List<Reward> jobRewards = new ArrayList<>();
        JobProgress progress = getJobProgress(job);
        int currentPrestige = progress.getPrestige();

        for (Reward reward : unclaimedRewards) {
            if (reward.getJob() == job &&
                    !reward.isClaimed() &&
                    reward.getPrestige() == currentPrestige) {
                jobRewards.add(reward);
            }
        }
        return jobRewards;
    }

    public void addReward(Reward reward) {
        // Deduplicate by (Job, Level, Prestige) — prevents double-inserting on reload.
        for (Reward existing : unclaimedRewards) {
            if (existing.getJob() == reward.getJob() &&
                    existing.getLevel() == reward.getLevel() &&
                    existing.getPrestige() == reward.getPrestige()) {
                return;
            }
        }
        unclaimedRewards.add(reward);
    }

    public boolean claimReward(Job job, int level) {
        for (Reward reward : unclaimedRewards) {
            if (reward.getJob() == job && reward.getLevel() == level && !reward.isClaimed()) {
                reward.claim();
                return true;
            }
        }
        return false;
    }

    public double getTotalUnclaimedMoney() {
        double total = 0.0;
        for (Reward reward : unclaimedRewards) {
            if (!reward.isClaimed()) {
                total += reward.getMoney();
            }
        }
        return total;
    }

    /** Filters by current prestige so old prestige rewards don't bleed through. */
    public double getTotalUnclaimedMoney(Job job) {
        double total = 0.0;
        JobProgress progress = getJobProgress(job);
        int currentPrestige = progress.getPrestige();

        for (Reward reward : unclaimedRewards) {
            if (reward.getJob() == job &&
                    !reward.isClaimed() &&
                    reward.getPrestige() == currentPrestige) {
                total += reward.getMoney();
            }
        }
        return total;
    }

    @Override
    public String toString() {
        return String.format("PlayerJobData[%s, Active: %s, Unclaimed: ₡%.2f, JobPoints: %d]",
                playerId, activeJob != null ? activeJob.getName() : "None", getTotalUnclaimedMoney(), jobPoints);
    }
}
