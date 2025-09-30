package dev.ked.bettershop.commands;

import dev.ked.bettershop.BetterShopPlugin;
import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopRegistry;
import dev.ked.bettershop.shop.ShopType;
import dev.ked.bettershop.ui.HologramManager;
import dev.ked.bettershop.ui.MaterialSelectorGUI;
import dev.ked.bettershop.ui.SignRenderer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles all /shop commands.
 */
public class ShopCommand implements CommandExecutor, TabCompleter, Listener {
    private final BetterShopPlugin plugin;
    private final ShopManager shopManager;
    private final ShopRegistry registry;
    private final ConfigManager config;
    private final SignRenderer signRenderer;
    private final HologramManager hologramManager;
    private final MaterialSelectorGUI materialSelector;
    private final Economy economy;
    private final MiniMessage miniMessage;

    // Track players in shop creation mode: player UUID -> (type, price)
    private final Map<UUID, ShopCreationData> creationMode = new HashMap<>();

    public ShopCommand(BetterShopPlugin plugin, ShopManager shopManager, ShopRegistry registry, ConfigManager config,
                       SignRenderer signRenderer, HologramManager hologramManager, MaterialSelectorGUI materialSelector, Economy economy) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.registry = registry;
        this.config = config;
        this.signRenderer = signRenderer;
        this.hologramManager = hologramManager;
        this.materialSelector = materialSelector;
        this.economy = economy;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "remove" -> handleRemove(player);
            case "info" -> handleInfo(player);
            case "collect" -> handleCollect(player);
            case "list" -> handleList(player);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("bettershop.create")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Usage: /shop create <buy|sell> <price>"));
            return;
        }

        // Parse shop type
        ShopType type;
        try {
            type = ShopType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Invalid type! Use 'buy' or 'sell'"));
            return;
        }

        // Parse price
        double price;
        try {
            price = Double.parseDouble(args[2]);
            if (price <= 0) {
                player.sendMessage(miniMessage.deserialize(config.getMessage("invalid-price")));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("invalid-price")));
            return;
        }

        // For BUY shops, check if item in hand. If not (or if holding a chest), open material selector GUI.
        ItemStack item = null;
        if (type == ShopType.BUY) {
            item = player.getInventory().getItemInMainHand();
            Material itemType = item.getType();
            // Open GUI if no item, or if holding a chest (since they're about to place one)
            if (itemType == Material.AIR || itemType == Material.CHEST || itemType == Material.TRAPPED_CHEST) {
                // Open material selector GUI
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<gray>Select the item type for your shop..."));
                materialSelector.openCategorySelector(player, (selectedMaterial) -> {
                    // Create a temporary item stack
                    ItemStack selectedItem = new ItemStack(selectedMaterial, 1);

                    // Check limits again after GUI closes
                    int currentShops = registry.getShopCount(player.getUniqueId());
                    int maxShops = config.getMaxShopsPerPlayer();
                    if (currentShops >= maxShops && !player.hasPermission("bettershop.admin")) {
                        String message = config.getMessage("shop-limit-reached", "limit", String.valueOf(maxShops));
                        player.sendMessage(miniMessage.deserialize(message));
                        return;
                    }

                    // Enter creation mode with selected material
                    creationMode.put(player.getUniqueId(), new ShopCreationData(type, price, selectedItem));

                    // Format the colored type string
                    String colorTag = type == ShopType.BUY ? config.getBuyColor() : config.getSellColor();
                    String coloredType = colorTag + type.name() + "</" + colorTag.substring(1); // <green>BUY</green>
                    String message = config.getMessage("shop-creation-mode", "type_colored", coloredType);
                    player.sendMessage(miniMessage.deserialize(message));
                });
                return;
            }
        }

        // Check world
        if (!config.isWorldEnabled(player.getWorld().getName())) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("world-disabled")));
            return;
        }

        // Check shop limit
        int currentShops = registry.getShopCount(player.getUniqueId());
        int maxShops = config.getMaxShopsPerPlayer();
        if (currentShops >= maxShops && !player.hasPermission("bettershop.admin")) {
            String message = config.getMessage("shop-limit-reached", "limit", String.valueOf(maxShops));
            player.sendMessage(miniMessage.deserialize(message));
            return;
        }

        // Enter creation mode (SELL shops don't have an item yet, it's detected from chest contents)
        ItemStack itemToStore = (item != null) ? item.clone() : null;
        creationMode.put(player.getUniqueId(), new ShopCreationData(type, price, itemToStore));

        // Format the colored type string
        String colorTag = type == ShopType.BUY ? config.getBuyColor() : config.getSellColor();
        String coloredType = colorTag + type.name() + "</" + colorTag.substring(1); // <green>BUY</green>
        String message = config.getMessage("shop-creation-mode", "type_colored", coloredType);
        player.sendMessage(miniMessage.deserialize(message));
    }

    @EventHandler
    public void onChestClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ShopCreationData data = creationMode.get(player.getUniqueId());

        if (data == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) {
            return;
        }

        event.setCancelled(true);
        handleChestSelection(player, block, data);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ShopCreationData data = creationMode.get(player.getUniqueId());

        if (data == null) {
            return;
        }

        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.CHEST && placed.getType() != Material.TRAPPED_CHEST) {
            return;
        }

        // Player placed a chest while in creation mode
        // Treat it as if they clicked it
        Bukkit.getScheduler().runTask(plugin, () -> {
            handleChestSelection(player, placed, data);
        });
    }

    private void handleChestSelection(Player player, Block block, ShopCreationData data) {
        // Check if shop already exists
        if (shopManager.getShopAt(block.getLocation()).isPresent()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("shop-already-exists")));
            creationMode.remove(player.getUniqueId());
            return;
        }

        // Territory checks
        dev.ked.bettershop.integration.TerritoryManager territoryManager = plugin.getTerritoryManager();
        if (territoryManager != null) {
            if (!territoryManager.canCreateShop(player, block.getLocation())) {
                String territory = territoryManager.getTerritoryName(block.getLocation());
                if (territoryManager.isWilderness(block.getLocation())) {
                    player.sendMessage(miniMessage.deserialize(config.getMessage("territory-no-wilderness")));
                } else {
                    player.sendMessage(miniMessage.deserialize(config.getMessage("territory-no-permission",
                            "territory", territory)));
                }
                creationMode.remove(player.getUniqueId());
                return;
            }
        }

        // For SELL shops, detect item from chest contents
        ItemStack shopItem = data.item;
        if (data.type == ShopType.SELL) {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) block.getState();
            ItemStack firstItem = null;

            for (ItemStack item : chest.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    firstItem = item;
                    break;
                }
            }

            if (firstItem == null) {
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Chest is empty! Add items first, then run the command again."));
                creationMode.remove(player.getUniqueId());
                return;
            }

            shopItem = firstItem.clone();
            shopItem.setAmount(1);
        }

        // Create shop
        boolean success = shopManager.createShop(block.getLocation(), player.getUniqueId(), data.type, shopItem, data.price);

        if (success) {
            Shop shop = shopManager.getShopAt(block.getLocation()).orElseThrow();

            // Create visuals
            signRenderer.createOrUpdateSign(shop);
            hologramManager.createHologram(shop);

            String message = config.getMessage("shop-created", "price", String.format("%.2f", data.price));
            player.sendMessage(miniMessage.deserialize(message));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        } else {
            player.sendMessage(miniMessage.deserialize(config.getMessage("shop-creation-failed")));
        }

        creationMode.remove(player.getUniqueId());
    }

    private void handleRemove(Player player) {
        if (!player.hasPermission("bettershop.remove")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-shop-found")));
            return;
        }

        Optional<Shop> shopOpt = shopManager.getShopAt(target.getLocation());
        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-shop-found")));
            return;
        }

        Shop shop = shopOpt.get();

        if (!shop.getOwner().equals(player.getUniqueId()) && !player.hasPermission("bettershop.admin")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("not-shop-owner")));
            return;
        }

        // Remove shop
        registry.unregisterShop(shop);
        signRenderer.removeSign(shop);
        hologramManager.removeHologram(shop);

        // Collect earnings with tax
        if (shop.getEarnings() > 0) {
            ShopManager.EarningsResultData earningsData = shopManager.collectEarnings(shop);
            economy.depositPlayer(player, earningsData.getEarnings());
            String message = config.getMessage("shop-remove-earnings", "earnings", String.format("%.2f", earningsData.getEarnings()));
            player.sendMessage(miniMessage.deserialize(message));

            // Send tax message if applicable
            if (earningsData.hasShopTax()) {
                String taxMessage = config.getMessage("territory-tax-applied",
                        "amount", String.format("%.2f", earningsData.getShopTax()),
                        "territory", earningsData.getTerritoryName()
                );
                player.sendMessage(miniMessage.deserialize(taxMessage));
            }
        }

        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-removed")));
    }

    private void handleInfo(Player player) {
        if (!player.hasPermission("bettershop.info")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-shop-found")));
            return;
        }

        Optional<Shop> shopOpt = shopManager.getShopAt(target.getLocation());
        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-shop-found")));
            return;
        }

        Shop shop = shopOpt.get();
        String ownerName = Bukkit.getOfflinePlayer(shop.getOwner()).getName();
        String color = shop.getType() == ShopType.BUY ? config.getBuyColor() : config.getSellColor();

        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-info-header")));
        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-info-type", "color", color, "type", shop.getType().name())));
        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-info-item", "item", shop.getItem().getType().name())));
        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-info-price", "price", String.format("%.2f", shop.getPrice()))));
        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-info-stock", "stock", String.valueOf(shopManager.getStock(shop)))));
        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-info-earnings", "earnings", String.format("%.2f", shop.getEarnings()))));
        player.sendMessage(miniMessage.deserialize(config.getMessage("shop-info-owner", "owner", ownerName)));
    }

    private void handleCollect(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-shop-found")));
            return;
        }

        Optional<Shop> shopOpt = shopManager.getShopAt(target.getLocation());
        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-shop-found")));
            return;
        }

        Shop shop = shopOpt.get();

        if (!shop.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("not-shop-owner")));
            return;
        }

        if (shop.getEarnings() <= 0) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-earnings")));
            return;
        }

        ShopManager.EarningsResultData earningsData = shopManager.collectEarnings(shop);
        economy.depositPlayer(player, earningsData.getEarnings());

        String message = config.getMessage("earnings-collected", "earnings", String.format("%.2f", earningsData.getEarnings()));
        player.sendMessage(miniMessage.deserialize(message));

        // Send tax message if applicable
        if (earningsData.hasShopTax()) {
            String taxMessage = config.getMessage("territory-tax-applied",
                    "amount", String.format("%.2f", earningsData.getShopTax()),
                    "territory", earningsData.getTerritoryName()
            );
            player.sendMessage(miniMessage.deserialize(taxMessage));
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void handleList(Player player) {
        if (!player.hasPermission("bettershop.list")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        List<Shop> shops = registry.getShopsByOwner(player.getUniqueId());
        int maxShops = config.getMaxShopsPerPlayer();

        String header = config.getMessage("shop-list-header", "count", String.valueOf(shops.size()), "max", String.valueOf(maxShops));
        player.sendMessage(miniMessage.deserialize(header));

        if (shops.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("shop-list-empty")));
            return;
        }

        for (int i = 0; i < shops.size(); i++) {
            Shop shop = shops.get(i);
            Location loc = shop.getLocation();
            String color = shop.getType() == ShopType.BUY ? config.getBuyColor() : config.getSellColor();

            String entry = config.getMessage("shop-list-entry",
                    "index", String.valueOf(i + 1),
                    "color", color,
                    "type", shop.getType().name(),
                    "item", shop.getItem().getType().name(),
                    "price", String.format("%.2f", shop.getPrice()),
                    "world", loc.getWorld().getName(),
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ())
            );

            player.sendMessage(miniMessage.deserialize(entry));
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("bettershop.admin.reload")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        plugin.reloadConfiguration();
        player.sendMessage(miniMessage.deserialize(config.getMessage("config-reloaded")));
    }

    private void sendHelp(Player player) {
        player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<gold>BetterShop Commands:"));
        player.sendMessage(miniMessage.deserialize("<gray>/shop create <buy|sell> <price> <white>- Create a shop"));
        player.sendMessage(miniMessage.deserialize("<gray>/shop remove <white>- Remove a shop"));
        player.sendMessage(miniMessage.deserialize("<gray>/shop info <white>- View shop info"));
        player.sendMessage(miniMessage.deserialize("<gray>/shop collect <white>- Collect shop earnings"));
        player.sendMessage(miniMessage.deserialize("<gray>/shop list <white>- List your shops"));

        if (player.hasPermission("bettershop.admin")) {
            player.sendMessage(miniMessage.deserialize("<gray>/shop reload <white>- Reload config"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "info", "collect", "list", "reload");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return Arrays.asList("buy", "sell");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return Arrays.asList("10", "50", "100", "500", "1000");
        }

        return Collections.emptyList();
    }

    private static class ShopCreationData {
        final ShopType type;
        final double price;
        final ItemStack item;

        ShopCreationData(ShopType type, double price, ItemStack item) {
            this.type = type;
            this.price = price;
            this.item = item;
        }
    }
}