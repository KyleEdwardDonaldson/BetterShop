package dev.ked.bazaar.discovery;

import dev.ked.bazaar.shop.Listing;
import dev.ked.bazaar.shop.ListingType;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Comparator;
import java.util.UUID;

/**
 * Filter and sort options for shop searches.
 */
public class ShopSearchFilter {
    private Material itemType;
    private ListingType listingType; // SELL or BUY
    private Double maxPrice;
    private Double minPrice;
    private Integer minStock;
    private Location nearLocation;
    private Double maxDistance;
    private UUID ownerId;
    private String ownerName;
    private Boolean silkRoadOnly = null; // null = all, true = silk road only, false = non-silk road
    private SortOption sortBy = SortOption.PRICE_LOW_TO_HIGH;

    public ShopSearchFilter() {
    }

    // Builder-style setters
    public ShopSearchFilter itemType(Material itemType) {
        this.itemType = itemType;
        return this;
    }

    public ShopSearchFilter listingType(ListingType listingType) {
        this.listingType = listingType;
        return this;
    }

    public ShopSearchFilter priceRange(Double min, Double max) {
        this.minPrice = min;
        this.maxPrice = max;
        return this;
    }

    public ShopSearchFilter minStock(Integer minStock) {
        this.minStock = minStock;
        return this;
    }

    public ShopSearchFilter nearLocation(Location location, Double maxDistance) {
        this.nearLocation = location;
        this.maxDistance = maxDistance;
        return this;
    }

    public ShopSearchFilter ownerId(UUID ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    public ShopSearchFilter ownerName(String ownerName) {
        this.ownerName = ownerName;
        return this;
    }

    public ShopSearchFilter silkRoadOnly(Boolean silkRoadOnly) {
        this.silkRoadOnly = silkRoadOnly;
        return this;
    }

    public ShopSearchFilter sortBy(SortOption sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    /**
     * Check if a listing matches this filter.
     */
    public boolean matches(Listing listing) {
        // Item type filter
        if (itemType != null && listing.getItem().getType() != itemType) {
            return false;
        }

        // Listing type filter (buy/sell)
        if (listingType != null && listing.getType() != listingType) {
            return false;
        }

        // Price range filter
        if (minPrice != null && listing.getPrice() < minPrice) {
            return false;
        }
        if (maxPrice != null && listing.getPrice() > maxPrice) {
            return false;
        }

        // Stock filter (only for SELL listings)
        if (minStock != null && listing.getType() == ListingType.SELL) {
            if (listing.getStock() < minStock) {
                return false;
            }
        }

        // Distance filter
        if (nearLocation != null && maxDistance != null) {
            if (!listing.getLocation().getWorld().equals(nearLocation.getWorld())) {
                return false;
            }
            double distance = listing.getLocation().distance(nearLocation);
            if (distance > maxDistance) {
                return false;
            }
        }

        // Owner filter
        if (ownerId != null && !listing.getOwner().equals(ownerId)) {
            return false;
        }
        // Note: ownerName filter would require BetterShop to store player names

        return true;
    }

    /**
     * Get comparator for sorting.
     */
    public Comparator<Listing> getComparator() {
        return switch (sortBy) {
            case PRICE_LOW_TO_HIGH -> Comparator.comparingDouble(Listing::getPrice);
            case PRICE_HIGH_TO_LOW -> Comparator.comparingDouble(Listing::getPrice).reversed();
            case STOCK_HIGH_TO_LOW -> Comparator.comparingInt(Listing::getStock).reversed();
            case STOCK_LOW_TO_HIGH -> Comparator.comparingInt(Listing::getStock);
            case DISTANCE_NEAR_TO_FAR -> {
                if (nearLocation == null) {
                    yield Comparator.comparing(l -> l.getItem().getType().name());
                }
                yield Comparator.comparingDouble(listing ->
                        listing.getLocation().distance(nearLocation));
            }
        };
    }

    public enum SortOption {
        PRICE_LOW_TO_HIGH("Price: Low → High"),
        PRICE_HIGH_TO_LOW("Price: High → Low"),
        STOCK_HIGH_TO_LOW("Stock: High → Low"),
        STOCK_LOW_TO_HIGH("Stock: Low → High"),
        DISTANCE_NEAR_TO_FAR("Distance: Near → Far");

        private final String displayName;

        SortOption(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Getters
    public Boolean getSilkRoadOnly() {
        return silkRoadOnly;
    }

    public SortOption getSortBy() {
        return sortBy;
    }
}
