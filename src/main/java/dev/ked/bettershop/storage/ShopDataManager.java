package dev.ked.bettershop.storage;

import com.google.gson.*;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopRegistry;
import dev.ked.bettershop.shop.ShopType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles persistence of shop data to JSON files.
 */
public class ShopDataManager {
    private final Path dataFile;
    private final Gson gson;
    private final Logger logger;

    public ShopDataManager(Path dataDirectory, Logger logger) {
        this.dataFile = dataDirectory.resolve("shops.json");
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.severe("Failed to create data directory: " + e.getMessage());
        }
    }

    /**
     * Save all shops to disk.
     * @deprecated Old Shop system - use new Listing-based persistence
     */
    @Deprecated
    public void saveShops(ShopRegistry registry) {
        logger.warning("ShopDataManager is deprecated - implement new ShopEntity/Listing persistence");
    }

    /**
     * Load all shops from disk.
     * @deprecated Old Shop system - use new Listing-based persistence
     */
    @Deprecated
    public void loadShops(ShopRegistry registry) {
        logger.warning("ShopDataManager is deprecated - implement new ShopEntity/Listing persistence");
    }
    private static class ShopData {
        String world;
        int x;
        int y;
        int z;
        String owner;
        String type;
        String item;
        double price;
        double earnings;
        Integer buyLimit; // For BUY shops: how many items to buy (0 = unlimited, null for backward compat)
        long createdAt;
        Boolean silkRoadEnabled; // Silk Road integration flag
        Map<String, Integer> reservedStock; // Contract ID -> quantity (stored as strings for JSON)
    }
}