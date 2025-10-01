# BetterShop Refactor Progress

## Implementation Status

### ✅ Phase 1: Data Model Refactor (COMPLETED)

**Created Files:**
- `shop/ShopEntity.java` - Shop entity class with name, listings, and territory tracking
- `shop/Listing.java` - Renamed from Shop.java, represents individual chest listings
- `shop/ListingType.java` - Enum for SELL/BUY listing types
- `shop/ShopType.java` - Marked as @Deprecated for backward compatibility
- `shop/ShopEntityManager.java` - Manager for shop entity operations

**Updated Files:**
- `shop/ShopRegistry.java` - Added dual tracking for ShopEntity and Listing objects
- `config/ConfigManager.java` - Added new configuration methods for shop mode and limits

**Key Features:**
- ShopEntity tracks multiple listings under one named shop
- Listing has UUID-based shop association
- Registry supports efficient lookups for both shops and listings
- Backward compatibility maintained with deprecated ShopType

---

### ✅ Phase 2: Shop Mode System (COMPLETED)

**Created Files:**
- `mode/ShopModeSession.java` - Session tracking with timeout and distance checks
- `mode/ShopModeManager.java` - Core shop mode logic and lifecycle management
- `mode/ShopModeItems.java` - Special chest item creation with NBT tags
- `listeners/ShopModeListener.java` - Event handling for shop mode

**Key Features:**
- Infinite SELL and BUY chest items with NBT tags
- Hotbar injection (slots 7-8) with state saving
- Automatic timeout after configurable inactivity period
- Distance-based exit when player moves too far
- Action bar UI showing shop name and commands
- Protection against dropping or moving shop mode items
- Territory validation on chest placement
- Listing limit enforcement

**Configuration Added:**
```yaml
shops:
  maxShopsPerPlayer: 3
  maxListingsPerShop: 20
  defaultShopName: "{player}'s Shop"

shopMode:
  enabled: true
  timeoutMinutes: 10
  maxDistance: 100.0
  saveHotbar: true

territory:
  autoDetect: true
  restrictToTerritory: true
  requireCommercialPlot: false
  allowWilderness: false
```

---

### 🔄 Phase 3: Listing Creation Flow (TODO)

**To Create:**
- `ui/ListingConfigGUI.java` - GUI for configuring SELL listings
- `ui/BuyListingConfigGUI.java` - GUI for configuring BUY listings
- Update `listeners/ShopModeListener.java` - Wire up GUI opening

**Requirements:**
- Price input (anvil GUI or chat-based)
- Quantity limit input
- Item detection for SELL listings
- Material selector integration for BUY listings
- Validation and confirmation flow

---

### 📋 Phase 4: Command Updates (TODO)

**To Update:**
- `commands/ShopCommand.java` - Add new subcommands

**New Commands:**
- `/shop create {name}` - Create shop and enter mode
- `/shop mode [name]` - Enter shop mode
- `/shop mode exit` - Exit shop mode
- `/shop rename {name}` - Rename shop
- `/shop delete` - Delete entire shop
- `/shop select {name}` - Set active shop

**Updated Commands:**
- `/shop list` - Show shops instead of listings
- `/shop info` - Show both listing and parent shop info
- `/shop collect` - Support collection from all listings
- `/shop remove` - Remove listing (not whole shop)

---

### 🗺️ Phase 5: Territory Integration (TODO)

**To Implement:**
- Territory detection on shop creation
- Plot/region validation
- Territory ID assignment
- Commercial plot checking (Towny)
- Territory restriction enforcement

**Files to Update:**
- `integration/TerritoryManager.java` - Add new methods
- `integration/TownyTerritoryManager.java` - Implement detection
- `integration/TownsAndNationsTerritoryManager.java` - Implement detection

---

### 🎨 Phase 6: UI Updates (TODO)

**Files to Update:**
- `ui/SignRenderer.java` - Add shop name to signs
- `ui/HologramManager.java` - Add shop name to holograms
- `ui/TradeGUI.java` - Add shop name to inventory title
- `src/main/resources/messages.yml` - Add new messages

**New Messages Needed:**
```yaml
shop-created: "Shop '{shop_name}' created!"
shop-mode-entered: "Entered shop mode: {shop_name}"
shop-mode-exited: "Exited shop mode."
listing-created: "Listing created: {type} {item} @ ${price}"
shop-territory-assigned: "Shop assigned to: {territory}"
shop-territory-mismatch: "Must place listings in {territory}!"
shop-name-taken: "You already have a shop named '{shop_name}'!"
listing-limit-reached: "Shop has reached max {limit} listings!"
```

---

### 💾 Phase 7: Data Migration (TODO)

**To Create:**
- `storage/DataMigration.java` - V1 to V2 migration logic
- Update `storage/ShopDataManager.java` - New save/load format

**Migration Steps:**
1. Detect V1 data format (no version field or version < 2)
2. Backup existing data to `.v1.backup`
3. Generate ShopEntity for each player (default name)
4. Convert old shops → listings
5. Assign listings to generated shops
6. Save in V2 format with version field

**New Data Format:**
```json
{
  "version": 2,
  "shops": [...],
  "listings": [...]
}
```

---

### 🧪 Phase 8: Testing & Validation (TODO)

**Test Cases:**
- Shop creation and mode entry
- Listing creation (SELL and BUY)
- Shop mode timeout and distance exit
- Territory restrictions
- Data migration from V1
- Backward compatibility
- Multi-shop management
- Shop deletion cascading

---

## Architecture Summary

### Data Model
```
ShopEntity (1) ──< (N) Listing
    ↓                    ↓
  UUID              Location + Item
  Name              Type (SELL/BUY)
  Owner             Price + Earnings
  Territory         Stock (dynamic)
  Listings[]        Reserved Stock
```

### Shop Mode Flow
```
1. /shop create {name}
2. ShopEntity created with territory detection
3. Enter shop mode → hotbar injection
4. Place SELL/BUY chests → Config GUI
5. Listing created → Associated with shop
6. Exit mode → hotbar restored
```

### Registry Architecture
```
ShopRegistry
├── shopsById: UUID → ShopEntity
├── shopsByOwnerAndName: UUID → (Name → UUID)
├── listingsById: UUID → Listing
├── listingsByLocation: Location → Listing
└── listingsByShop: ShopUUID → Listing[]
```

---

## Next Steps

**Immediate Priority:**
1. ✅ Complete Phase 3 (Listing Config GUIs)
2. Update Phase 4 (Commands)
3. Implement Phase 5 (Territory)
4. Update Phase 6 (UI Components)
5. Create Phase 7 (Migration)
6. Test Phase 8 (Validation)

**Testing Priority:**
- Unit tests for ShopEntity and Listing
- Shop mode session tests
- Registry lookup tests
- Migration tests with sample data

**Documentation Priority:**
- Update README.md with new commands
- Update CLAUDE.md with architecture
- Add user guide for shop mode

---

## Known Issues / TODOs

- [ ] ShopCommand needs full refactor for new command structure
- [ ] ShopManager (current) needs rename to ListingManager
- [ ] Update all event handlers to use Listing instead of Shop
- [ ] Update API classes for external plugin compatibility
- [ ] Create backward compatibility layer for old API methods
- [ ] Update TerritoryManager interface with new methods
- [ ] Messages.yml needs new placeholders and messages
- [ ] Config.yml needs full update with new structure

---

## Backward Compatibility Notes

**Maintained:**
- ShopType enum (deprecated but functional)
- Old config paths with fallbacks
- Registry methods for location-based lookups

**Breaking Changes:**
- Internal API: Shop → Listing rename
- Data format: V1 → V2 (migration handles this)
- Command syntax: `/shop create` now requires name

**Migration Path:**
- Auto-migration on first load
- Backup created automatically
- Old commands still work with warnings
- Default shop names generated for existing data
