package com.swag.swagjobs;

import com.swag.swagjobs.model.Job;
import com.swag.swagjobs.model.PlayerJobData;
import com.swag.swagjobs.model.JobProgress;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SwagJobsAPI {

    public static PlayerJobData getPlayerData(Player player) {
        return SwagJobsPlugin.getInstance().getPlayerDataManager().getPlayerData(player.getUniqueId());
    }

    public static JobProgress getJobProgress(Player player, Job job) {
        PlayerJobData data = getPlayerData(player);
        return data != null ? data.getJobProgress(job) : null;
    }

    public static Job getActiveJob(Player player) {
        PlayerJobData data = getPlayerData(player);
        return data != null ? data.getActiveJob() : null;
    }
}
