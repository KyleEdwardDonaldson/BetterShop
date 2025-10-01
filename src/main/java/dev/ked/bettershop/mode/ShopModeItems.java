package dev.ked.bettershop.mode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Arrays;

/**
 * Utility class for creating and identifying shop mode special chest items.
 */
public class ShopModeItems {
    private static final String NBT_KEY = "bettershop_mode_chest";
    private static final String SELL_VALUE = "sell";
    private static final String BUY_VALUE = "buy";

    /**
     * Create a SELL chest item for shop mode.
     */
    public ItemStack createSellChestItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        // Set display name (green)
        meta.displayName(Component.text("SELL Chest", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Set lore
        meta.lore(Arrays.asList(
                Component.text("Place this chest to create a", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("SELL listing (you sell items TO players)", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("This chest is infinite!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        // Add glow effect
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Add NBT tag
        NamespacedKey key = new NamespacedKey("bettershop", NBT_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, SELL_VALUE);

        // Make unbreakable (prevent accidental destruction)
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a BUY chest item for shop mode.
     */
    public ItemStack createBuyChestItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        // Set display name (blue)
        meta.displayName(Component.text("BUY Chest", NamedTextColor.BLUE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Set lore
        meta.lore(Arrays.asList(
                Component.text("Place this chest to create a", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("BUY listing (you buy items FROM players)", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("This chest is infinite!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        // Add glow effect
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Add NBT tag
        NamespacedKey key = new NamespacedKey("bettershop", NBT_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, BUY_VALUE);

        // Make unbreakable
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if an item is a SELL chest.
     */
    public boolean isSellChest(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey("bettershop", NBT_KEY);

        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return false;
        }

        String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return SELL_VALUE.equals(value);
    }

    /**
     * Check if an item is a BUY chest.
     */
    public boolean isBuyChest(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey("bettershop", NBT_KEY);

        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return false;
        }

        String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return BUY_VALUE.equals(value);
    }

    /**
     * Check if an item is any shop mode chest.
     */
    public boolean isShopModeChest(ItemStack item) {
        return isSellChest(item) || isBuyChest(item);
    }
}
