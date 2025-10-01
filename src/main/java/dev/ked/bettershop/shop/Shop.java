package dev.ked.bettershop.shop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Shop {
    private final Location location;
    private final UUID owner;
    private final ShopType type;
    private ItemStack item; // Not final - can be set later for empty SELL shops
    private double price;
    private double earnings;
    private int buyLimit; // For BUY shops: how many items owner wants to buy (0 = unlimited)
    private final long createdAt;
    private boolean silkRoadEnabled = false;
    private Map<UUID, Integer> reservedStock = new HashMap<>(); // UUID = contractId

    public Shop(Location location, UUID owner, ShopType type, ItemStack item, double price) {
        this.location = location;
        this.owner = owner;
        this.type = type;
        if (item != null) {
            this.item = item.clone();
            this.item.setAmount(1); // Normalize to single item
        } else {
            this.item = null; // Empty shop (will be set later)
        }
        this.price = price;
        this.earnings = 0.0;
        this.buyLimit = 0; // 0 = unlimited
        this.createdAt = System.currentTimeMillis();
        this.reservedStock = new HashMap<>();
    }

    // Constructor with buy limit
    public Shop(Location location, UUID owner, ShopType type, ItemStack item, double price, int buyLimit) {
        this.location = location;
        this.owner = owner;
        this.type = type;
        if (item != null) {
            this.item = item.clone();
            this.item.setAmount(1); // Normalize to single item
        } else {
            this.item = null; // Empty shop (will be set later)
        }
        this.price = price;
        this.earnings = 0.0;
        this.buyLimit = buyLimit;
        this.createdAt = System.currentTimeMillis();
        this.reservedStock = new HashMap<>();
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
        return item != null ? item.clone() : null;
    }

    public void setItem(ItemStack item) {
        if (item != null) {
            this.item = item.clone();
            this.item.setAmount(1); // Normalize to single item
        } else {
            this.item = null;
        }
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

    public int getBuyLimit() {
        return buyLimit;
    }

    public void setBuyLimit(int buyLimit) {
        this.buyLimit = buyLimit;
    }

    /**
     * Get remaining buy limit (how many more items the shop will buy).
     * Only applicable to BUY shops.
     * @param currentStock Current stock in the chest
     * @return Remaining items to buy (0 if unlimited or SELL shop)
     */
    public int getRemainingBuyLimit(int currentStock) {
        if (type != ShopType.BUY || buyLimit == 0) {
            return 0; // Unlimited or not a buy shop
        }
        int remaining = buyLimit - currentStock;
        return Math.max(0, remaining);
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

    public boolean isSilkRoadEnabled() {
        return silkRoadEnabled;
    }

    public void setSilkRoadEnabled(boolean silkRoadEnabled) {
        this.silkRoadEnabled = silkRoadEnabled;
    }

    /**
     * Reserve stock for a Silk Road contract.
     * @param contractId The contract UUID
     * @param quantity The quantity to reserve
     */
    public void reserveStock(UUID contractId, int quantity) {
        reservedStock.put(contractId, quantity);
    }

    /**
     * Release a stock reservation.
     * @param contractId The contract UUID
     */
    public void releaseReservation(UUID contractId) {
        reservedStock.remove(contractId);
    }

    /**
     * Get the reserved stock map.
     * @return Map of contract IDs to reserved quantities
     */
    public Map<UUID, Integer> getReservedStock() {
        return reservedStock;
    }

    /**
     * Set the reserved stock map (used for data loading).
     * @param reservedStock Map of contract IDs to reserved quantities
     */
    public void setReservedStock(Map<UUID, Integer> reservedStock) {
        this.reservedStock = reservedStock != null ? reservedStock : new HashMap<>();
    }

    /**
     * Get the total quantity of reserved stock.
     * @return Total reserved quantity
     */
    public int getTotalReservedStock() {
        return reservedStock.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public String toString() {
        return "Shop{" +
                "location=" + location +
                ", owner=" + owner +
                ", type=" + type +
                ", item=" + (item != null ? item.getType() : "none") +
                ", price=" + price +
                ", earnings=" + earnings +
                ", silkRoadEnabled=" + silkRoadEnabled +
                '}';
    }
}