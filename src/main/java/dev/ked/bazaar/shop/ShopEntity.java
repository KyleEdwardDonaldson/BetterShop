package dev.ked.bazaar.shop;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a shop entity that contains multiple listings.
 * A shop is a player's business that can have many individual item listings.
 */
public class ShopEntity {
    private final UUID id;
    private final UUID owner;
    private String name;
    private final List<UUID> listingIds;
    private String territoryId; // Towny/TaN region/plot identifier
    private final Location creationLocation; // Reference location for territory
    private final long createdAt;

    public ShopEntity(UUID id, UUID owner, String name, Location creationLocation) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.listingIds = new ArrayList<>();
        this.creationLocation = creationLocation.clone();
        this.territoryId = null;
        this.createdAt = System.currentTimeMillis();
    }

    public ShopEntity(UUID id, UUID owner, String name, Location creationLocation, String territoryId, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.listingIds = new ArrayList<>();
        this.creationLocation = creationLocation.clone();
        this.territoryId = territoryId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<UUID> getListingIds() {
        return new ArrayList<>(listingIds);
    }

    public void addListing(UUID listingId) {
        if (!listingIds.contains(listingId)) {
            listingIds.add(listingId);
        }
    }

    public void removeListing(UUID listingId) {
        listingIds.remove(listingId);
    }

    public int getListingCount() {
        return listingIds.size();
    }

    public String getTerritoryId() {
        return territoryId;
    }

    public void setTerritoryId(String territoryId) {
        this.territoryId = territoryId;
    }

    public Location getCreationLocation() {
        return creationLocation.clone();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Calculate total earnings across all listings in this shop.
     * Note: This requires fetching all listings from the registry.
     */
    public double getTotalEarnings(List<Listing> listings) {
        return listings.stream()
                .filter(listing -> listingIds.contains(listing.getId()))
                .mapToDouble(Listing::getEarnings)
                .sum();
    }

    @Override
    public String toString() {
        return "ShopEntity{" +
                "id=" + id +
                ", owner=" + owner +
                ", name='" + name + '\'' +
                ", listingCount=" + listingIds.size() +
                ", territoryId='" + territoryId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
