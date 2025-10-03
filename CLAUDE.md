# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**BetterShop** is a Paper Minecraft plugin (1.21.x, Java 17+) that provides an intuitive player-to-player shop system. Players can create chest-based shops with clean visual presentation (signs, holograms) and interact through either quick-buy or a modern GUI interface.

This plugin runs on the **Stormcraft** Minecraft server, deployed on a VPS via Pterodactyl panel.

## Deployment Context

### Server Infrastructure
- **Server**: Stormcraft Minecraft server
- **Platform**: VPS with Pterodactyl panel
- **Pterodactyl Volume**: `/var/lib/pterodactyl/volumes/<server-id>/plugins/`
- **Build Target**: When building the JAR, output directly to the Pterodactyl plugins folder for immediate deployment

### Build Process
When compiling this plugin:
- Build the JAR using Maven: `mvn clean package`
- Output the JAR directly to the Pterodactyl volume plugins folder instead of `target/`
- This allows for immediate plugin reload/restart without manual file copying

Example build command:
```bash
mvn clean package && cp target/BetterShop-*.jar /var/lib/pterodactyl/volumes/<server-id>/plugins/
```

Or configure Maven to output directly to the deployment location.

## Architecture

### Core Components

- **ShopManager**: Handles shop creation, removal, and transaction processing
- **ShopRegistry**: Maintains active shops and coordinates persistence
- **TradeGUI**: Provides inventory-based UI for browsing and purchasing with quantity selection
- **SignRenderer**: Formats shop signs with clean, colorful display
- **HologramManager**: Creates floating text displays above shops using armor stands
- **ShopDataManager**: JSON-based persistence for shops and transaction history

### Shop Model

Each shop has:
- **Location**: Chest block location
- **Owner**: UUID of shop creator
- **Type**: BUY (players buy from shop) or SELL (players sell to shop)
- **Item**: ItemStack being traded (with NBT data preserved)
- **Price**: Cost per item
- **Stock**: Tracked from chest contents automatically
- **Earnings**: Accumulated money waiting for owner to collect

### Package Structure

```
dev.ked.bettershop
 ├─ BetterShopPlugin.java (main plugin class)
 ├─ shop/
 │   ├─ Shop.java (shop model)
 │   ├─ ShopType.java (enum: BUY, SELL)
 │   ├─ ShopManager.java (business logic)
 │   └─ ShopRegistry.java (active shop tracking)
 ├─ ui/
 │   ├─ SignRenderer.java (sign formatting)
 │   ├─ HologramManager.java (floating text)
 │   └─ TradeGUI.java (inventory GUI)
 ├─ commands/ShopCommand.java
 ├─ listeners/
 │   ├─ ShopInteractListener.java (chest clicks)
 │   └─ ShopProtectionListener.java (prevent breaking)
 ├─ storage/ShopDataManager.java
 └─ config/ConfigManager.java
```

## Key Implementation Details

### Shop Creation Flow

1. Player runs `/shop create buy <price>` with item in hand
2. Plugin enters "placement mode" for that player
3. Player right-clicks a chest to designate it as shop
4. Shop object created and registered
5. Sign placed on chest (if available surface)
6. Hologram spawned above chest
7. Shop saved to data.json

### Shop Interaction

**Quick Buy** (Shift + Right-click):
- Instant purchase of 1 item
- Fast path for common transactions
- Sound + message feedback

**GUI Mode** (Right-click):
- Opens custom inventory showing:
  - Item display with full lore/enchantments
  - Quantity selector buttons (1, 8, 16, 32, 64, custom)
  - Dynamic price calculation
  - Stock indicator
  - Confirm purchase button
- All transactions validated (stock, money, inventory space)

### Visual Components

**Signs**:
- Line 1: [BUY] or [SELL] with color coding
- Line 2: Item name (truncated if needed)
- Line 3: Price per item
- Line 4: Stock: X remaining

**Holograms** (armor stands):
- Invisible, marker, no gravity
- Custom name visible through walls
- Shows: "🛒 <Item> - $<price>"
- Updates when stock changes

### Transaction Processing

Buy shop transaction:
1. Verify buyer has money
2. Verify shop has stock in chest
3. Verify buyer has inventory space
4. Remove items from chest
5. Take money from buyer via Vault
6. Add money to shop earnings
7. Update hologram/sign
8. Notify both parties

Sell shop transaction:
1. Verify seller has items
2. Verify shop owner has money in earnings pool
3. Verify chest has space
4. Take items from seller
5. Add items to chest
6. Give money to seller via Vault
7. Deduct from shop earnings pool
8. Update displays

### Persistence

Save to `data.json` on:
- Shop creation/removal
- Every 5 minutes (auto-save)
- Server shutdown

Store for each shop:
- Location (world, x, y, z)
- Owner UUID
- Shop type
- Item (serialized with NBT)
- Price
- Accumulated earnings
- Creation timestamp

Transaction history (last 50 per shop):
- Buyer/seller UUID
- Quantity
- Total price
- Timestamp

### Protection

Prevent:
- Breaking shop chests
- Breaking attached signs
- Placing hoppers/droppers adjacent to shops
- Explosions damaging shops
- Pistons moving shop blocks
- Non-owners accessing shop chest inventory directly (must use shop interface)

## Commands

Base command: `/shop` (aliases: `/bshop`, `/playershop`)

- `/shop create <buy|sell> <price>` - Create shop with held item (requires `bettershop.create`)
- `/shop remove` - Remove shop you're looking at (requires `bettershop.remove` or ownership)
- `/shop info` - View shop details and earnings (requires `bettershop.info`)
- `/shop collect` - Collect earnings from nearby shop (requires ownership)
- `/shop list` - List all your shops (requires `bettershop.list`)
- `/shop reload` - Reload config (requires `bettershop.admin.reload`)
- `/shop admin remove` - Force remove any shop (requires `bettershop.admin`)

## Configuration Files

### config.yml
```yaml
# Economy settings
economy:
  enabled: true
  transactionTax: 0.0  # Percentage tax on sales (0.05 = 5%)

# Shop limits
limits:
  maxShopsPerPlayer: 10
  maxShopDistance: 5  # Max distance to interact

# Visual settings
visuals:
  hologramsEnabled: true
  particlesEnabled: true
  signFormat:
    buyColor: "<green>"
    sellColor: "<blue>"

# GUI settings
gui:
  enabled: true
  quickBuyOnShiftClick: true

# Allowed worlds
enabledWorlds:
  - world
  - world_nether
  - world_the_end

# Protection
protection:
  preventHoppers: true
  preventExplosions: true
  preventPistons: true
```

### messages.yml
- All player messages with MiniMessage support
- Placeholders: `{price}`, `{item}`, `{quantity}`, `{total}`, `{stock}`, `{owner}`

## Development Notes

### Technology Stack
- Paper API 1.21.3
- Vault API (economy)
- Java 17
- Build tool: Maven

### Dependencies
- **Vault**: Required for economy (hard dependency)
- **PlaceholderAPI**: Optional for placeholders

### Performance Considerations
- Shop lookups by chunk coordinate for O(1) access
- Hologram updates only on stock changes, not per tick
- GUI inventories cached and reused when possible
- Transaction validation early-exits on failure conditions

### Edge Cases
- Handle shop chest destruction (remove shop, notify owner)
- Handle owner going offline during GUI interaction
- Handle shop becoming full (sell shops)
- Handle shop running out of stock (buy shops)
- Handle world unload with active GUI open
- Handle currency overflow (max transaction limits)
- Validate item matching for sell shops (exact match including NBT)
