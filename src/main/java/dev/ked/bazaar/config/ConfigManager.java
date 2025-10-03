package dev.ked.bazaar.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages plugin configuration files.
 */
public class ConfigManager {
    private final Plugin plugin;
    private final Logger logger;
    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Load or reload all configuration files.
     */
    public void reload() {
        // Save default config.yml if it doesn't exist
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load messages.yml
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from jar
        try (InputStream messagesStream = plugin.getResource("messages.yml")) {
            if (messagesStream != null) {
                YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(messagesStream));
                messages.setDefaults(defaultMessages);
            }
        } catch (IOException e) {
            logger.warning("Could not load default messages: " + e.getMessage());
        }

        logger.info("Configuration loaded successfully");
    }

    // Economy settings
    public boolean isEconomyEnabled() {
        return config.getBoolean("economy.enabled", true);
    }

    public double getTransactionTax() {
        return config.getDouble("economy.transactionTax", 0.0);
    }

    // Shop limits
    public int getMaxShopsPerPlayer() {
        return config.getInt("shops.maxShopsPerPlayer", config.getInt("limits.maxShopsPerPlayer", 3));
    }

    public int getMaxListingsPerShop() {
        return config.getInt("shops.maxListingsPerShop", 20);
    }

    public String getDefaultShopName() {
        return config.getString("shops.defaultShopName", "{player}'s Shop");
    }

    public double getMaxShopDistance() {
        return config.getDouble("limits.maxShopDistance", 5.0);
    }

    // Shop Mode settings
    public boolean isShopModeEnabled() {
        return config.getBoolean("shopMode.enabled", true);
    }

    public int getShopModeTimeoutMinutes() {
        return config.getInt("shopMode.timeoutMinutes", 10);
    }

    public double getShopModeMaxDistance() {
        return config.getDouble("shopMode.maxDistance", 100.0);
    }

    public boolean shouldSaveHotbar() {
        return config.getBoolean("shopMode.saveHotbar", true);
    }

    // Territory settings
    public boolean isTerritoryAutoDetectEnabled() {
        return config.getBoolean("territory.autoDetect", true);
    }

    public boolean isTerritoryRestrictionEnabled() {
        return config.getBoolean("territory.restrictToTerritory", true);
    }

    public boolean isCommercialPlotRequired() {
        return config.getBoolean("territory.requireCommercialPlot", false);
    }

    public boolean isWildernessAllowed() {
        // Check both old and new config locations for backward compatibility
        if (isTownyEnabled()) {
            return config.getBoolean("territory.allowWilderness", getTownyAllowWilderness());
        }
        if (isTownsAndNationsEnabled()) {
            return config.getBoolean("territory.allowWilderness", getTownsAndNationsAllowWilderness());
        }
        return config.getBoolean("territory.allowWilderness", false);
    }

    // Visual settings
    public boolean areHologramsEnabled() {
        return config.getBoolean("visuals.hologramsEnabled", true);
    }

    public boolean areParticlesEnabled() {
        return config.getBoolean("visuals.particlesEnabled", true);
    }

    public String getBuyColor() {
        return config.getString("visuals.signFormat.buyColor", "<green>");
    }

    public String getSellColor() {
        return config.getString("visuals.signFormat.sellColor", "<blue>");
    }

    // GUI settings
    public boolean isGuiEnabled() {
        return config.getBoolean("gui.enabled", true);
    }

    public boolean isQuickBuyOnShiftClick() {
        return config.getBoolean("gui.quickBuyOnShiftClick", true);
    }

    // World settings
    public List<String> getEnabledWorlds() {
        return config.getStringList("enabledWorlds");
    }

    public boolean isWorldEnabled(String worldName) {
        List<String> enabledWorlds = getEnabledWorlds();
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    // Protection settings
    public boolean shouldPreventHoppers() {
        return config.getBoolean("protection.preventHoppers", true);
    }

    public boolean shouldPreventExplosions() {
        return config.getBoolean("protection.preventExplosions", true);
    }

    public boolean shouldPreventPistons() {
        return config.getBoolean("protection.preventPistons", true);
    }

    // Towny Integration
    public boolean isTownyEnabled() {
        return config.getBoolean("towny.enabled", false);
    }

    public boolean getTownyAllowWilderness() {
        return config.getBoolean("towny.allowWilderness", true);
    }

    public boolean getTownyRequireCommercialPlot() {
        return config.getBoolean("towny.requireCommercialPlot", false);
    }

    public boolean getTownyShopTaxEnabled() {
        return config.getBoolean("towny.shopTax.enabled", false);
    }

    public double getTownyShopTaxRate() {
        return config.getDouble("towny.shopTax.rate", 0.05);
    }

    public boolean getTownyTransactionTaxEnabled() {
        return config.getBoolean("towny.transactionTax.enabled", false);
    }

    public double getTownyOutsiderTaxRate() {
        return config.getDouble("towny.transactionTax.outsiderRate", 0.10);
    }

    public boolean getTownyCrossNationRestrictions() {
        return config.getBoolean("towny.crossNationRestrictions", false);
    }

    // Towns and Nations Integration
    public boolean isTownsAndNationsEnabled() {
        return config.getBoolean("townsandnations.enabled", false);
    }

    public boolean getTownsAndNationsAllowWilderness() {
        return config.getBoolean("townsandnations.allowWilderness", false);
    }

    public boolean getTownsAndNationsShopTaxEnabled() {
        return config.getBoolean("townsandnations.shopTax.enabled", false);
    }

    public double getTownsAndNationsShopTaxRate() {
        return config.getDouble("townsandnations.shopTax.rate", 0.05);
    }

    public boolean getTownsAndNationsTransactionTaxEnabled() {
        return config.getBoolean("townsandnations.transactionTax.enabled", false);
    }

    public double getTownsAndNationsOutsiderTaxRate() {
        return config.getDouble("townsandnations.transactionTax.outsiderRate", 0.15);
    }

    public boolean getTownsAndNationsTreasuryFundingEnabled() {
        return config.getBoolean("townsandnations.treasuryFunding.enabled", false);
    }

    // Messages
    public String getMessage(String key) {
        String message = messages.getString(key, "Message not found: " + key);
        // Always replace {prefix} with the actual prefix
        String prefix = messages.getString("prefix", "<gray>[BetterShop]</gray> ");
        message = message.replace("{prefix}", prefix);
        return message;
    }

    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }

        return message;
    }
}