package dev.ked.bettershop.integration;

import dev.ked.bettershop.config.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.leralix.tan.api.internal.managers.ClaimManager;
import org.leralix.tan.api.internal.managers.PlayerManager;
import org.leralix.tan.api.internal.managers.TerritoryManager;
import org.leralix.tan.dataclass.territory.TownData;
import org.leralix.tan.dataclass.territory.TerritoryData;
import org.leralix.tan.enums.RolePermission;
import org.tan.api.interfaces.TanClaimedChunk;
import org.tan.api.interfaces.TanPlayer;
import org.tan.api.interfaces.TanTerritory;
import org.tan.api.interfaces.TanTown;

import java.util.Optional;
import java.util.UUID;

/**
 * Territory integration for Towns and Nations plugin by Leralix.
 *
 * Integrates with TaN 0.15.4+ API for territory management, permissions,
 * and treasury operations.
 */
public class TownsAndNationsTerritoryManager implements dev.ked.bettershop.integration.TerritoryManager {
    private final ConfigManager config;
    private final Economy economy;
    private final ClaimManager claimManager;
    private final PlayerManager playerManager;
    private final TerritoryManager territoryManager;

    public TownsAndNationsTerritoryManager(ConfigManager config, Economy economy) {
        this.config = config;
        this.economy = economy;
        this.claimManager = ClaimManager.getInstance();
        this.playerManager = new PlayerManager();
        this.territoryManager = TerritoryManager.getInstance();
    }

    @Override
    public boolean canCreateShop(Player player, Location location) {
        Block block = location.getBlock();

        // Check if block is claimed
        if (!claimManager.isBlockClaimed(block)) {
            // Wilderness
            return config.getTownsAndNationsAllowWilderness();
        }

        // Get territory that owns this chunk
        Optional<TanTerritory> territoryOpt = claimManager.getTerritoryOfBlock(block);
        if (territoryOpt.isEmpty()) {
            return config.getTownsAndNationsAllowWilderness();
        }

        TanTerritory territory = territoryOpt.get();

        // Check if player is a member of this territory
        TanPlayer tanPlayer = playerManager.get(player);
        if (tanPlayer == null) {
            return false;
        }

        // Check if player is a member of this territory
        // Members can create shops, non-members cannot
        for (TanPlayer member : territory.getMembers()) {
            if (member.getUUID().equals(player.getUniqueId())) {
                return true; // Player is a member
            }
        }

        return false; // Not a member
    }

    @Override
    public boolean canUseShop(Player player, Location location) {
        // In TaN, if a player can access a chunk, they can use shops
        // We'll allow all players to use shops (no ENTER permission check)
        return true;
    }

    @Override
    public double getShopTaxRate(Location location) {
        if (!config.getTownsAndNationsShopTaxEnabled()) {
            return 0.0;
        }

        Block block = location.getBlock();
        if (!claimManager.isBlockClaimed(block)) {
            // No tax in wilderness
            return 0.0;
        }

        return config.getTownsAndNationsShopTaxRate();
    }

    @Override
    public double getTransactionTaxRate(Location location, Player buyer) {
        if (!config.getTownsAndNationsTransactionTaxEnabled()) {
            return 0.0;
        }

        Block block = location.getBlock();
        if (!claimManager.isBlockClaimed(block)) {
            // No tax in wilderness
            return 0.0;
        }

        // Get the territory that owns this chunk
        Optional<TanTerritory> territoryOpt = claimManager.getTerritoryOfBlock(block);
        if (territoryOpt.isEmpty()) {
            return 0.0;
        }

        TanTerritory territory = territoryOpt.get();

        // Check if buyer is a member of this territory
        TanPlayer buyerData = playerManager.get(buyer);
        if (buyerData == null) {
            // Not registered, count as outsider
            return config.getTownsAndNationsOutsiderTaxRate();
        }

        // Check if buyer has a town and if it matches this territory
        if (buyerData.hasTown()) {
            Optional<TanTown> buyerTownOpt = buyerData.getTown();
            if (buyerTownOpt.isPresent() && buyerTownOpt.get().getID().equals(territory.getID())) {
                // Same town member, no transaction tax
                return 0.0;
            }
        }

        // Buyer is not from this town/territory
        return config.getTownsAndNationsOutsiderTaxRate();
    }

    @Override
    public void payTax(Location location, double amount) {
        Block block = location.getBlock();
        if (!claimManager.isBlockClaimed(block)) {
            return; // No territory to pay tax to
        }

        Optional<TanTerritory> territoryOpt = claimManager.getTerritoryOfBlock(block);
        if (territoryOpt.isEmpty()) {
            return;
        }

        TanTerritory territory = territoryOpt.get();

        // Access the underlying TownData to add to balance
        // We need to get the town through the territory manager
        Optional<TanTown> townOpt = territoryManager.getTown(territory.getID());
        if (townOpt.isEmpty()) {
            return;
        }

        // Unfortunately, the wrapper doesn't expose balance methods
        // We need to access the underlying TownData
        // This is a limitation of the current API - we'll use reflection or direct access
        if (townOpt.get() instanceof org.leralix.tan.api.internal.wrappers.TownDataWrapper) {
            // Get the actual TownData from storage
            org.leralix.tan.storage.stored.TownDataStorage townStorage =
                org.leralix.tan.storage.stored.TownDataStorage.getInstance();
            TownData townData = townStorage.get(territory.getID());
            if (townData != null) {
                townData.addToBalance(amount);
            }
        }
    }

    @Override
    public String getTerritoryName(Location location) {
        Block block = location.getBlock();

        if (!claimManager.isBlockClaimed(block)) {
            return "Wilderness";
        }

        Optional<TanTerritory> territoryOpt = claimManager.getTerritoryOfBlock(block);
        if (territoryOpt.isEmpty()) {
            return "Wilderness";
        }

        return territoryOpt.get().getName();
    }

    @Override
    public boolean isWilderness(Location location) {
        return !claimManager.isBlockClaimed(location.getBlock());
    }

    @Override
    public boolean canTreasuryFund(UUID shopOwner, Location location, double amount) {
        if (!config.getTownsAndNationsTreasuryFundingEnabled()) {
            return false;
        }

        Block block = location.getBlock();
        if (!claimManager.isBlockClaimed(block)) {
            return false; // No town in wilderness
        }

        Optional<TanTerritory> territoryOpt = claimManager.getTerritoryOfBlock(block);
        if (territoryOpt.isEmpty()) {
            return false;
        }

        TanTerritory territory = territoryOpt.get();

        // Check if shop owner is member of this territory/town
        Optional<TanPlayer> ownerOpt = playerManager.get(shopOwner);
        if (ownerOpt.isEmpty()) {
            return false;
        }

        TanPlayer owner = ownerOpt.get();
        if (!owner.hasTown()) {
            return false;
        }

        Optional<TanTown> ownerTownOpt = owner.getTown();
        if (ownerTownOpt.isEmpty() || !ownerTownOpt.get().getID().equals(territory.getID())) {
            return false; // Owner not in this town
        }

        // Check if town has enough money
        org.leralix.tan.storage.stored.TownDataStorage townStorage =
            org.leralix.tan.storage.stored.TownDataStorage.getInstance();
        TownData townData = townStorage.get(territory.getID());

        return townData != null && townData.getBalance() >= amount;
    }

    @Override
    public boolean withdrawFromTreasury(UUID shopOwner, Location location, double amount) {
        if (!canTreasuryFund(shopOwner, location, amount)) {
            return false;
        }

        Block block = location.getBlock();
        Optional<TanTerritory> territoryOpt = claimManager.getTerritoryOfBlock(block);
        if (territoryOpt.isEmpty()) {
            return false;
        }

        // Withdraw from town treasury
        org.leralix.tan.storage.stored.TownDataStorage townStorage =
            org.leralix.tan.storage.stored.TownDataStorage.getInstance();
        TownData townData = townStorage.get(territoryOpt.get().getID());

        if (townData == null) {
            return false;
        }

        townData.removeFromBalance(amount);
        return true;
    }
}
