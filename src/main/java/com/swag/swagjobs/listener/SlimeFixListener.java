package com.swag.swagjobs.listener;

import org.bukkit.entity.Slime;
import org.bukkit.entity.MagmaCube;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SlimeSplitEvent;

public class SlimeFixListener implements Listener {

    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        // When a slime/magma cube dies in a grinder, the "split" causes them to glitch through walls.
        // We cancel the split to keep the grinder clean.
        // If you are using a stacking plugin, it usually handles the loot/stack reduction anyway.
        event.setCancelled(true);
    }
}
