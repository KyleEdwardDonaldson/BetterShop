package dev.ked.bettershop.map;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.shop.Listing;
import dev.ked.bettershop.shop.ShopRegistry;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BlueMap integration for shop markers.
 */
public class BlueMapIntegration extends MapIntegration {
    private static final String MARKER_SET_ID = "bettershop_listings";
    private static final String MARKER_SET_LABEL = "Shop Listings";

    private BlueMapAPI blueMapAPI;
    private final Map<UUID, String> markerIds = new HashMap<>();

    public BlueMapIntegration(BetterShopPlugin plugin, ShopRegistry shopRegistry) {
        super(plugin, shopRegistry);
    }

    @Override
    public boolean initialize() {
        try {
            BlueMapAPI.onEnable(api -> {
                this.blueMapAPI = api;
                this.enabled = true;
                plugin.getLogger().info("BlueMap integration enabled");

                // Refresh markers on enable
                refreshAllMarkers();
            });

            BlueMapAPI.onDisable(api -> {
                this.enabled = false;
                plugin.getLogger().info("BlueMap integration disabled");
            });

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("BlueMap not found or integration failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void addShopMarker(Listing listing) {
        if (!enabled || blueMapAPI == null || listing.getItem() == null) return;

        try {
            Location loc = listing.getLocation();
            String markerId = "listing_" + listing.getId().toString();

            // Get or create marker set for each map
            for (BlueMapMap map : blueMapAPI.getMaps()) {
                if (!map.getWorld().equals(loc.getWorld())) continue;

                MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(
                        MARKER_SET_ID,
                        id -> MarkerSet.builder()
                                .label(MARKER_SET_LABEL)
                                .toggleable(true)
                                .defaultHidden(false)
                                .build()
                );

                // Create POI marker
                POIMarker marker = POIMarker.builder()
                        .label(getItemName(listing) + " - " + listing.getType().name())
                        .position(loc.getX(), loc.getY(), loc.getZ())
                        .detail(getMarkerDescription(listing))
                        .icon(getIconUrl(listing), 0, 0)
                        .build();

                markerSet.put(markerId, marker);
            }

            markerIds.put(listing.getId(), markerId);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add BlueMap marker for listing " + listing.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void removeShopMarker(Listing listing) {
        if (!enabled || blueMapAPI == null) return;

        try {
            String markerId = markerIds.remove(listing.getId());
            if (markerId == null) return;

            Location loc = listing.getLocation();

            for (BlueMapMap map : blueMapAPI.getMaps()) {
                if (!map.getWorld().equals(loc.getWorld())) continue;

                MarkerSet markerSet = map.getMarkerSets().get(MARKER_SET_ID);
                if (markerSet != null) {
                    markerSet.remove(markerId);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove BlueMap marker for listing " + listing.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void clearAllMarkers() {
        if (!enabled || blueMapAPI == null) return;

        try {
            for (BlueMapMap map : blueMapAPI.getMaps()) {
                map.getMarkerSets().remove(MARKER_SET_ID);
            }
            markerIds.clear();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clear BlueMap markers: " + e.getMessage());
        }
    }

    @Override
    public String getMapName() {
        return "BlueMap";
    }

    private String getIconUrl(Listing listing) {
        // BlueMap can use custom icon URLs or built-in icons
        String iconType = listing.getType().name().toLowerCase();
        String silkRoadSuffix = listing.isSilkRoadEnabled() ? "_sr" : "";
        return "assets/shop_" + iconType + silkRoadSuffix + ".png";
    }
}
