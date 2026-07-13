package com.swag.swagjobs.model;

public class Reward {
    private final Job job;
    private final int level;
    private final int prestige;
    private final double money;
    private boolean claimed;

    public Reward(Job job, int level, int prestige, double money, boolean claimed) {
        this.job = job;
        this.level = level;
        this.prestige = prestige;
        this.money = money;
        this.claimed = claimed;
    }

    public Reward(Job job, int level, int prestige, double money) {
        this(job, level, prestige, money, false);
    }

    public Job getJob() { return job; }
    public int getLevel() { return level; }
    public int getPrestige() { return prestige; }
    public double getMoney() { return money; }
    public boolean isClaimed() { return claimed; }
    public void claim() { this.claimed = true; }
}