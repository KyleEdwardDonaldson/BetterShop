package dev.ked.bettershop.ui;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages hologram displays above shops using armor stands.
 */
public class HologramManager {
    private final ConfigManager config;
    private final ShopManager shopManager;

    // Map of shop location -> hologram armor stand
    private final Map<Location, ArmorStand> holograms = new ConcurrentHashMap<>();

    public HologramManager(ConfigManager config, ShopManager shopManager) {
        this.config = config;
        this.shopManager = shopManager;
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

        // Format for SELL shops: 🛒 Diamond - $10 (5 left)
        // Format for BUY shops: 🛒 Diamond - $10 (Buying)
        Component name = Component.text("🛒 ", NamedTextColor.YELLOW)
                .append(Component.text(itemName, NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text("$" + priceStr, color));

        if (shop.getType() == ShopType.SELL) {
            name = name.append(Component.text(" (" + stock + " left)", NamedTextColor.GRAY));
        } else {
            name = name.append(Component.text(" (Buying)", NamedTextColor.GRAY));
        }

        armorStand.customName(name);
    }

    /**
     * Get the location for the hologram (above the chest).
     */
    private Location getHologramLocation(Location chestLoc) {
        return chestLoc.clone().add(0.5, 1.5, 0.5);
    }

    /**
     * Get a simple display name for the item.
     */
    private String getItemDisplayName(Shop shop) {
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