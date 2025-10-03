# BetterShop Refactor Implementation Document

## Overview
This document outlines the implementation plan for refactoring BetterShop from a single-chest-per-shop model to a hierarchical Shop → Listing architecture with shop mode functionality.

## Goals
1. Introduce Shop entities that contain multiple Listings
2. Implement Shop Mode with infinite chest placement
3. Integrate with Towny/Towns and Nations plot detection
4. Maintain backward compatibility with existing data
5. Preserve all existing features (Silk Road, taxes, GUI, etc.)

---

## Phase 1: Data Model Refactor

### 1.1 Create New Shop Entity Class
**File**: `src/main/java/dev/ked/bettershop/shop/Shop.java` → Rename to `Listing.java`
**File**: `src/main/java/dev/ked/bettershop/shop/ShopEntity.java` (new)

**ShopEntity.java**:
```java
- UUID id
- UUID owner
- String name
- List<UUID> listingIds
- String territoryId (Towny/TaN region/plot)
- long createdAt
- Location creationLocation (for territory reference)
```

### 1.2 Rename Shop to Listing
**Changes**:
- Rename `Shop.java` → `Listing.java`
- Add `UUID shopId` field to Listing
- Add `UUID id` field to Listing
- Update all references throughout codebase

**Affected Files**:
- `shop/Shop.java` → `shop/Listing.java`
- `shop/ShopManager.java` → `shop/ListingManager.java` (or keep ShopManager, add ListingManager)
- `shop/ShopRegistry.java` → Update to track both Shops and Listings
- `shop/ShopType.java` → `shop/ListingType.java`
- All event classes
- All listener classes
- All UI classes
- Command classes
- Storage classes

### 1.3 Update ShopRegistry
**File**: `src/main/java/dev/ked/bettershop/shop/ShopRegistry.java`

**Add Maps**:
```java
- Map<UUID, ShopEntity> shopsById
- Map<UUID, Map<String, UUID>> shopsByOwnerAndName  // owner -> (name -> shopId)
- Map<UUID, Listing> listingsById
- Map<UUID, List<UUID>> listingsByShop  // shopId -> listingIds
```

**Methods to Add**:
- `registerShop(ShopEntity shop)`
- `unregisterShop(UUID shopId)`
- `getShopById(UUID id)`
- `getShopByOwnerAndName(UUID owner, String name)`
- `getShopsByOwner(UUID owner)` - now returns ShopEntity list
- `getListingsByShop(UUID shopId)`
- `registerListing(Listing listing)`
- `unregisterListing(UUID listingId)`
- `getListingById(UUID id)`

### 1.4 Create Shop Manager
**File**: `src/main/java/dev/ked/bettershop/shop/ShopManager.java` (refactor existing or create new)

**Responsibilities**:
- Shop entity creation/removal
- Shop naming and validation
- Territory association
- Shop-wide earnings aggregation
- Shop limits enforcement

**Keep Existing ShopManager as ListingManager** (Option A - Recommended):
- Rename ShopManager → ListingManager (handles listing transactions)
- Create new ShopManager (handles shop entity operations)

**Or Extend ShopManager** (Option B):
- Keep ShopManager name
- Add shop entity methods alongside listing methods

---

## Phase 2: Shop Mode System

### 2.1 Create ShopMode Manager
**File**: `src/main/java/dev/ked/bettershop/mode/ShopModeManager.java`

**Class Structure**:
```java
class ShopModeManager {
    Map<UUID, ShopModeSession> activeSessions

    methods:
    - enterShopMode(Player player, UUID shopId)
    - exitShopMode(Player player)
    - isInShopMode(Player player)
    - getActiveSession(Player player)
    - handleTimeout()
    - handleDistanceCheck()
}
```

**ShopModeSession**:
```java
class ShopModeSession {
    UUID playerId
    UUID shopId
    long enteredAt
    Location entryLocation
    ItemStack[] previousHotbar  // To restore on exit
}
```

### 2.2 Create Special Chest Items
**File**: `src/main/java/dev/ked/bettershop/mode/ShopModeItems.java`

**Methods**:
```java
- ItemStack createSellChestItem()
- ItemStack createBuyChestItem()
- boolean isSellChest(ItemStack item)
- boolean isBuyChest(ItemStack item)
- boolean isShopModeChest(ItemStack item)
```

**Item Properties**:
- Custom NBT tag: `bettershop:mode_chest` = "sell" or "buy"
- Material: CHEST (green name for sell, blue for buy)
- Glowing effect
- Lore explaining usage
- Unbreakable, no stacking

### 2.3 Hotbar Injection
**Implementation in ShopModeManager**:
```java
enterShopMode(Player player, UUID shopId):
    1. Save current hotbar (slots 7-8)
    2. Create special chest items
    3. Set slot 7 = SELL chest
    4. Set slot 8 = BUY chest
    5. Send action bar message
    6. Store session
```

### 2.4 Shop Mode Listeners
**File**: `src/main/java/dev/ked/bettershop/listeners/ShopModeListener.java`

**Events to Handle**:
- `PlayerQuitEvent` → Auto-exit shop mode
- `PlayerTeleportEvent` → Check distance, exit if too far
- `BlockPlaceEvent` → Detect special chest placement
- `PlayerInteractEvent` → Prevent dropping special chests
- `InventoryClickEvent` → Prevent moving special chests out of hotbar
- `PlayerDropItemEvent` → Cancel if dropping special chest

### 2.5 Timeout & Distance Checks
**Implementation**: Scheduled task in ShopModeManager

```java
Bukkit.getScheduler().runTaskTimer():
    For each active session:
        - Check if timeout exceeded (configurable)
        - Check if player moved too far from entry location
        - If either true: exitShopMode(player)
```

---

## Phase 3: Listing Creation Flow

### 3.1 Listing Configuration GUIs
**File**: `src/main/java/dev/ked/bettershop/ui/ListingConfigGUI.java`

**For SELL Listings**:
- Inventory GUI (27 slots)
- Price input (anvil GUI or chat input)
- Quantity limit input
- Item detection/selection
- Confirm button

**For BUY Listings**:
- Reuse existing MaterialSelectorGUI
- Add price input
- Add buy limit input
- Confirm button

### 3.2 Update BlockPlaceEvent Handler
**File**: `src/main/java/dev/ked/bettershop/listeners/ShopModeListener.java`

**Logic**:
```java
@EventHandler
onBlockPlace(BlockPlaceEvent event):
    Player player = event.getPlayer()
    ItemStack item = event.getItemInHand()

    if (!ShopModeItems.isShopModeChest(item)):
        return

    ShopModeSession session = shopModeManager.getActiveSession(player)
    if (session == null):
        event.setCancelled(true)
        return

    Block placed = event.getBlockPlaced()

    // Validate territory restrictions
    if (!canPlaceListingHere(player, session.getShopId(), placed.getLocation())):
        event.setCancelled(true)
        player.sendMessage("Cannot place listing outside shop territory!")
        return

    if (ShopModeItems.isSellChest(item)):
        openSellListingConfig(player, session.getShopId(), placed.getLocation())
    else if (ShopModeItems.isBuyChest(item)):
        openBuyListingConfig(player, session.getShopId(), placed.getLocation())

    // Give back the special chest item (infinite)
    Bukkit.getScheduler().runTaskLater():
        restoreShopModeHotbar(player)
```

### 3.3 Listing Creation
**In ShopManager or ListingManager**:

```java
createListing(UUID shopId, Location location, ListingType type, ItemStack item, double price, int limit):
    1. Generate UUID for listing
    2. Create Listing object
    3. Associate with ShopEntity
    4. Register in ShopRegistry
    5. Create sign/hologram
    6. Save to storage
    7. Fire ListingCreatedEvent
```

---

## Phase 4: Command Updates

### 4.1 Update ShopCommand
**File**: `src/main/java/dev/ked/bettershop/commands/ShopCommand.java`

**New Subcommands**:
- `/shop create {name}` → Create shop entity, enter shop mode
- `/shop mode` → Re-enter shop mode for existing shop (if player has only one)
- `/shop mode {name}` → Enter shop mode for specific shop
- `/shop mode exit` → Exit shop mode
- `/shop rename {name}` → Rename current shop
- `/shop delete` → Delete entire shop (all listings)
- `/shop select {name}` → Set active shop for future operations

**Updated Subcommands**:
- `/shop list` → List shops (with listing counts)
- `/shop info` → Show listing info + parent shop info
- `/shop collect` → Collect from specific listing OR all listings in shop
- `/shop remove` → Remove specific listing (not whole shop)

**Backward Compatibility**:
- `/shop create buy 10` → Auto-create shop if needed, then create listing

### 4.2 Tab Completion Updates
**Add Completions**:
- `/shop mode` → Player's shop names
- `/shop rename` → Current shop name suggestion
- `/shop select` → Player's shop names

---

## Phase 5: Territory Integration

### 5.1 Plot/Region Detection
**File**: `src/main/java/dev/ked/bettershop/integration/TerritoryManager.java`

**New Methods**:
```java
- String detectTerritory(Location location)
- boolean isCommercialPlot(Location location)
- boolean canPlaceListingInTerritory(Player player, UUID shopId, Location location)
- String getTerritoryId(Location location)
```

**Implementations**:
- `TownyTerritoryManager.java` → Use Towny API for plot detection
- `TownsAndNationsTerritoryManager.java` → Use TaN API for territory detection

### 5.2 Shop Territory Assignment
**On Shop Creation**:
```java
/shop create {name}:
    1. Get player location
    2. Detect territory (if enabled)
    3. Validate player can create shop in territory
    4. Create ShopEntity with territoryId
    5. Restrict future listing placement to same territory
```

### 5.3 Listing Placement Validation
**On Listing Creation**:
```java
canPlaceListingHere(Player player, UUID shopId, Location location):
    1. Get ShopEntity by shopId
    2. Get shop's territoryId
    3. Get location's territoryId
    4. Compare:
        - If territoryId is null (wilderness): allow if config permits
        - If territoryIds match: allow
        - If territoryIds differ: deny
```

### 5.4 Configuration
**config.yml**:
```yaml
territory:
  autoDetect: true
  restrictToTerritory: true
  requireCommercialPlot: false  # Towny only
  allowWilderness: false
```

---

## Phase 6: UI Updates

### 6.1 Update Sign Renderer
**File**: `src/main/java/dev/ked/bettershop/ui/SignRenderer.java`

**Sign Format**:
```
Line 1: [SELL] or [BUY]
Line 2: {shop_name}
Line 3: {item} - ${price}
Line 4: Stock: {stock}
```

### 6.2 Update Hologram Manager
**File**: `src/main/java/dev/ked/bettershop/ui/HologramManager.java`

**Hologram Format**:
```
Line 1: {shop_name}
Line 2: {item} - ${price}
Line 3: Stock: {stock} ({reserved} in transit)
```

### 6.3 Update Trade GUI
**File**: `src/main/java/dev/ked/bettershop/ui/TradeGUI.java`

**GUI Header**:
- Add shop name to inventory title
- Format: "{shop_name} - {item}"

### 6.4 Update Messages
**File**: `src/main/resources/messages.yml`

**Add Placeholders**:
- `{shop_name}`
- `{shop_id}`
- `{listing_count}`
- `{territory}`

**New Messages**:
```yaml
shop-created: "<prefix><green>Shop '{shop_name}' created!"
shop-mode-entered: "<prefix><gray>Entered shop mode: <white>{shop_name}"
shop-mode-exited: "<prefix><gray>Exited shop mode."
listing-created: "<prefix><green>Listing created: {type} {item} @ ${price}"
shop-territory-assigned: "<prefix><gray>Shop assigned to: <white>{territory}"
shop-territory-mismatch: "<prefix><red>Must place listings in {territory}!"
shop-name-taken: "<prefix><red>You already have a shop named '{shop_name}'!"
shop-limit-reached: "<prefix><red>You can only have {limit} shops!"
listing-limit-reached: "<prefix><red>Shop '{shop_name}' has reached max {limit} listings!"
```

---

## Phase 7: Data Storage & Migration

### 7.1 Update Storage Format
**File**: `src/main/java/dev/ked/bettershop/storage/ShopDataManager.java`

**New JSON Structure**:
```json
{
  "version": 2,
  "shops": [
    {
      "id": "uuid",
      "owner": "uuid",
      "name": "Kyle's Emporium",
      "territoryId": "oakville_commercial_1",
      "createdAt": 1234567890,
      "creationLocation": {
        "world": "world",
        "x": 100,
        "y": 64,
        "z": 200
      }
    }
  ],
  "listings": [
    {
      "id": "uuid",
      "shopId": "uuid",
      "location": {...},
      "type": "SELL",
      "item": {...},
      "price": 100.0,
      "earnings": 500.0,
      "buyLimit": 0,
      "silkRoadEnabled": false,
      "reservedStock": {}
    }
  ]
}
```

### 7.2 Migration Logic
**File**: `src/main/java/dev/ked/bettershop/storage/DataMigration.java`

**Migration Steps**:
```java
migrateV1toV2():
    1. Load existing data.json (version 1 or unversioned)
    2. Backup to data.json.v1.backup
    3. For each owner:
        a. Create ShopEntity with name "{PlayerName}'s Shop"
        b. Generate UUID
        c. Assign all their listings to this shop
    4. Convert old "shops" array → "listings" array
    5. Add "shops" array with generated entities
    6. Set version = 2
    7. Save new format
    8. Log migration results
```

**Backward Compatibility Check**:
```java
onPluginLoad():
    if (data.json has no "version" field OR version < 2):
        runMigration()
```

### 7.3 Save/Load Methods
**Update Methods**:
- `saveShops()` → Save both shops and listings
- `loadShops()` → Load both shops and listings
- `saveShop(ShopEntity shop)`
- `saveListing(Listing listing)`

---

## Phase 8: Testing & Validation

### 8.1 Unit Tests
**Create Test Classes**:
- `ShopEntityTest.java`
- `ListingTest.java`
- `ShopRegistryTest.java`
- `ShopModeManagerTest.java`
- `DataMigrationTest.java`

### 8.2 Integration Tests
**Test Scenarios**:
1. Create shop → Enter shop mode → Place listing → Exit mode
2. Create multiple shops with same owner
3. Create listings in different territories (should fail)
4. Migration from V1 to V2 data format
5. Shop mode timeout and distance checks
6. Backward compatibility with old command syntax
7. Listing transactions with shop hierarchy
8. Earnings collection per listing vs per shop
9. Shop deletion with multiple listings
10. Silk Road integration with listings

### 8.3 Manual Testing Checklist
- [ ] Create shop with `/shop create {name}`
- [ ] Enter shop mode
- [ ] Place SELL chest, configure listing
- [ ] Place BUY chest, configure listing
- [ ] Exit shop mode manually
- [ ] Exit shop mode by timeout
- [ ] Exit shop mode by distance
- [ ] Create second shop
- [ ] Switch between shops with `/shop mode {name}`
- [ ] List all shops with `/shop list`
- [ ] View listing info with `/shop info`
- [ ] Collect earnings from listing
- [ ] Remove single listing
- [ ] Delete entire shop
- [ ] Test territory restrictions (Towny)
- [ ] Test territory restrictions (TaN)
- [ ] Test migration from old data
- [ ] Test backward compatible commands
- [ ] Test Silk Road with new structure

---

## Implementation Order

### Sprint 1: Foundation (Phases 1-2)
1. Create ShopEntity class
2. Rename Shop → Listing throughout codebase
3. Update ShopRegistry with new maps
4. Create ShopManager for shop entities
5. Create ShopModeManager
6. Create ShopModeItems
7. Create ShopModeListener

**Deliverable**: Data model in place, shop mode can be entered/exited

### Sprint 2: Listing Creation (Phase 3)
1. Create ListingConfigGUI
2. Update BlockPlaceEvent handler
3. Implement listing creation flow
4. Add validation logic
5. Test listing creation in shop mode

**Deliverable**: Players can create listings via shop mode

### Sprint 3: Commands & UI (Phases 4 & 6)
1. Update all commands
2. Add new subcommands
3. Update tab completion
4. Update sign renderer
5. Update hologram manager
6. Update trade GUI
7. Update messages.yml

**Deliverable**: Full command suite and updated visuals

### Sprint 4: Territory & Storage (Phases 5 & 7)
1. Implement territory detection
2. Add plot/region validation
3. Update storage format
4. Create migration logic
5. Test migration thoroughly

**Deliverable**: Territory integration complete, migration working

### Sprint 5: Testing & Polish (Phase 8)
1. Write unit tests
2. Write integration tests
3. Manual testing
4. Bug fixes
5. Documentation updates
6. Performance optimization

**Deliverable**: Production-ready release

---

## Rollback Plan

If critical issues arise:
1. Keep old data format as backup (.v1.backup)
2. Provide rollback command: `/shop admin rollback`
3. Rollback script restores old data and disables new features
4. Document known issues and workarounds

---

## Configuration Changes

**config.yml additions**:
```yaml
shops:
  maxShopsPerPlayer: 3
  maxListingsPerShop: 20
  defaultShopName: "{player}'s Shop"

shopMode:
  enabled: true
  timeoutMinutes: 10
  maxDistance: 100
  saveHotbar: true

territory:
  autoDetect: true
  restrictToTerritory: true
  requireCommercialPlot: false
  allowWilderness: false
```

---

## Performance Considerations

1. **Registry Lookups**: Keep location-based listing lookups as O(1)
2. **Shop Mode Sessions**: Use HashMap for O(1) session lookups
3. **Territory Checks**: Cache territory IDs to avoid repeated API calls
4. **GUI Rendering**: Reuse inventory instances where possible
5. **Data Saving**: Async saves for shops and listings

---

## API Changes (for external plugins)

**New API Methods**:
```java
BetterShopAPI:
    - ShopEntity getShopById(UUID id)
    - List<ShopEntity> getShops(UUID owner)
    - Listing getListingById(UUID id)
    - List<Listing> getListings(UUID shopId)
    - boolean isInShopMode(Player player)
```

**Deprecated Methods** (maintain for backward compatibility):
```java
@Deprecated Shop getShopAt(Location) → Use getListingAt(Location)
@Deprecated List<Shop> getShopsByOwner(UUID) → Use getShops(UUID)
```

---

## Documentation Updates

**Files to Update**:
1. README.md → New command syntax and concepts
2. CLAUDE.md → New architecture and data model
3. INTEGRATION-STATUS.md → New API methods
4. Wiki/docs → User guide for shop mode

---

## Success Criteria

- [ ] All existing features work with new architecture
- [ ] Shop mode is intuitive and bug-free
- [ ] Territory integration works with Towny and TaN
- [ ] Migration preserves all data without loss
- [ ] Performance is equal or better than before
- [ ] Backward compatibility with old commands
- [ ] No regressions in Silk Road integration
- [ ] All tests pass
- [ ] Documentation is complete

---

## Timeline Estimate

- Sprint 1: 3-4 days
- Sprint 2: 2-3 days
- Sprint 3: 3-4 days
- Sprint 4: 2-3 days
- Sprint 5: 2-3 days

**Total**: 12-17 days (assuming full-time work)

---

## Notes

- Maintain backward compatibility throughout
- Test migration extensively with real server data
- Consider phased rollout (beta testing with subset of players)
- Monitor performance after deployment
- Gather user feedback on shop mode UX
