package dev.ked.bazaar;

import dev.ked.bazaar.commands.ShopCommand;
import dev.ked.bazaar.config.ConfigManager;
import dev.ked.bazaar.integration.MythicItemHandler;
import dev.ked.bazaar.integration.TerritoryManager;
import dev.ked.bazaar.integration.TownyTerritoryManager;
import dev.ked.bazaar.integration.TownsAndNationsTerritoryManager;
import dev.ked.bazaar.listeners.GUIListener;
import dev.ked.bazaar.listeners.ShopModeListener;
import dev.ked.bazaar.map.MapManager;
import dev.ked.bazaar.mode.ShopModeManager;
import dev.ked.bazaar.shop.ShopEntityManager;
import dev.ked.bazaar.shop.ShopRegistry;
import dev.ked.bazaar.ui.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for BetterShop.
 */
public class BazaarPlugin extends JavaPlugin {
    // Core managers
    private ConfigManager configManager;
    private ShopRegistry shopRegistry;
    private ShopEntityManager shopEntityManager;
    private ShopModeManager shopModeManager;

    // UI components
    private SignRenderer signRenderer;
    private HologramManager hologramManager;
    private ListingConfigGUI listingConfigGUI;
    private BuyListingConfigGUI buyListingConfigGUI;

    // Map integration
    private MapManager mapManager;
    private GUIListener guiListener;

    // Territory integration
    private TerritoryManager territoryManager;

    // MythicMobs integration
    private MythicItemHandler mythicItemHandler;

    // Economy
    private Economy economy;

    @Override
    public void onEnable() {
        // Check for Vault
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! BetterShop requires Vault for economy support.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.reload();

        // Set up territory integration
        territoryManager = setupTerritoryIntegration();

        // Set up MythicMobs integration
        mythicItemHandler = new MythicItemHandler(this);

        // Initialize registry and managers
        shopRegistry = new ShopRegistry();
        shopEntityManager = new ShopEntityManager(shopRegistry, configManager);
        shopEntityManager.setTerritoryManager(territoryManager);

        // Initialize shop mode
        shopModeManager = new ShopModeManager(this, configManager, shopRegistry);

        // Initialize UI components
        signRenderer = new SignRenderer(configManager, shopRegistry);
        signRenderer.setMythicItemHandler(mythicItemHandler);

        hologramManager = new HologramManager(configManager, shopRegistry);
        hologramManager.setMythicItemHandler(mythicItemHandler);

        listingConfigGUI = new ListingConfigGUI(this, configManager, shopRegistry, shopEntityManager, hologramManager, signRenderer);
        buyListingConfigGUI = new BuyListingConfigGUI(this, configManager, shopRegistry, shopEntityManager, hologramManager, signRenderer, mythicItemHandler);

        // Initialize map integration
        mapManager = new MapManager(this, shopRegistry);
        mapManager.initializeIntegrations();

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Start shop mode check task
        shopModeManager.startCheckTask();

        getLogger().info("BetterShop v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        // Stop shop mode check task
        if (shopModeManager != null) {
            shopModeManager.stopCheckTask();
            shopModeManager.exitAllSessions();
        }

        // Clean up GUIs
        if (listingConfigGUI != null) {
            listingConfigGUI.cleanup();
            listingConfigGUI.unregister();
        }
        if (buyListingConfigGUI != null) {
            buyListingConfigGUI.cleanup();
            buyListingConfigGUI.unregister();
        }

        // Remove all holograms
        if (hologramManager != null) {
            hologramManager.removeAllHolograms();
        }

        // Clear map markers
        if (mapManager != null) {
            mapManager.clearAllMarkers();
        }

        getLogger().info("BetterShop disabled!");
    }

    /**
     * Register event listeners.
     */
    private void registerListeners() {
        ShopModeListener shopModeListener = new ShopModeListener(this, shopModeManager, shopEntityManager, configManager);
        getServer().getPluginManager().registerEvents(shopModeListener, this);

        // Register GUI listener for shop directory
        guiListener = new GUIListener(this);
        getServer().getPluginManager().registerEvents(guiListener, this);

        // Register GUI listeners
        listingConfigGUI.register();
        buyListingConfigGUI.register();
    }

    /**
     * Register commands.
     */
    private void registerCommands() {
        ShopCommand shopCommand = new ShopCommand(this, shopEntityManager, shopRegistry, shopModeManager, configManager);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);
    }

    /**
     * Set up economy integration with Vault.
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Set up territory integration (Towny or Towns and Nations).
     */
    private TerritoryManager setupTerritoryIntegration() {
        // Try Towny first
        if (configManager.isTownyEnabled() && getServer().getPluginManager().getPlugin("Towny") != null) {
            getLogger().info("Towny integration enabled!");
            return new TownyTerritoryManager(configManager);
        }

        // Try Towns and Nations
        if (configManager.isTownsAndNationsEnabled() && getServer().getPluginManager().getPlugin("TownsAndNations") != null) {
            getLogger().info("Towns and Nations integration enabled!");
            return new TownsAndNationsTerritoryManager(configManager, economy);
        }

        getLogger().info("No territory plugin found. Territory features disabled.");
        return null;
    }

    /**
     * Reload configuration.
     */
    public void reloadConfiguration() {
        configManager.reload();
        getLogger().info("Configuration reloaded!");
    }

    // ===== GETTERS FOR DEPENDENCIES =====

    public Economy getEconomy() {
        return economy;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ShopRegistry getShopRegistry() {
        return shopRegistry;
    }

    public ShopEntityManager getShopEntityManager() {
        return shopEntityManager;
    }

    public ShopModeManager getShopModeManager() {
        return shopModeManager;
    }

    public SignRenderer getSignRenderer() {
        return signRenderer;
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public GUIListener getGUIListener() {
        return guiListener;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public ListingConfigGUI getListingConfigGUI() {
        return listingConfigGUI;
    }

    public BuyListingConfigGUI getBuyListingConfigGUI() {
        return buyListingConfigGUI;
    }

    public TerritoryManager getTerritoryManager() {
        return territoryManager;
    }

    public MythicItemHandler getMythicItemHandler() {
        return mythicItemHandler;
    }
}
