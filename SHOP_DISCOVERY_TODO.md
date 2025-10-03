# Shop Discovery System - Integration Plan

## Status: Partially Implemented in SilkRoad, Needs BetterShop Integration

### Overview
The Shop Discovery System (shop directory GUI + map markers) was initially implemented in SilkRoad but should actually be a **BetterShop feature** since it applies to all shops, not just Silk Road enabled ones.

### What Exists in SilkRoad (Needs Moving)
Located in `/var/repos/SilkRoad/src/main/java/dev/ked/silkroad/`:

**Shop Discovery:**
- `shops/ShopInfo.java` - Shop data model (redundant - use BetterShop's Listing)
- `shops/ShopRegistry.java` - Shop registry (redundant - BetterShop has one)
- `shops/ShopSearchFilter.java` - **Keep concept, adapt for Listing**
- `gui/ShopDirectoryGUI.java` - **Move to BetterShop**

**Map Integration:**
- `map/MapIntegration.java` - **Move to BetterShop**
- `map/BlueMapIntegration.java` - **Move to BetterShop**
- `map/DynmapIntegration.java` - **Move to BetterShop**
- `map/SquaremapIntegration.java` - **Move to BetterShop**
- `map/MapManager.java` - **Move to BetterShop**

### BetterShop's Existing Structure
- `Shop.java` - Already has `silkRoadEnabled` and `reservedStock` fields ✅
- `ShopRegistry.java` - Tracks ShopEntity and Listing objects ✅
- `Listing.java` - Individual chest shops (use this instead of ShopInfo)
- `ShopEntity.java` - Shop business with multiple listings

### Implementation Plan

#### Phase 1: Move Map Integration to BetterShop
1. Copy map integration files to `/var/repos/BetterShop/src/main/java/dev/ked/bettershop/map/`
2. Adapt them to use `Listing` instead of `ShopInfo`
3. Add MapManager to BetterShop plugin initialization
4. Add config option: `maps.enabled: true`

#### Phase 2: Shop Directory in BetterShop
1. Create `ShopSearchFilter.java` in `/var/repos/BetterShop/src/main/java/dev/ked/bettershop/discovery/`
   - Works with `Listing` objects
   - Filters: item type, listing type (SELL/BUY), price range, stock, distance, owner
   - Sorting: price, stock, distance
2. Create `ShopDirectoryGUI.java` in `/var/repos/BetterShop/src/main/java/dev/ked/bettershop/ui/`
   - Browse all listings
   - Filter and sort
   - Click to teleport or view details
3. Add command: `/shop browse` or `/shop directory`
4. Add permission: `bettershop.directory`

#### Phase 3: SilkRoad Integration
SilkRoad should **consume** BetterShop's directory, not provide its own:

1. **Contract Browser Integration:**
   - Use BetterShop's ShopRegistry to find Silk Road enabled shops
   - Filter: `listing.getShop().isSilkRoadEnabled()`
   - Show "In Transit" stock in GUI

2. **Map Marker Enhancement:**
   - BetterShop shows all shop markers
   - SilkRoad adds overlay/badge for Silk Road enabled shops
   - SilkRoad adds "X in transit" to marker tooltips
   - Different icon or color for shops with active contracts

3. **Commands:**
   - `/shop browse` - All shops (BetterShop)
   - `/shop browse --silkroad` - Only Silk Road shops (BetterShop with SilkRoad filter)
   - `/sr shops` - Alias that opens BetterShop directory with silkroad filter

### Map Marker Display Logic

**Base Markers (BetterShop):**
- All listings shown on map
- Green = SELL shops
- Blue = BUY shops
- Tooltip: Owner, Item, Price, Stock

**SilkRoad Overlay:**
- Gold star/badge on Silk Road enabled shops
- Add to tooltip: "Silk Road Enabled"
- Add to tooltip: "X items in transit" if reserved stock > 0

### File Organization

```
BetterShop/
├── src/main/java/dev/ked/bettershop/
│   ├── map/
│   │   ├── MapIntegration.java
│   │   ├── MapManager.java
│   │   ├── BlueMapIntegration.java
│   │   ├── DynmapIntegration.java
│   │   └── SquaremapIntegration.java
│   ├── discovery/
│   │   └── ShopSearchFilter.java
│   └── ui/
│       └── ShopDirectoryGUI.java

SilkRoad/
├── src/main/java/dev/ked/silkroad/
│   ├── integration/
│   │   └── BetterShopMapOverlay.java  (adds silk road markers)
│   └── gui/
│       └── ContractBrowserGUI.java  (uses BetterShop's registry)
```

### Commands After Refactor

**BetterShop:**
- `/shop browse` - Open shop directory
- `/shop browse <item>` - Filter by item
- `/shop browse --selling` - Show only SELL shops
- `/shop browse --buying` - Show only BUY shops

**SilkRoad:**
- `/sr shops` - Alias for `/shop browse --silkroad`
- `/sr contracts` - Browse delivery contracts (uses BetterShop API)

### Config Options

**BetterShop config.yml:**
```yaml
maps:
  enabled: true
  refresh_interval: 300  # seconds
  bluemap: true
  dynmap: true
  squaremap: true

directory:
  enabled: true
  max_results_per_page: 45
  default_sort: PRICE_LOW_TO_HIGH
```

**SilkRoad config.yml:**
```yaml
integration:
  bettershop:
    map_overlay: true  # Add silk road badges to map
    directory_filter: true  # Add silk road filter to shop browse
```

### Benefits of This Architecture

1. **Separation of Concerns:**
   - BetterShop = Shop management + discovery
   - SilkRoad = Delivery contracts + progression

2. **Better UX:**
   - Players can browse ALL shops, not just Silk Road ones
   - Map shows complete shop landscape
   - Silk Road features are additive, not separate

3. **Standalone Functionality:**
   - BetterShop works without SilkRoad
   - SilkRoad enhances BetterShop when both present

4. **Code Reuse:**
   - One shop directory for everyone
   - One map integration system
   - No duplicate GUIs

### Migration Steps

1. ✅ Document current state
2. ✅ Move map integration to BetterShop
   - MapIntegration.java - Base class adapted for Listing
   - BlueMapIntegration.java - POI markers for listings
   - DynmapIntegration.java - Marker API for listings
   - SquaremapIntegration.java - Layer system for listings
   - MapManager.java - Coordinates all 3 integrations
3. ✅ Create shop directory in BetterShop
   - ShopSearchFilter.java - Adapted for Listing objects with silkRoadOnly filter
   - ShopDirectoryGUI.java - 54-slot paginated browser with filter/sort
   - /shop browse command - Added to ShopCommand
   - /shop browse --silkroad - Filter for Silk Road shops
   - GUIListener.java - Click handling for directory GUI
4. ✅ Update SilkRoad to use BetterShop's API
   - Updated SilkRoadCommand.java to use BetterShop's ShopDirectoryGUI
   - /sr shops and /sr directory now open BetterShop's directory with silkRoadOnly=true filter
   - Removed handleShopDirectoryClick from SilkRoad's GUIListener
5. ✅ Remove duplicate code from SilkRoad
   - Deleted /shops/ directory (ShopInfo, ShopRegistry, ShopSearchFilter)
   - Deleted /map/ directory (MapIntegration, BlueMap, Dynmap, Squaremap, MapManager)
   - Deleted ShopDirectoryGUI.java from /gui/
6. ✅ Integration complete
   - MapManager registered in BetterShopPlugin.onEnable()
   - GUIListener registered in BetterShopPlugin.registerListeners()
   - Public getters added for MapManager and GUIListener
   - MapManager.clearAllMarkers() called in BetterShopPlugin.onDisable()
7. ⏳ Test integration (ready for testing)

### Notes
- The refactor can be done incrementally
- Keep SilkRoad's version working until BetterShop's is ready
- BetterShop's `Shop.java` already has `silkRoadEnabled` field - perfect for filtering!
- Map markers should be added by BetterShop, enhanced by SilkRoad
