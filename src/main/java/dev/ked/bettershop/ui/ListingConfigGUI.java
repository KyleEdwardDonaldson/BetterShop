package dev.ked.bettershop.ui;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for configuring SELL listings.
 * Allows players to set price, quantity limit, and confirm creation.
 */
public class ListingConfigGUI implements Listener {
    private final BetterShopPlugin plugin;
    private final ConfigManager config;
    private final ShopRegistry registry;
    private final ShopEntityManager shopManager;
    private final HologramManager hologramManager;
    private final SignRenderer signRenderer;
    private final MiniMessage miniMessage;

    private final Map<UUID, ConfigSession> sessions = new HashMap<>();

    public ListingConfigGUI(BetterShopPlugin plugin, ConfigManager config, ShopRegistry registry,
                            ShopEntityManager shopManager, HologramManager hologramManager, SignRenderer signRenderer) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.shopManager = shopManager;
        this.hologramManager = hologramManager;
        this.signRenderer = signRenderer;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Open SELL listing configuration GUI.
     */
    public void openSellConfig(Player player, UUID shopId, Location chestLocation) {
        // Create session
        ConfigSession session = new ConfigSession(shopId, chestLocation, ListingType.SELL);
        sessions.put(player.getUniqueId(), session);

        // Open GUI
        openGUI(player, session);
    }

    /**
     * Open the main configuration GUI.
     */
    private void openGUI(Player player, ConfigSession session) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Configure Listing", NamedTextColor.DARK_PURPLE));

        // Price input button (slot 10)
        ItemStack priceItem = createGuiItem(Material.GOLD_INGOT,
                Component.text("Set Price", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Current: $" + session.price, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to set price in chat", NamedTextColor.YELLOW));
        inv.setItem(10, priceItem);

        // Quantity limit button (slot 12) - only for SELL listings
        if (session.type == ListingType.SELL) {
            ItemStack limitItem = createGuiItem(Material.HOPPER,
                    Component.text("Set Quantity Limit", NamedTextColor.AQUA, TextDecoration.BOLD),
                    Component.text("Current: " + (session.quantityLimit == 0 ? "Unlimited" : session.quantityLimit), NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to set limit in chat", NamedTextColor.YELLOW),
                    Component.text("(0 = unlimited)", NamedTextColor.GRAY));
            inv.setItem(12, limitItem);
        }

        // Item display (slot 14) - show detected or selected item
        if (session.item != null) {
            ItemStack itemDisplay = session.item.clone();
            ItemMeta meta = itemDisplay.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("This is the item for your listing", NamedTextColor.GRAY));
            meta.lore(lore);
            itemDisplay.setItemMeta(meta);
            inv.setItem(14, itemDisplay);
        } else {
            ItemStack selectItem = createGuiItem(Material.CHEST,
                    Component.text("Select Item", NamedTextColor.YELLOW, TextDecoration.BOLD),
                    Component.text("Click to choose item type", NamedTextColor.GRAY));
            inv.setItem(14, selectItem);
        }

        // Cancel button (slot 18)
        ItemStack cancelItem = createGuiItem(Material.RED_WOOL,
                Component.text("Cancel", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Click to cancel listing creation", NamedTextColor.GRAY));
        inv.setItem(18, cancelItem);

        // Confirm button (slot 26)
        ItemStack confirmItem;
        if (isSessionValid(session)) {
            confirmItem = createGuiItem(Material.GREEN_WOOL,
                    Component.text("Confirm", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Click to create listing", NamedTextColor.GRAY));
        } else {
            confirmItem = createGuiItem(Material.GRAY_WOOL,
                    Component.text("Confirm", NamedTextColor.GRAY, TextDecoration.BOLD),
                    Component.text("Set price to continue", NamedTextColor.RED));
        }
        inv.setItem(26, confirmItem);

        player.openInventory(inv);
    }

    /**
     * Check if session has all required fields.
     */
    private boolean isSessionValid(ConfigSession session) {
        return session.price > 0 && (session.type == ListingType.BUY || session.item != null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!event.getView().title().equals(Component.text("Configure Listing", NamedTextColor.DARK_PURPLE))) {
            return;
        }

        event.setCancelled(true);

        ConfigSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        int slot = event.getSlot();

        switch (slot) {
            case 10: // Set price
                player.closeInventory();
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>Enter the price per item in chat (e.g., 100):"));
                session.awaitingPriceInput = true;
                break;

            case 12: // Set quantity limit (SELL only)
                if (session.type == ListingType.SELL) {
                    player.closeInventory();
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>Enter quantity limit in chat (0 = unlimited):"));
                    session.awaitingLimitInput = true;
                }
                break;

            case 14: // Select item (if null)
                if (session.item == null) {
                    player.closeInventory();
                    // Open material selector (reuse existing MaterialSelectorGUI)
                    openMaterialSelector(player, session);
                }
                break;

            case 18: // Cancel
                player.closeInventory();
                sessions.remove(player.getUniqueId());
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Listing creation cancelled."));
                // Remove the chest
                session.chestLocation.getBlock().setType(Material.AIR);
                break;

            case 26: // Confirm
                if (isSessionValid(session)) {
                    confirmListing(player, session);
                } else {
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Please set a price first!"));
                }
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!event.getView().title().equals(Component.text("Configure Listing", NamedTextColor.DARK_PURPLE))) {
            return;
        }

        ConfigSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        // If awaiting input, don't remove session yet
        if (session.awaitingPriceInput || session.awaitingLimitInput || session.awaitingItemSelection) {
            return;
        }

        // Re-open GUI after 1 tick if they didn't cancel
        if (sessions.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (sessions.containsKey(player.getUniqueId())) {
                    openGUI(player, session);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ConfigSession session = sessions.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        if (session.awaitingPriceInput) {
            event.setCancelled(true);
            String input = event.getMessage();

            try {
                double price = Double.parseDouble(input);
                if (price <= 0) {
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Price must be greater than 0!"));
                } else {
                    session.price = price;
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Price set to $" + price));
                    session.awaitingPriceInput = false;

                    // Reopen GUI
                    Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, session));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Invalid number! Try again:"));
            }
        } else if (session.awaitingLimitInput) {
            event.setCancelled(true);
            String input = event.getMessage();

            try {
                int limit = Integer.parseInt(input);
                if (limit < 0) {
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Limit must be 0 or greater!"));
                } else {
                    session.quantityLimit = limit;
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Quantity limit set to " + (limit == 0 ? "unlimited" : limit)));
                    session.awaitingLimitInput = false;

                    // Reopen GUI
                    Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, session));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Invalid number! Try again:"));
            }
        }
    }

    /**
     * Open material selector for choosing item type.
     */
    private void openMaterialSelector(Player player, ConfigSession session) {
        session.awaitingItemSelection = true;
        MaterialSelectorGUI selector = new MaterialSelectorGUI(plugin.getMythicItemHandler());
        selector.openCategorySelector(player,
            // Vanilla item selected
            (selectedMaterial) -> {
                session.item = new ItemStack(selectedMaterial, 1);
                session.awaitingItemSelection = false;
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Item selected: " + selectedMaterial.name()));

                // Reopen config GUI
                Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, session));
            },
            // Mythic item selected (not used for SELL listings, but required by API)
            (mythicItemId) -> {
                // SELL listings don't use material selector for mythic items
                // They detect items from chest instead
            }
        );
    }

    /**
     * Confirm and create the listing.
     */
    private void confirmListing(Player player, ConfigSession session) {
        player.closeInventory();

        // Get shop entity
        Optional<ShopEntity> shopOpt = registry.getShopById(session.shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop not found!"));
            sessions.remove(player.getUniqueId());
            return;
        }

        ShopEntity shop = shopOpt.get();

        // For SELL listings, detect item from chest if not manually set
        if (session.type == ListingType.SELL && session.item == null) {
            ItemStack detectedItem = detectItemFromChest(session.chestLocation);
            if (detectedItem != null) {
                session.item = detectedItem;
            }
        }

        // Create listing
        UUID listingId = UUID.randomUUID();
        Listing listing = new Listing(
                listingId,
                session.shopId,
                session.chestLocation,
                player.getUniqueId(),
                session.type,
                session.item,
                session.price,
                session.quantityLimit
        );

        // Register listing
        registry.registerListing(listing);

        // Create visuals
        signRenderer.createOrUpdateSign(listing);
        hologramManager.createHologram(listing);

        // Send success message
        String itemName = session.item != null ? session.item.getType().name().toLowerCase().replace('_', ' ') : "any item";
        String typeColor = session.type == ListingType.SELL ? "<green>" : "<blue>";
        String message = config.getMessage("listing-created",
                "type", typeColor + session.type.name() + "</" + typeColor.substring(1),
                "item", itemName,
                "price", String.format("%.2f", session.price));
        player.sendMessage(miniMessage.deserialize(message));

        // Play sound
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

        // Clean up session
        sessions.remove(player.getUniqueId());
    }

    /**
     * Detect item from chest contents.
     */
    private ItemStack detectItemFromChest(Location chestLocation) {
        if (!(chestLocation.getBlock().getState() instanceof org.bukkit.block.Chest chest)) {
            return null;
        }

        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                ItemStack detected = item.clone();
                detected.setAmount(1);
                return detected;
            }
        }

        return null;
    }

    /**
     * Create a GUI item with name and lore.
     */
    private ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));

        List<Component> loreList = new ArrayList<>();
        for (Component line : lore) {
            loreList.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreList);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Register this listener.
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Unregister this listener.
     */
    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    /**
     * Clean up all sessions (for plugin disable).
     */
    public void cleanup() {
        for (UUID playerId : sessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    /**
     * Session for tracking configuration state.
     */
    private static class ConfigSession {
        UUID shopId;
        Location chestLocation;
        ListingType type;
        ItemStack item;
        double price = 0;
        int quantityLimit = 0;
        boolean awaitingPriceInput = false;
        boolean awaitingLimitInput = false;
        boolean awaitingItemSelection = false;

        ConfigSession(UUID shopId, Location chestLocation, ListingType type) {
            this.shopId = shopId;
            this.chestLocation = chestLocation;
            this.type = type;
        }
    }
}
