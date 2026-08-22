package com.swag.swagjobs.listener;

import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.gui.PrestigeShopEditGUI;
import com.swag.swagjobs.gui.PrestigeShopGUI;
import com.swag.swagjobs.manager.PrestigeShopManager;
import com.swag.swagjobs.model.PrestigeShopItem;
import com.swag.swagjobs.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PrestigeShopListener implements Listener {

    enum SessionState { AWAITING_NAME, AWAITING_RARITY, AWAITING_COST, AWAITING_TYPE, AWAITING_COMMANDS, AWAITING_MAX_PURCHASES }

    static class ShopEditSession {
        final int targetSlot;
        final String existingId;   // null when adding new
        SessionState state;
        final PrestigeShopItem.Builder builder;
        final List<String> accumulatedCommands = new ArrayList<>();
        final ItemStack capturedItem; // full held ItemStack captured on main thread (may be null)

        ShopEditSession(int targetSlot, String existingId, SessionState state,
                        PrestigeShopItem.Builder builder, ItemStack capturedItem) {
            this.targetSlot = targetSlot;
            this.existingId = existingId;
            this.state = state;
            this.builder = builder;
            this.capturedItem = capturedItem;
        }
    }

    private final SwagJobsPlugin plugin;
    private final Map<UUID, ShopEditSession> pendingSessions = new ConcurrentHashMap<>();

    public PrestigeShopListener(SwagJobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String rawTitle = event.getView().getTitle();
        if (rawTitle == null) return;
        String title = ChatColor.stripColor(rawTitle).trim();
        if (!title.contains("Shop Editor")) return;

        event.setCancelled(true);

        // Only act on top inventory clicks
        if (event.getClickedInventory() == null ||
                !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();
        PrestigeShopManager manager = plugin.getPrestigeShopManager();

        if (slot == 0) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> new PrestigeShopGUI(plugin).open(player));
            return;
        }

        if (slot == 8) {
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        PrestigeShopItem existing = manager.getItemBySlot(slot);

        if (existing != null) {
            if (event.isLeftClick()) {
                manager.removeItem(existing.getId());
                player.sendMessage("§c" + MessageUtil.getBracketTag() + " Item §f" + existing.getId() + "§c removed from shop.");
                Bukkit.getScheduler().runTask(plugin, () -> new PrestigeShopEditGUI(plugin).open(player));
                return;
            }
            if (event.isRightClick()) {
                ShopEditSession session = new ShopEditSession(slot, existing.getId(), SessionState.AWAITING_COST,
                        PrestigeShopItem.builder(existing.getId())
                                .slot(existing.getSlot())
                                .material(existing.getMaterial())
                                .name(existing.getName())
                                .rarity(existing.getRarity())
                                .lore(existing.getLore())
                                .type(existing.getType())
                                .commands(existing.getCommands())
                                .itemData(existing.getItemData())
                                .maxPurchases(existing.getMaxPurchasesPerPlayer())
                                .enabled(existing.isEnabled()),
                        null); // not capturing a new item
                pendingSessions.put(player.getUniqueId(), session);
                player.closeInventory();
                player.sendMessage("§e" + MessageUtil.getBracketTag() + " Type the new cost in Job Points (or §ccancel§e):");
                return;
            }
        } else {
            if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE) {
                ItemStack held = player.getInventory().getItemInMainHand();
                boolean hasItem = held != null && !held.getType().isAir();
                Material heldMat = hasItem ? held.getType() : Material.PAPER;
                ItemStack heldClone = hasItem ? held.clone() : null;

                ShopEditSession session = new ShopEditSession(slot, null, SessionState.AWAITING_NAME,
                        PrestigeShopItem.builder(generateId(slot)).slot(slot).material(heldMat),
                        heldClone);
                pendingSessions.put(player.getUniqueId(), session);
                player.closeInventory();
                player.sendMessage("§e" + MessageUtil.getBracketTag() + " Holding: §f" + heldMat.name()
                        + "§e. Type the shop item name (or §ccancel§e):");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String rawTitle = event.getView().getTitle();
        if (rawTitle == null) return;
        String title = ChatColor.stripColor(rawTitle).trim();
        if (!title.contains("Prestige Shop") || title.contains("Shop Editor")) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null ||
                !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        PrestigeShopItem item = plugin.getPrestigeShopManager().getItemBySlot(slot);
        if (item == null) return;

        int playerPoints = plugin.getPlayerDataManager().getTotalJobPoints(player.getUniqueId());
        PrestigeShopManager.PurchaseResult result = plugin.getPrestigeShopManager().purchase(player, item);

        switch (result) {
            case NOT_ENOUGH_POINTS -> player.sendMessage(
                    "§cNot enough Job Points! Need §f" + item.getCost() + "§c, you have §f" + playerPoints + "§c.");
            case LIMIT_REACHED -> player.sendMessage(
                    "§cYou've already bought this item the maximum allowed times.");
            case DELIVERY_FAILED -> player.sendMessage(
                    "§cPurchase failed — could not deliver the item. Your points were not spent. Contact an admin.");
            case SUCCESS -> {
                player.sendMessage("§a§lPURCHASED! §7You bought §f"
                        + ChatColor.translateAlternateColorCodes('&', item.getName()) + "§7.");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                Bukkit.getScheduler().runTask(plugin, () -> new PrestigeShopGUI(plugin).open(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdminChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ShopEditSession session = pendingSessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (input.equalsIgnoreCase("cancel")) {
            pendingSessions.remove(player.getUniqueId());
            player.sendMessage("§c" + MessageUtil.getBracketTag() + " Cancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> new PrestigeShopEditGUI(plugin).open(player));
            return;
        }

        switch (session.state) {
            case AWAITING_NAME -> {
                session.builder.name(input);
                session.state = SessionState.AWAITING_RARITY;
                player.sendMessage("§e" + MessageUtil.getBracketTag() + " Type the rarity (§fCOMMON §7/ §fUNCOMMON §7/ §fRARE §7/ §fEPIC §7/ §fLEGENDARY§e) or §fnone§e to skip:");
            }

            case AWAITING_RARITY -> {
                if (!input.equalsIgnoreCase("none")) {
                    String r = input.toUpperCase();
                    if (!r.equals("COMMON") && !r.equals("UNCOMMON") && !r.equals("RARE")
                            && !r.equals("EPIC") && !r.equals("LEGENDARY")) {
                        player.sendMessage("§c" + MessageUtil.getBracketTag() + " Invalid rarity. Use COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, or none:");
                        break;
                    }
                    session.builder.rarity(r);
                }
                session.state = SessionState.AWAITING_COST;
                player.sendMessage("§e" + MessageUtil.getBracketTag() + " Type the cost in Job Points:");
            }

            case AWAITING_COST -> {
                try {
                    int cost = Integer.parseInt(input);
                    if (cost < 0) throw new NumberFormatException();
                    session.builder.cost(cost);
                    session.state = SessionState.AWAITING_TYPE;
                    player.sendMessage("§e" + MessageUtil.getBracketTag() + " Type §fITEM §e(gives hand item) or §fCOMMAND§e:");
                } catch (NumberFormatException e) {
                    player.sendMessage("§c" + MessageUtil.getBracketTag() + " Invalid number. Try again or type §fcancel§c:");
                }
            }

            case AWAITING_TYPE -> {
                switch (input.toUpperCase()) {
                    case "ITEM" -> {
                        session.builder.type(PrestigeShopItem.ShopItemType.ITEM);
                        if (session.capturedItem != null) {
                            session.builder.itemData(session.capturedItem);
                        }
                        session.state = SessionState.AWAITING_MAX_PURCHASES;
                        player.sendMessage("§e" + MessageUtil.getBracketTag() + " Type max purchases per player (§f-1§e for unlimited):");
                    }
                    case "COMMAND" -> {
                        session.builder.type(PrestigeShopItem.ShopItemType.COMMAND);
                        session.state = SessionState.AWAITING_COMMANDS;
                        player.sendMessage("§e" + MessageUtil.getBracketTag() + " Type each console command on a separate line."
                                + " Use §f%player%§e for buyer. Type §fdone§e when finished:");
                    }
                    default -> player.sendMessage("§c" + MessageUtil.getBracketTag() + " Please type §fITEM§c or §fCOMMAND§c:");
                }
            }

            case AWAITING_COMMANDS -> {
                if (input.equalsIgnoreCase("done")) {
                    session.builder.commands(session.accumulatedCommands);
                    session.state = SessionState.AWAITING_MAX_PURCHASES;
                    player.sendMessage("§e" + MessageUtil.getBracketTag() + " Type max purchases per player (§f-1§e for unlimited):");
                } else {
                    session.accumulatedCommands.add(input);
                    player.sendMessage("§7" + MessageUtil.getBracketTag() + " Command added. Continue or type §fdone§7:");
                }
            }

            case AWAITING_MAX_PURCHASES -> {
                try {
                    int max = Integer.parseInt(input);
                    if (max < -1) throw new NumberFormatException();
                    session.builder.maxPurchases(max);

                    PrestigeShopItem newItem = session.builder.build();

                    if (session.existingId != null) {
                        plugin.getPrestigeShopManager().removeItem(session.existingId);
                    }

                    plugin.getPrestigeShopManager().addItem(newItem);
                    pendingSessions.remove(player.getUniqueId());

                    player.sendMessage("§a" + MessageUtil.getBracketTag() + " §l✔ Item added to slot §f" + newItem.getSlot()
                            + "§a! Use §f/jobs shopedit§a to continue editing.");
                    Bukkit.getScheduler().runTask(plugin, () -> new PrestigeShopEditGUI(plugin).open(player));
                } catch (NumberFormatException e) {
                    player.sendMessage("§c" + MessageUtil.getBracketTag() + " Invalid number. Use -1 for unlimited or a positive integer:");
                }
            }
        }
    }

    private String generateId(int slot) {
        return "item_slot_" + slot;
    }
}
