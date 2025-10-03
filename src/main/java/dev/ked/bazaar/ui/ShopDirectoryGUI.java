package dev.ked.bazaar.ui;

import dev.ked.bazaar.BazaarPlugin;
import dev.ked.bazaar.discovery.ShopSearchFilter;
import dev.ked.bazaar.shop.Listing;
import dev.ked.bazaar.shop.ListingType;
import dev.ked.bazaar.shop.ShopRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI for browsing all BetterShop listings.
 */
public class ShopDirectoryGUI {
    private final BazaarPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private int page = 0;
    private List<Listing> listings;
    private ShopSearchFilter filter;
    private ListingType filterType = null; // null = all, SELL = only sell, BUY = only buy
    private ShopSearchFilter.SortOption sortOption = ShopSearchFilter.SortOption.PRICE_LOW_TO_HIGH;

    public ShopDirectoryGUI(BazaarPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54,
                Component.text("Shop Directory").color(NamedTextColor.GOLD));

        loadListings();
        buildGUI();
    }

    /**
     * Constructor with filter preset (e.g., silk road only).
     */
    public ShopDirectoryGUI(BazaarPlugin plugin, Player player, Boolean silkRoadOnly) {
        this.plugin = plugin;
        this.player = player;
        String title = silkRoadOnly != null && silkRoadOnly ? "Silk Road Shops" : "Shop Directory";
        this.inventory = Bukkit.createInventory(null, 54,
                Component.text(title).color(NamedTextColor.GOLD));

        // Pre-set filter
        this.filter = new ShopSearchFilter()
                .silkRoadOnly(silkRoadOnly)
                .nearLocation(player.getLocation(), null)
                .sortBy(sortOption);

        loadListings();
        buildGUI();
    }

    private void loadListings() {
        ShopRegistry shopRegistry = plugin.getShopRegistry();

        // Build filter
        if (filter == null) {
            filter = new ShopSearchFilter()
                    .nearLocation(player.getLocation(), null) // For distance sorting
                    .sortBy(sortOption);
        }

        if (filterType != null) {
            filter.listingType(filterType);
        }

        // Get all listings and filter
        listings = shopRegistry.getAllListings().stream()
                .filter(listing -> listing.getItem() != null) // Skip empty listings
                .filter(filter::matches)
                .sorted(filter.getComparator())
                .collect(Collectors.toList());
    }

    private void buildGUI() {
        inventory.clear();

        // Calculate pagination
        int listingsPerPage = 45;
        int startIndex = page * listingsPerPage;
        int endIndex = Math.min(startIndex + listingsPerPage, listings.size());

        // Display listings
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Listing listing = listings.get(i);
            inventory.setItem(slot++, createListingItem(listing));
        }

        // Fill empty slots
        ItemStack grayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, "§r", null);
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, grayPane);
        }

        // Bottom row
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, grayPane);
        }

        // Control buttons
        inventory.setItem(45, createFilterTypeButton());
        inventory.setItem(46, createSortButton());
        inventory.setItem(48, createPreviousButton());
        inventory.setItem(49, createInfoButton());
        inventory.setItem(50, createNextButton());
        inventory.setItem(53, createCloseButton());

        // Show placeholder if no listings
        if (listings.isEmpty()) {
            ItemStack placeholder = createItem(Material.BARRIER,
                    "§c§lNo Shops Found",
                    List.of(
                            "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                            "§7No shops match your",
                            "§7current filter settings.",
                            "",
                            "§7Try changing the filter!",
                            "§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    ));
            inventory.setItem(22, placeholder);
        }
    }

    private ItemStack createListingItem(Listing listing) {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eOwner: §f" + Bukkit.getOfflinePlayer(listing.getOwner()).getName());
        lore.add("§eType: §f" + (listing.getType() == ListingType.SELL ? "§aSelling" : "§bBuying"));
        lore.add("§ePrice: §a$" + String.format("%.2f", listing.getPrice()));

        if (listing.getType() == ListingType.SELL) {
            int totalReserved = listing.getTotalReservedStock();
            if (totalReserved > 0) {
                lore.add("§7  (§e" + totalReserved + " §7in transit)");
            }
        }

        // Silk Road badge
        if (listing.isSilkRoadEnabled()) {
            lore.add("§6⭐ Silk Road Enabled");
        }

        lore.add("");
        lore.add("§eLocation: §f" + listing.getLocation().getBlockX() + ", " +
                listing.getLocation().getBlockY() + ", " +
                listing.getLocation().getBlockZ());

        double distance = player.getLocation().distance(listing.getLocation());
        lore.add("§eDistance: §f" + String.format("%.0f", distance) + " blocks");

        lore.add("");
        lore.add("§7Click for more options");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        Material displayMat = listing.getItem().getType();
        String itemName = getItemName(listing);
        return createItem(displayMat, "§6§l" + itemName, lore);
    }

    private String getItemName(Listing listing) {
        if (listing.isMythicItem()) {
            return listing.getMythicItemId();
        }
        if (listing.getItem() == null) {
            return "Unknown";
        }
        String name = listing.getItem().getType().name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private ItemStack createFilterTypeButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        String current = filterType == null ? "All Shops" :
                filterType == ListingType.SELL ? "Sell Shops Only" : "Buy Shops Only";
        lore.add("§eCurrent: §a" + current);
        lore.add("");
        lore.add("§7Click to cycle:");
        lore.add(filterType == null ? "§a▸ All Shops" : "§7  All Shops");
        lore.add(filterType == ListingType.SELL ? "§a▸ Sell Shops Only" : "§7  Sell Shops Only");
        lore.add(filterType == ListingType.BUY ? "§a▸ Buy Shops Only" : "§7  Buy Shops Only");
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.HOPPER, "§6§lFilter Type", lore);
    }

    private ItemStack createSortButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eCurrent: §a" + sortOption.getDisplayName());
        lore.add("");
        lore.add("§7Click to cycle sorting:");
        for (ShopSearchFilter.SortOption option : ShopSearchFilter.SortOption.values()) {
            String prefix = option == sortOption ? "§a▸ " : "§7  ";
            lore.add(prefix + option.getDisplayName());
        }
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.COMPARATOR, "§6§lSort By", lore);
    }

    private ItemStack createInfoButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add("§eTotal Listings: §f" + listings.size());
        lore.add("§ePage: §f" + (page + 1) + "/" + getMaxPages());
        lore.add("");

        String filterInfo = filterType == null ? "All" :
                filterType == ListingType.SELL ? "Selling" : "Buying";
        lore.add("§eFilter: §f" + filterInfo);
        lore.add("§eSort: §f" + sortOption.getDisplayName());

        if (filter.getSilkRoadOnly() != null) {
            lore.add("§eSilk Road: §f" + (filter.getSilkRoadOnly() ? "Only" : "Excluded"));
        }

        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return createItem(Material.BOOK, "§6§lDirectory Info", lore);
    }

    private ItemStack createPreviousButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Previous page");
        if (page == 0) {
            lore.add("§c§lFirst page");
        }

        return createItem(Material.ARROW, "§e§l← Previous", lore);
    }

    private ItemStack createNextButton() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Next page");
        if (page >= getMaxPages() - 1) {
            lore.add("§c§lLast page");
        }

        return createItem(Material.ARROW, "§e§lNext →", lore);
    }

    private ItemStack createCloseButton() {
        return createItem(Material.BARRIER, "§c§lClose",
                List.of("§7Close directory"));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));

            if (lore != null && !lore.isEmpty()) {
                List<Component> componentLore = new ArrayList<>();
                for (String line : lore) {
                    componentLore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(componentLore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private int getMaxPages() {
        return Math.max(1, (int) Math.ceil(listings.size() / 45.0));
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot >= 0 && slot < 45) {
            // Listing click
            int listingIndex = (page * 45) + slot;
            if (listingIndex < listings.size()) {
                Listing listing = listings.get(listingIndex);
                // Send location info to player
                player.sendMessage(Component.text("Shop Location: " +
                        listing.getLocation().getBlockX() + ", " +
                        listing.getLocation().getBlockY() + ", " +
                        listing.getLocation().getBlockZ())
                        .color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("Owner: " +
                        Bukkit.getOfflinePlayer(listing.getOwner()).getName())
                        .color(NamedTextColor.GRAY));
            }
        } else if (slot == 45) {
            // Cycle filter type
            if (filterType == null) {
                filterType = ListingType.SELL;
            } else if (filterType == ListingType.SELL) {
                filterType = ListingType.BUY;
            } else {
                filterType = null;
            }
            page = 0;
            loadListings();
            buildGUI();
        } else if (slot == 46) {
            // Cycle sort
            ShopSearchFilter.SortOption[] options = ShopSearchFilter.SortOption.values();
            int currentIndex = sortOption.ordinal();
            sortOption = options[(currentIndex + 1) % options.length];
            page = 0;
            loadListings();
            buildGUI();
        } else if (slot == 48) {
            // Previous page
            if (page > 0) {
                page--;
                buildGUI();
            }
        } else if (slot == 50) {
            // Next page
            if (page < getMaxPages() - 1) {
                page++;
                buildGUI();
            }
        } else if (slot == 53) {
            // Close
            player.closeInventory();
        }
    }

    public Inventory getInventory() {
        return inventory;
    }
}
