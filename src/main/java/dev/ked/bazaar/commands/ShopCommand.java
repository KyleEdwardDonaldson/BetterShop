package dev.ked.bazaar.commands;

import dev.ked.bazaar.BazaarPlugin;
import dev.ked.bazaar.config.ConfigManager;
import dev.ked.bazaar.mode.ShopModeManager;
import dev.ked.bazaar.shop.*;
import dev.ked.bazaar.ui.ShopDirectoryGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all /shop commands for the new shop system.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {
    private final BazaarPlugin plugin;
    private final ShopEntityManager shopManager;
    private final ShopRegistry registry;
    private final ShopModeManager modeManager;
    private final ConfigManager config;
    private final MiniMessage miniMessage;

    public ShopCommand(BazaarPlugin plugin, ShopEntityManager shopManager, ShopRegistry registry,
                       ShopModeManager modeManager, ConfigManager config) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.registry = registry;
        this.modeManager = modeManager;
        this.config = config;
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
            case "mode" -> handleMode(player, args);
            case "rename" -> handleRename(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "info" -> handleInfo(player);
            case "collect" -> handleCollect(player);
            case "remove" -> handleRemoveListing(player);
            case "browse", "directory" -> handleBrowse(player, args);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }

        return true;
    }

    /**
     * /shop create {name}
     * Create a new shop and enter shop mode.
     */
    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("bettershop.create")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Usage: /shop create <name>"));
            return;
        }

        // Join all args after "create" as shop name
        String shopName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // Validate shop name
        if (shopName.trim().isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop name cannot be empty!"));
            return;
        }

        // Check if name is already taken
        if (registry.isShopNameTaken(player.getUniqueId(), shopName)) {
            String message = config.getMessage("shop-name-taken", "shop_name", shopName);
            player.sendMessage(miniMessage.deserialize(message));
            return;
        }

        // Check if player can create shop at this location
        if (!shopManager.canCreateShop(player, player.getLocation())) {
            int maxShops = config.getMaxShopsPerPlayer();
            String message = config.getMessage("shop-limit-reached", "limit", String.valueOf(maxShops));
            player.sendMessage(miniMessage.deserialize(message));
            return;
        }

        // Create shop
        Optional<ShopEntity> shopOpt = shopManager.createShop(player.getUniqueId(), shopName, player.getLocation());

        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Failed to create shop!"));
            return;
        }

        ShopEntity shop = shopOpt.get();

        // Send creation message
        String message = config.getMessage("shop-created", "shop_name", shopName);
        player.sendMessage(miniMessage.deserialize(message));

        // Show territory if assigned
        if (shop.getTerritoryId() != null) {
            String territoryMsg = config.getMessage("shop-territory-assigned", "territory", shop.getTerritoryId());
            player.sendMessage(miniMessage.deserialize(territoryMsg));
        }

        // Enter shop mode
        if (modeManager.enterShopMode(player, shop.getId())) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }

    /**
     * /shop mode [name]
     * /shop mode exit
     * Enter or exit shop mode.
     */
    private void handleMode(Player player, String[] args) {
        if (!player.hasPermission("bettershop.mode")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        // Exit mode
        if (args.length >= 2 && args[1].equalsIgnoreCase("exit")) {
            if (modeManager.exitShopMode(player)) {
                // Exit message is sent by manager
            } else {
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You are not in shop mode!"));
            }
            return;
        }

        // Enter mode
        ShopEntity shop = null;

        if (args.length >= 2) {
            // Specific shop name provided
            String shopName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Optional<ShopEntity> shopOpt = registry.getShopByOwnerAndName(player.getUniqueId(), shopName);

            if (shopOpt.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop '" + shopName + "' not found!"));
                return;
            }

            shop = shopOpt.get();
        } else {
            // No name provided - use first shop if player has only one
            List<ShopEntity> shops = registry.getShopsByOwner(player.getUniqueId());

            if (shops.isEmpty()) {
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You don't have any shops! Use /shop create <name>"));
                return;
            }

            if (shops.size() > 1) {
                player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<yellow>You have multiple shops. Specify one: /shop mode <name>"));
                listPlayerShops(player, shops);
                return;
            }

            shop = shops.get(0);
        }

        // Enter shop mode
        modeManager.enterShopMode(player, shop.getId());
    }

    /**
     * /shop rename <name>
     * Rename the shop you're currently in.
     */
    private void handleRename(Player player, String[] args) {
        if (!player.hasPermission("bettershop.rename")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Usage: /shop rename <new name>"));
            return;
        }

        // Must be in shop mode
        if (!modeManager.isInShopMode(player)) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You must be in shop mode! Use /shop mode"));
            return;
        }

        UUID shopId = modeManager.getActiveSession(player).get().getShopId();
        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (shopManager.renameShop(shopId, newName)) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Shop renamed to '" + newName + "'!"));
        } else {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Failed to rename shop! Name might be taken."));
        }
    }

    /**
     * /shop delete
     * Delete entire shop (all listings).
     */
    private void handleDelete(Player player, String[] args) {
        if (!player.hasPermission("bettershop.delete")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        // Must be in shop mode
        if (!modeManager.isInShopMode(player)) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You must be in shop mode! Use /shop mode"));
            return;
        }

        UUID shopId = modeManager.getActiveSession(player).get().getShopId();
        Optional<ShopEntity> shopOpt = registry.getShopById(shopId);

        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop not found!"));
            return;
        }

        ShopEntity shop = shopOpt.get();
        String shopName = shop.getName();

        // Exit shop mode first
        modeManager.exitShopMode(player, false);

        // Delete shop (cascades to all listings)
        if (shopManager.removeShop(shopId)) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Shop '" + shopName + "' deleted!"));
        } else {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Failed to delete shop!"));
        }
    }

    /**
     * /shop list
     * List all shops owned by player.
     */
    private void handleList(Player player) {
        if (!player.hasPermission("bettershop.list")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        List<ShopEntity> shops = registry.getShopsByOwner(player.getUniqueId());
        int maxShops = config.getMaxShopsPerPlayer();

        String header = config.getMessage("shop-list-header", "count", String.valueOf(shops.size()), "max", String.valueOf(maxShops));
        player.sendMessage(miniMessage.deserialize(header));

        if (shops.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("shop-list-empty")));
            return;
        }

        listPlayerShops(player, shops);
    }

    /**
     * Helper to list shops.
     */
    private void listPlayerShops(Player player, List<ShopEntity> shops) {
        for (int i = 0; i < shops.size(); i++) {
            ShopEntity shop = shops.get(i);
            int listingCount = shop.getListingCount();
            Location loc = shop.getCreationLocation();

            String entry = "<gray>" + (i + 1) + ". <white>" + shop.getName() +
                    " <gray>(" + listingCount + " listings) <dark_gray>[" +
                    loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "]";

            player.sendMessage(miniMessage.deserialize(entry));
        }
    }

    /**
     * /shop info
     * View listing info (look at chest).
     */
    private void handleInfo(Player player) {
        if (!player.hasPermission("bettershop.info")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No listing found!"));
            return;
        }

        Optional<Listing> listingOpt = registry.getListingAt(target.getLocation());
        if (listingOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No listing found!"));
            return;
        }

        Listing listing = listingOpt.get();
        Optional<ShopEntity> shopOpt = registry.getShopById(listing.getShopId());

        if (shopOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>Shop not found!"));
            return;
        }

        ShopEntity shop = shopOpt.get();
        String ownerName = Bukkit.getOfflinePlayer(listing.getOwner()).getName();
        String typeColor = listing.getType() == ListingType.SELL ? "<green>" : "<blue>";

        player.sendMessage(miniMessage.deserialize("<gray>========== <white>Listing Info <gray>=========="));
        player.sendMessage(miniMessage.deserialize("<gray>Shop: <white>" + shop.getName()));
        player.sendMessage(miniMessage.deserialize("<gray>Type: " + typeColor + listing.getType().name()));
        player.sendMessage(miniMessage.deserialize("<gray>Item: <white>" + listing.getItem().getType().name()));
        player.sendMessage(miniMessage.deserialize("<gray>Price: <gold>$" + String.format("%.2f", listing.getPrice())));
        player.sendMessage(miniMessage.deserialize("<gray>Earnings: <gold>$" + String.format("%.2f", listing.getEarnings())));
        player.sendMessage(miniMessage.deserialize("<gray>Owner: <white>" + ownerName));
    }

    /**
     * /shop collect
     * Collect earnings from listing.
     */
    private void handleCollect(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No listing found!"));
            return;
        }

        Optional<Listing> listingOpt = registry.getListingAt(target.getLocation());
        if (listingOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No listing found!"));
            return;
        }

        Listing listing = listingOpt.get();

        if (!listing.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You don't own this listing!"));
            return;
        }

        if (listing.getEarnings() <= 0) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("no-earnings")));
            return;
        }

        double earnings = listing.getEarnings();
        listing.setEarnings(0);

        // Deposit money
        plugin.getEconomy().depositPlayer(player, earnings);

        String message = config.getMessage("earnings-collected", "earnings", String.format("%.2f", earnings));
        player.sendMessage(miniMessage.deserialize(message));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    /**
     * /shop remove
     * Remove a listing (not entire shop).
     */
    private void handleRemoveListing(Player player) {
        if (!player.hasPermission("bettershop.remove")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No listing found!"));
            return;
        }

        Optional<Listing> listingOpt = registry.getListingAt(target.getLocation());
        if (listingOpt.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No listing found!"));
            return;
        }

        Listing listing = listingOpt.get();

        if (!listing.getOwner().equals(player.getUniqueId()) && !player.hasPermission("bettershop.admin")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>You don't own this listing!"));
            return;
        }

        // Collect earnings first
        if (listing.getEarnings() > 0) {
            plugin.getEconomy().depositPlayer(player, listing.getEarnings());
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Collected $" + String.format("%.2f", listing.getEarnings())));
        }

        // Remove listing
        registry.unregisterListing(listing.getId());

        player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<green>Listing removed!"));
    }

    /**
     * /shop browse [--silkroad]
     * Open the shop directory GUI.
     */
    private void handleBrowse(Player player, String[] args) {
        if (!player.hasPermission("bettershop.browse")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        // Check for silk road filter
        Boolean silkRoadOnly = null;
        if (args.length > 1 && args[1].equalsIgnoreCase("--silkroad")) {
            silkRoadOnly = true;
        }

        ShopDirectoryGUI gui = silkRoadOnly != null ?
                new ShopDirectoryGUI(plugin, player, silkRoadOnly) :
                new ShopDirectoryGUI(plugin, player);
        gui.open();
    }

    /**
     * /shop reload
     * Reload configuration.
     */
    private void handleReload(Player player) {
        if (!player.hasPermission("bettershop.admin.reload")) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("prefix") + "<red>No permission!"));
            return;
        }

        plugin.reloadConfiguration();
        player.sendMessage(miniMessage.deserialize(config.getMessage("config-reloaded")));
    }

    /**
     * Send help message.
     */
    private void sendHelp(Player player) {
        player.sendMessage(miniMessage.deserialize("<gray>========== <gold>BetterShop <gray>=========="));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop create <name> <white>- Create a shop"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop mode [name] <white>- Enter shop mode"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop mode exit <white>- Exit shop mode"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop rename <name> <white>- Rename your shop"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop delete <white>- Delete shop"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop list <white>- List your shops"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop info <white>- View listing info"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop collect <white>- Collect earnings"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop remove <white>- Remove listing"));
        player.sendMessage(miniMessage.deserialize("<yellow>/shop browse <white>- Browse all shops"));

        if (player.hasPermission("bettershop.admin")) {
            player.sendMessage(miniMessage.deserialize("<yellow>/shop reload <white>- Reload config"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("create", "mode", "rename", "delete", "list", "info", "collect", "remove", "browse", "directory", "reload")
                    .stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "mode":
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("exit");
                    // Add player's shop names
                    List<ShopEntity> shops = registry.getShopsByOwner(player.getUniqueId());
                    suggestions.addAll(shops.stream().map(ShopEntity::getName).collect(Collectors.toList()));
                    return suggestions;

                case "browse":
                case "directory":
                    return Collections.singletonList("--silkroad");

                case "rename":
                case "create":
                    return Collections.singletonList("<name>");
            }
        }

        return Collections.emptyList();
    }
}
