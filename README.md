# 🏪 BetterShop

An intuitive player-to-player shop system for Minecraft Paper 1.21.3+ with GUI trading, holograms, and sign displays.

## Features

- **Two Shop Types:**
  - **SELL Shops** - You sell items TO players (they buy from your stock)
  - **BUY Shops** - You buy items FROM players (they sell to you)

- **Visual Displays:**
  - Clean signs on chests showing item, price, and stock
  - Floating holograms above shops (🛒 Item - $Price (Stock left))
  - Modern GUI for bulk purchases with quantity selectors

- **Easy Management:**
  - Automatic stock tracking from chest contents
  - Real-time updates when items are added/removed
  - Earnings collection system
  - Shop protection from griefing (hoppers, explosions, pistons)

- **Material Selector:**
  - Create BUY shops for items you don't own yet
  - Browse 9 categories of materials
  - Over 100 items to choose from

- **Territory Integration:** *(Optional)*
  - **Towny** support - Wilderness toggle, commercial plots, build permissions
  - **Towns and Nations** support - Territory claims, permissions, treasury funding
  - **Tax System** - Shop earnings tax and transaction tax for outsiders
  - **Treasury Funding** - BUY shops funded by town treasury (TaN only)

## Installation

1. **Requirements:**
   - Paper/Spigot 1.21.3+
   - Java 17+
   - Vault (required)
   - An economy plugin (EssentialsX, etc.)
   - *Optional:* Towny or Towns and Nations for territory integration

2. **Build from Source:**
   ```bash
   # For Towny support only:
   mvn clean package

   # For Towns and Nations support:
   # Place TownsAndNations-0.15.4.jar in project root first
   mvn clean package
   ```
   - Place `target/bettershop-0.1.0.jar` in `plugins/` folder
   - Restart server

3. **Optional - Territory Integration:**
   - Install either Towny or Towns and Nations plugin on your server
   - Enable in `config.yml` (see Territory Integration section below)

## Usage

### Creating a SELL Shop (You sell items TO players)

**Recommended: Set up BEFORE placing chest to avoid theft:**
1. Run: `/shop create sell 5` (sell for $5 each)
2. **NOW place a new chest** OR right-click an existing chest with items
3. Shop will detect item type from chest contents

**Alternative: Use existing chest:**
1. Place chest and add items (e.g., 64 diamonds)
2. Run: `/shop create sell 5`
3. Right-click the chest

Players will buy from your stock, and you earn money (collected via `/shop collect`).

### Creating a BUY Shop (You buy items FROM players)

**Option 1: With Item in Hand (Fast)**
1. Hold an item in your hand (the item you want to buy)
2. Run: `/shop create buy 10` (pay $10 each)
3. Place a new chest OR right-click an existing empty chest

**Option 2: Without Item (Material Selector)**
1. Run: `/shop create buy 10` (no item in hand)
2. Select category in GUI (Building, Resources, Food, etc.)
3. Select specific material
4. Place a new chest OR right-click an existing empty chest

Perfect for buying items you don't have yet! When players sell to your shop:
- Items go into the chest (only you can access it)
- Money is deducted from your Vault balance automatically
- You can retrieve items by opening the chest

### Trading with Shops

**As a Customer:**
- **SELL shops:** Shift-click for quick buy, or right-click for GUI
- **BUY shops:** Shift-click for quick sell, or right-click for GUI

**As Shop Owner:**
- Right-click your own shop chest to access inventory directly
- Add/remove items to restock SELL shops
- Collect purchased items from BUY shop chests

### Managing Your Shops

- `/shop info` - View shop details (look at shop)
- `/shop collect` - Collect earnings (look at shop)
- `/shop list` - List all your shops
- `/shop remove` - Remove a shop (look at shop)

### Restocking

Simply open the shop chest and add/remove items. Signs and holograms update automatically!

## Territory Integration

BetterShop integrates with **Towny** and **Towns and Nations** for advanced territory-based shop features.

### Features

**Shop Creation Restrictions:**
- Require shops to be created in claimed territory only
- Respect build permissions in towns/territories
- Optional: Allow shops in wilderness (configurable)
- Optional: Require commercial plots (Towny only)

**Tax System:**
- **Shop Tax** - Town/territory receives % of shop earnings when collected
- **Transaction Tax** - Outsiders pay extra tax when buying/selling at shops
- All taxes automatically deposited to town/territory treasury

**Treasury Features (Towns and Nations only):**
- BUY shops can be funded by town treasury
- Shop owner must be member of the town
- Automatic withdrawal from town treasury for purchases

**Territory Display:**
- Error messages show territory names
- Tax notifications show which town/territory received the payment

### Configuration

**Towny Integration:**
```yaml
towny:
  enabled: true
  allowWilderness: true              # Allow shops in wilderness
  requireCommercialPlot: false       # Require commercial/embassy plots
  crossNationRestrictions: false     # Prevent cross-nation trading
  shopTax:
    enabled: true
    rate: 0.05                       # 5% of earnings go to town treasury
  transactionTax:
    enabled: true
    outsiderRate: 0.10               # 10% tax for non-town members
```

**Towns and Nations Integration:**
```yaml
townsandnations:
  enabled: true
  allowWilderness: false             # Require claimed territory
  shopTax:
    enabled: true
    rate: 0.05                       # 5% of earnings go to town treasury
  transactionTax:
    enabled: true
    outsiderRate: 0.15               # 15% tax for non-town members
  treasuryFunding:
    enabled: true                    # Allow town treasury to fund BUY shops
```

### How Taxes Work

**Shop Earnings Tax:**
1. Player collects earnings from SELL shop: `/shop collect`
2. Tax is calculated based on territory's shop tax rate
3. Player receives earnings minus tax
4. Tax is deposited to town/territory treasury
5. Notification shows: "Tax paid: $5.00 to [Town Name]"

**Transaction Tax:**
1. Outsider (non-member) buys/sells at a shop
2. Additional tax is calculated on the transaction
3. Buyer pays extra OR seller receives less
4. Tax is deposited to town/territory treasury
5. Notification shows: "Transaction tax: $2.00 ([Town Name])"

**Example:**
- Shop in "Oakville" sells diamonds for $100 each
- Oakville has 5% shop tax and 10% outsider transaction tax
- Shop owner collects $1000 earnings → receives $950 ($50 to town)
- Outsider buys 1 diamond → pays $110 ($10 to town)
- Town member buys 1 diamond → pays $100 (no transaction tax)

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop create sell <price>` | Create a sell shop | `bettershop.create` |
| `/shop create buy <price>` | Create a buy shop | `bettershop.create` |
| `/shop remove` | Remove a shop | `bettershop.remove` |
| `/shop info` | View shop details | `bettershop.info` |
| `/shop collect` | Collect earnings | Default |
| `/shop list` | List your shops | `bettershop.list` |
| `/shop reload` | Reload config | `bettershop.admin.reload` |

## Configuration

### Basic Configuration

`config.yml`:
```yaml
economy:
  enabled: true
  transactionTax: 0.0  # Base tax on sales (0.05 = 5%)

limits:
  maxShopsPerPlayer: 10
  maxShopDistance: 5.0

visuals:
  hologramsEnabled: true
  particlesEnabled: true
  signFormat:
    buyColor: "<green>"
    sellColor: "<blue>"

gui:
  enabled: true
  quickBuyOnShiftClick: true

enabledWorlds:
  - world

protection:
  preventHoppers: true
  preventExplosions: true
  preventPistons: true
```

### Territory Integration Configuration

See the **Territory Integration** section above for detailed Towny and Towns and Nations configuration options.

## Permissions

- `bettershop.create` - Create shops (default: true)
- `bettershop.remove` - Remove own shops (default: true)
- `bettershop.info` - View shop info (default: true)
- `bettershop.list` - List own shops (default: true)
- `bettershop.admin` - All admin permissions (default: op)
- `bettershop.admin.reload` - Reload config (default: op)

## Example Workflows

**Selling Diamonds (SELL shop):**
1. Run `/shop create sell 100` (sell for $100 each)
2. Place a new chest, add 64 diamonds
3. Chest automatically becomes a shop!
4. Customers buy diamonds → your stock decreases
5. `/shop collect` to get your earnings ($)

**Buying Wheat (BUY shop):**
1. Run `/shop create buy 5` (pay $5 each)
2. Select "Farming" → "Wheat" in GUI
3. Place a new chest (no items needed)
4. Players sell wheat → items appear in chest
5. Make sure you have money in your Vault balance!
6. Open chest to retrieve purchased wheat

**Money Flow:**
- **SELL shops:** Customer pays $ → You collect earnings
- **BUY shops:** Your Vault balance pays $ → Customer receives money instantly

## Support

Report issues at: https://github.com/anthropics/claude-code/issues

## License

Apache License 2.0