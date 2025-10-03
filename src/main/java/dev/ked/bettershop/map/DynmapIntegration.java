package dev.ked.bettershop.map;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.shop.Listing;
import dev.ked.bettershop.shop.ShopRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dynmap integration for shop markers.
 */
public class DynmapIntegration extends MapIntegration {
    private static final String MARKER_SET_ID = "bettershop.listings";
    private static final String MARKER_SET_LABEL = "Shop Listings";

    private DynmapCommonAPI dynmapAPI;
    private MarkerAPI markerAPI;
    private MarkerSet markerSet;
    private MarkerIcon sellIcon;
    private MarkerIcon buyIcon;
    private final Map<UUID, Marker> markers = new HashMap<>();

    public DynmapIntegration(BetterShopPlugin plugin, ShopRegistry shopRegistry) {
        super(plugin, shopRegistry);
    }

    @Override
    public boolean initialize() {
        try {
            Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
            if (dynmap == null || !dynmap.isEnabled()) {
                return false;
            }

            dynmapAPI = (DynmapCommonAPI) dynmap;
            markerAPI = dynmapAPI.getMarkerAPI();

            if (markerAPI == null) {
                plugin.getLogger().warning("Dynmap MarkerAPI not available");
                return false;
            }

            // Create or get marker set
            markerSet = markerAPI.getMarkerSet(MARKER_SET_ID);
            if (markerSet == null) {
                markerSet = markerAPI.createMarkerSet(MARKER_SET_ID, MARKER_SET_LABEL, null, false);
            }
            markerSet.setHideByDefault(false);
            markerSet.setLayerPriority(10);

            // Get or create custom icons
            sellIcon = markerAPI.getMarkerIcon("shop_sell");
            if (sellIcon == null) {
                sellIcon = markerAPI.getMarkerIcon("cart"); // Fallback to default icon
            }

            buyIcon = markerAPI.getMarkerIcon("shop_buy");
            if (buyIcon == null) {
                buyIcon = markerAPI.getMarkerIcon("coins"); // Fallback to default icon
            }

            enabled = true;
            plugin.getLogger().info("Dynmap integration enabled");

            // Refresh markers
            refreshAllMarkers();

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Dynmap integration failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void addShopMarker(Listing listing) {
        if (!enabled || markerSet == null || listing.getItem() == null) return;

        try {
            Location loc = listing.getLocation();
            String markerId = "listing_" + listing.getId().toString();

            // Determine icon
            MarkerIcon icon = listing.getType().name().equals("SELL") ? sellIcon : buyIcon;

            // Create marker label
            String label = getItemName(listing) + " - " + listing.getType().name();

            // Create marker
            Marker marker = markerSet.createMarker(
                    markerId,
                    label,
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    icon,
                    false // not persistent (we manage it)
            );

            if (marker != null) {
                marker.setDescription(getMarkerDescription(listing));
                marker.setLabel(label, true);
                markers.put(listing.getId(), marker);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add Dynmap marker for listing " + listing.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void removeShopMarker(Listing listing) {
        if (!enabled) return;

        try {
            Marker marker = markers.remove(listing.getId());
            if (marker != null) {
                marker.deleteMarker();
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove Dynmap marker for listing " + listing.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void clearAllMarkers() {
        if (!enabled || markerSet == null) return;

        try {
            for (Marker marker : markers.values()) {
                marker.deleteMarker();
            }
            markers.clear();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clear Dynmap markers: " + e.getMessage());
        }
    }

    @Override
    public String getMapName() {
        return "Dynmap";
    }
}
