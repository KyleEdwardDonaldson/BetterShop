package dev.ked.bettershop.shop;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking active shops with efficient lookup by location and chunk.
 */
public class ShopRegistry {
    // Map of chunk key -> list of shops in that chunk
    private final Map<String, List<Shop>> shopsByChunk = new ConcurrentHashMap<>();

    // Map of exact location -> shop for O(1) lookups
    private final Map<String, Shop> shopsByLocation = new ConcurrentHashMap<>();

    // Map of owner UUID -> list of their shops
    private final Map<UUID, List<Shop>> shopsByOwner = new ConcurrentHashMap<>();

    /**
     * Register a new shop in the registry.
     */
    public void registerShop(Shop shop) {
        String locationKey = getLocationKey(shop.getLocation());
        String chunkKey = getChunkKey(shop.getLocation());

        shopsByLocation.put(locationKey, shop);
        shopsByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(shop);
        shopsByOwner.computeIfAbsent(shop.getOwner(), k -> new ArrayList<>()).add(shop);
    }

    /**
     * Unregister a shop from the registry.
     */
    public void unregisterShop(Shop shop) {
        String locationKey = getLocationKey(shop.getLocation());
        String chunkKey = getChunkKey(shop.getLocation());

        shopsByLocation.remove(locationKey);

        List<Shop> chunkShops = shopsByChunk.get(chunkKey);
        if (chunkShops != null) {
            chunkShops.remove(shop);
            if (chunkShops.isEmpty()) {
                shopsByChunk.remove(chunkKey);
            }
        }

        List<Shop> ownerShops = shopsByOwner.get(shop.getOwner());
        if (ownerShops != null) {
            ownerShops.remove(shop);
            if (ownerShops.isEmpty()) {
                shopsByOwner.remove(shop.getOwner());
            }
        }
    }

    /**
     * Get a shop at a specific location.
     */
    public Optional<Shop> getShopAt(Location location) {
        return Optional.ofNullable(shopsByLocation.get(getLocationKey(location)));
    }

    /**
     * Get all shops in a chunk.
     */
    public List<Shop> getShopsInChunk(Chunk chunk) {
        return shopsByChunk.getOrDefault(getChunkKey(chunk), Collections.emptyList());
    }

    /**
     * Get all shops owned by a player.
     */
    public List<Shop> getShopsByOwner(UUID owner) {
        return new ArrayList<>(shopsByOwner.getOrDefault(owner, Collections.emptyList()));
    }

    /**
     * Get all registered shops.
     */
    public Collection<Shop> getAllShops() {
        return shopsByLocation.values();
    }

    /**
     * Clear all shops from the registry.
     */
    public void clear() {
        shopsByChunk.clear();
        shopsByLocation.clear();
        shopsByOwner.clear();
    }

    /**
     * Get the number of shops owned by a player.
     */
    public int getShopCount(UUID owner) {
        return shopsByOwner.getOrDefault(owner, Collections.emptyList()).size();
    }

    private String getLocationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}