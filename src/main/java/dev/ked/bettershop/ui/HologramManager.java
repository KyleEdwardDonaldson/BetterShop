package dev.ked.bettershop.ui;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopRegistry;
import dev.ked.bettershop.shop.ShopType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages hologram displays above shops using armor stands.
 */
public class HologramManager {
    private final ConfigManager config;
    private final ShopManager shopManager;
    private final ShopRegistry shopRegistry;

    // Map of shop location -> hologram armor stand
    private final Map<Location, ArmorStand> holograms = new ConcurrentHashMap<>();

    public HologramManager(ConfigManager config, ShopManager shopManager, ShopRegistry shopRegistry) {
        this.config = config;
        this.shopManager = shopManager;
        this.shopRegistry = shopRegistry;
    }

    /**
     * Create a hologram for a shop.
     */
    public void createHologram(Shop shop) {
        if (!config.areHologramsEnabled()) {
            return;
        }

        // Remove existing hologram if present
        removeHologram(shop);

        Location hologramLoc = getHologramLocation(shop.getLocation());
        ArmorStand armorStand = (ArmorStand) hologramLoc.getWorld().spawnEntity(hologramLoc, EntityType.ARMOR_STAND);

        // Configure armor stand
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        armorStand.setMarker(true);
        armorStand.setCustomNameVisible(true);
        armorStand.setPersistent(false);

        updateHologramText(armorStand, shop);

        holograms.put(shop.getLocation(), armorStand);
    }

    /**
     * Update the hologram text for a shop.
     */
    public void updateHologram(Shop shop) {
        if (!config.areHologramsEnabled()) {
            return;
        }

        ArmorStand hologram = holograms.get(shop.getLocation());
        if (hologram != null && hologram.isValid()) {
            updateHologramText(hologram, shop);
        } else {
            // Recreate if missing
            createHologram(shop);
        }
    }

    /**
     * Remove a hologram for a shop.
     */
    public void removeHologram(Shop shop) {
        ArmorStand hologram = holograms.remove(shop.getLocation());
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
    }

    /**
     * Remove all holograms.
     */
    public void removeAllHolograms() {
        for (ArmorStand hologram : holograms.values()) {
            if (hologram.isValid()) {
                hologram.remove();
            }
        }
        holograms.clear();
    }

    /**
     * Update the custom name of a hologram armor stand.
     */
    private void updateHologramText(ArmorStand armorStand, Shop shop) {
        String itemName = getItemDisplayName(shop);
        int stock = shopManager.getStock(shop);
        String priceStr = formatPrice(shop.getPrice());

        TextColor color = shop.getType() == ShopType.BUY ? NamedTextColor.GREEN : NamedTextColor.BLUE;

        // Format for SELL shops: 🛒 Diamond - $10 (Stock: 5, 2 in transit)
        // Format for BUY shops: 🛒 Diamond - $10 (Buying 45/100) or (Buying) if unlimited
        Component name = Component.text("🛒 ", NamedTextColor.YELLOW)
                .append(Component.text(itemName, NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text("$" + priceStr, color));

        if (shop.getType() == ShopType.SELL) {
            int reservedStock = shop.getTotalReservedStock();
            if (reservedStock > 0) {
                // Show "Stock: X, Y in transit"
                name = name.append(Component.text(" (Stock: " + stock + ", ", NamedTextColor.GRAY))
                        .append(Component.text(reservedStock + " in transit", NamedTextColor.YELLOW))
                        .append(Component.text(")", NamedTextColor.GRAY));
            } else {
                name = name.append(Component.text(" (" + stock + " left)", NamedTextColor.GRAY));
            }
        } else {
            // BUY shop
            int buyLimit = shop.getBuyLimit();
            if (buyLimit == 0) {
                // Unlimited
                name = name.append(Component.text(" (Buying)", NamedTextColor.GRAY));
            } else {
                // Limited
                int remaining = shop.getRemainingBuyLimit(stock);
                if (remaining == 0) {
                    name = name.append(Component.text(" (Full)", NamedTextColor.RED));
                } else {
                    name = name.append(Component.text(" (Buying " + remaining + " more)", NamedTextColor.GRAY));
                }
            }
        }

        armorStand.customName(name);
    }

    /**
     * Get the location for the hologram (above the chest).
     * Automatically offsets vertically if nearby shops detected to prevent collisions.
     */
    private Location getHologramLocation(Location chestLoc) {
        double baseHeight = 1.5;
        double verticalOffset = 0.0;

        // Find nearby shops within 3 blocks
        List<Shop> nearbyShops = getNearbyShops(chestLoc, 3.0);

        if (!nearbyShops.isEmpty()) {
            // Sort by location to ensure consistent ordering
            nearbyShops.sort(Comparator.comparingDouble(shop -> {
                Location loc = shop.getLocation();
                return loc.getBlockX() * 1000000 + loc.getBlockY() * 1000 + loc.getBlockZ();
            }));

            // Find the index of the current location in the sorted list
            int index = 0;
            for (int i = 0; i < nearbyShops.size(); i++) {
                Location shopLoc = nearbyShops.get(i).getLocation();
                if (shopLoc.getBlockX() == chestLoc.getBlockX() &&
                    shopLoc.getBlockY() == chestLoc.getBlockY() &&
                    shopLoc.getBlockZ() == chestLoc.getBlockZ()) {
                    index = i;
                    break;
                }
            }

            // Offset by 0.35 blocks per nearby shop
            verticalOffset = index * 0.35;
        }

        return chestLoc.clone().add(0.5, baseHeight + verticalOffset, 0.5);
    }

    /**
     * Get nearby shops within a certain radius.
     */
    private List<Shop> getNearbyShops(Location location, double radius) {
        List<Shop> nearby = new ArrayList<>();
        double radiusSquared = radius * radius;

        for (Shop shop : shopRegistry.getAllShops()) {
            Location shopLoc = shop.getLocation();
            if (shopLoc.getWorld().equals(location.getWorld())) {
                double distanceSquared = shopLoc.distanceSquared(location);
                if (distanceSquared <= radiusSquared) {
                    nearby.add(shop);
                }
            }
        }

        return nearby;
    }

    /**
     * Get a simple display name for the item.
     */
    private String getItemDisplayName(Shop shop) {
        if (shop.getItem() == null) {
            return "Empty Shop";
        }
        String materialName = shop.getItem().getType().name();
        // Convert DIAMOND_SWORD to Diamond Sword
        String[] parts = materialName.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return result.toString();
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
}