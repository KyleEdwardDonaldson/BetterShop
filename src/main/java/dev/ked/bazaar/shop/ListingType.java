package dev.ked.bazaar.shop;

/**
 * Represents the type of listing (buy or sell).
 * SELL = shop owner sells items TO players (players buy from shop)
 * BUY = shop owner buys items FROM players (players sell to shop)
 */
public enum ListingType {
    /**
     * Players buy items from this listing (shop sells TO players)
     */
    SELL,

    /**
     * Players sell items to this listing (shop buys FROM players)
     */
    BUY
}
