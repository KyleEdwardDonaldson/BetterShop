package dev.ked.bettershop.map;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.shop.Listing;
import dev.ked.bettershop.shop.ShopRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all map integrations (BlueMap, Dynmap, Squaremap).
 * Displays shop listings on dynamic maps.
 */
public class MapManager {
    private final BetterShopPlugin plugin;
    private final ShopRegistry shopRegistry;
    private final List<MapIntegration> integrations = new ArrayList<>();

    public MapManager(BetterShopPlugin plugin, ShopRegistry shopRegistry) {
        this.plugin = plugin;
        this.shopRegistry = shopRegistry;
    }

    /**
     * Initialize all available map integrations.
     */
    public void initializeIntegrations() {
        plugin.getLogger().info("Initializing map integrations...");

        // Try BlueMap
        BlueMapIntegration blueMap = new BlueMapIntegration(plugin, shopRegistry);
        if (blueMap.initialize()) {
            integrations.add(blueMap);
            plugin.getLogger().info("✓ BlueMap integration enabled");
        }

        // Try Dynmap
        DynmapIntegration dynmap = new DynmapIntegration(plugin, shopRegistry);
        if (dynmap.initialize()) {
            integrations.add(dynmap);
            plugin.getLogger().info("✓ Dynmap integration enabled");
        }

        // Try Squaremap
        SquaremapIntegration squaremap = new SquaremapIntegration(plugin, shopRegistry);
        if (squaremap.initialize()) {
            integrations.add(squaremap);
            plugin.getLogger().info("✓ Squaremap integration enabled");
        }

        if (integrations.isEmpty()) {
            plugin.getLogger().warning("No map plugins found - shop markers will not be displayed");
        } else {
            plugin.getLogger().info("Map integrations: " + integrations.size() + " active");
        }
    }

    /**
     * Add a shop marker to all enabled maps.
     */
    public void addShopMarker(Listing listing) {
        for (MapIntegration integration : integrations) {
            if (integration.isEnabled()) {
                integration.addShopMarker(listing);
            }
        }
    }

    /**
     * Remove a shop marker from all enabled maps.
     */
    public void removeShopMarker(Listing listing) {
        for (MapIntegration integration : integrations) {
            if (integration.isEnabled()) {
                integration.removeShopMarker(listing);
            }
        }
    }

    /**
     * Update a shop marker on all enabled maps.
     */
    public void updateShopMarker(Listing listing) {
        for (MapIntegration integration : integrations) {
            if (integration.isEnabled()) {
                integration.updateShopMarker(listing);
            }
        }
    }

    /**
     * Refresh all shop markers on all maps.
     */
    public void refreshAllMarkers() {
        plugin.getLogger().info("Refreshing all shop markers...");
        for (MapIntegration integration : integrations) {
            if (integration.isEnabled()) {
                integration.refreshAllMarkers();
            }
        }
    }

    /**
     * Clear all shop markers from all maps.
     */
    public void clearAllMarkers() {
        for (MapIntegration integration : integrations) {
            if (integration.isEnabled()) {
                integration.clearAllMarkers();
            }
        }
    }

    /**
     * Get list of enabled map integrations.
     */
    public List<String> getEnabledMaps() {
        List<String> enabled = new ArrayList<>();
        for (MapIntegration integration : integrations) {
            if (integration.isEnabled()) {
                enabled.add(integration.getMapName());
            }
        }
        return enabled;
    }

    /**
     * Check if any map integration is enabled.
     */
    public boolean hasAnyEnabled() {
        return !integrations.isEmpty() && integrations.stream().anyMatch(MapIntegration::isEnabled);
    }
}
