package dev.ked.bettershop.events;

import dev.ked.bettershop.shop.Shop;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a stock reservation is released.
 */
public class ShopStockReleaseEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;
    private final UUID contractId;
    private final int quantity;

    public ShopStockReleaseEvent(Shop shop, UUID contractId, int quantity) {
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
