# Shop Discovery System - Refactoring Complete ✅

## Summary

The Shop Discovery System (shop directory GUI + map markers) has been successfully refactored from SilkRoad to BetterShop. This system now properly belongs to BetterShop as a core feature, with SilkRoad integrating as an enhancement layer.

## What Was Completed

### Phase 1: BetterShop Map Integration ✅

**Location**: `/var/repos/BetterShop/src/main/java/dev/ked/bettershop/map/`

**Files Created**:
- `MapIntegration.java` - Abstract base class for all map plugins
- `BlueMapIntegration.java` - POI marker integration for BlueMap
- `DynmapIntegration.java` - Marker API integration for Dynmap
- `SquaremapIntegration.java` - Layer system integration for Squaremap
- `MapManager.java` - Coordinates all 3 map plugin integrations

**Features**:
- Displays all BetterShop listings on dynamic maps
- Shows Silk Road enabled shops with gold star badges
- Displays "X in transit" stock information for Silk Road deliveries
- Different colors: Green for SELL listings, Blue for BUY listings
- Automatically refreshes when listings are added/removed

### Phase 2: BetterShop Shop Directory ✅

**Location**: `/var/repos/BetterShop/src/main/java/dev/ked/bettershop/`

**Files Created**:
- `discovery/ShopSearchFilter.java` - Filter and sort system for listings
  - Filters: item type, listing type (SELL/BUY), price range, stock, distance, owner, silkRoadOnly
  - Sorting: price (low/high), stock (low/high), distance (near/far)

- `ui/ShopDirectoryGUI.java` - 54-slot paginated shop browser
  - 45 listings per page with pagination
  - Filter by SELL/BUY/ALL
  - Sort by multiple criteria
  - Shows Silk Road badges
  - Displays "in transit" stock counts
  - Click to view location and owner

- `listeners/GUIListener.java` - Click handling for directory GUI

**Commands Added**:
```
/shop browse              # Browse all shops
/shop browse --silkroad   # Filter to only Silk Road enabled shops
/shop directory           # Alias for browse
```

**Permissions**:
- `bettershop.browse` - Access shop directory

### Phase 3: BetterShop Plugin Integration ✅

**Modified**: `/var/repos/BetterShop/src/main/java/dev/ked/bettershop/BetterShopPlugin.java`

**Changes**:
- Added `MapManager` and `GUIListener` fields
- Initialize `MapManager` in `onEnable()`
- Register `GUIListener` in `registerListeners()`
- Clear map markers in `onDisable()`
- Added public getters for `MapManager` and `GUIListener`

**Modified**: `/var/repos/BetterShop/src/main/java/dev/ked/bettershop/commands/ShopCommand.java`

**Changes**:
- Added `handleBrowse()` method
- Added "browse" and "directory" cases to command switch
- Added tab completion for browse command with `--silkroad` flag
- Added help text for `/shop browse`

### Phase 4: SilkRoad Integration ✅

**Modified**: `/var/repos/SilkRoad/src/main/java/dev/ked/silkroad/commands/SilkRoadCommand.java`

**Changes**:
- Updated `/sr shops` and `/sr directory` to use BetterShop's `ShopDirectoryGUI`
- Passes `silkRoadOnly=true` flag to filter to only Silk Road enabled shops
- Imports BetterShop's classes

**Modified**: `/var/repos/SilkRoad/src/main/java/dev/ked/silkroad/gui/GUIListener.java`

**Changes**:
- Removed `handleShopDirectoryClick()` method (now handled by BetterShop)
- Removed "Shop Directory" title check

### Phase 5: Cleanup ✅

**Deleted from SilkRoad**:
- `/shops/ShopInfo.java` - Duplicate data model (use BetterShop's Listing)
- `/shops/ShopRegistry.java` - Duplicate registry (use BetterShop's ShopRegistry)
- `/shops/ShopSearchFilter.java` - Duplicate filter (use BetterShop's version)
- `/map/MapIntegration.java` - Duplicate base class
- `/map/BlueMapIntegration.java` - Duplicate integration
- `/map/DynmapIntegration.java` - Duplicate integration
- `/map/SquaremapIntegration.java` - Duplicate integration
- `/map/MapManager.java` - Duplicate manager
- `/gui/ShopDirectoryGUI.java` - Duplicate GUI

## Architecture Benefits

### 1. Proper Separation of Concerns
- **BetterShop** = Shop management + discovery (core functionality)
- **SilkRoad** = Delivery contracts + progression (enhancement layer)

### 2. Universal Access
- All players can browse shops and see map markers
- Players without Silk Road still get full discovery features
- No fragmentation between "regular" and "Silk Road" shops

### 3. Single Source of Truth
- One shop directory for all players
- One map integration system
- No duplicate GUIs or conflicting data

### 4. Extensibility
- SilkRoad can add overlays and filters without duplicating code
- Other plugins can integrate with BetterShop's directory
- Map markers automatically include Silk Road status

### 5. Maintainability
- Changes to shop discovery only need to be made in one place
- Reduced code duplication
- Clear ownership of features

## How It Works

### For Regular Players:
```
/shop browse
```
- Opens shop directory showing all listings
- Can filter by SELL/BUY
- Can sort by price, stock, or distance
- Shows which shops have Silk Road enabled (gold star badge)

### For Silk Road Users:
```
/sr shops
```
- Opens shop directory filtered to only Silk Road enabled shops
- Same features as regular browse
- Shows "X in transit" for items being delivered
- Provides focused view of Silk Road economy

### Map Markers:
- **All listings** appear on BlueMap/Dynmap/Squaremap automatically
- **Green markers** = SELL shops
- **Blue markers** = BUY shops
- **Gold star badge** = Silk Road enabled
- **Tooltip shows**: Item, price, owner, stock, "X in transit" if applicable

## Testing Checklist

- [ ] `/shop browse` opens directory with all listings
- [ ] `/shop browse --silkroad` filters to only Silk Road shops
- [ ] `/sr shops` opens directory with Silk Road filter
- [ ] Filter buttons work (ALL/SELL/BUY)
- [ ] Sort buttons cycle through all options
- [ ] Pagination works (previous/next)
- [ ] Clicking listing shows location and owner info
- [ ] Map markers appear on BlueMap (if installed)
- [ ] Map markers appear on Dynmap (if installed)
- [ ] Map markers appear on Squaremap (if installed)
- [ ] Silk Road badges show on enabled listings
- [ ] "In transit" stock displays correctly
- [ ] GUI closes properly
- [ ] No errors in console

## Next Steps (Optional Enhancements)

### 1. SilkRoad Map Overlay
Could create a specialized overlay that adds extra styling to Silk Road markers:
- Different icon for Silk Road shops
- Pulsing animation for shops with active contracts
- Route lines showing delivery paths

### 2. Teleportation Integration
Add `/shop teleport` command to teleport to listings:
- Could cost money
- Could have cooldown
- Could require permission

### 3. Favorites System
Allow players to favorite shops:
- Quick access to preferred vendors
- Notifications when they restock

### 4. Shop Reviews
Rating and review system:
- Players can rate shops
- Leave comments
- Sort by rating

## Configuration

### BetterShop config.yml
Add these sections:
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

## Documentation Updated

- `/var/repos/BetterShop/SHOP_DISCOVERY_TODO.md` - Migration plan and progress
- `/var/repos/BetterShop/SHOP_DISCOVERY_COMPLETE.md` - This summary document
- Command help text updated in both plugins

## Compatibility

- **BetterShop**: Works standalone with full shop discovery features
- **SilkRoad**: Requires BetterShop, adds filter to directory
- **Map Plugins**: Optional - BlueMap, Dynmap, and/or Squaremap
- **Paper**: 1.21.3+
- **Java**: 21+

## Migration Notes

The refactoring was done in a way that maintains backward compatibility:
- Old SilkRoad shop data still works with BetterShop
- `Listing.silkRoadEnabled` flag is preserved
- `Listing.reservedStock` map continues to work
- No data migration required

## Success Criteria ✅

- [x] All map integration code moved to BetterShop
- [x] Shop directory implemented in BetterShop
- [x] SilkRoad commands use BetterShop's GUI
- [x] Duplicate files removed from SilkRoad
- [x] Plugin initialization updated
- [x] No compilation errors
- [x] Clean separation of concerns
- [x] Documentation complete

---

**Status**: Ready for testing
**Date Completed**: October 1, 2025
**Total Files Added**: 9
**Total Files Modified**: 4
**Total Files Deleted**: 9
