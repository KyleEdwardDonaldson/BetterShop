package dev.ked.bettershop.notification;

import dev.ked.bettershop.config.ConfigManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages batched notifications for shop owners.
 * Only starts batch timer after first notification is added.
 */
public class NotificationManager {
    private final Plugin plugin;
    private final ConfigManager config;
    private final MiniMessage miniMessage;

    // Batched notifications: Owner UUID -> List of notifications
    private final Map<UUID, List<SaleNotification>> pendingNotifications = new ConcurrentHashMap<>();

    // Active batch task
    private BukkitTask batchTask = null;

    public NotificationManager(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Add a sale notification for a shop owner.
     * Starts batch timer if not already running.
     */
    public void addSaleNotification(UUID ownerId, String shopName, String buyerName,
                                     String itemName, int quantity, double totalPrice) {
        if (!config.isSaleNotificationsEnabled()) {
            return;
        }

        // Check if sale meets minimum threshold
        if (totalPrice < config.getMinNotificationValue()) {
            return;
        }

        SaleNotification notification = new SaleNotification(
            shopName, buyerName, itemName, quantity, totalPrice
        );

        pendingNotifications.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(notification);

        // Start batch task if not already running
        startBatchTaskIfNeeded();
    }

    /**
     * Add a low stock notification for a shop owner.
     */
    public void addLowStockNotification(UUID ownerId, String shopName, String itemName, int currentStock) {
        if (!config.isLowStockAlertsEnabled()) {
            return;
        }

        int threshold = config.getLowStockThreshold();
        if (currentStock > threshold) {
            return;
        }

        StockNotification notification = new StockNotification(shopName, itemName, currentStock);

        // Send immediately (not batched)
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) {
            String message = config.getMessage("notification-low-stock",
                "shop", shopName,
                "item", itemName,
                "stock", String.valueOf(currentStock));
            owner.sendMessage(miniMessage.deserialize(message));

            if (config.isNotificationSoundsEnabled()) {
                owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        }
    }

    /**
     * Add a partnership earnings notification.
     */
    public void addPartnershipNotification(UUID partnerId, String shopName, double earnings, double percentage) {
        if (!config.isPartnershipNotificationsEnabled()) {
            return;
        }

        PartnershipNotification notification = new PartnershipNotification(shopName, earnings, percentage);

        pendingNotifications.computeIfAbsent(partnerId, k -> new ArrayList<>()).add(notification);
        startBatchTaskIfNeeded();
    }

    /**
     * Start the batch task if not already running and there are pending notifications.
     */
    private void startBatchTaskIfNeeded() {
        if (batchTask != null || pendingNotifications.isEmpty()) {
            return;
        }

        long batchInterval = config.getNotificationBatchInterval() * 20L; // Convert seconds to ticks

        batchTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processBatch, batchInterval, batchInterval);
    }

    /**
     * Process and send all batched notifications.
     */
    private void processBatch() {
        if (pendingNotifications.isEmpty()) {
            // Stop batch task if no notifications
            stopBatchTask();
            return;
        }

        // Process each owner's notifications
        for (Map.Entry<UUID, List<SaleNotification>> entry : pendingNotifications.entrySet()) {
            UUID ownerId = entry.getKey();
            List<SaleNotification> notifications = entry.getValue();

            if (notifications.isEmpty()) {
                continue;
            }

            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && owner.isOnline()) {
                sendBatchedNotifications(owner, notifications);
            }
            // If owner offline, notifications are lost (can be stored for later if desired)
        }

        // Clear all pending notifications
        pendingNotifications.clear();
    }

    /**
     * Send batched notifications to a player.
     */
    private void sendBatchedNotifications(Player owner, List<SaleNotification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }

        // Separate sale notifications and partnership notifications
        List<SaleNotification> sales = new ArrayList<>();
        List<PartnershipNotification> partnerships = new ArrayList<>();

        for (SaleNotification notification : notifications) {
            if (notification instanceof PartnershipNotification) {
                partnerships.add((PartnershipNotification) notification);
            } else {
                sales.add(notification);
            }
        }

        // Send sale summary
        if (!sales.isEmpty()) {
            sendSaleSummary(owner, sales);
        }

        // Send partnership summary
        if (!partnerships.isEmpty()) {
            sendPartnershipSummary(owner, partnerships);
        }

        // Play sound
        if (config.isNotificationSoundsEnabled()) {
            owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    /**
     * Send a summary of sale notifications.
     */
    private void sendSaleSummary(Player owner, List<SaleNotification> sales) {
        int totalSales = sales.size();
        double totalEarnings = sales.stream().mapToDouble(SaleNotification::getTotalPrice).sum();

        // Group by shop
        Map<String, List<SaleNotification>> byShop = new HashMap<>();
        for (SaleNotification sale : sales) {
            byShop.computeIfAbsent(sale.getShopName(), k -> new ArrayList<>()).add(sale);
        }

        if (byShop.size() == 1 && totalSales == 1) {
            // Single sale - detailed message
            SaleNotification sale = sales.get(0);
            String message = config.getMessage("notification-sale",
                "shop", sale.getShopName(),
                "player", sale.getBuyerName(),
                "quantity", String.valueOf(sale.getQuantity()),
                "item", sale.getItemName(),
                "total", String.format("%.2f", sale.getTotalPrice()));
            owner.sendMessage(miniMessage.deserialize(message));
        } else {
            // Multiple sales - summary message
            String message = config.getMessage("notification-sale-batch",
                "count", String.valueOf(totalSales),
                "shops", String.valueOf(byShop.size()),
                "total", String.format("%.2f", totalEarnings));
            owner.sendMessage(miniMessage.deserialize(message));

            // Show per-shop breakdown if enabled
            if (config.isDetailedBatchEnabled() && byShop.size() <= 5) {
                for (Map.Entry<String, List<SaleNotification>> entry : byShop.entrySet()) {
                    String shopName = entry.getKey();
                    List<SaleNotification> shopSales = entry.getValue();
                    double shopEarnings = shopSales.stream().mapToDouble(SaleNotification::getTotalPrice).sum();

                    String shopMessage = config.getMessage("notification-sale-batch-detail",
                        "shop", shopName,
                        "count", String.valueOf(shopSales.size()),
                        "total", String.format("%.2f", shopEarnings));
                    owner.sendMessage(miniMessage.deserialize(shopMessage));
                }
            }
        }
    }

    /**
     * Send a summary of partnership earnings.
     */
    private void sendPartnershipSummary(Player partner, List<PartnershipNotification> partnerships) {
        double totalEarnings = partnerships.stream().mapToDouble(PartnershipNotification::getEarnings).sum();

        if (partnerships.size() == 1) {
            PartnershipNotification notification = partnerships.get(0);
            String message = config.getMessage("notification-partnership",
                "shop", notification.getShopName(),
                "earnings", String.format("%.2f", notification.getEarnings()),
                "percentage", String.format("%.1f", notification.getPercentage() * 100));
            partner.sendMessage(miniMessage.deserialize(message));
        } else {
            String message = config.getMessage("notification-partnership-batch",
                "count", String.valueOf(partnerships.size()),
                "total", String.format("%.2f", totalEarnings));
            partner.sendMessage(miniMessage.deserialize(message));
        }
    }

    /**
     * Stop the batch task.
     */
    private void stopBatchTask() {
        if (batchTask != null) {
            batchTask.cancel();
            batchTask = null;
        }
    }

    /**
     * Cleanup on plugin disable.
     */
    public void shutdown() {
        stopBatchTask();
        pendingNotifications.clear();
    }

    // Notification data classes
    public static class SaleNotification {
        private final String shopName;
        private final String buyerName;
        private final String itemName;
        private final int quantity;
        private final double totalPrice;

        public SaleNotification(String shopName, String buyerName, String itemName, int quantity, double totalPrice) {
            this.shopName = shopName;
            this.buyerName = buyerName;
            this.itemName = itemName;
            this.quantity = quantity;
            this.totalPrice = totalPrice;
        }

        public String getShopName() { return shopName; }
        public String getBuyerName() { return buyerName; }
        public String getItemName() { return itemName; }
        public int getQuantity() { return quantity; }
        public double getTotalPrice() { return totalPrice; }
    }

    public static class StockNotification {
        private final String shopName;
        private final String itemName;
        private final int currentStock;

        public StockNotification(String shopName, String itemName, int currentStock) {
            this.shopName = shopName;
            this.itemName = itemName;
            this.currentStock = currentStock;
        }

        public String getShopName() { return shopName; }
        public String getItemName() { return itemName; }
        public int getCurrentStock() { return currentStock; }
    }

    public static class PartnershipNotification extends SaleNotification {
        private final double percentage;

        public PartnershipNotification(String shopName, double earnings, double percentage) {
            super(shopName, null, null, 0, earnings);
            this.percentage = percentage;
        }

        public double getEarnings() { return getTotalPrice(); }
        public double getPercentage() { return percentage; }
    }
}
