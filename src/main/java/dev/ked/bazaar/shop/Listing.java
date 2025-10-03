package dev.ked.bazaar.shop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an individual listing (chest) within a shop.
 * A listing is a single sell or buy point for one item type.
 */
public class Listing {
    private final UUID id;
    private final UUID shopId; // Parent shop entity
    private final Location location;
    private final UUID owner;
    private final ListingType type;
    private ItemStack item; // Not final - can be set later for empty SELL listings
    private String mythicItemId; // For MythicMobs items (null if not mythic)
    private double price;
    private double earnings;
    private int buyLimit; // For BUY listings: how many items owner wants to buy (0 = unlimited)
    private final long createdAt;
    private boolean silkRoadEnabled = false;
    private Map<UUID, Integer> reservedStock = new HashMap<>(); // UUID = contractId

    public Listing(UUID id, UUID shopId, Location location, UUID owner, ListingType type, ItemStack item, double price) {
        this.id = id;
        this.shopId = shopId;
        this.location = location;
        this.owner = owner;
        this.type = type;
        if (item != null) {
            this.item = item.clone();
            this.item.setAmount(1); // Normalize to single item
        } else {
            this.item = null; // Empty listing (will be set later)
        }
        this.price = price;
        this.earnings = 0.0;
        this.buyLimit = 0; // 0 = unlimited
        this.createdAt = System.currentTimeMillis();
        this.reservedStock = new HashMap<>();
    }

    // Constructor with buy limit
    public Listing(UUID id, UUID shopId, Location location, UUID owner, ListingType type, ItemStack item, double price, int buyLimit) {
        this.id = id;
        this.shopId = shopId;
        this.location = location;
        this.owner = owner;
        this.type = type;
        if (item != null) {
            this.item = item.clone();
            this.item.setAmount(1); // Normalize to single item
        } else {
            this.item = null; // Empty listing (will be set later)
        }
        this.price = price;
        this.earnings = 0.0;
        this.buyLimit = buyLimit;
        this.createdAt = System.currentTimeMillis();
        this.reservedStock = new HashMap<>();
    }

    // Full constructor for loading from storage
    public Listing(UUID id, UUID shopId, Location location, UUID owner, ListingType type, ItemStack item,
                   double price, double earnings, int buyLimit, long createdAt, boolean silkRoadEnabled,
                   Map<UUID, Integer> reservedStock, String mythicItemId) {
        this.id = id;
        this.shopId = shopId;
        this.location = location;
        this.owner = owner;
        this.type = type;
        if (item != null) {
            this.item = item.clone();
            this.item.setAmount(1);
        } else {
            this.item = null;
        }
        this.mythicItemId = mythicItemId;
        this.price = price;
        this.earnings = earnings;
        this.buyLimit = buyLimit;
        this.createdAt = createdAt;
        this.silkRoadEnabled = silkRoadEnabled;
        this.reservedStock = reservedStock != null ? reservedStock : new HashMap<>();
    }

    // Constructor for mythic items
    public Listing(UUID id, UUID shopId, Location location, UUID owner, ListingType type, String mythicItemId,
                   double price, int buyLimit) {
        this.id = id;
        this.shopId = shopId;
        this.location = location;
        this.owner = owner;
        this.type = type;
        this.item = null; // Mythic items are handled via mythicItemId
        this.mythicItemId = mythicItemId;
        this.price = price;
        this.earnings = 0.0;
        this.buyLimit = buyLimit;
        this.createdAt = System.currentTimeMillis();
        this.reservedStock = new HashMap<>();
    }

    public UUID getId() {
        return id;
    }

    public UUID getShopId() {
        return shopId;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwner() {
        return owner;
    }

    public ListingType getType() {
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
     * Get remaining buy limit (how many more items the listing will buy).
     * Only applicable to BUY listings.
     * @param currentStock Current stock in the chest
     * @return Remaining items to buy (0 if unlimited or SELL listing)
     */
    public int getRemainingBuyLimit(int currentStock) {
        if (type != ListingType.BUY || buyLimit == 0) {
            return 0; // Unlimited or not a buy listing
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

    /**
     * Check if this listing is for a MythicMobs item.
     * @return true if this is a mythic item listing
     */
    public boolean isMythicItem() {
        return mythicItemId != null;
    }

    /**
     * Get the MythicMobs item ID.
     * @return The mythic item ID, or null if not a mythic item
     */
    public String getMythicItemId() {
        return mythicItemId;
    }

    /**
     * Set the MythicMobs item ID.
     * @param mythicItemId The mythic item ID
     */
    public void setMythicItemId(String mythicItemId) {
        this.mythicItemId = mythicItemId;
    }

    @Override
    public String toString() {
        return "Listing{" +
                "id=" + id +
                ", shopId=" + shopId +
                ", location=" + location +
                ", owner=" + owner +
                ", type=" + type +
                ", item=" + (item != null ? item.getType() : "none") +
                ", mythicItemId=" + mythicItemId +
                ", price=" + price +
                ", earnings=" + earnings +
                ", silkRoadEnabled=" + silkRoadEnabled +
                '}';
    }
}
