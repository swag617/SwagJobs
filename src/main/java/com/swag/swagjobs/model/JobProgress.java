package com.swag.swagjobs.model;

/**
 * Represents a player's progress in a single job
 */
public class JobProgress {
    private final Job job;
    private int level;
    private double xp;
    private int prestige;

    public JobProgress(Job job) {
        this.job = job;
        this.level = 1;
        this.xp = 0.0;
        this.prestige = 0;
    }

    public JobProgress(Job job, int level, double xp, int prestige) {
        this.job = job;
        this.level = level;
        this.xp = xp;
        this.prestige = prestige;
    }

    // Getters
    public Job getJob() {
        return job;
    }

    public int getLevel() {
        return level;
    }

    public double getXp() {
        return xp;
    }

    public int getPrestige() {
        return prestige;
    }

    // Setters
    public void setLevel(int level) {
        this.level = level;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
    }

    /**
     * Add XP to this job
     * @return true if leveled up
     */
    public boolean addXp(double amount, double xpRequired) {
        this.xp += amount;

        if (this.xp >= xpRequired) {
            this.xp -= xpRequired;
            this.level++;
            return true; // Leveled up
        }

        return false; // No level up
    }

    /**
     * Reset progress for prestige
     */
    public void resetForPrestige() {
        this.level = 1;
        this.xp = 0.0;
        this.prestige++;
    }

    @Override
    public String toString() {
        return String.format("%s [Level %d, XP: %.2f, Prestige: %d]",
                job.getName(), level, xp, prestige);
    }
}