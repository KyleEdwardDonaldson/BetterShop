# Towny & Towns and Nations Integration Status

## ✅ Completed

### Towny Integration (Full Implementation)
1. **Dependencies Added** - pom.xml updated with Towny
2. **TerritoryManager Interface** - Abstract interface for all territory plugins
3. **TownyTerritoryManager** - Full Towny integration implementation
4. **Config Settings** - All integration settings added to config.yml
5. **ConfigManager** - Getter methods for all settings
6. **Wired Up** - Territory managers initialized in BetterShopPlugin
7. **Shop Creation Checks** - Territory validation before shop creation
8. **Tax System** - Shop tax and transaction tax fully implemented
9. **Error Messages** - Territory-specific messages added

### Towns and Nations Integration (TaN by Leralix) - ✅ COMPLETED
1. **Dependencies Added** - pom.xml updated with TownsAndNations 0.15.4 JAR (system dependency)
2. **TownsAndNationsTerritoryManager** - Full TaN integration implementation
3. **Config Settings** - All integration settings added to config.yml
4. **ConfigManager** - Getter methods for all TaN settings
5. **Wired Up** - Auto-detection of TownsAndNations plugin
6. **✅ API Integration** - Uses TownsAndNations 0.15.4 internal API:
   - `ClaimManager` - Chunk claim checking and territory lookup
   - `PlayerManager` - Player data and town membership
   - `TerritoryManager` - Town/region data access
   - `TownDataStorage` - Direct access for treasury operations
7. **Tax System** - Shop earnings tax and transaction tax fully implemented
8. **Treasury Funding** - BUY shops can be funded by town treasury
9. **Territory Checks** - Build permission validation for shop creation

## 📋 Integration Features

### Towny Features
✅ Wilderness toggle (default: true)
✅ Commercial plot requirement (default: false)
✅ Build permission checks
✅ Shop earnings tax (town treasury)
✅ Transaction tax for outsiders
✅ Cross-nation trade restrictions
✅ Territory name display

### Towns and Nations Features
✅ Claimed territory requirement (default: false for wilderness)
✅ Build permission checks via TanAPI
✅ Shop earnings tax (town treasury)
✅ Transaction tax for outsiders
✅ Treasury funding for BUY shops
✅ Territory name display
✅ Chunk-based claim system
✅ Player town membership verification

## 🔧 Config Example

```yaml
towny:
  enabled: true
  allowWilderness: true
  requireCommercialPlot: false
  crossNationRestrictions: false
  shopTax:
    enabled: true
    rate: 0.05  # Town gets 5%
  transactionTax:
    enabled: true
    outsiderRate: 0.10  # 10% for outsiders

townsandnations:
  enabled: true
  allowWilderness: false
  shopTax:
    enabled: true
    rate: 0.05
  transactionTax:
    enabled: true
    outsiderRate: 0.15
  treasuryFunding:
    enabled: true  # Town pays for BUY shops
```

## 🎯 How It Works

### Shop Creation
1. Player runs `/shop create <type> <price>`
2. BetterShop checks active territory manager (Towny or TaN)
3. Validates wilderness permissions
4. Checks build permissions in claimed territory
5. Validates commercial plot requirements (Towny only)
6. Creates shop if all checks pass

### Transactions
1. Player buys/sells at a shop
2. Transaction tax calculated for outsiders
3. Tax paid to town/nation treasury
4. Player receives tax notification

### Earnings Collection
1. Shop owner collects earnings
2. Shop tax calculated based on territory
3. Tax paid to town/nation treasury
4. Owner receives net earnings
5. Owner receives tax notification

## 📝 API Usage

### Towny
- Uses Towny API v0.100.3.0
- Accessed via `TownyAPI.getInstance()`
- Key classes: `TownBlock`, `Town`, `Resident`

### Towns and Nations
- Uses TownsAndNations 0.15.4 internal API
- Accessed via singleton managers:
  - `ClaimManager.getInstance()` - Chunk claims and territory lookup
  - `PlayerManager` - Player data and membership
  - `TerritoryManager.getInstance()` - Town/region data
  - `TownDataStorage.getInstance()` - Direct treasury operations
- Key interfaces:
  - `TanPlayer` - Player wrapper with town membership
  - `TanTerritory` - Territory wrapper with permissions
  - `TanTown` - Town-specific data
  - `TanClaimedChunk` - Chunk claim information

## ✨ Status

**Towny Integration**: ✅ Fully functional and production-ready!

**Towns and Nations Integration**: ✅ Fully functional and production-ready!

Both integrations are now complete with full API implementation. The plugin uses TownsAndNations 0.15.4 JAR as a system dependency for compilation. All territory features including permissions, taxes, and treasury operations are fully implemented.

**Setup Requirements**:
- Place `TownsAndNations-0.15.4.jar` in the project root for compilation
- Install TownsAndNations plugin on your server for runtime
- Enable the appropriate integration in `config.yml`

The plugin will automatically detect which territory plugin is installed and use the appropriate integration. Only one territory plugin should be enabled at a time in config.yml.
