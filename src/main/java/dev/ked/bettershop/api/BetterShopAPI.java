package dev.ked.bettershop.api;

import dev.ked.bettershop.events.ShopSilkRoadTransactionEvent;
import dev.ked.bettershop.events.ShopStockReleaseEvent;
import dev.ked.bettershop.events.ShopStockReserveEvent;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Public API for external plugins to interact with BetterShop.
 * This class is specifically designed for Silk Road integration.
 */
public class BetterShopAPI {
    private final ShopRegistry registry;
    private final ShopManager shopManager;
    private final Plugin plugin;
    private final Logger logger;

    public BetterShopAPI(ShopRegistry registry, ShopManager shopManager, Plugin plugin) {
        this.registry = registry;
        this.shopManager = shopManager;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Get all shops that have Silk Road enabled.
     * @return List of Silk Road enabled shops
     */
    public List<Shop> getSilkRoadShops() {
        return registry.getAllShops().stream()
                .filter(Shop::isSilkRoadEnabled)
                .collect(Collectors.toList());
    }

    /**
     * Get all Silk Road enabled shops in a specific region.
     * Region is determined by territory plugins (Towny/Towns and Nations) or world name.
     * @param region The region name (town, nation, or world name)
     * @return List of Silk Road enabled shops in the region
     */
    public List<Shop> getSilkRoadShops(String region) {
        if (region == null || region.isEmpty()) {
            return Collections.emptyList();
        }

        return getSilkRoadShops().stream()
                .filter(shop -> {
                    String shopRegion = getRegionForShop(shop);
                    return region.equalsIgnoreCase(shopRegion);
                })
                .collect(Collectors.toList());
    }

    /**
     * Check if a shop has Silk Road enabled.
     * @param shop The shop to check
     * @return true if Silk Road is enabled
     */
    public boolean isSilkRoadEnabled(Shop shop) {
        if (shop == null) {
            return false;
        }
        return shop.isSilkRoadEnabled();
    }

    /**
     * Reserve stock for a Silk Road contract.
     * @param shop The shop to reserve stock from
     * @param quantity The quantity to reserve
     * @param contractId The contract UUID
     * @return true if successful, false otherwise
     */
    public boolean reserveStock(Shop shop, int quantity, UUID contractId) {
        if (shop == null || contractId == null || quantity <= 0) {
            logger.warning("Invalid parameters for stock reservation");
            return false;
        }

        if (!shop.isSilkRoadEnabled()) {
            logger.warning("Cannot reserve stock - shop does not have Silk Road enabled");
            return false;
        }

        // Check if shop has enough stock
        int availableStock = shopManager.getStock(shop) - shop.getTotalReservedStock();
        if (availableStock < quantity) {
            logger.warning("Insufficient stock for reservation: " + availableStock + " available, " + quantity + " requested");
            return false;
        }

        // Fire cancellable event
        ShopStockReserveEvent event = new ShopStockReserveEvent(shop, contractId, quantity);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            logger.info("Stock reservation cancelled by event handler");
            return false;
        }

        // Reserve the stock
        shop.reserveStock(contractId, quantity);
        logger.info("Reserved " + quantity + " items in shop at " + shop.getLocation() + " for contract " + contractId);

        return true;
    }

    /**
     * Release a stock reservation.
     * @param shop The shop to release stock from
     * @param contractId The contract UUID
     */
    public void releaseReservation(Shop shop, UUID contractId) {
        if (shop == null || contractId == null) {
            logger.warning("Invalid parameters for reservation release");
            return;
        }

        Integer quantity = shop.getReservedStock().get(contractId);
        if (quantity == null) {
            logger.warning("No reservation found for contract " + contractId);
            return;
        }

        // Release the reservation
        shop.releaseReservation(contractId);

        // Fire event
        ShopStockReleaseEvent event = new ShopStockReleaseEvent(shop, contractId, quantity);
        Bukkit.getPluginManager().callEvent(event);

        logger.info("Released reservation of " + quantity + " items for contract " + contractId);
    }

    /**
     * Complete a Silk Road transaction.
     * This removes reserved stock from the chest, adds earnings to the shop, and fires an event.
     * @param shop The shop
     * @param contractId The contract UUID
     * @param buyerId The buyer UUID
     * @param amount The transaction amount
     * @return true if successful, false otherwise
     */
    public boolean completeTransaction(Shop shop, UUID contractId, UUID buyerId, double amount) {
        if (shop == null || contractId == null || buyerId == null) {
            logger.warning("Invalid parameters for transaction completion");
            return false;
        }

        Integer quantity = shop.getReservedStock().get(contractId);
        if (quantity == null) {
            logger.warning("No reservation found for contract " + contractId);
            return false;
        }

        // Remove items from chest
        if (!removeItemsFromChest(shop, quantity)) {
            logger.severe("Failed to remove items from chest for contract " + contractId);
            return false;
        }

        // Add earnings to shop
        shop.addEarnings(amount);

        // Release the reservation
        shop.releaseReservation(contractId);

        // Fire event
        ShopSilkRoadTransactionEvent event = new ShopSilkRoadTransactionEvent(shop, contractId, buyerId, quantity, amount);
        Bukkit.getPluginManager().callEvent(event);

        logger.info("Completed Silk Road transaction: " + quantity + " items, $" + amount + " for contract " + contractId);

        return true;
    }

    /**
     * Get the region name for a shop.
     * Priority: Towny town/nation > Towns and Nations > World name
     */
    private String getRegionForShop(Shop shop) {
        Location loc = shop.getLocation();

        // Try Towny integration
        if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            try {
                com.palmergames.bukkit.towny.TownyAPI townyAPI = com.palmergames.bukkit.towny.TownyAPI.getInstance();
                com.palmergames.bukkit.towny.object.TownBlock townBlock = townyAPI.getTownBlock(loc);
                if (townBlock != null && townBlock.hasTown()) {
                    com.palmergames.bukkit.towny.object.Town town = townBlock.getTownOrNull();
                    if (town != null) {
                        // Return nation name if available, otherwise town name
                        if (town.hasNation()) {
                            return town.getNationOrNull().getName();
                        }
                        return town.getName();
                    }
                }
            } catch (Exception e) {
                logger.warning("Error getting Towny region for shop: " + e.getMessage());
            }
        }

        // Try Towns and Nations integration
        if (Bukkit.getPluginManager().isPluginEnabled("TownsAndNations")) {
            try {
                // Towns and Nations API would be used here
                // Since we don't have the exact API, we'll skip for now
                // Implementation would be similar to Towny
            } catch (Exception e) {
                logger.warning("Error getting TownsAndNations region for shop: " + e.getMessage());
            }
        }

        // Fallback to world name
        return loc.getWorld().getName();
    }

    /**
     * Remove items from a shop's chest.
     */
    private boolean removeItemsFromChest(Shop shop, int quantity) {
        try {
            org.bukkit.block.Block block = shop.getLocation().getBlock();
            if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
                return false;
            }

            org.bukkit.inventory.Inventory inv = chest.getInventory();
            org.bukkit.inventory.ItemStack toRemove = shop.getItem().clone();
            toRemove.setAmount(quantity);

            return inv.removeItem(toRemove).isEmpty();
        } catch (Exception e) {
            logger.severe("Error removing items from chest: " + e.getMessage());
            return false;
        }
    }
}
