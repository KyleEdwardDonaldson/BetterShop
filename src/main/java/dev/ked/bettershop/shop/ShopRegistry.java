package dev.ked.bettershop.shop;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking active shops and listings with efficient lookup.
 * Manages both ShopEntity (shop business) and Listing (individual chests).
 */
public class ShopRegistry {
    // ===== SHOP ENTITY TRACKING =====
    // Map of shop UUID -> ShopEntity
    private final Map<UUID, ShopEntity> shopsById = new ConcurrentHashMap<>();

    // Map of owner UUID -> (shop name -> shop UUID) for quick name lookups
    private final Map<UUID, Map<String, UUID>> shopsByOwnerAndName = new ConcurrentHashMap<>();

    // Map of owner UUID -> list of shop UUIDs
    private final Map<UUID, List<UUID>> shopIdsByOwner = new ConcurrentHashMap<>();

    // ===== LISTING TRACKING =====
    // Map of listing UUID -> Listing
    private final Map<UUID, Listing> listingsById = new ConcurrentHashMap<>();

    // Map of shop UUID -> list of listing UUIDs
    private final Map<UUID, List<UUID>> listingIdsByShop = new ConcurrentHashMap<>();

    // Map of chunk key -> list of listings in that chunk
    private final Map<String, List<Listing>> listingsByChunk = new ConcurrentHashMap<>();

    // Map of exact location -> listing for O(1) lookups
    private final Map<String, Listing> listingsByLocation = new ConcurrentHashMap<>();

    // Map of owner UUID -> list of their listings (for backward compatibility)
    private final Map<UUID, List<Listing>> listingsByOwner = new ConcurrentHashMap<>();

    // ===== SHOP ENTITY METHODS =====

    /**
     * Register a new shop entity in the registry.
     */
    public void registerShop(ShopEntity shop) {
        shopsById.put(shop.getId(), shop);
        shopIdsByOwner.computeIfAbsent(shop.getOwner(), k -> new ArrayList<>()).add(shop.getId());
        shopsByOwnerAndName.computeIfAbsent(shop.getOwner(), k -> new HashMap<>())
                .put(shop.getName().toLowerCase(), shop.getId());
    }

    /**
     * Unregister a shop entity from the registry.
     */
    public void unregisterShop(UUID shopId) {
        ShopEntity shop = shopsById.remove(shopId);
        if (shop == null) {
            return;
        }

        List<UUID> ownerShops = shopIdsByOwner.get(shop.getOwner());
        if (ownerShops != null) {
            ownerShops.remove(shopId);
            if (ownerShops.isEmpty()) {
                shopIdsByOwner.remove(shop.getOwner());
            }
        }

        Map<String, UUID> ownerNames = shopsByOwnerAndName.get(shop.getOwner());
        if (ownerNames != null) {
            ownerNames.remove(shop.getName().toLowerCase());
            if (ownerNames.isEmpty()) {
                shopsByOwnerAndName.remove(shop.getOwner());
            }
        }

        // Also remove all associated listings
        List<UUID> listingIds = new ArrayList<>(shop.getListingIds());
        for (UUID listingId : listingIds) {
            unregisterListing(listingId);
        }
    }

    /**
     * Get a shop entity by its UUID.
     */
    public Optional<ShopEntity> getShopById(UUID shopId) {
        return Optional.ofNullable(shopsById.get(shopId));
    }

    /**
     * Get a shop entity by owner and name.
     */
    public Optional<ShopEntity> getShopByOwnerAndName(UUID owner, String name) {
        Map<String, UUID> ownerShops = shopsByOwnerAndName.get(owner);
        if (ownerShops == null) {
            return Optional.empty();
        }
        UUID shopId = ownerShops.get(name.toLowerCase());
        return Optional.ofNullable(shopsById.get(shopId));
    }

    /**
     * Get all shop entities owned by a player.
     */
    public List<ShopEntity> getShopsByOwner(UUID owner) {
        List<UUID> shopIds = shopIdsByOwner.getOrDefault(owner, Collections.emptyList());
        List<ShopEntity> shops = new ArrayList<>();
        for (UUID shopId : shopIds) {
            ShopEntity shop = shopsById.get(shopId);
            if (shop != null) {
                shops.add(shop);
            }
        }
        return shops;
    }

    /**
     * Get all registered shop entities.
     */
    public Collection<ShopEntity> getAllShops() {
        return new ArrayList<>(shopsById.values());
    }

    /**
     * Get the number of shops owned by a player.
     */
    public int getShopCount(UUID owner) {
        return shopIdsByOwner.getOrDefault(owner, Collections.emptyList()).size();
    }

    /**
     * Check if a shop name is already taken by this owner.
     */
    public boolean isShopNameTaken(UUID owner, String name) {
        Map<String, UUID> ownerShops = shopsByOwnerAndName.get(owner);
        return ownerShops != null && ownerShops.containsKey(name.toLowerCase());
    }

    // ===== LISTING METHODS =====

    /**
     * Register a new listing in the registry.
     */
    public void registerListing(Listing listing) {
        String locationKey = getLocationKey(listing.getLocation());
        String chunkKey = getChunkKey(listing.getLocation());

        listingsById.put(listing.getId(), listing);
        listingsByLocation.put(locationKey, listing);
        listingsByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(listing);
        listingsByOwner.computeIfAbsent(listing.getOwner(), k -> new ArrayList<>()).add(listing);
        listingIdsByShop.computeIfAbsent(listing.getShopId(), k -> new ArrayList<>()).add(listing.getId());

        // Update shop entity's listing list
        ShopEntity shop = shopsById.get(listing.getShopId());
        if (shop != null) {
            shop.addListing(listing.getId());
        }
    }

    /**
     * Unregister a listing from the registry.
     */
    public void unregisterListing(UUID listingId) {
        Listing listing = listingsById.remove(listingId);
        if (listing == null) {
            return;
        }

        String locationKey = getLocationKey(listing.getLocation());
        String chunkKey = getChunkKey(listing.getLocation());

        listingsByLocation.remove(locationKey);

        List<Listing> chunkListings = listingsByChunk.get(chunkKey);
        if (chunkListings != null) {
            chunkListings.remove(listing);
            if (chunkListings.isEmpty()) {
                listingsByChunk.remove(chunkKey);
            }
        }

        List<Listing> ownerListings = listingsByOwner.get(listing.getOwner());
        if (ownerListings != null) {
            ownerListings.remove(listing);
            if (ownerListings.isEmpty()) {
                listingsByOwner.remove(listing.getOwner());
            }
        }

        List<UUID> shopListings = listingIdsByShop.get(listing.getShopId());
        if (shopListings != null) {
            shopListings.remove(listingId);
            if (shopListings.isEmpty()) {
                listingIdsByShop.remove(listing.getShopId());
            }
        }

        // Update shop entity's listing list
        ShopEntity shop = shopsById.get(listing.getShopId());
        if (shop != null) {
            shop.removeListing(listingId);
        }
    }

    /**
     * Get a listing by its UUID.
     */
    public Optional<Listing> getListingById(UUID listingId) {
        return Optional.ofNullable(listingsById.get(listingId));
    }

    /**
     * Get a listing at a specific location.
     */
    public Optional<Listing> getListingAt(Location location) {
        return Optional.ofNullable(listingsByLocation.get(getLocationKey(location)));
    }

    /**
     * Get all listings in a chunk.
     */
    public List<Listing> getListingsInChunk(Chunk chunk) {
        return new ArrayList<>(listingsByChunk.getOrDefault(getChunkKey(chunk), Collections.emptyList()));
    }

    /**
     * Get all listings owned by a player.
     */
    public List<Listing> getListingsByOwner(UUID owner) {
        return new ArrayList<>(listingsByOwner.getOrDefault(owner, Collections.emptyList()));
    }

    /**
     * Get all listings in a shop.
     */
    public List<Listing> getListingsByShop(UUID shopId) {
        List<UUID> listingIds = listingIdsByShop.getOrDefault(shopId, Collections.emptyList());
        List<Listing> listings = new ArrayList<>();
        for (UUID listingId : listingIds) {
            Listing listing = listingsById.get(listingId);
            if (listing != null) {
                listings.add(listing);
            }
        }
        return listings;
    }

    /**
     * Get all registered listings.
     */
    public Collection<Listing> getAllListings() {
        return new ArrayList<>(listingsById.values());
    }

    /**
     * Get the number of listings in a shop.
     */
    public int getListingCount(UUID shopId) {
        return listingIdsByShop.getOrDefault(shopId, Collections.emptyList()).size();
    }

    // ===== BACKWARD COMPATIBILITY METHODS =====

    /**
     * @deprecated Use {@link #getListingAt(Location)} instead.
     */
    @Deprecated
    public Optional<Shop> getShopAt(Location location) {
        // This method is kept for backward compatibility with existing code
        // It will be phased out after all code is updated
        return Optional.empty(); // Will be handled during full migration
    }

    /**
     * @deprecated Use {@link #getListingsInChunk(Chunk)} instead.
     */
    @Deprecated
    public List<Shop> getShopsInChunk(Chunk chunk) {
        return Collections.emptyList(); // Will be handled during full migration
    }

    /**
     * Clear all shops and listings from the registry.
     */
    public void clear() {
        shopsById.clear();
        shopIdsByOwner.clear();
        shopsByOwnerAndName.clear();
        listingsById.clear();
        listingIdsByShop.clear();
        listingsByChunk.clear();
        listingsByLocation.clear();
        listingsByOwner.clear();
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