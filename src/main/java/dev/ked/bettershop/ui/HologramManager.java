package dev.ked.bettershop.ui;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.integration.MythicItemHandler;
import dev.ked.bettershop.shop.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages hologram displays above listings using armor stands.
 */
public class HologramManager {
    private final ConfigManager config;
    private final ShopRegistry registry;
    private MythicItemHandler mythicItemHandler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Map of listing location -> hologram armor stand
    private final Map<Location, ArmorStand> holograms = new ConcurrentHashMap<>();

    public HologramManager(ConfigManager config, ShopRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    public void setMythicItemHandler(MythicItemHandler mythicItemHandler) {
        this.mythicItemHandler = mythicItemHandler;
    }

    /**
     * Create a hologram for a listing.
     */
    public void createHologram(Listing listing) {
        if (!config.areHologramsEnabled()) {
            return;
        }

        // Remove existing hologram if present
        removeHologram(listing);

        Location hologramLoc = getHologramLocation(listing.getLocation());
        ArmorStand armorStand = (ArmorStand) hologramLoc.getWorld().spawnEntity(hologramLoc, EntityType.ARMOR_STAND);

        // Configure armor stand
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setMarker(true);
        armorStand.setCustomNameVisible(true);
        armorStand.setInvulnerable(true);
        armorStand.setPersistent(true);

        // Set custom name
        updateHologramText(armorStand, listing);

        // Store reference
        holograms.put(listing.getLocation(), armorStand);
    }

    /**
     * Update hologram text for a listing.
     */
    public void updateHologram(Listing listing) {
        ArmorStand armorStand = holograms.get(listing.getLocation());
        if (armorStand != null && armorStand.isValid()) {
            updateHologramText(armorStand, listing);
        } else {
            createHologram(listing);
        }
    }

    /**
     * Remove a hologram for a listing.
     */
    public void removeHologram(Listing listing) {
        ArmorStand armorStand = holograms.remove(listing.getLocation());
        if (armorStand != null && armorStand.isValid()) {
            armorStand.remove();
        }
    }

    /**
     * Remove all holograms.
     */
    public void removeAllHolograms() {
        for (ArmorStand armorStand : holograms.values()) {
            if (armorStand.isValid()) {
                armorStand.remove();
            }
        }
        holograms.clear();
    }

    /**
     * Update hologram text based on listing data.
     */
    private void updateHologramText(ArmorStand armorStand, Listing listing) {
        // Get shop name
        String shopName = "Unknown Shop";
        Optional<ShopEntity> shopOpt = registry.getShopById(listing.getShopId());
        if (shopOpt.isPresent()) {
            shopName = shopOpt.get().getName();
        }

        // Get stock
        int stock = getStock(listing);

        // Get item name
        String itemName = getItemDisplayName(listing);
        NamedTextColor typeColor = listing.getType() == ListingType.SELL ? NamedTextColor.GREEN : NamedTextColor.BLUE;

        Component hologramText = Component.text("🛒 ", NamedTextColor.GOLD)
                .append(Component.text(shopName, NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(itemName, typeColor))
                .append(Component.text(" $", NamedTextColor.GOLD))
                .append(Component.text(String.format("%.0f", listing.getPrice()), NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(stock, NamedTextColor.WHITE))
                .append(Component.text(")", NamedTextColor.GRAY));

        armorStand.customName(hologramText);
    }

    /**
     * Get display name for an item (handles both vanilla and mythic items).
     */
    private String getItemDisplayName(Listing listing) {
        if (listing.isMythicItem() && mythicItemHandler != null) {
            ItemStack mythicItem = mythicItemHandler.getMythicItem(listing.getMythicItemId(), 1);
            if (mythicItem != null && mythicItem.getItemMeta().hasDisplayName()) {
                // Remove formatting codes for hologram display
                Component displayName = mythicItem.getItemMeta().displayName();
                if (displayName != null) {
                    return miniMessage.stripTags(miniMessage.serialize(displayName));
                }
            }
            return listing.getMythicItemId();
        } else if (listing.getItem() != null) {
            return listing.getItem().getType().name().toLowerCase().replace('_', ' ');
        }
        return "unknown";
    }

    /**
     * Get stock for a listing.
     */
    private int getStock(Listing listing) {
        org.bukkit.block.Block block = listing.getLocation().getBlock();
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
     * Get the location for a hologram above a chest.
     */
    private Location getHologramLocation(Location chestLocation) {
        return chestLocation.clone().add(0.5, 1.5, 0.5);
    }

    /**
     * Clean up invalid holograms.
     */
    public void cleanupInvalidHolograms() {
        holograms.entrySet().removeIf(entry -> {
            ArmorStand stand = entry.getValue();
            return stand == null || !stand.isValid();
        });
    }
}
