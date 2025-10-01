package dev.ked.bettershop.shop;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.integration.TerritoryManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages shop entity operations including creation, removal, and validation.
 * Handles shop-level operations (not individual listing operations).
 */
public class ShopEntityManager {
    private final ShopRegistry registry;
    private final ConfigManager config;
    private TerritoryManager territoryManager;

    public ShopEntityManager(ShopRegistry registry, ConfigManager config) {
        this.registry = registry;
        this.config = config;
    }

    public void setTerritoryManager(TerritoryManager territoryManager) {
        this.territoryManager = territoryManager;
    }

    /**
     * Create a new shop entity.
     * @param owner The owner of the shop
     * @param name The name of the shop
     * @param location The creation location (for territory detection)
     * @return The created shop entity, or empty if creation failed
     */
    public Optional<ShopEntity> createShop(UUID owner, String name, Location location) {
        // Validate name is not empty
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }

        // Check if name is already taken by this owner
        if (registry.isShopNameTaken(owner, name)) {
            return Optional.empty();
        }

        // Check shop limit
        int currentShops = registry.getShopCount(owner);
        int maxShops = config.getMaxShopsPerPlayer();
        if (currentShops >= maxShops) {
            return Optional.empty();
        }

        // Detect territory
        String territoryId = null;
        if (territoryManager != null) {
            territoryId = territoryManager.getTerritoryId(location);
        }

        // Create shop entity
        UUID shopId = UUID.randomUUID();
        ShopEntity shop = new ShopEntity(shopId, owner, name, location, territoryId, System.currentTimeMillis());

        // Register shop
        registry.registerShop(shop);

        return Optional.of(shop);
    }

    /**
     * Remove a shop entity and all its listings.
     * @param shopId The UUID of the shop to remove
     * @return true if the shop was removed, false if not found
     */
    public boolean removeShop(UUID shopId) {
        Optional<ShopEntity> shopOpt = registry.getShopById(shopId);
        if (shopOpt.isEmpty()) {
            return false;
        }

        // Unregister will cascade to all listings
        registry.unregisterShop(shopId);
        return true;
    }

    /**
     * Rename a shop.
     * @param shopId The UUID of the shop
     * @param newName The new name
     * @return true if renamed successfully, false otherwise
     */
    public boolean renameShop(UUID shopId, String newName) {
        Optional<ShopEntity> shopOpt = registry.getShopById(shopId);
        if (shopOpt.isEmpty()) {
            return false;
        }

        ShopEntity shop = shopOpt.get();

        // Check if new name is already taken by this owner
        if (!shop.getName().equalsIgnoreCase(newName) && registry.isShopNameTaken(shop.getOwner(), newName)) {
            return false;
        }

        shop.setName(newName);
        return true;
    }

    /**
     * Get a shop by ID.
     */
    public Optional<ShopEntity> getShop(UUID shopId) {
        return registry.getShopById(shopId);
    }

    /**
     * Get a shop by owner and name.
     */
    public Optional<ShopEntity> getShop(UUID owner, String name) {
        return registry.getShopByOwnerAndName(owner, name);
    }

    /**
     * Get all shops owned by a player.
     */
    public List<ShopEntity> getShops(UUID owner) {
        return registry.getShopsByOwner(owner);
    }

    /**
     * Get all listings in a shop.
     */
    public List<Listing> getListings(UUID shopId) {
        return registry.getListingsByShop(shopId);
    }

    /**
     * Calculate total earnings across all listings in a shop.
     */
    public double getTotalEarnings(UUID shopId) {
        List<Listing> listings = registry.getListingsByShop(shopId);
        return listings.stream()
                .mapToDouble(Listing::getEarnings)
                .sum();
    }

    /**
     * Check if a player can create a shop at a location.
     * Validates territory permissions and shop limits.
     */
    public boolean canCreateShop(Player player, Location location) {
        // Check shop limit
        int currentShops = registry.getShopCount(player.getUniqueId());
        int maxShops = config.getMaxShopsPerPlayer();
        if (currentShops >= maxShops && !player.hasPermission("bettershop.admin")) {
            return false;
        }

        // Check territory permissions
        if (territoryManager != null) {
            return territoryManager.canCreateShop(player, location);
        }

        return true;
    }

    /**
     * Check if a listing can be placed in a shop's territory.
     * @param shopId The shop UUID
     * @param location The listing location
     * @return true if the listing can be placed, false otherwise
     */
    public boolean canPlaceListingHere(UUID shopId, Location location) {
        Optional<ShopEntity> shopOpt = registry.getShopById(shopId);
        if (shopOpt.isEmpty()) {
            return false;
        }

        ShopEntity shop = shopOpt.get();

        // If no territory manager, allow placement anywhere
        if (territoryManager == null) {
            return true;
        }

        // Check if territory integration requires same territory
        if (!config.isTerritoryRestrictionEnabled()) {
            return true;
        }

        // Get shop's territory
        String shopTerritory = shop.getTerritoryId();
        String locationTerritory = territoryManager.getTerritoryId(location);

        // Both null (wilderness) or both match
        if (shopTerritory == null && locationTerritory == null) {
            return config.isWildernessAllowed();
        }

        return shopTerritory != null && shopTerritory.equals(locationTerritory);
    }

    /**
     * Check if a shop has reached its listing limit.
     */
    public boolean hasReachedListingLimit(UUID shopId) {
        int currentListings = registry.getListingCount(shopId);
        int maxListings = config.getMaxListingsPerShop();
        return currentListings >= maxListings;
    }

    /**
     * Get the number of shops owned by a player.
     */
    public int getShopCount(UUID owner) {
        return registry.getShopCount(owner);
    }

    /**
     * Get all registered shops.
     */
    public List<ShopEntity> getAllShops() {
        return (List<ShopEntity>) registry.getAllShops();
    }
}
