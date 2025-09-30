package dev.ked.bettershop.ui;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Manages the trade GUI for shop interactions.
 */
public class TradeGUI implements Listener {
    private final ConfigManager config;
    private final ShopManager shopManager;
    private final Economy economy;
    private final MiniMessage miniMessage;

    // Track open GUIs: inventory -> shop
    private final Map<Inventory, Shop> openGUIs = new HashMap<>();

    // Track selected quantities: player UUID -> quantity
    private final Map<UUID, Integer> selectedQuantities = new HashMap<>();

    private static final int ITEM_DISPLAY_SLOT = 13;
    private static final int[] QUANTITY_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] QUANTITY_VALUES = {1, 8, 16, 32, 64, -1, 0}; // -1 = custom, 0 = all
    private static final int CONFIRM_SLOT = 31;
    private static final int CANCEL_SLOT = 30;

    public TradeGUI(ConfigManager config, ShopManager shopManager, Economy economy) {
        this.config = config;
        this.shopManager = shopManager;
        this.economy = economy;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Open the trade GUI for a player.
     */
    public void openGUI(Player player, Shop shop) {
        int stock = shopManager.getStock(shop);
        selectedQuantities.put(player.getUniqueId(), 1);

        Inventory inventory = createInventory(shop, player, 1, stock);
        openGUIs.put(inventory, shop);

        player.openInventory(inventory);
    }

    /**
     * Create the inventory GUI.
     */
    private Inventory createInventory(Shop shop, Player player, int quantity, int stock) {
        String title = shop.getType() == ShopType.BUY ? "Buy " : "Sell ";
        title += getItemDisplayName(shop.getItem());

        Inventory inv = Bukkit.createInventory(null, 36, Component.text(title));

        // Fill with glass panes
        ItemStack grayPane = createGlassPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, grayPane);
        }

        // Item display
        inv.setItem(ITEM_DISPLAY_SLOT, createItemDisplay(shop, quantity, stock));

        // Quantity selectors
        for (int i = 0; i < QUANTITY_SLOTS.length; i++) {
            int qtyValue = QUANTITY_VALUES[i];
            inv.setItem(QUANTITY_SLOTS[i], createQuantitySelector(qtyValue, quantity, stock));
        }

        // Confirm button
        double totalPrice = shop.getPrice() * quantity;
        inv.setItem(CONFIRM_SLOT, createConfirmButton(totalPrice));

        // Cancel button
        inv.setItem(CANCEL_SLOT, createCancelButton());

        return inv;
    }

    /**
     * Handle inventory clicks.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        Shop shop = openGUIs.get(inventory);

        if (shop == null) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        int stock = shopManager.getStock(shop);

        // Handle quantity selection
        for (int i = 0; i < QUANTITY_SLOTS.length; i++) {
            if (slot == QUANTITY_SLOTS[i]) {
                int newQuantity = QUANTITY_VALUES[i];

                if (newQuantity == -1) {
                    // Custom quantity - prompt in chat
                    player.closeInventory();
                    player.sendMessage(miniMessage.deserialize("<gray>Type a custom quantity in chat (or 'cancel'):"));
                    // TODO: Implement chat listener for custom quantity
                    return;
                } else if (newQuantity == 0) {
                    // All items
                    newQuantity = Math.min(stock, 64);
                }

                newQuantity = Math.min(newQuantity, stock);
                selectedQuantities.put(player.getUniqueId(), newQuantity);

                // Refresh GUI
                Inventory newInv = createInventory(shop, player, newQuantity, stock);
                openGUIs.remove(inventory);
                openGUIs.put(newInv, shop);
                player.openInventory(newInv);
                return;
            }
        }

        // Handle confirm
        if (slot == CONFIRM_SLOT) {
            int quantity = selectedQuantities.getOrDefault(player.getUniqueId(), 1);
            handleTransaction(player, shop, quantity);
            player.closeInventory();
            return;
        }

        // Handle cancel
        if (slot == CANCEL_SLOT) {
            player.closeInventory();
        }
    }

    /**
     * Handle inventory close.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        Shop shop = openGUIs.remove(inventory);

        if (shop != null && event.getPlayer() instanceof Player player) {
            selectedQuantities.remove(player.getUniqueId());
        }
    }

    /**
     * Process the transaction.
     */
    private void handleTransaction(Player player, Shop shop, int quantity) {
        ShopManager.TransactionResultData resultData;

        // BUY shop = player sells TO the shop
        // SELL shop = player buys FROM the shop
        if (shop.getType() == ShopType.SELL) {
            resultData = shopManager.processBuyTransaction(player, shop, quantity);
        } else {
            resultData = shopManager.processSellTransaction(player, shop, quantity);
        }

        ShopManager.TransactionResult result = resultData.getResult();

        // Send result message
        String messageKey = switch (result) {
            case SUCCESS -> shop.getType() == ShopType.SELL ? "purchase-success" : "sell-success";
            case INSUFFICIENT_FUNDS -> "purchase-insufficient-funds";
            case INSUFFICIENT_STOCK -> "purchase-insufficient-stock";
            case NO_INVENTORY_SPACE -> "purchase-no-space";
            case SHOP_INSUFFICIENT_FUNDS -> "sell-shop-no-funds";
            case INSUFFICIENT_ITEMS -> "sell-insufficient-items";
            case CHEST_FULL -> "sell-chest-full";
            case WRONG_SHOP_TYPE -> "wrong-shop-type";
            default -> "transaction-failed";
        };

        double total = shop.getPrice() * quantity;
        String message = config.getMessage(messageKey,
                "quantity", String.valueOf(quantity),
                "item", getItemDisplayName(shop.getItem()),
                "total", String.format("%.2f", total),
                "balance", String.format("%.2f", economy.getBalance(player)),
                "stock", String.valueOf(shopManager.getStock(shop))
        );

        player.sendMessage(miniMessage.deserialize(message));

        // Send tax message if applicable
        if (result == ShopManager.TransactionResult.SUCCESS && resultData.hasTransactionTax()) {
            String taxMessage = config.getMessage("territory-transaction-tax",
                    "amount", String.format("%.2f", resultData.getTransactionTax()),
                    "territory", resultData.getTerritoryName()
            );
            player.sendMessage(miniMessage.deserialize(taxMessage));
        }
    }

    // Helper methods for creating GUI items

    private ItemStack createGlassPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItemDisplay(Shop shop, int quantity, int stock) {
        ItemStack display = shop.getItem().clone();
        display.setAmount(Math.min(quantity, 64));

        ItemMeta meta = display.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());

        lore.add(Component.empty());
        lore.add(Component.text("Price: ", NamedTextColor.GRAY)
                .append(Component.text("$" + formatPrice(shop.getPrice()), NamedTextColor.YELLOW)));
        lore.add(Component.text("Stock: ", NamedTextColor.GRAY)
                .append(Component.text(stock, NamedTextColor.WHITE)));
        lore.add(Component.text("Total: ", NamedTextColor.GOLD)
                .append(Component.text("$" + formatPrice(shop.getPrice() * quantity), NamedTextColor.YELLOW)));

        meta.lore(lore);
        display.setItemMeta(meta);

        return display;
    }

    private ItemStack createQuantitySelector(int value, int currentQuantity, int stock) {
        String label;
        if (value == -1) {
            label = "Custom";
        } else if (value == 0) {
            label = "All (" + Math.min(stock, 64) + ")";
        } else {
            label = String.valueOf(value);
        }

        Material material = (value == currentQuantity) ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(label, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createConfirmButton(double total) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("✓ Confirm", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Total: $" + formatPrice(total), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("✗ Cancel", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);

        return item;
    }

    private String getItemDisplayName(ItemStack item) {
        String materialName = item.getType().name();
        String[] parts = materialName.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return result.toString();
    }

    private String formatPrice(double price) {
        if (price == (long) price) {
            return String.format("%d", (long) price);
        } else {
            return String.format("%.2f", price);
        }
    }
}