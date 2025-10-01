package dev.ked.bettershop.events;

import dev.ked.bettershop.shop.Shop;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when stock is reserved for a Silk Road contract.
 */
public class ShopStockReserveEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;

    private final Shop shop;
    private final UUID contractId;
    private final int quantity;

    public ShopStockReserveEvent(Shop shop, UUID contractId, int quantity) {
        this.shop = shop;
        this.contractId = contractId;
        this.quantity = quantity;
    }

    public Shop getShop() {
        return shop;
    }

    public UUID getContractId() {
        return contractId;
    }

    public int getQuantity() {
        return quantity;
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
