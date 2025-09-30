package dev.ked.bettershop.ui;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;

/**
 * Handles creation and updating of shop signs.
 */
public class SignRenderer {
    private final ConfigManager config;
    private final ShopManager shopManager;
    private final MiniMessage miniMessage;

    private static final BlockFace[] SIGN_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public SignRenderer(ConfigManager config, ShopManager shopManager) {
        this.config = config;
        this.shopManager = shopManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Create or update a sign for a shop.
     */
    public boolean createOrUpdateSign(Shop shop) {
        Location signLocation = findSignLocation(shop.getLocation());
        if (signLocation == null) {
            return false;
        }

        Block signBlock = signLocation.getBlock();
        Sign sign;

        if (signBlock.getState() instanceof Sign) {
            sign = (Sign) signBlock.getState();
        } else {
            // Place a new wall sign
            BlockFace face = getAttachmentFace(shop.getLocation(), signLocation);
            if (face == null) {
                return false;
            }

            signBlock.setType(Material.OAK_WALL_SIGN);
            BlockData blockData = signBlock.getBlockData();
            if (blockData instanceof WallSign wallSign) {
                wallSign.setFacing(face.getOppositeFace());
                signBlock.setBlockData(wallSign);
            }

            sign = (Sign) signBlock.getState();
        }

        updateSignText(sign, shop);
        return true;
    }

    /**
     * Update the text on an existing sign.
     */
    public void updateSignText(Sign sign, Shop shop) {
        String color = shop.getType() == ShopType.BUY ? config.getBuyColor() : config.getSellColor();
        String typeText = shop.getType().name();
        int stock = shopManager.getStock(shop);
        String itemName = getItemDisplayName(shop);

        // Line 1: [BUY] or [SELL] with color
        Component line1 = miniMessage.deserialize(color + "[" + typeText + "]");

        // Line 2: Item name (truncated if needed)
        Component line2 = Component.text(truncate(itemName, 15));

        // Line 3: Price
        Component line3 = miniMessage.deserialize("<yellow>$" + formatPrice(shop.getPrice()));

        // Line 4: Stock (only for SELL shops) or "Buying" for BUY shops
        Component line4;
        if (shop.getType() == ShopType.SELL) {
            line4 = miniMessage.deserialize("<gray>Stock: <white>" + stock);
        } else {
            line4 = miniMessage.deserialize("<gray>Buying");
        }

        sign.line(0, line1);
        sign.line(1, line2);
        sign.line(2, line3);
        sign.line(3, line4);

        sign.update();
    }

    /**
     * Remove a sign associated with a shop.
     */
    public void removeSign(Shop shop) {
        Location signLocation = findExistingSign(shop.getLocation());
        if (signLocation != null) {
            signLocation.getBlock().setType(Material.AIR);
        }
    }

    /**
     * Find a suitable location for a sign near the chest.
     */
    private Location findSignLocation(Location chestLoc) {
        // First check if a sign already exists
        Location existing = findExistingSign(chestLoc);
        if (existing != null) {
            return existing;
        }

        // Try to find an empty block face
        for (BlockFace face : SIGN_FACES) {
            Block adjacent = chestLoc.getBlock().getRelative(face);
            if (adjacent.getType() == Material.AIR) {
                return adjacent.getLocation();
            }
        }

        return null;
    }

    /**
     * Find an existing sign attached to or near the chest.
     */
    private Location findExistingSign(Location chestLoc) {
        for (BlockFace face : SIGN_FACES) {
            Block adjacent = chestLoc.getBlock().getRelative(face);
            if (adjacent.getState() instanceof Sign) {
                return adjacent.getLocation();
            }
        }
        return null;
    }

    /**
     * Get the face that the sign should attach to.
     */
    private BlockFace getAttachmentFace(Location chest, Location sign) {
        int dx = sign.getBlockX() - chest.getBlockX();
        int dz = sign.getBlockZ() - chest.getBlockZ();

        if (dx == 1) return BlockFace.WEST;
        if (dx == -1) return BlockFace.EAST;
        if (dz == 1) return BlockFace.NORTH;
        if (dz == -1) return BlockFace.SOUTH;

        return null;
    }

    /**
     * Get a display name for the item.
     */
    private String getItemDisplayName(Shop shop) {
        Component itemName = shop.getItem().displayName();
        return MiniMessage.miniMessage().stripTags(MiniMessage.miniMessage().serialize(itemName));
    }

    /**
     * Format price for display.
     */
    private String formatPrice(double price) {
        if (price == (long) price) {
            return String.format("%d", (long) price);
        } else {
            return String.format("%.2f", price);
        }
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 1) + "…";
    }
}