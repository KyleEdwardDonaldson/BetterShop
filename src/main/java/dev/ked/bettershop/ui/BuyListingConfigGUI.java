package dev.ked.bettershop.ui;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.integration.MythicItemHandler;
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
 * GUI for configuring BUY listings.
 * Similar to ListingConfigGUI but with buy limit instead of quantity limit.
 */
public class BuyListingConfigGUI implements Listener {
    private final BetterShopPlugin plugin;
    private final ConfigManager config;
    private final ShopRegistry registry;
    private final ShopEntityManager shopManager;
    private final HologramManager hologramManager;
    private final SignRenderer signRenderer;
    private final MiniMessage miniMessage;
    private final MythicItemHandler mythicItemHandler;

    private final Map<UUID, BuyConfigSession> sessions = new HashMap<>();

    public BuyListingConfigGUI(BetterShopPlugin plugin, ConfigManager config, ShopRegistry registry,
                               ShopEntityManager shopManager, HologramManager hologramManager, SignRenderer signRenderer,
                               MythicItemHandler mythicItemHandler) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.shopManager = shopManager;
        this.hologramManager = hologramManager;
        this.signRenderer = signRenderer;
        this.miniMessage = MiniMessage.miniMessage();
        this.mythicItemHandler = mythicItemHandler;
    }

    /**
     * Open BUY listing configuration GUI.
     */
    public void openBuyConfig(Player player, UUID shopId, Location chestLocation) {
        // Create session
        BuyConfigSession session = new BuyConfigSession(shopId, chestLocation);
        sessions.put(player.getUniqueId(), session);

        // Open material selector first for BUY listings
        openMaterialSelector(player, session);
    }

    /**
     * Open material selector for choosing what item to buy.
     */
    private void openMaterialSelector(Player player, BuyConfigSession session) {
        session.awaitingItemSelection = true;
        MaterialSelectorGUI selector = new MaterialSelectorGUI(mythicItemHandler);
        selector.openCategorySelector(player,
            // Vanilla item selected
            (selectedMaterial) -> {
                session.item = new ItemStack(selectedMaterial, 1);
                session.mythicItemId = null;
                session.awaitingItemSelection = false;
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>You will buy: " + selectedMaterial.name()));

                // Open main config GUI
                Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, session));
            },
            // Mythic item selected
            (mythicItemId) -> {
                if (mythicItemHandler != null && mythicItemHandler.isEnabled()) {
                    ItemStack mythicItem = mythicItemHandler.getMythicItem(mythicItemId, 1);
                    if (mythicItem != null) {
                        session.item = mythicItem;
                        session.mythicItemId = mythicItemId;
                        session.awaitingItemSelection = false;

                        String displayName = mythicItem.getItemMeta().hasDisplayName()
                            ? mythicItem.getItemMeta().displayName().toString()
                            : mythicItemId;
                        player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>You will buy: " + displayName));

                        // Open main config GUI
                        Bukkit.getScheduler().runTask(plugin, () -> openGUI(player, session));
                    } else {
                        player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Failed to load mythic item!"));
                    }
                }
            }
        );
    }

    /**
     * Open the main configuration GUI.
     */
    private void openGUI(Player player, BuyConfigSession session) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Configure BUY Listing", NamedTextColor.BLUE));

        // Item display (slot 4)
        if (session.item != null) {
            ItemStack itemDisplay = session.item.clone();
            ItemMeta meta = itemDisplay.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("You will buy this from players", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Click to change item", NamedTextColor.YELLOW));
            meta.lore(lore);
            itemDisplay.setItemMeta(meta);
            inv.setItem(4, itemDisplay);
        }

        // Price input button (slot 11)
        ItemStack priceItem = createGuiItem(Material.GOLD_INGOT,
                Component.text("Set Price", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Current: $" + session.price, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("How much you pay per item", NamedTextColor.GRAY),
                Component.text("Click to set price in chat", NamedTextColor.YELLOW));
        inv.setItem(11, priceItem);

        // Buy limit button (slot 15)
        ItemStack limitItem = createGuiItem(Material.HOPPER,
                Component.text("Set Buy Limit", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("Current: " + (session.buyLimit == 0 ? "Unlimited" : session.buyLimit), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Total items you want to buy", NamedTextColor.GRAY),
                Component.text("Click to set limit in chat", NamedTextColor.YELLOW),
                Component.text("(0 = unlimited)", NamedTextColor.GRAY));
        inv.setItem(15, limitItem);

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
                    Component.text("Click to create BUY listing", NamedTextColor.GRAY));
        } else {
            confirmItem = createGuiItem(Material.GRAY_WOOL,
                    Component.text("Confirm", NamedTextColor.GRAY, TextDecoration.BOLD),
                    Component.text("Set item and price to continue", NamedTextColor.RED));
        }
        inv.setItem(26, confirmItem);

        player.openInventory(inv);
    }

    /**
     * Check if session has all required fields.
     */
    private boolean isSessionValid(BuyConfigSession session) {
        return session.price > 0 && session.item != null;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!event.getView().title().equals(Component.text("Configure BUY Listing", NamedTextColor.BLUE))) {
            return;
        }

        event.setCancelled(true);

        BuyConfigSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        int slot = event.getSlot();

        switch (slot) {
            case 4: // Change item
                player.closeInventory();
                openMaterialSelector(player, session);
                break;

            case 11: // Set price
                player.closeInventory();
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>Enter the price you'll pay per item (e.g., 10):"));
                session.awaitingPriceInput = true;
                break;

            case 15: // Set buy limit
                player.closeInventory();
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>Enter total items to buy (0 = unlimited):"));
                session.awaitingLimitInput = true;
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
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Please set item and price first!"));
                }
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!event.getView().title().equals(Component.text("Configure BUY Listing", NamedTextColor.BLUE))) {
            return;
        }

        BuyConfigSession session = sessions.get(player.getUniqueId());
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
        BuyConfigSession session = sessions.get(player.getUniqueId());

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
                    session.buyLimit = limit;
                    player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Buy limit set to " + (limit == 0 ? "unlimited" : limit)));
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
     * Confirm and create the listing.
     */
    private void confirmListing(Player player, BuyConfigSession session) {
        player.closeInventory();

        // Get shop entity
        Optional<ShopEntity> shopOpt = registry.getShopById(session.shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop not found!"));
            sessions.remove(player.getUniqueId());
            return;
        }

        // Create listing
        UUID listingId = UUID.randomUUID();
        Listing listing;

        // Check if this is a mythic item
        if (session.mythicItemId != null) {
            listing = new Listing(
                    listingId,
                    session.shopId,
                    session.chestLocation,
                    player.getUniqueId(),
                    ListingType.BUY,
                    session.mythicItemId,
                    session.price,
                    session.buyLimit
            );
        } else {
            listing = new Listing(
                    listingId,
                    session.shopId,
                    session.chestLocation,
                    player.getUniqueId(),
                    ListingType.BUY,
                    session.item,
                    session.price,
                    session.buyLimit
            );
        }

        // Register listing
        registry.registerListing(listing);

        // Create visuals
        signRenderer.createOrUpdateSign(listing);
        hologramManager.createHologram(listing);

        // Send success message
        String itemName;
        if (session.mythicItemId != null) {
            itemName = session.item.getItemMeta().hasDisplayName()
                ? session.item.getItemMeta().displayName().toString()
                : session.mythicItemId;
        } else {
            itemName = session.item.getType().name().toLowerCase().replace('_', ' ');
        }

        String message = config.getMessage("listing-created",
                "type", "<blue>BUY</blue>",
                "item", itemName,
                "price", String.format("%.2f", session.price));
        player.sendMessage(miniMessage.deserialize(message));

        // Play sound
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

        // Clean up session
        sessions.remove(player.getUniqueId());
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
     * Session for tracking BUY configuration state.
     */
    private static class BuyConfigSession {
        UUID shopId;
        Location chestLocation;
        ItemStack item;
        String mythicItemId; // For MythicMobs items
        double price = 0;
        int buyLimit = 0;
        boolean awaitingPriceInput = false;
        boolean awaitingLimitInput = false;
        boolean awaitingItemSelection = false;

        BuyConfigSession(UUID shopId, Location chestLocation) {
            this.shopId = shopId;
            this.chestLocation = chestLocation;
        }
    }
}
