package dev.ked.bettershop.shop;

import dev.ked.bettershop.integration.TerritoryManager;
import dev.ked.bettershop.notification.NotificationManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages shop operations including creation, removal, and transactions.
 */
public class ShopManager {
    private final ShopRegistry registry;
    private final Economy economy;
    private TerritoryManager territoryManager;
    private NotificationManager notificationManager;

    public ShopManager(ShopRegistry registry, Economy economy) {
        this.registry = registry;
        this.economy = economy;
    }

    public void setTerritoryManager(TerritoryManager territoryManager) {
        this.territoryManager = territoryManager;
    }

    public void setNotificationManager(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    /**
     * Create a shop at the specified location.
     */
    public boolean createShop(Location location, UUID owner, ShopType type, ItemStack item, double price) {
        return createShop(location, owner, type, item, price, 0);
    }

    public boolean createShop(Location location, UUID owner, ShopType type, ItemStack item, double price, int buyLimit) {
        // Validate location is a chest
        Block block = location.getBlock();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return false;
        }

        // Check if shop already exists
        if (registry.getShopAt(location).isPresent()) {
            return false;
        }

        // Create and register shop
        Shop shop = new Shop(location, owner, type, item, price, buyLimit);
        registry.registerShop(shop);

        return true;
    }

    /**
     * Remove a shop at the specified location.
     */
    public boolean removeShop(Location location) {
        Optional<Shop> shopOpt = registry.getShopAt(location);
        if (shopOpt.isEmpty()) {
            return false;
        }

        registry.unregisterShop(shopOpt.get());
        return true;
    }

    /**
     * Get shop at a location.
     */
    public Optional<Shop> getShopAt(Location location) {
        return registry.getShopAt(location);
    }

    /**
     * Calculate current stock of a shop from chest contents.
     * This returns the physical stock in the chest, NOT including reserved stock.
     */
    public int getStock(Shop shop) {
        Block block = shop.getLocation().getBlock();
        if (!(block.getState() instanceof Chest chest)) {
            return 0;
        }

        Inventory inventory = chest.getInventory();
        ItemStack shopItem = shop.getItem();

        // If shop item is null (empty SELL shop), return 0
        if (shopItem == null) {
            return 0;
        }

        int count = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.isSimilar(shopItem)) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * Get available stock (physical stock minus reserved stock).
     * This is what players can actually buy.
     */
    public int getAvailableStock(Shop shop) {
        int physicalStock = getStock(shop);
        int reservedStock = shop.getTotalReservedStock();
        return Math.max(0, physicalStock - reservedStock);
    }

    /**
     * Process a buy transaction (player buying from SELL shop - shop sells to player).
     * Returns a TransactionResultData with result status and tax information.
     */
    public TransactionResultData processBuyTransaction(Player buyer, Shop shop, int quantity) {
        if (shop.getType() != ShopType.SELL) {
            return new TransactionResultData(TransactionResult.WRONG_SHOP_TYPE);
        }

        // Check if shop has an item set (not an empty SELL shop)
        if (shop.getItem() == null) {
            return new TransactionResultData(TransactionResult.INSUFFICIENT_STOCK);
        }

        double totalPrice = shop.getPrice() * quantity;

        // Calculate transaction tax for outsiders
        double transactionTax = 0.0;
        String territoryName = null;
        if (territoryManager != null) {
            double taxRate = territoryManager.getTransactionTaxRate(shop.getLocation(), buyer);
            transactionTax = totalPrice * taxRate;
            if (transactionTax > 0.0) {
                territoryName = territoryManager.getTerritoryName(shop.getLocation());
            }
        }

        double finalPrice = totalPrice + transactionTax;

        // Check buyer has enough money
        if (!economy.has(buyer, finalPrice)) {
            return new TransactionResultData(TransactionResult.INSUFFICIENT_FUNDS);
        }

        // Check shop has stock (must check available stock, not just physical stock)
        int availableStock = getAvailableStock(shop);
        if (availableStock < quantity) {
            return new TransactionResultData(TransactionResult.INSUFFICIENT_STOCK);
        }

        // Check buyer has inventory space
        if (!hasInventorySpace(buyer, shop.getItem(), quantity)) {
            return new TransactionResultData(TransactionResult.NO_INVENTORY_SPACE);
        }

        // Remove items from chest
        if (!removeItemsFromChest(shop.getLocation(), shop.getItem(), quantity)) {
            return new TransactionResultData(TransactionResult.FAILED);
        }

        // Process payment - buyer pays, shop owner earns
        economy.withdrawPlayer(buyer, finalPrice);
        shop.addEarnings(totalPrice);

        // Pay transaction tax to territory
        if (transactionTax > 0.0 && territoryManager != null) {
            territoryManager.payTax(shop.getLocation(), transactionTax);
        }

        // Give items to buyer
        ItemStack itemToGive = shop.getItem().clone();
        itemToGive.setAmount(quantity);
        buyer.getInventory().addItem(itemToGive);

        // Send sale notification to shop owner
        if (notificationManager != null) {
            String shopName = "Shop"; // Old Shop class doesn't have names, use generic
            String itemName = getItemDisplayName(shop.getItem());
            notificationManager.addSaleNotification(
                shop.getOwner(),
                shopName,
                buyer.getName(),
                itemName,
                quantity,
                totalPrice
            );
        }

        return new TransactionResultData(TransactionResult.SUCCESS, transactionTax, territoryName);
    }

    /**
     * Process a sell transaction (player selling to BUY shop - shop buys from player).
     */
    public TransactionResultData processSellTransaction(Player seller, Shop shop, int quantity) {
        if (shop.getType() != ShopType.BUY) {
            return new TransactionResultData(TransactionResult.WRONG_SHOP_TYPE);
        }

        // Check if shop has reached its buy limit
        if (shop.getBuyLimit() > 0) {
            int currentStock = getStock(shop);
            int remaining = shop.getRemainingBuyLimit(currentStock);
            if (remaining == 0) {
                return new TransactionResultData(TransactionResult.CHEST_FULL); // Shop is full
            }
            // Limit quantity to remaining space
            if (quantity > remaining) {
                quantity = remaining;
            }
        }

        double totalPrice = shop.getPrice() * quantity;

        // Calculate transaction tax for outsiders
        double transactionTax = 0.0;
        String territoryName = null;
        if (territoryManager != null) {
            double taxRate = territoryManager.getTransactionTaxRate(shop.getLocation(), seller);
            transactionTax = totalPrice * taxRate;
            if (transactionTax > 0.0) {
                territoryName = territoryManager.getTerritoryName(shop.getLocation());
            }
        }

        double finalPrice = totalPrice - transactionTax; // Seller gets less if they're an outsider

        // Check shop owner has enough money in their Vault balance
        UUID ownerId = shop.getOwner();
        org.bukkit.OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);

        if (!economy.has(owner, totalPrice)) {
            return new TransactionResultData(TransactionResult.SHOP_INSUFFICIENT_FUNDS);
        }

        // Check seller has items
        if (!hasItems(seller, shop.getItem(), quantity)) {
            return new TransactionResultData(TransactionResult.INSUFFICIENT_ITEMS);
        }

        // Check chest has space
        if (!hasChestSpace(shop.getLocation(), shop.getItem(), quantity)) {
            return new TransactionResultData(TransactionResult.CHEST_FULL);
        }

        // Remove items from seller
        if (!removeItemsFromPlayer(seller, shop.getItem(), quantity)) {
            return new TransactionResultData(TransactionResult.FAILED);
        }

        // Add items to chest (owner can access them)
        if (!addItemsToChest(shop.getLocation(), shop.getItem(), quantity)) {
            return new TransactionResultData(TransactionResult.FAILED);
        }

        // Process payment - owner pays full price, seller gets price minus tax
        economy.withdrawPlayer(owner, totalPrice);
        economy.depositPlayer(seller, finalPrice);

        // Pay transaction tax to territory
        if (transactionTax > 0.0 && territoryManager != null) {
            territoryManager.payTax(shop.getLocation(), transactionTax);
        }

        // Send sale notification to shop owner (for BUY shops, they're "buying" from players)
        if (notificationManager != null) {
            String shopName = "Shop"; // Old Shop class doesn't have names, use generic
            String itemName = getItemDisplayName(shop.getItem());
            notificationManager.addSaleNotification(
                shop.getOwner(),
                shopName,
                seller.getName(),
                itemName,
                quantity,
                totalPrice
            );
        }

        return new TransactionResultData(TransactionResult.SUCCESS, transactionTax, territoryName);
    }

    /**
     * Collect earnings from a shop.
     */
    public EarningsResultData collectEarnings(Shop shop) {
        double earnings = shop.getEarnings();

        // Apply shop tax to earnings
        double shopTax = 0.0;
        String territoryName = null;
        if (territoryManager != null) {
            double taxRate = territoryManager.getShopTaxRate(shop.getLocation());
            shopTax = earnings * taxRate;
            if (shopTax > 0.0) {
                territoryName = territoryManager.getTerritoryName(shop.getLocation());
            }
        }

        double finalEarnings = earnings - shopTax;

        // Pay shop tax to territory
        if (shopTax > 0.0 && territoryManager != null) {
            territoryManager.payTax(shop.getLocation(), shopTax);
        }

        shop.setEarnings(0.0);
        return new EarningsResultData(finalEarnings, shopTax, territoryName);
    }

    // Helper methods

    private boolean hasInventorySpace(Player player, ItemStack item, int quantity) {
        Inventory inv = player.getInventory();
        int remaining = quantity;

        for (ItemStack invItem : inv.getStorageContents()) {
            if (invItem == null) {
                remaining -= item.getMaxStackSize();
            } else if (invItem.isSimilar(item)) {
                remaining -= (item.getMaxStackSize() - invItem.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return remaining <= 0;
    }

    private boolean hasItems(Player player, ItemStack item, int quantity) {
        int count = 0;
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null && invItem.isSimilar(item)) {
                count += invItem.getAmount();
            }
        }
        return count >= quantity;
    }

    private boolean hasChestSpace(Location location, ItemStack item, int quantity) {
        Block block = location.getBlock();
        if (!(block.getState() instanceof Chest chest)) {
            return false;
        }

        Inventory inv = chest.getInventory();
        int remaining = quantity;

        for (ItemStack invItem : inv.getContents()) {
            if (invItem == null) {
                remaining -= item.getMaxStackSize();
            } else if (invItem.isSimilar(item)) {
                remaining -= (item.getMaxStackSize() - invItem.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return remaining <= 0;
    }

    private boolean removeItemsFromChest(Location location, ItemStack item, int quantity) {
        Block block = location.getBlock();
        if (!(block.getState() instanceof Chest chest)) {
            return false;
        }

        Inventory inv = chest.getInventory();
        ItemStack toRemove = item.clone();
        toRemove.setAmount(quantity);

        return inv.removeItem(toRemove).isEmpty();
    }

    private boolean removeItemsFromPlayer(Player player, ItemStack item, int quantity) {
        ItemStack toRemove = item.clone();
        toRemove.setAmount(quantity);
        return player.getInventory().removeItem(toRemove).isEmpty();
    }

    private boolean addItemsToChest(Location location, ItemStack item, int quantity) {
        Block block = location.getBlock();
        if (!(block.getState() instanceof Chest chest)) {
            return false;
        }

        Inventory inv = chest.getInventory();
        ItemStack toAdd = item.clone();
        toAdd.setAmount(quantity);

        return inv.addItem(toAdd).isEmpty();
    }

    private String getItemDisplayName(ItemStack item) {
        if (item == null) {
            return "Unknown Item";
        }
        String materialName = item.getType().name();
        String[] parts = materialName.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return result.toString();
    }

    public enum TransactionResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_STOCK,
        NO_INVENTORY_SPACE,
        WRONG_SHOP_TYPE,
        SHOP_INSUFFICIENT_FUNDS,
        INSUFFICIENT_ITEMS,
        CHEST_FULL,
        FAILED
    }

    /**
     * Wraps transaction result with tax information.
     */
    public static class TransactionResultData {
        private final TransactionResult result;
        private final double transactionTax;
        private final String territoryName;

        public TransactionResultData(TransactionResult result) {
            this(result, 0.0, null);
        }

        public TransactionResultData(TransactionResult result, double transactionTax, String territoryName) {
            this.result = result;
            this.transactionTax = transactionTax;
            this.territoryName = territoryName;
        }

        public TransactionResult getResult() {
            return result;
        }

        public double getTransactionTax() {
            return transactionTax;
        }

        public String getTerritoryName() {
            return territoryName;
        }

        public boolean hasTransactionTax() {
            return transactionTax > 0.0;
        }
    }

    /**
     * Wraps earnings collection result with tax information.
     */
    public static class EarningsResultData {
        private final double earnings;
        private final double shopTax;
        private final String territoryName;

        public EarningsResultData(double earnings, double shopTax, String territoryName) {
            this.earnings = earnings;
            this.shopTax = shopTax;
            this.territoryName = territoryName;
        }

        public double getEarnings() {
            return earnings;
        }

        public double getShopTax() {
            return shopTax;
        }

        public String getTerritoryName() {
            return territoryName;
        }

        public boolean hasShopTax() {
            return shopTax > 0.0;
        }
    }
}