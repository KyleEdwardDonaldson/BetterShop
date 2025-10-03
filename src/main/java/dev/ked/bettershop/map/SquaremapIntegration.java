package dev.ked.bettershop.map;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.shop.Listing;
import dev.ked.bettershop.shop.ShopRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import xyz.jpenilla.squaremap.api.*;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Squaremap integration for shop markers.
 */
public class SquaremapIntegration extends MapIntegration {
    private static final String MARKER_SET_KEY = "bettershop_listings";
    private static final String MARKER_SET_LABEL = "Shop Listings";

    private Squaremap squaremapAPI;
    private SimpleLayerProvider layerProvider;
    private final Map<UUID, Marker> markers = new HashMap<>();

    public SquaremapIntegration(BetterShopPlugin plugin, ShopRegistry shopRegistry) {
        super(plugin, shopRegistry);
    }

    @Override
    public boolean initialize() {
        try {
            if (Bukkit.getPluginManager().getPlugin("squaremap") == null) {
                return false;
            }

            squaremapAPI = SquaremapProvider.get();
            layerProvider = SimpleLayerProvider.builder(MARKER_SET_LABEL)
                    .showControls(true)
                    .defaultHidden(false)
                    .layerPriority(10)
                    .build();

            // Register layer provider for all worlds
            for (MapWorld world : squaremapAPI.mapWorlds()) {
                world.layerRegistry().register(Key.of(MARKER_SET_KEY), layerProvider);
            }

            enabled = true;
            plugin.getLogger().info("Squaremap integration enabled");

            // Refresh markers
            refreshAllMarkers();

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Squaremap integration failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void addShopMarker(Listing listing) {
        if (!enabled || layerProvider == null || listing.getItem() == null) return;

        try {
            Location loc = listing.getLocation();

            // Create marker point
            Point point = Point.of(loc.getX(), loc.getZ());

            // Get icon key based on listing type
            Key iconKey = listing.getType().name().equals("SELL") ?
                    Key.of("shop_sell") : Key.of("shop_buy");

            // Create marker label
            String label = getItemName(listing) + " - " + listing.getType().name();

            // Create marker options
            MarkerOptions options = MarkerOptions.builder()
                    .hoverTooltip(label)
                    .clickTooltip(getMarkerDescription(listing))
                    .build();

            // Create marker
            Marker marker = Marker.icon(
                    point,
                    iconKey,
                    16, // icon size
                    16
            ).markerOptions(options);

            // Add to layer
            layerProvider.addMarker(Key.of("listing_" + listing.getId().toString()), marker);
            markers.put(listing.getId(), marker);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add Squaremap marker for listing " + listing.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void removeShopMarker(Listing listing) {
        if (!enabled || layerProvider == null) return;

        try {
            markers.remove(listing.getId());
            layerProvider.removeMarker(Key.of("listing_" + listing.getId().toString()));

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove Squaremap marker for listing " + listing.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void clearAllMarkers() {
        if (!enabled || layerProvider == null) return;

        try {
            layerProvider.clearMarkers();
            markers.clear();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clear Squaremap markers: " + e.getMessage());
        }
    }

    @Override
    public String getMapName() {
        return "Squaremap";
    }
}
