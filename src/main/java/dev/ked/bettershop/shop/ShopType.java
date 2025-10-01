package dev.ked.bettershop.shop;

/**
 * @deprecated Use {@link ListingType} instead. This enum is kept for backward compatibility.
 */
@Deprecated
public enum ShopType {
    /**
     * Players buy items from this shop
     */
    BUY,

    /**
     * Players sell items to this shop
     */
    SELL;

    /**
     * Convert to ListingType for internal use.
     */
    public ListingType toListingType() {
        return ListingType.valueOf(this.name());
    }

    /**
     * Create from ListingType for backward compatibility.
     */
    public static ShopType fromListingType(ListingType type) {
        return ShopType.valueOf(type.name());
    }
}