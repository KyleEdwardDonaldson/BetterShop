package dev.ked.bettershop.shop;

import java.util.*;

/**
 * Represents a partnership where multiple players share revenue from a shop.
 */
public class ShopPartnership {
    private final UUID shopId;
    private final UUID primaryOwner;
    private final Map<UUID, Double> partners; // Partner UUID -> revenue share percentage (0.0-1.0)

    public ShopPartnership(UUID shopId, UUID primaryOwner) {
        this.shopId = shopId;
        this.primaryOwner = primaryOwner;
        this.partners = new HashMap<>();
    }

    /**
     * Add a partner with a revenue share percentage.
     * @param partnerId Partner's UUID
     * @param sharePercentage Percentage of revenue (0.0-1.0, e.g., 0.30 = 30%)
     * @return true if added successfully
     */
    public boolean addPartner(UUID partnerId, double sharePercentage) {
        if (partnerId.equals(primaryOwner)) {
            return false; // Cannot add owner as partner
        }

        if (sharePercentage <= 0.0 || sharePercentage >= 1.0) {
            return false; // Invalid percentage
        }

        // Check if total share would exceed 100%
        double totalShare = getTotalPartnerShare() + sharePercentage;
        if (totalShare >= 1.0) {
            return false; // Would leave owner with 0% or less
        }

        partners.put(partnerId, sharePercentage);
        return true;
    }

    /**
     * Remove a partner.
     */
    public boolean removePartner(UUID partnerId) {
        return partners.remove(partnerId) != null;
    }

    /**
     * Update a partner's revenue share.
     */
    public boolean updatePartnerShare(UUID partnerId, double newSharePercentage) {
        if (!partners.containsKey(partnerId)) {
            return false;
        }

        if (newSharePercentage <= 0.0 || newSharePercentage >= 1.0) {
            return false;
        }

        // Check if total share would exceed 100% (excluding current partner's share)
        double totalShareWithoutPartner = getTotalPartnerShare() - partners.get(partnerId);
        if (totalShareWithoutPartner + newSharePercentage >= 1.0) {
            return false;
        }

        partners.put(partnerId, newSharePercentage);
        return true;
    }

    /**
     * Get the total percentage shared with partners.
     */
    public double getTotalPartnerShare() {
        return partners.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Get the owner's revenue share percentage.
     */
    public double getOwnerShare() {
        return 1.0 - getTotalPartnerShare();
    }

    /**
     * Calculate revenue distribution for a given amount.
     * @param totalEarnings Total earnings to distribute
     * @return Map of UUID -> earnings amount
     */
    public Map<UUID, Double> distributeEarnings(double totalEarnings) {
        Map<UUID, Double> distribution = new HashMap<>();

        // Distribute to partners
        for (Map.Entry<UUID, Double> entry : partners.entrySet()) {
            double partnerEarnings = totalEarnings * entry.getValue();
            distribution.put(entry.getKey(), partnerEarnings);
        }

        // Remaining goes to owner
        double ownerEarnings = totalEarnings * getOwnerShare();
        distribution.put(primaryOwner, ownerEarnings);

        return distribution;
    }

    /**
     * Check if a player is a partner.
     */
    public boolean isPartner(UUID playerId) {
        return partners.containsKey(playerId) || playerId.equals(primaryOwner);
    }

    /**
     * Get a partner's share percentage.
     */
    public double getPartnerShare(UUID partnerId) {
        if (partnerId.equals(primaryOwner)) {
            return getOwnerShare();
        }
        return partners.getOrDefault(partnerId, 0.0);
    }

    // Getters
    public UUID getShopId() {
        return shopId;
    }

    public UUID getPrimaryOwner() {
        return primaryOwner;
    }

    public Map<UUID, Double> getPartners() {
        return new HashMap<>(partners); // Return copy
    }

    public int getPartnerCount() {
        return partners.size();
    }

    public boolean hasPartners() {
        return !partners.isEmpty();
    }
}
