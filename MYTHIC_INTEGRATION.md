# MythicMobs Integration for BetterShop

This document outlines the integration needed to sell MythicMobs-related items in BetterShop, specifically boss drops and custom items from the Stormcraft-Events plugin.

---

## Overview

**Goal:** Allow players to purchase MythicMobs custom items (especially boss drops from Storm Titan, Tempest Guardian, etc.) through the BetterShop UI.

**Challenge:** BetterShop's buy UI is preset with vanilla categories. We need to extend it to support MythicMobs custom items while maintaining the existing UI flow.

---

## Required Changes

### 1. Add MythicMobs Item Category

**File:** Category configuration (likely in `config.yml` or category definition file)

**New Category Structure:**
```yaml
categories:
  # ... existing categories (blocks, food, tools, etc.) ...

  mythic_items:
    display_name: "§5§lMythic Items"
    icon: NETHER_STAR  # Glowing star icon
    slot: 16  # Position in category selection GUI
    description:
      - "§7Rare drops from mythic bosses"
      - "§7Storm-infused gear and materials"
    permission: "bettershop.category.mythic"  # Optional permission
    items:
      # Storm Titan drops
      - type: MYTHIC_ITEM
        mythic_id: "storm_titan_core"
        display_name: "§c§l⚡ Storm Titan Core"
        buy_price: 50000
        sell_price: 25000
        stock: -1  # Unlimited

      - type: MYTHIC_ITEM
        mythic_id: "tempest_shard"
        display_name: "§b§lTempest Shard"
        buy_price: 10000
        sell_price: 5000

      - type: MYTHIC_ITEM
        mythic_id: "storm_infused_crystal"
        display_name: "§5§lStorm-Infused Crystal"
        buy_price: 5000
        sell_price: 2500

      # Crafting materials from events
      - type: MYTHIC_ITEM
        mythic_id: "corrupted_essence"
        display_name: "§8§lCorrupted Essence"
        buy_price: 1000
        sell_price: 500

      - type: MYTHIC_ITEM
        mythic_id: "rift_fragment"
        display_name: "§5§lRift Fragment"
        buy_price: 3000
        sell_price: 1500
```

---

### 2. MythicMobs Item Handler

**New Class:** `MythicItemHandler.java` (or extend existing item handler)

**Purpose:** Interface with MythicMobs API to create, validate, and compare custom items.

```java
package dev.ked.bettershop.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Handles MythicMobs item integration for BetterShop.
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
        }

        return null;
    }

    /**
     * Check if an item is a specific MythicMobs item.
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

    public boolean isEnabled() {
        return enabled;
    }
}
```

---

### 3. Shop Transaction Handler Updates

**File:** Transaction handler or purchase logic class

**Required Changes:**

```java
// When processing a purchase:
if (item.getType().equals("MYTHIC_ITEM")) {
    String mythicId = item.getMythicId();

    // Get the item from MythicMobs
    ItemStack mythicItem = mythicItemHandler.getMythicItem(mythicId, amount);

    if (mythicItem == null) {
        player.sendMessage("§cError: Mythic item not found!");
        return false;
    }

    // Add to player inventory
    player.getInventory().addItem(mythicItem);

    // Deduct currency
    economy.withdrawPlayer(player, totalCost);

    player.sendMessage("§aPurchased " + amount + "x " + mythicItem.getItemMeta().getDisplayName());
    return true;
}
```

**For Selling:**

```java
// When player sells an item:
if (category.hasType("MYTHIC_ITEM")) {
    for (ShopItem shopItem : category.getItems()) {
        if (shopItem.getType().equals("MYTHIC_ITEM")) {
            if (mythicItemHandler.isMythicItem(playerItem, shopItem.getMythicId())) {
                // This is the matching mythic item
                double sellPrice = shopItem.getSellPrice() * amount;
                economy.depositPlayer(player, sellPrice);
                playerItem.setAmount(playerItem.getAmount() - amount);
                return true;
            }
        }
    }
}
```

---

### 4. GUI Updates

**Category Selection GUI:**

Add a new slot (e.g., slot 16) with a **Nether Star** icon:
```java
ItemStack mythicCategory = new ItemStack(Material.NETHER_STAR);
ItemMeta meta = mythicCategory.getItemMeta();
meta.displayName(Component.text("§5§l✦ Mythic Items ✦"));
meta.lore(Arrays.asList(
    Component.text("§7Rare drops from mythic bosses"),
    Component.text("§7Storm-infused gear and materials"),
    Component.text(""),
    Component.text("§eClick to browse!")
));
meta.addEnchant(Enchantment.LUCK, 1, true);
meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
mythicCategory.setItemMeta(meta);

gui.setItem(16, mythicCategory);
```

**Item Browse GUI:**

When displaying mythic items, use the actual MythicMobs item model:
```java
for (ShopItem item : category.getItems()) {
    ItemStack displayItem;

    if (item.getType().equals("MYTHIC_ITEM")) {
        // Get actual mythic item for display
        displayItem = mythicItemHandler.getMythicItem(item.getMythicId(), 1);

        if (displayItem == null) {
            // Fallback to barrier block if item not found
            displayItem = new ItemStack(Material.BARRIER);
        }

        // Add lore with price info
        ItemMeta meta = displayItem.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
        lore.add(Component.text(""));
        lore.add(Component.text("§aBuy: §f" + item.getBuyPrice() + " essence"));
        lore.add(Component.text("§cSell: §f" + item.getSellPrice() + " essence"));
        meta.lore(lore);
        displayItem.setItemMeta(meta);
    } else {
        // Regular item handling
        displayItem = new ItemStack(Material.valueOf(item.getType()));
    }

    gui.addItem(displayItem);
}
```

---

## MythicMobs Item Definitions

**File:** `plugins/MythicMobs/Items/storm_boss_drops.yml`

These items should be created in MythicMobs:

```yaml
# Storm Titan Core - Main boss drop
storm_titan_core:
  Id: NETHER_STAR
  Display: '&c&l⚡ Storm Titan Core ⚡'
  Lore:
    - '&7The heart of a Storm Titan'
    - '&7Pulsing with raw storm energy'
    - ''
    - '&5&lLEGENDARY'
  Enchantments:
    - LUCK:1
  Options:
    Color: 255,50,50
    Glowing: true

# Tempest Shard - Guardian drop
tempest_shard:
  Id: PRISMARINE_SHARD
  Display: '&b&lTempest Shard'
  Lore:
    - '&7Fragment from a Tempest Guardian'
    - '&7Contains volatile storm magic'
    - ''
    - '&9&lRARE'
  Enchantments:
    - LUCK:1
  Options:
    Glowing: true

# Storm-Infused Crystal - Event reward
storm_infused_crystal:
  Id: AMETHYST_SHARD
  Display: '&5&lStorm-Infused Crystal'
  Lore:
    - '&7Crystallized storm energy'
    - '&7Found during Storm Surge events'
    - ''
    - '&9&lRARE'
  Options:
    Color: 150,50,255
    Glowing: true

# Corrupted Essence - Rift drop
corrupted_essence:
  Id: ECHO_SHARD
  Display: '&8&lCorrupted Essence'
  Lore:
    - '&7Essence corrupted by storm rifts'
    - '&7Useful for dark enchantments'
    - ''
    - '&a&lUNCOMMON'

# Rift Fragment - Rift defense reward
rift_fragment:
  Id: END_CRYSTAL
  Display: '&5&lRift Fragment'
  Lore:
    - '&7A piece of a storm rift'
    - '&7Residual dimensional energy'
    - ''
    - '&9&lRARE'
  Options:
    Glowing: true
```

---

## Configuration Example

**Full `mythic_items` category config:**

```yaml
categories:
  mythic_items:
    display_name: "§5§l✦ Mythic Items ✦"
    icon: NETHER_STAR
    slot: 16
    description:
      - "§7Rare drops from mythic bosses"
      - "§7Storm-infused gear and materials"
      - "§8These items are also obtainable"
      - "§8by defeating bosses and events"

    items:
      # Legendary Boss Drops
      storm_titan_core:
        type: MYTHIC_ITEM
        mythic_id: "storm_titan_core"
        buy_price: 50000
        sell_price: 25000
        buy_limit: 1  # Can only buy 1 at a time
        description:
          - "§7The heart of a Storm Titan"
          - "§c§lREQUIRES: 50,000 essence"

      # Rare Boss Drops
      tempest_shard:
        type: MYTHIC_ITEM
        mythic_id: "tempest_shard"
        buy_price: 10000
        sell_price: 5000
        buy_limit: 5

      storm_infused_crystal:
        type: MYTHIC_ITEM
        mythic_id: "storm_infused_crystal"
        buy_price: 5000
        sell_price: 2500
        buy_limit: 10

      # Event Materials
      corrupted_essence:
        type: MYTHIC_ITEM
        mythic_id: "corrupted_essence"
        buy_price: 1000
        sell_price: 500
        buy_limit: 64

      rift_fragment:
        type: MYTHIC_ITEM
        mythic_id: "rift_fragment"
        buy_price: 3000
        sell_price: 1500
        buy_limit: 16

      # Siege rewards
      siege_trophy:
        type: MYTHIC_ITEM
        mythic_id: "siege_trophy"
        buy_price: 2000
        sell_price: 1000
        buy_limit: 10
```

---

## Implementation Checklist

- [ ] **Add MythicItemHandler class** with reflection-based API
- [ ] **Update category config** to support `type: MYTHIC_ITEM`
- [ ] **Update transaction handler** to process mythic item purchases
- [ ] **Update sell handler** to recognize mythic items
- [ ] **Add mythic category slot** to GUI (slot 16 with Nether Star)
- [ ] **Create MythicMobs item definitions** in MM config
- [ ] **Test purchasing** mythic items
- [ ] **Test selling** mythic items back to shop
- [ ] **Add permission checks** for mythic category access (optional)
- [ ] **Update shop help command** to mention mythic items

---

## Testing Plan

1. **Install MythicMobs** on test server
2. **Create test items** using provided YAML definitions
3. **Configure BetterShop** with mythic category
4. **Test purchase flow:**
   - Open shop → Select "Mythic Items" → Buy item → Verify inventory
5. **Test sell flow:**
   - Obtain mythic item → Open shop → Sell item → Verify payment
6. **Test error handling:**
   - Missing mythic item in MM config
   - Insufficient funds
   - Full inventory

---

## Alternative: Simple Approach

If full MythicMobs integration is too complex, you could use a **simplified approach**:

### Mythic Items as Vanilla with NBT

Store mythic item metadata in BetterShop's own NBT tags:

```java
// When selling
ItemStack shopItem = new ItemStack(Material.NETHER_STAR);
ItemMeta meta = shopItem.getItemMeta();
meta.getPersistentDataContainer().set(
    new NamespacedKey(plugin, "mythic_id"),
    PersistentDataType.STRING,
    "storm_titan_core"
);
shopItem.setItemMeta(meta);
```

Then match against that NBT when players sell items. This avoids needing MythicMobs API entirely, but **won't work for actual MythicMobs drops** - only shop-purchased items.

---

## Recommended: Full MythicMobs Integration

For true integration with boss drops and event rewards, **use the full MythicMobs API approach** outlined above. This ensures:
- Players can sell actual boss drops
- Items match exactly what drops from events
- No duplication issues
- Consistent item stats/lore

---

## Questions to Resolve

1. **Currency:** Are players buying with essence (Vault economy) or another currency?
2. **Permissions:** Should mythic category require a special permission?
3. **Stock limits:** Should legendary items have daily/weekly purchase limits?
4. **Price balancing:** What should price ratios be vs. drop rates?
5. **Sell-back ratio:** Standard 50% or different for mythic items?

---

**Last Updated:** 2025-10-01
**Author:** Claude Code
