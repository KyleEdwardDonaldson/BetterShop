package dev.ked.bettershop;

import dev.ked.bettershop.commands.ShopCommand;
import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.integration.TerritoryManager;
import dev.ked.bettershop.integration.TownyTerritoryManager;
import dev.ked.bettershop.integration.TownsAndNationsTerritoryManager;
import dev.ked.bettershop.listeners.ShopInteractListener;
import dev.ked.bettershop.listeners.ShopProtectionListener;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopRegistry;
import dev.ked.bettershop.storage.ShopDataManager;
import dev.ked.bettershop.ui.HologramManager;
import dev.ked.bettershop.ui.MaterialSelectorGUI;
import dev.ked.bettershop.ui.SignRenderer;
import dev.ked.bettershop.ui.TradeGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main plugin class for BetterShop.
 */
public class BetterShopPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private ShopRegistry shopRegistry;
    private ShopManager shopManager;
    private ShopDataManager dataManager;
    private SignRenderer signRenderer;
    private HologramManager hologramManager;
    private MaterialSelectorGUI materialSelector;
    private TradeGUI tradeGUI;
    private TerritoryManager territoryManager;
    private Economy economy;

    private BukkitTask autoSaveTask;

    @Override
    public void onEnable() {
        // Check for Vault
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! BetterShop requires Vault for economy support.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        configManager = new ConfigManager(this);
        configManager.reload();

        // Set up territory integration
        territoryManager = setupTerritoryIntegration();

        shopRegistry = new ShopRegistry();
        shopManager = new ShopManager(shopRegistry, economy);
        shopManager.setTerritoryManager(territoryManager);

        dataManager = new ShopDataManager(getDataFolder().toPath(), getLogger());

        signRenderer = new SignRenderer(configManager, shopManager);
        hologramManager = new HologramManager(configManager, shopManager, shopRegistry);
        materialSelector = new MaterialSelectorGUI();
        tradeGUI = new TradeGUI(configManager, shopManager, economy);

        // Load shops from disk
        dataManager.loadShops(shopRegistry);

        // Recreate holograms and signs for loaded shops
        for (var shop : shopRegistry.getAllShops()) {
            signRenderer.createOrUpdateSign(shop);
            hologramManager.createHologram(shop);
        }

        // Register commands
        ShopCommand shopCommand = new ShopCommand(this, shopManager, shopRegistry, configManager,
                signRenderer, hologramManager, materialSelector, economy);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        // Register listeners
        getServer().getPluginManager().registerEvents(shopCommand, this);
        getServer().getPluginManager().registerEvents(materialSelector, this);
        getServer().getPluginManager().registerEvents(new ShopInteractListener(shopManager, configManager, tradeGUI, economy), this);
        getServer().getPluginManager().registerEvents(new ShopProtectionListener(shopManager, shopRegistry, configManager, signRenderer, hologramManager), this);
        getServer().getPluginManager().registerEvents(tradeGUI, this);

        // Start auto-save task (every 5 minutes)
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            dataManager.saveShops(shopRegistry);
        }, 6000L, 6000L); // 5 minutes in ticks

        getLogger().info("BetterShop has been enabled with " + shopRegistry.getAllShops().size() + " shops!");
    }

    @Override
    public void onDisable() {
        // Cancel auto-save task
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        // Save all shops
        if (dataManager != null && shopRegistry != null) {
            dataManager.saveShops(shopRegistry);
        }

        // Remove all holograms
        if (hologramManager != null) {
            hologramManager.removeAllHolograms();
        }

        getLogger().info("BetterShop has been disabled!");
    }

    /**
     * Set up Vault economy.
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
     * Set up territory plugin integration (Towny/Towns and Nations).
     */
    private TerritoryManager setupTerritoryIntegration() {
        // Check for Towny
        if (configManager.isTownyEnabled() && getServer().getPluginManager().getPlugin("Towny") != null) {
            getLogger().info("Towny detected! Enabling Towny integration.");
            return new TownyTerritoryManager(configManager);
        }

        // Check for Towns and Nations
        if (configManager.isTownsAndNationsEnabled() && getServer().getPluginManager().getPlugin("TownsAndNations") != null) {
            getLogger().info("Towns and Nations detected! Enabling TaN integration.");
            return new TownsAndNationsTerritoryManager(configManager, economy);
        }

        getLogger().info("No territory plugin integration enabled.");
        return null;
    }

    /**
     * Reload configuration and update all shops.
     */
    public void reloadConfiguration() {
        configManager.reload();

        // Update all shop visuals
        for (var shop : shopRegistry.getAllShops()) {
            signRenderer.createOrUpdateSign(shop);
            hologramManager.updateHologram(shop);
        }

        getLogger().info("Configuration reloaded!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ShopRegistry getShopRegistry() {
        return shopRegistry;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public TerritoryManager getTerritoryManager() {
        return territoryManager;
    }
}