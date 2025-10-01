package dev.ked.bettershop.events;

import dev.ked.bettershop.shop.Shop;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a Silk Road delivery completes.
 */
public class ShopSilkRoadTransactionEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;
    private final UUID contractId;
    private final UUID buyerId;
    private final int quantity;
    private final double amount;

    public ShopSilkRoadTransactionEvent(Shop shop, UUID contractId, UUID buyerId, int quantity, double amount) {
        this.shop = shop;
        this.contractId = contractId;
        this.buyerId = buyerId;
        this.quantity = quantity;
        this.amount = amount;
    }

    public Shop getShop() {
        return shop;
    }

    public UUID getContractId() {
        return contractId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getAmount() {
        return amount;
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
