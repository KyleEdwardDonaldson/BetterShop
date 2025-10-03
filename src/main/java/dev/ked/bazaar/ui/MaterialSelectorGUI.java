package dev.ked.bazaar.ui;

import dev.ked.bazaar.integration.MythicItemHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

/**
 * GUI for selecting materials when creating BUY shops without an item in hand.
 */
public class MaterialSelectorGUI implements Listener {
    private final Map<Inventory, MaterialSelectorData> openGUIs = new HashMap<>();
    private final MythicItemHandler mythicItemHandler;

    // Material categories
    private static final Map<String, List<Material>> CATEGORIES = new LinkedHashMap<>();

    static {
        // Building Blocks
        CATEGORIES.put("Building", Arrays.asList(
                Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
                Material.COBBLESTONE, Material.OAK_PLANKS, Material.SPRUCE_PLANKS,
                Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS,
                Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS,
                Material.BRICKS, Material.STONE_BRICKS, Material.MUD_BRICKS,
                Material.GLASS, Material.SAND, Material.SANDSTONE, Material.GRAVEL
        ));

        // Resources
        CATEGORIES.put("Resources", Arrays.asList(
                Material.DIAMOND, Material.EMERALD, Material.IRON_INGOT, Material.GOLD_INGOT,
                Material.COPPER_INGOT, Material.NETHERITE_INGOT, Material.COAL,
                Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ,
                Material.AMETHYST_SHARD, Material.ENDER_PEARL, Material.BLAZE_ROD,
                Material.SLIME_BALL, Material.GHAST_TEAR, Material.NETHER_STAR,
                Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.IRON_BLOCK,
                Material.GOLD_BLOCK, Material.NETHERITE_BLOCK
        ));

        // Food
        CATEGORIES.put("Food", Arrays.asList(
                Material.APPLE, Material.GOLDEN_APPLE, Material.BREAD, Material.COOKED_BEEF,
                Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN, Material.COOKED_MUTTON,
                Material.COOKED_RABBIT, Material.COOKED_COD, Material.COOKED_SALMON,
                Material.CARROT, Material.POTATO, Material.BAKED_POTATO, Material.BEETROOT,
                Material.MELON_SLICE, Material.SWEET_BERRIES, Material.GLOW_BERRIES,
                Material.COOKIE, Material.CAKE, Material.PUMPKIN_PIE
        ));

        // Farming
        CATEGORIES.put("Farming", Arrays.asList(
                Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROT, Material.POTATO,
                Material.BEETROOT, Material.BEETROOT_SEEDS, Material.MELON_SEEDS,
                Material.PUMPKIN_SEEDS, Material.SUGAR_CANE, Material.BAMBOO,
                Material.KELP, Material.COCOA_BEANS, Material.SWEET_BERRIES,
                Material.GLOW_BERRIES, Material.BONE_MEAL, Material.EGG,
                Material.LEATHER, Material.FEATHER, Material.STRING, Material.HONEYCOMB
        ));

        // Tools & Weapons
        CATEGORIES.put("Tools", Arrays.asList(
                Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE,
                Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE, Material.BOW,
                Material.CROSSBOW, Material.TRIDENT, Material.SHIELD,
                Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
                Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE
        ));

        // Armor
        CATEGORIES.put("Armor", Arrays.asList(
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                Material.LEATHER_HELMET, Material.ELYTRA
        ));

        // Redstone
        CATEGORIES.put("Redstone", Arrays.asList(
                Material.REDSTONE, Material.REDSTONE_TORCH, Material.REPEATER,
                Material.COMPARATOR, Material.PISTON, Material.STICKY_PISTON,
                Material.HOPPER, Material.DROPPER, Material.DISPENSER,
                Material.OBSERVER, Material.TNT, Material.LEVER,
                Material.STONE_BUTTON, Material.STONE_PRESSURE_PLATE, Material.TRIPWIRE_HOOK
        ));

        // Decoration
        CATEGORIES.put("Decoration", Arrays.asList(
                Material.PAINTING, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME,
                Material.ARMOR_STAND, Material.FLOWER_POT, Material.TORCH,
                Material.LANTERN, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
                Material.BELL, Material.CANDLE, Material.WHITE_CARPET,
                Material.WHITE_BANNER, Material.SHIELD
        ));

        // Mob Drops
        CATEGORIES.put("Mob Drops", Arrays.asList(
                Material.ROTTEN_FLESH, Material.BONE, Material.SPIDER_EYE,
                Material.GUNPOWDER, Material.STRING, Material.SLIME_BALL,
                Material.ENDER_PEARL, Material.BLAZE_ROD, Material.GHAST_TEAR,
                Material.MAGMA_CREAM, Material.PHANTOM_MEMBRANE, Material.SHULKER_SHELL,
                Material.DRAGON_BREATH, Material.TOTEM_OF_UNDYING, Material.NETHER_STAR
        ));
    }

    public MaterialSelectorGUI(MythicItemHandler mythicItemHandler) {
        this.mythicItemHandler = mythicItemHandler;
    }

    /**
     * Open the category selection GUI.
     */
    public void openCategorySelector(Player player, Consumer<Material> onSelect, Consumer<String> onMythicSelect) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text("Select Item Category"));

        int slot = 10;
        for (String category : CATEGORIES.keySet()) {
            ItemStack item = createCategoryItem(category);
            inv.setItem(slot, item);
            slot++;
            if (slot == 17) slot = 19; // Skip to next row
        }

        // Add Mythic Items category if enabled
        if (mythicItemHandler != null && mythicItemHandler.isEnabled()) {
            ItemStack mythicCategory = createMythicCategoryItem();
            inv.setItem(16, mythicCategory);
        }

        // Add glass panes for decoration
        ItemStack grayPane = createGlassPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, grayPane);
            }
        }

        openGUIs.put(inv, new MaterialSelectorData(null, onSelect, onMythicSelect));
        player.openInventory(inv);
    }

    /**
     * Open the material selection GUI for a specific category.
     */
    public void openMaterialSelector(Player player, String category, Consumer<Material> onSelect, Consumer<String> onMythicSelect) {
        List<Material> materials = CATEGORIES.get(category);
        if (materials == null) {
            return;
        }

        int size = ((materials.size() + 8) / 9) * 9; // Round up to nearest multiple of 9
        size = Math.min(54, Math.max(27, size)); // Between 27 and 54 slots

        Inventory inv = Bukkit.createInventory(null, size, Component.text("Select " + category));

        for (int i = 0; i < materials.size() && i < size - 9; i++) {
            Material material = materials.get(i);
            inv.setItem(i, createMaterialItem(material));
        }

        // Back button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta meta = backButton.getItemMeta();
        meta.displayName(Component.text("← Back", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        backButton.setItemMeta(meta);
        inv.setItem(size - 5, backButton);

        openGUIs.put(inv, new MaterialSelectorData(category, onSelect, onMythicSelect));
        player.openInventory(inv);
    }

    /**
     * Open the mythic items selection GUI.
     */
    public void openMythicItemSelector(Player player, Consumer<String> onMythicSelect, Consumer<Material> onVanillaSelect) {
        if (mythicItemHandler == null || !mythicItemHandler.isEnabled()) {
            return;
        }

        List<String> mythicItemIds = mythicItemHandler.getAllMythicItemIds();

        int size = ((mythicItemIds.size() + 8) / 9) * 9;
        size = Math.min(54, Math.max(27, size));

        Inventory inv = Bukkit.createInventory(null, size, Component.text("Select Mythic Item", NamedTextColor.DARK_PURPLE));

        for (int i = 0; i < mythicItemIds.size() && i < size - 9; i++) {
            String mythicId = mythicItemIds.get(i);
            ItemStack mythicItem = mythicItemHandler.getMythicItem(mythicId, 1);

            if (mythicItem != null) {
                // Add lore to indicate this is a mythic item
                ItemMeta meta = mythicItem.getItemMeta();
                List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
                lore.add(Component.empty());
                lore.add(Component.text("Click to select", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                mythicItem.setItemMeta(meta);

                inv.setItem(i, mythicItem);
            }
        }

        // Back button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta meta = backButton.getItemMeta();
        meta.displayName(Component.text("← Back", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        backButton.setItemMeta(meta);
        inv.setItem(size - 5, backButton);

        openGUIs.put(inv, new MaterialSelectorData("Mythic Items", onVanillaSelect, onMythicSelect));
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        MaterialSelectorData data = openGUIs.get(inventory);

        if (data == null) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        // Check if back button
        if (clicked.getType() == Material.ARROW) {
            player.closeInventory();
            openCategorySelector(player, data.onSelect, data.onMythicSelect);
            return;
        }

        // If in category view, open material selector
        if (data.category == null) {
            // Check if mythic category was clicked
            if (clicked.getType() == Material.NETHER_STAR) {
                player.closeInventory();
                openMythicItemSelector(player, data.onMythicSelect, data.onSelect);
                return;
            }

            String categoryName = getCategoryFromItem(clicked);
            if (categoryName != null) {
                player.closeInventory();
                openMaterialSelector(player, categoryName, data.onSelect, data.onMythicSelect);
            }
            return;
        }

        // Check if in mythic items view
        if ("Mythic Items".equals(data.category)) {
            // Get mythic ID from the item
            String mythicId = mythicItemHandler.getMythicId(clicked);
            if (mythicId != null) {
                player.closeInventory();
                data.onMythicSelect.accept(mythicId);
            }
            return;
        }

        // Material selected
        Material selected = clicked.getType();
        player.closeInventory();
        data.onSelect.accept(selected);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openGUIs.remove(event.getInventory());
    }

    private ItemStack createCategoryItem(String category) {
        Material icon = switch (category) {
            case "Building" -> Material.BRICKS;
            case "Resources" -> Material.DIAMOND;
            case "Food" -> Material.COOKED_BEEF;
            case "Farming" -> Material.WHEAT;
            case "Tools" -> Material.DIAMOND_PICKAXE;
            case "Armor" -> Material.DIAMOND_CHESTPLATE;
            case "Redstone" -> Material.REDSTONE;
            case "Decoration" -> Material.PAINTING;
            case "Mob Drops" -> Material.BONE;
            default -> Material.CHEST;
        };

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(category, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        List<Material> materials = CATEGORIES.get(category);
        meta.lore(List.of(
                Component.text(materials.size() + " items", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMythicCategoryItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Mythic Items ✦", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<String> mythicItems = mythicItemHandler.getAllMythicItemIds();
        meta.lore(Arrays.asList(
                Component.text("Rare drops from mythic bosses", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Storm-infused gear and materials", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(mythicItems.size() + " items", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMaterialItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = formatMaterialName(material);
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private String formatMaterialName(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return result.toString();
    }

    private String getCategoryFromItem(ItemStack item) {
        Material type = item.getType();
        for (Map.Entry<String, List<Material>> entry : CATEGORIES.entrySet()) {
            // Check if this is the category icon
            ItemStack categoryItem = createCategoryItem(entry.getKey());
            if (categoryItem.getType() == type) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static class MaterialSelectorData {
        final String category; // null if in category view
        final Consumer<Material> onSelect;
        final Consumer<String> onMythicSelect;

        MaterialSelectorData(String category, Consumer<Material> onSelect, Consumer<String> onMythicSelect) {
            this.category = category;
            this.onSelect = onSelect;
            this.onMythicSelect = onMythicSelect;
        }
    }
}