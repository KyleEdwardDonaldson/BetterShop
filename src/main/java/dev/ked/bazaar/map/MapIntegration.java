package dev.ked.bazaar.map;

import dev.ked.bazaar.BazaarPlugin;
import dev.ked.bazaar.shop.Listing;
import dev.ked.bazaar.shop.ShopRegistry;
import org.bukkit.Bukkit;

/**
 * Abstract base class for map integrations (BlueMap, Dynmap, Squaremap).
 * Displays all BetterShop listings on dynamic maps.
 */
public abstract class MapIntegration {
    protected final BazaarPlugin plugin;
    protected final ShopRegistry shopRegistry;
    protected boolean enabled = false;

    public MapIntegration(BazaarPlugin plugin, ShopRegistry shopRegistry) {
        this.plugin = plugin;
        this.shopRegistry = shopRegistry;
    }

    /**
     * Initialize the integration.
     * @return true if successfully initialized
     */
    public abstract boolean initialize();

    /**
     * Add a shop marker to the map.
     */
    public abstract void addShopMarker(Listing listing);

    /**
     * Remove a shop marker from the map.
     */
    public abstract void removeShopMarker(Listing listing);

    /**
     * Update a shop marker (remove and re-add).
     */
    public void updateShopMarker(Listing listing) {
        removeShopMarker(listing);
        addShopMarker(listing);
    }

    /**
     * Refresh all shop markers.
     */
    public void refreshAllMarkers() {
        clearAllMarkers();
        for (Listing listing : shopRegistry.getAllListings()) {
            if (listing.getItem() != null) { // Skip empty listings
                addShopMarker(listing);
            }
        }
        plugin.getLogger().info(getMapName() + ": Refreshed " + shopRegistry.getAllListings().size() + " shop markers");
    }

    /**
     * Clear all shop markers.
     */
    public abstract void clearAllMarkers();

    /**
     * Get the name of this map integration.
     */
    public abstract String getMapName();

    /**
     * Check if this integration is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get icon name based on listing type.
     */
    protected String getIconName(Listing listing) {
        return listing.getType().name().toLowerCase() + "_shop";
    }

    /**
     * Get marker color based on listing type.
     */
    protected int getMarkerColor(Listing listing) {
        // SELL listings = Green (0x00FF00), BUY listings = Blue (0x0000FF)
        return listing.getType().name().equals("SELL") ? 0x00FF00 : 0x0000FF;
    }

    /**
     * Get owner name from UUID.
     */
    protected String getOwnerName(Listing listing) {
        return Bukkit.getOfflinePlayer(listing.getOwner()).getName();
    }

    /**
     * Format item name for display.
     */
    protected String getItemName(Listing listing) {
        if (listing.isMythicItem()) {
            return listing.getMythicItemId();
        }
        if (listing.getItem() == null) {
            return "Unknown";
        }
        String name = listing.getItem().getType().name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Format the marker description.
     */
    protected String getMarkerDescription(Listing listing) {
        StringBuilder desc = new StringBuilder();

        String typeName = listing.getType().name().equals("SELL") ? "Selling" : "Buying";
        desc.append("<b>").append(typeName).append(": ").append(getItemName(listing)).append("</b><br>");
        desc.append("Price: <span style='color:gold'>$").append(String.format("%.2f", listing.getPrice())).append("</span><br>");

        // Show stock for SELL listings
        if (listing.getType().name().equals("SELL")) {
            int totalReserved = listing.getTotalReservedStock();
            if (totalReserved > 0) {
                desc.append("<span style='color:orange'>").append(totalReserved).append(" in transit</span><br>");
            }
        }

        // Show Silk Road status
        if (listing.isSilkRoadEnabled()) {
            desc.append("<span style='color:gold'>⭐ Silk Road Enabled</span><br>");
        }

        desc.append("Owner: ").append(getOwnerName(listing)).append("<br>");
        desc.append("Location: ").append(listing.getLocation().getBlockX()).append(", ")
            .append(listing.getLocation().getBlockY()).append(", ")
            .append(listing.getLocation().getBlockZ());

        return desc.toString();
    }
}
