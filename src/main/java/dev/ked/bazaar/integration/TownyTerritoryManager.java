package dev.ked.bazaar.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import dev.ked.bazaar.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Towny integration for territory management.
 */
public class TownyTerritoryManager implements TerritoryManager {
    private final ConfigManager config;
    private final TownyAPI townyAPI;

    public TownyTerritoryManager(ConfigManager config) {
        this.config = config;
        this.townyAPI = TownyAPI.getInstance();
    }

    @Override
    public boolean canCreateShop(Player player, Location location) {
        TownBlock townBlock = townyAPI.getTownBlock(location);

        // Wilderness check
        if (townBlock == null) {
            return config.getTownyAllowWilderness();
        }

        Town town = townBlock.getTownOrNull();
        if (town == null) {
            return false;
        }

        // Check if commercial plot is required
        if (config.getTownyRequireCommercialPlot()) {
            TownBlockType type = townBlock.getType();
            if (type != TownBlockType.COMMERCIAL && type != TownBlockType.EMBASSY) {
                return false;
            }
        }

        // Check build permissions (checking if player can place a chest)
        return PlayerCacheUtil.getCachePermission(player, location, org.bukkit.Material.CHEST, TownyPermission.ActionType.BUILD);
    }

    @Override
    public boolean canUseShop(Player player, Location location) {
        TownBlock townBlock = townyAPI.getTownBlock(location);

        if (townBlock == null) {
            return true; // Wilderness always accessible
        }

        // Check if cross-nation trading is restricted
        if (config.getTownyCrossNationRestrictions()) {
            Resident playerResident = townyAPI.getResident(player);
            if (playerResident != null && playerResident.hasTown()) {
                Town playerTown = playerResident.getTownOrNull();
                Town locationTown = townBlock.getTownOrNull();

                if (playerTown != null && locationTown != null) {
                    Nation playerNation = playerTown.getNationOrNull();
                    Nation locationNation = locationTown.getNationOrNull();

                    // If both have nations and they're different, check restrictions
                    if (playerNation != null && locationNation != null && !playerNation.equals(locationNation)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public double getShopTaxRate(Location location) {
        if (!config.getTownyShopTaxEnabled()) {
            return 0.0;
        }

        TownBlock townBlock = townyAPI.getTownBlock(location);
        if (townBlock == null) {
            return 0.0; // No tax in wilderness
        }

        // Get town's shop tax rate (if custom metadata is supported)
        // Otherwise use config default
        return config.getTownyShopTaxRate();
    }

    @Override
    public double getTransactionTaxRate(Location location, Player buyer) {
        if (!config.getTownyTransactionTaxEnabled()) {
            return 0.0;
        }

        TownBlock townBlock = townyAPI.getTownBlock(location);
        if (townBlock == null) {
            return 0.0;
        }

        Resident buyerResident = townyAPI.getResident(buyer);
        if (buyerResident == null) {
            return config.getTownyOutsiderTaxRate();
        }

        Town buyerTown = buyerResident.getTownOrNull();
        Town shopTown = townBlock.getTownOrNull();

        // If buyer is from a different town, apply outsider tax
        if (buyerTown == null || shopTown == null || !buyerTown.equals(shopTown)) {
            return config.getTownyOutsiderTaxRate();
        }

        return 0.0; // Same town, no tax
    }

    @Override
    public void payTax(Location location, double amount) {
        if (amount <= 0) {
            return;
        }

        TownBlock townBlock = townyAPI.getTownBlock(location);
        if (townBlock == null) {
            return;
        }

        Town town = townBlock.getTownOrNull();
        if (town != null) {
            // Add to town treasury
            try {
                town.getAccount().deposit(amount, "BetterShop Tax");
            } catch (Exception e) {
                // Handle economy exception
            }
        }
    }

    @Override
    public boolean canTreasuryFund(UUID shopOwner, Location location, double amount) {
        // Towny doesn't support treasury funding for individual shops
        return false;
    }

    @Override
    public boolean withdrawFromTreasury(UUID shopOwner, Location location, double amount) {
        // Not supported by Towny
        return false;
    }

    @Override
    public String getTerritoryName(Location location) {
        TownBlock townBlock = townyAPI.getTownBlock(location);
        if (townBlock == null) {
            return "Wilderness";
        }

        Town town = townBlock.getTownOrNull();
        return town != null ? town.getName() : "Unknown";
    }

    @Override
    public boolean isWilderness(Location location) {
        return townyAPI.getTownBlock(location) == null;
    }

    @Override
    public String getTerritoryId(Location location) {
        TownBlock townBlock = townyAPI.getTownBlock(location);
        if (townBlock == null) {
            return null; // Wilderness
        }

        Town town = townBlock.getTownOrNull();
        if (town == null) {
            return null;
        }

        // Use town UUID as territory ID
        return "towny:" + town.getUUID().toString();
    }
}