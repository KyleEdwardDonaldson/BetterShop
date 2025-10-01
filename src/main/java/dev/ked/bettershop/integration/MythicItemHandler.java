package dev.ked.bettershop.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MythicMobs item integration for BetterShop.
 * Uses reflection to avoid compile-time dependency on MythicMobs.
 */
public class MythicItemHandler {
    private final Plugin plugin;
    private boolean enabled = false;

    public MythicItemHandler(Plugin plugin) {
        this.plugin = plugin;
        try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            this.enabled = true;
            plugin.getLogger().info("MythicMobs item integration enabled");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("MythicMobs not found - mythic items disabled");
        }
    }

    /**
     * Get a MythicMobs item by internal name.
     *
     * @param mythicId The internal MythicMobs item ID
     * @param amount The amount of items to generate
     * @return ItemStack of the mythic item, or null if not found
     */
    public ItemStack getMythicItem(String mythicId, int amount) {
        if (!enabled) return null;

        try {
            // Use reflection to avoid compile-time dependency
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object itemManager = mythicBukkitClass.getMethod("getItemManager").invoke(mythicBukkit);

            Object itemOptional = itemManager.getClass()
                .getMethod("getItem", String.class)
                .invoke(itemManager, mythicId);

            if ((boolean) itemOptional.getClass().getMethod("isPresent").invoke(itemOptional)) {
                Object mythicItem = itemOptional.getClass().getMethod("get").invoke(itemOptional);
                ItemStack item = (ItemStack) mythicItem.getClass()
                    .getMethod("generateItemStack", int.class)
                    .invoke(mythicItem, amount);
                return item;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get MythicMobs item: " + mythicId);
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Check if an item is a specific MythicMobs item.
     *
     * @param item The ItemStack to check
     * @param mythicId The MythicMobs item ID to compare against
     * @return true if the item matches the mythic ID
     */
    public boolean isMythicItem(ItemStack item, String mythicId) {
        if (!enabled || item == null) return false;

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object itemManager = mythicBukkitClass.getMethod("getItemManager").invoke(mythicBukkit);

            // Get internal name from item
            String internalName = (String) itemManager.getClass()
                .getMethod("getMythicTypeFromItem", ItemStack.class)
                .invoke(itemManager, item);

            return mythicId.equalsIgnoreCase(internalName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if MythicMobs item exists.
     *
     * @param mythicId The MythicMobs item ID to check
     * @return true if the item exists in MythicMobs
     */
    public boolean mythicItemExists(String mythicId) {
        if (!enabled) return false;

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object itemManager = mythicBukkitClass.getMethod("getItemManager").invoke(mythicBukkit);

            Object itemOptional = itemManager.getClass()
                .getMethod("getItem", String.class)
                .invoke(itemManager, mythicId);

            return (boolean) itemOptional.getClass().getMethod("isPresent").invoke(itemOptional);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all available MythicMobs item IDs.
     *
     * @return List of mythic item IDs
     */
    public List<String> getAllMythicItemIds() {
        if (!enabled) return new ArrayList<>();

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object itemManager = mythicBukkitClass.getMethod("getItemManager").invoke(mythicBukkit);

            @SuppressWarnings("unchecked")
            Object items = itemManager.getClass().getMethod("getItems").invoke(itemManager);

            List<String> itemIds = new ArrayList<>();
            if (items instanceof Iterable) {
                for (Object item : (Iterable<?>) items) {
                    String internalName = (String) item.getClass().getMethod("getInternalName").invoke(item);
                    itemIds.add(internalName);
                }
            }
            return itemIds;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get MythicMobs item list");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Get the MythicMobs internal ID from an ItemStack.
     *
     * @param item The ItemStack to check
     * @return The mythic item ID, or null if not a mythic item
     */
    public String getMythicId(ItemStack item) {
        if (!enabled || item == null) return null;

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object itemManager = mythicBukkitClass.getMethod("getItemManager").invoke(mythicBukkit);

            String internalName = (String) itemManager.getClass()
                .getMethod("getMythicTypeFromItem", ItemStack.class)
                .invoke(itemManager, item);

            return internalName;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if MythicMobs integration is enabled.
     *
     * @return true if MythicMobs is available
     */
    public boolean isEnabled() {
        return enabled;
    }
}
