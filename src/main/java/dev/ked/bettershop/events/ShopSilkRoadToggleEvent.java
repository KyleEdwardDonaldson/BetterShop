package dev.ked.bettershop.events;

import dev.ked.bettershop.shop.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when Silk Road is enabled or disabled for a shop.
 */
public class ShopSilkRoadToggleEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;

    private final Shop shop;
    private final Player player;
    private final boolean enabled;

    public ShopSilkRoadToggleEvent(Shop shop, Player player, boolean enabled) {
        this.shop = shop;
        this.player = player;
        this.enabled = enabled;
    }

    public Shop getShop() {
        return shop;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Check if Silk Road is being enabled.
     * @return true if enabling, false if disabling
     */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
