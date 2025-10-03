package dev.ked.bazaar.mode;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Represents an active shop mode session for a player.
 * Tracks session state including shop ID, entry time, and saved hotbar.
 */
public class ShopModeSession {
    private final UUID playerId;
    private final UUID shopId;
    private final long enteredAt;
    private final Location entryLocation;
    private final ItemStack[] previousHotbar; // Slots 7-8 saved
    private long lastActivity;

    public ShopModeSession(UUID playerId, UUID shopId, Location entryLocation, ItemStack[] previousHotbar) {
        this.playerId = playerId;
        this.shopId = shopId;
        this.enteredAt = System.currentTimeMillis();
        this.entryLocation = entryLocation.clone();
        this.previousHotbar = previousHotbar;
        this.lastActivity = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getShopId() {
        return shopId;
    }

    public long getEnteredAt() {
        return enteredAt;
    }

    public Location getEntryLocation() {
        return entryLocation.clone();
    }

    public ItemStack[] getPreviousHotbar() {
        return previousHotbar;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * Check if session has timed out.
     * @param timeoutMinutes Timeout in minutes
     * @return true if session has timed out
     */
    public boolean hasTimedOut(int timeoutMinutes) {
        long timeoutMillis = timeoutMinutes * 60 * 1000L;
        return (System.currentTimeMillis() - lastActivity) > timeoutMillis;
    }

    /**
     * Check if player has moved too far from entry location.
     * @param maxDistance Maximum allowed distance
     * @return true if player is too far
     */
    public boolean isTooFarFromEntry(Location currentLocation, double maxDistance) {
        if (!currentLocation.getWorld().equals(entryLocation.getWorld())) {
            return true;
        }
        return currentLocation.distance(entryLocation) > maxDistance;
    }

    /**
     * Get session duration in seconds.
     */
    public long getDurationSeconds() {
        return (System.currentTimeMillis() - enteredAt) / 1000;
    }
}
