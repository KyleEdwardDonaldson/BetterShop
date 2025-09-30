package dev.ked.bettershop.shop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Shop {
    private final Location location;
    private final UUID owner;
    private final ShopType type;
    private final ItemStack item;
    private double price;
    private double earnings;
    private final long createdAt;

    public Shop(Location location, UUID owner, ShopType type, ItemStack item, double price) {
        this.location = location;
        this.owner = owner;
        this.type = type;
        this.item = item.clone();
        this.item.setAmount(1); // Normalize to single item
        this.price = price;
        this.earnings = 0.0;
        this.createdAt = System.currentTimeMillis();
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwner() {
        return owner;
    }

    public ShopType getType() {
        return type;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getEarnings() {
        return earnings;
    }

    public void addEarnings(double amount) {
        this.earnings += amount;
    }

    public void setEarnings(double earnings) {
        this.earnings = earnings;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the current stock count from the chest at this location.
     * This method should be called by ShopManager which has access to the chest.
     */
    public int getStock() {
        // Stock is dynamically calculated from chest contents
        // This is handled by ShopManager
        return 0;
    }

    @Override
    public String toString() {
        return "Shop{" +
                "location=" + location +
                ", owner=" + owner +
                ", type=" + type +
                ", item=" + item.getType() +
                ", price=" + price +
                ", earnings=" + earnings +
                '}';
    }
}