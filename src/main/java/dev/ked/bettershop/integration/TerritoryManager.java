package dev.ked.bettershop.integration;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Interface for territory plugin integrations (Towny, Nations, etc.)
 */
public interface TerritoryManager {

    /**
     * Check if a player can create a shop at this location.
     */
    boolean canCreateShop(Player player, Location location);

    /**
     * Check if a player can use/trade with a shop at this location.
     */
    boolean canUseShop(Player player, Location location);

    /**
     * Get the tax rate for shop earnings at this location.
     * @return Tax rate (0.0 to 1.0, e.g., 0.05 = 5%)
     */
    double getShopTaxRate(Location location);

    /**
     * Get the transaction tax rate for purchases at this location.
     * Applied when buyer is from outside the territory.
     * @return Tax rate (0.0 to 1.0)
     */
    double getTransactionTaxRate(Location location, Player buyer);

    /**
     * Pay tax to the territory owner (town/nation treasury).
     */
    void payTax(Location location, double amount);

    /**
     * Check if a nation/town treasury can fund a BUY shop.
     * @param shopOwner The shop owner
     * @param location The shop location
     * @param amount The amount needed
     * @return true if treasury can fund this amount
     */
    boolean canTreasuryFund(UUID shopOwner, Location location, double amount);

    /**
     * Withdraw money from nation/town treasury for BUY shop.
     * @param shopOwner The shop owner
     * @param location The shop location
     * @param amount The amount to withdraw
     * @return true if successful
     */
    boolean withdrawFromTreasury(UUID shopOwner, Location location, double amount);

    /**
     * Get the territory name at this location (for display).
     */
    String getTerritoryName(Location location);

    /**
     * Check if location is in wilderness/unclaimed.
     */
    boolean isWilderness(Location location);
}