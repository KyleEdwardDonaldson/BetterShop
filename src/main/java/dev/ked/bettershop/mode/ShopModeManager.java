package dev.ked.bettershop.mode;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.ShopEntity;
import dev.ked.bettershop.shop.ShopRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages shop mode sessions for players.
 * Handles entering/exiting shop mode, timeout checks, and distance validation.
 */
public class ShopModeManager {
    private final BetterShopPlugin plugin;
    private final ConfigManager config;
    private final ShopRegistry registry;
    private final ShopModeItems modeItems;
    private final MiniMessage miniMessage;

    private final Map<UUID, ShopModeSession> activeSessions = new ConcurrentHashMap<>();
    private BukkitTask checkTask;

    public ShopModeManager(BetterShopPlugin plugin, ConfigManager config, ShopRegistry registry) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.modeItems = new ShopModeItems();
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Start the periodic check task for timeouts and distance.
     */
    public void startCheckTask() {
        if (!config.isShopModeEnabled()) {
            return;
        }

        // Check every 30 seconds
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSessions, 600L, 600L);
    }

    /**
     * Stop the check task.
     */
    public void stopCheckTask() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /**
     * Enter shop mode for a player.
     * @param player The player
     * @param shopId The shop UUID
     * @return true if successfully entered shop mode
     */
    public boolean enterShopMode(Player player, UUID shopId) {
        if (!config.isShopModeEnabled()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop mode is disabled!"));
            return false;
        }

        // Check if already in shop mode
        if (isInShopMode(player)) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>You are already in shop mode!"));
            return false;
        }

        // Verify shop exists
        Optional<ShopEntity> shopOpt = registry.getShopById(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop not found!"));
            return false;
        }

        ShopEntity shop = shopOpt.get();

        // Verify ownership
        if (!shop.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You don't own this shop!"));
            return false;
        }

        // Save hotbar slots 7-8
        ItemStack[] previousHotbar = null;
        if (config.shouldSaveHotbar()) {
            previousHotbar = new ItemStack[2];
            previousHotbar[0] = player.getInventory().getItem(7);
            previousHotbar[1] = player.getInventory().getItem(8);
        }

        // Create session
        ShopModeSession session = new ShopModeSession(
                player.getUniqueId(),
                shopId,
                player.getLocation(),
                previousHotbar
        );

        activeSessions.put(player.getUniqueId(), session);

        // Give special chest items
        player.getInventory().setItem(7, modeItems.createSellChestItem());
        player.getInventory().setItem(8, modeItems.createBuyChestItem());

        // Send messages
        String message = config.getMessage("shop-mode-entered", "name", shop.getName());
        player.sendMessage(miniMessage.deserialize(message));

        // Send action bar
        sendActionBar(player, shop.getName());

        return true;
    }

    /**
     * Exit shop mode for a player.
     * @param player The player
     * @param sendMessage Whether to send exit message
     * @return true if successfully exited
     */
    public boolean exitShopMode(Player player, boolean sendMessage) {
        ShopModeSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        // Restore hotbar
        if (config.shouldSaveHotbar() && session.getPreviousHotbar() != null) {
            ItemStack[] previousHotbar = session.getPreviousHotbar();
            player.getInventory().setItem(7, previousHotbar[0]);
            player.getInventory().setItem(8, previousHotbar[1]);
        } else {
            // Just clear the special chests
            if (modeItems.isShopModeChest(player.getInventory().getItem(7))) {
                player.getInventory().setItem(7, null);
            }
            if (modeItems.isShopModeChest(player.getInventory().getItem(8))) {
                player.getInventory().setItem(8, null);
            }
        }

        // Clear action bar
        player.sendActionBar(Component.empty());

        // Send exit message
        if (sendMessage) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("shop-mode-exited")));
        }

        return true;
    }

    /**
     * Exit shop mode without message.
     */
    public boolean exitShopMode(Player player) {
        return exitShopMode(player, true);
    }

    /**
     * Check if a player is in shop mode.
     */
    public boolean isInShopMode(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * Get active session for a player.
     */
    public Optional<ShopModeSession> getActiveSession(Player player) {
        return Optional.ofNullable(activeSessions.get(player.getUniqueId()));
    }

    /**
     * Get active session by player UUID.
     */
    public Optional<ShopModeSession> getActiveSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * Restore shop mode hotbar items (for when inventory is reopened).
     */
    public void restoreShopModeHotbar(Player player) {
        if (!isInShopMode(player)) {
            return;
        }

        // Re-add special chests if they're missing
        if (!modeItems.isShopModeChest(player.getInventory().getItem(7))) {
            player.getInventory().setItem(7, modeItems.createSellChestItem());
        }
        if (!modeItems.isShopModeChest(player.getInventory().getItem(8))) {
            player.getInventory().setItem(8, modeItems.createBuyChestItem());
        }
    }

    /**
     * Send action bar to player showing shop mode status.
     */
    private void sendActionBar(Player player, String shopName) {
        String actionBar = "<gray>Shop Mode: <white>" + shopName + " <gray>| <green>SELL [7] <blue>BUY [8] <gray>| <yellow>/shop mode exit";
        player.sendActionBar(miniMessage.deserialize(actionBar));
    }

    /**
     * Periodic check for session timeouts and distance.
     */
    private void checkSessions() {
        int timeoutMinutes = config.getShopModeTimeoutMinutes();
        double maxDistance = config.getShopModeMaxDistance();

        for (Map.Entry<UUID, ShopModeSession> entry : activeSessions.entrySet()) {
            UUID playerId = entry.getKey();
            ShopModeSession session = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                // Player is offline, remove session
                activeSessions.remove(playerId);
                continue;
            }

            // Check timeout
            if (session.hasTimedOut(timeoutMinutes)) {
                exitShopMode(player, false);
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>Shop mode timed out due to inactivity."));
                continue;
            }

            // Check distance
            if (session.isTooFarFromEntry(player.getLocation(), maxDistance)) {
                exitShopMode(player, false);
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>Shop mode exited - you moved too far away."));
                continue;
            }

            // Update action bar
            Optional<ShopEntity> shopOpt = registry.getShopById(session.getShopId());
            if (shopOpt.isPresent()) {
                sendActionBar(player, shopOpt.get().getName());
            }
        }
    }

    /**
     * Exit all active sessions (for plugin disable).
     */
    public void exitAllSessions() {
        for (UUID playerId : activeSessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                exitShopMode(player, false);
            }
        }
        activeSessions.clear();
    }

    /**
     * Get shop mode items utility.
     */
    public ShopModeItems getModeItems() {
        return modeItems;
    }
}
