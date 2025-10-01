package dev.ked.bettershop.listeners;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.mode.ShopModeItems;
import dev.ked.bettershop.mode.ShopModeManager;
import dev.ked.bettershop.mode.ShopModeSession;
import dev.ked.bettershop.shop.ShopEntityManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Handles events related to shop mode.
 * Manages shop mode chest placement, prevents item loss, and handles session cleanup.
 */
public class ShopModeListener implements Listener {
    private final BetterShopPlugin plugin;
    private final ShopModeManager modeManager;
    private final ShopEntityManager shopManager;
    private final ConfigManager config;
    private final MiniMessage miniMessage;

    public ShopModeListener(BetterShopPlugin plugin, ShopModeManager modeManager, ShopEntityManager shopManager, ConfigManager config) {
        this.plugin = plugin;
        this.modeManager = modeManager;
        this.shopManager = shopManager;
        this.config = config;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Handle player leaving - exit shop mode.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (modeManager.isInShopMode(player)) {
            modeManager.exitShopMode(player, false);
        }
    }

    /**
     * Handle player teleporting - check distance and exit if too far.
     */
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Optional<ShopModeSession> sessionOpt = modeManager.getActiveSession(player);

        if (sessionOpt.isEmpty()) {
            return;
        }

        ShopModeSession session = sessionOpt.get();

        // Check if teleporting to a different world or too far
        if (!event.getTo().getWorld().equals(session.getEntryLocation().getWorld()) ||
                event.getTo().distance(session.getEntryLocation()) > config.getShopModeMaxDistance()) {

            modeManager.exitShopMode(player, false);
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>Shop mode exited due to teleportation."));
        }
    }

    /**
     * Handle dropping shop mode items - prevent it.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        ShopModeItems modeItems = modeManager.getModeItems();

        if (modeItems.isShopModeChest(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You cannot drop shop mode items!"));
        }
    }

    /**
     * Handle inventory clicks - prevent moving shop mode items.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        ShopModeItems modeItems = modeManager.getModeItems();

        // Check if trying to move shop mode chests
        boolean isCurrentShopChest = currentItem != null && modeItems.isShopModeChest(currentItem);
        boolean isCursorShopChest = cursorItem != null && modeItems.isShopModeChest(cursorItem);

        if (isCurrentShopChest || isCursorShopChest) {
            // Allow clicks in slots 7 and 8 (hotbar slots for shop mode)
            if (event.getSlot() == 7 || event.getSlot() == 8) {
                return;
            }

            // Prevent moving out of slots 7 and 8
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You cannot move shop mode items!"));
        }
    }

    /**
     * Handle breaking shop mode chests - prevent dropping them.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Check if it's a shop mode chest that was just placed
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            // Check if any player in shop mode placed this (track by checking drops)
            Player player = event.getPlayer();

            // If the chest has a shop mode item in the drops, cancel and don't drop
            event.setDropItems(false);

            // If player is in shop mode and breaking their own placed chest (not a listing yet), allow break but no drop
            if (modeManager.isInShopMode(player)) {
                // This is fine - they're cleaning up a misplaced chest
                return;
            }
        }
    }

    /**
     * Handle block placement - detect shop mode chest placement.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        ShopModeItems modeItems = modeManager.getModeItems();

        // Check if it's a shop mode chest
        if (!modeItems.isShopModeChest(item)) {
            return;
        }

        // Check if player is in shop mode
        Optional<ShopModeSession> sessionOpt = modeManager.getActiveSession(player);
        if (sessionOpt.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You must be in shop mode to place this!"));
            return;
        }

        ShopModeSession session = sessionOpt.get();
        Block placed = event.getBlockPlaced();

        // Validate territory restrictions
        if (!shopManager.canPlaceListingHere(session.getShopId(), placed.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize(config.getMessage("shop-territory-mismatch")));
            return;
        }

        // Check if listing limit is reached
        if (shopManager.hasReachedListingLimit(session.getShopId())) {
            event.setCancelled(true);
            int maxListings = config.getMaxListingsPerShop();
            player.sendMessage(miniMessage.deserialize(config.getMessage("listing-limit-reached", "limit", String.valueOf(maxListings))));
            return;
        }

        // Update activity timestamp
        session.updateActivity();

        // Determine listing type and open configuration GUI
        boolean isSellChest = modeItems.isSellChest(item);

        // Schedule GUI opening for next tick (after block is placed)
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Check if chest is still there
            if (placed.getType() != Material.CHEST && placed.getType() != Material.TRAPPED_CHEST) {
                return;
            }

            // Open configuration GUI (will be implemented in Phase 3)
            if (isSellChest) {
                openSellListingConfig(player, session, placed.getLocation());
            } else {
                openBuyListingConfig(player, session, placed.getLocation());
            }

            // Restore shop mode hotbar (give back the infinite chest)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                modeManager.restoreShopModeHotbar(player);
            }, 5L);
        });
    }

    /**
     * Open SELL listing configuration GUI.
     */
    private void openSellListingConfig(Player player, ShopModeSession session, org.bukkit.Location location) {
        // Get ListingConfigGUI from plugin
        dev.ked.bettershop.ui.ListingConfigGUI configGUI = plugin.getListingConfigGUI();
        if (configGUI != null) {
            configGUI.openSellConfig(player, session.getShopId(), location);
        } else {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Configuration GUI not available!"));
        }
    }

    /**
     * Open BUY listing configuration GUI.
     */
    private void openBuyListingConfig(Player player, ShopModeSession session, org.bukkit.Location location) {
        // Get BuyListingConfigGUI from plugin
        dev.ked.bettershop.ui.BuyListingConfigGUI configGUI = plugin.getBuyListingConfigGUI();
        if (configGUI != null) {
            configGUI.openBuyConfig(player, session.getShopId(), location);
        } else {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Configuration GUI not available!"));
        }
    }
}
