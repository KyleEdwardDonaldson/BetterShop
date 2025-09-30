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
     */
    public void saveShops(ShopRegistry registry) {
        try {
            List<ShopData> shopDataList = new ArrayList<>();

            for (Shop shop : registry.getAllShops()) {
                ShopData data = new ShopData();
                data.world = shop.getLocation().getWorld().getName();
                data.x = shop.getLocation().getBlockX();
                data.y = shop.getLocation().getBlockY();
                data.z = shop.getLocation().getBlockZ();
                data.owner = shop.getOwner().toString();
                data.type = shop.getType().name();
                data.item = serializeItem(shop.getItem());
                data.price = shop.getPrice();
                data.earnings = shop.getEarnings();
                data.createdAt = shop.getCreatedAt();

                shopDataList.add(data);
            }

            String json = gson.toJson(shopDataList);
            Files.writeString(dataFile, json);

            logger.info("Saved " + shopDataList.size() + " shops to disk");
        } catch (IOException e) {
            logger.severe("Failed to save shops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load all shops from disk.
     */
    public void loadShops(ShopRegistry registry) {
        if (!Files.exists(dataFile)) {
            logger.info("No shop data file found, starting fresh");
            return;
        }

        try {
            String json = Files.readString(dataFile);
            ShopData[] shopDataArray = gson.fromJson(json, ShopData[].class);

            if (shopDataArray == null) {
                logger.warning("Shop data file is empty or invalid");
                return;
            }

            int loaded = 0;
            int failed = 0;

            for (ShopData data : shopDataArray) {
                try {
                    World world = Bukkit.getWorld(data.world);
                    if (world == null) {
                        logger.warning("World " + data.world + " not found, skipping shop");
                        failed++;
                        continue;
                    }

                    Location location = new Location(world, data.x, data.y, data.z);
                    UUID owner = UUID.fromString(data.owner);
                    ShopType type = ShopType.valueOf(data.type);
                    ItemStack item = deserializeItem(data.item);

                    if (item == null) {
                        logger.warning("Failed to deserialize item for shop at " + location);
                        failed++;
                        continue;
                    }

                    Shop shop = new Shop(location, owner, type, item, data.price);
                    shop.setEarnings(data.earnings);

                    registry.registerShop(shop);
                    loaded++;
                } catch (Exception e) {
                    logger.warning("Failed to load shop: " + e.getMessage());
                    failed++;
                }
            }

            logger.info("Loaded " + loaded + " shops (" + failed + " failed)");
        } catch (IOException e) {
            logger.severe("Failed to load shops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Serialize ItemStack to Base64 string.
     */
    private String serializeItem(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            logger.severe("Failed to serialize item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserialize ItemStack from Base64 string.
     */
    private ItemStack deserializeItem(String data) {
        if (data == null) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.severe("Failed to deserialize item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Internal data class for JSON serialization.
     */
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
        long createdAt;
    }
}