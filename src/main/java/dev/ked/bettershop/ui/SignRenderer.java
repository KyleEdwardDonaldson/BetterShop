package dev.ked.bettershop.ui;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.integration.MythicItemHandler;
import dev.ked.bettershop.shop.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Handles creation and updating of listing signs.
 */
public class SignRenderer {
    private final ConfigManager config;
    private final ShopRegistry registry;
    private final MiniMessage miniMessage;
    private MythicItemHandler mythicItemHandler;

    private static final BlockFace[] SIGN_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public SignRenderer(ConfigManager config, ShopRegistry registry) {
        this.config = config;
        this.registry = registry;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void setMythicItemHandler(MythicItemHandler mythicItemHandler) {
        this.mythicItemHandler = mythicItemHandler;
    }

    /**
     * Create or update a sign for a listing.
     */
    public boolean createOrUpdateSign(Listing listing) {
        Location signLocation = findSignLocation(listing.getLocation());
        if (signLocation == null) {
            return false;
        }

        Block signBlock = signLocation.getBlock();
        Sign sign;

        if (signBlock.getState() instanceof Sign) {
            sign = (Sign) signBlock.getState();
        } else {
            // Place new sign
            signBlock.setType(Material.OAK_WALL_SIGN);
            if (!(signBlock.getState() instanceof Sign)) {
                return false;
            }
            sign = (Sign) signBlock.getState();

            // Set facing direction
            if (sign.getBlockData() instanceof WallSign wallSign) {
                BlockFace facing = getSignFacing(listing.getLocation(), signLocation);
                wallSign.setFacing(facing);
                sign.setBlockData(wallSign);
            }
        }

        // Update sign text
        updateSignText(sign, listing);
        sign.update();
        return true;
    }

    /**
     * Remove a sign for a listing.
     */
    public void removeSign(Listing listing) {
        Location signLocation = findSignLocation(listing.getLocation());
        if (signLocation != null) {
            Block signBlock = signLocation.getBlock();
            if (signBlock.getState() instanceof Sign) {
                signBlock.setType(Material.AIR);
            }
        }
    }

    /**
     * Update the text on a sign.
     */
    private void updateSignText(Sign sign, Listing listing) {
        // Get shop name
        String shopName = "Unknown";
        Optional<ShopEntity> shopOpt = registry.getShopById(listing.getShopId());
        if (shopOpt.isPresent()) {
            shopName = shopOpt.get().getName();
        }

        // Get type color
        String typeColor = listing.getType() == ListingType.SELL ? config.getSellColor() : config.getBuyColor();

        // Line 1: [SELL] or [BUY]
        sign.line(0, miniMessage.deserialize(typeColor + "[" + listing.getType().name() + "]"));

        // Line 2: Shop name (truncated if needed)
        String truncatedName = shopName.length() > 15 ? shopName.substring(0, 12) + "..." : shopName;
        sign.line(1, Component.text(truncatedName));

        // Line 3: Item name and price
        String itemName = getItemDisplayName(listing);
        if (itemName != null) {
            String truncatedItem = itemName.length() > 10 ? itemName.substring(0, 9) + "." : itemName;
            sign.line(2, miniMessage.deserialize("<white>" + truncatedItem + " <gold>$" + String.format("%.0f", listing.getPrice())));
        } else {
            sign.line(2, Component.text("No Item"));
        }

        // Line 4: Stock (dynamically calculated)
        int stock = getStock(listing);
        sign.line(3, miniMessage.deserialize("<gray>Stock: <white>" + stock));
    }

    /**
     * Get display name for an item (handles both vanilla and mythic items).
     */
    private String getItemDisplayName(Listing listing) {
        if (listing.isMythicItem() && mythicItemHandler != null) {
            ItemStack mythicItem = mythicItemHandler.getMythicItem(listing.getMythicItemId(), 1);
            if (mythicItem != null && mythicItem.getItemMeta().hasDisplayName()) {
                // Remove formatting codes for sign display
                Component displayName = mythicItem.getItemMeta().displayName();
                if (displayName != null) {
                    return miniMessage.stripTags(miniMessage.serialize(displayName));
                }
            }
            return listing.getMythicItemId();
        } else if (listing.getItem() != null) {
            return listing.getItem().getType().name().toLowerCase().replace('_', ' ');
        }
        return null;
    }

    /**
     * Get stock for a listing.
     */
    private int getStock(Listing listing) {
        Block block = listing.getLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
            return 0;
        }

        int count = 0;

        // Handle mythic items
        if (listing.isMythicItem() && mythicItemHandler != null) {
            for (ItemStack item : chest.getInventory().getContents()) {
                if (item != null && mythicItemHandler.isMythicItem(item, listing.getMythicItemId())) {
                    count += item.getAmount();
                }
            }
        } else if (listing.getItem() != null) {
            // Handle vanilla items
            for (ItemStack item : chest.getInventory().getContents()) {
                if (item != null && item.isSimilar(listing.getItem())) {
                    count += item.getAmount();
                }
            }
        }

        return count;
    }

    /**
     * Find a suitable location for a sign adjacent to the chest.
     */
    private Location findSignLocation(Location chestLocation) {
        Block chestBlock = chestLocation.getBlock();

        for (BlockFace face : SIGN_FACES) {
            Block adjacent = chestBlock.getRelative(face);

            // Check if block is air or already a sign
            if (adjacent.getType() == Material.AIR || adjacent.getState() instanceof Sign) {
                return adjacent.getLocation();
            }
        }

        return null;
    }

    /**
     * Get the facing direction for a wall sign.
     */
    private BlockFace getSignFacing(Location chestLocation, Location signLocation) {
        int dx = signLocation.getBlockX() - chestLocation.getBlockX();
        int dz = signLocation.getBlockZ() - chestLocation.getBlockZ();

        if (dx > 0) return BlockFace.WEST;
        if (dx < 0) return BlockFace.EAST;
        if (dz > 0) return BlockFace.NORTH;
        if (dz < 0) return BlockFace.SOUTH;

        return BlockFace.NORTH;
    }
}
