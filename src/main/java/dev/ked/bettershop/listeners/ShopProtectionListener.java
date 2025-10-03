package dev.ked.bettershop.listeners;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopRegistry;
import dev.ked.bettershop.ui.HologramManager;
import dev.ked.bettershop.ui.SignRenderer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;
import java.util.Optional;

/**
 * Protects shops from various forms of damage and interference.
 */
public class ShopProtectionListener implements Listener {
    private final ShopManager shopManager;
    private final ShopRegistry registry;
    private final ConfigManager config;
    private final SignRenderer signRenderer;
    private final HologramManager hologramManager;
    private final MiniMessage miniMessage;

    public ShopProtectionListener(ShopManager shopManager, ShopRegistry registry, ConfigManager config,
                                   SignRenderer signRenderer, HologramManager hologramManager) {
        this.shopManager = shopManager;
        this.registry = registry;
        this.config = config;
        this.signRenderer = signRenderer;
        this.hologramManager = hologramManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Prevent breaking shop chests.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<Shop> shopOpt = shopManager.getShopAt(block.getLocation());

        if (shopOpt.isEmpty()) {
            return;
        }

        Shop shop = shopOpt.get();
        Player player = event.getPlayer();

        // Allow owner to break their own shop
        if (shop.getOwner().equals(player.getUniqueId()) || player.hasPermission("bettershop.admin")) {
            // Remove shop and clean up
            registry.unregisterShop(shop);
            signRenderer.removeSign(shop);
            hologramManager.removeHologram(shop);

            // Return earnings if any
            if (shop.getEarnings() > 0) {
                // TODO: Implement economy deposit
                String message = config.getMessage("shop-remove-earnings",
                        "earnings", String.format("%.2f", shop.getEarnings()));
                player.sendMessage(miniMessage.deserialize(message));
            }

            String message = config.getMessage("shop-removed");
            player.sendMessage(miniMessage.deserialize(message));
        } else {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize(config.getMessage("not-shop-owner")));
        }
    }

    /**
     * Prevent breaking signs attached to shops.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (!(block.getState() instanceof Sign)) {
            return;
        }

        // Check if this sign is near a shop
        for (Block adjacent : getAdjacentBlocks(block)) {
            Optional<Shop> shopOpt = shopManager.getShopAt(adjacent.getLocation());
            if (shopOpt.isPresent()) {
                Shop shop = shopOpt.get();
                Player player = event.getPlayer();

                if (!shop.getOwner().equals(player.getUniqueId()) && !player.hasPermission("bettershop.admin")) {
                    event.setCancelled(true);
                    player.sendMessage(miniMessage.deserialize(config.getMessage("not-shop-owner")));
                }
                break;
            }
        }
    }

    /**
     * Prevent placing hoppers near shops.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperPlace(BlockPlaceEvent event) {
        if (!config.shouldPreventHoppers()) {
            return;
        }

        Block placed = event.getBlock();
        if (placed.getType() != Material.HOPPER && placed.getType() != Material.DROPPER) {
            return;
        }

        // Check if adjacent to a shop
        for (Block adjacent : getAdjacentBlocks(placed)) {
            if (shopManager.getShopAt(adjacent.getLocation()).isPresent()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(miniMessage.deserialize(
                        config.getMessage("prefix") + "<red>Cannot place hoppers near shops!"));
                break;
            }
        }
    }

    /**
     * Prevent explosions from damaging shops.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (!config.shouldPreventExplosions()) {
            return;
        }

        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (shopManager.getShopAt(block.getLocation()).isPresent()) {
                iterator.remove();
            }
        }
    }

    /**
     * Prevent pistons from moving shop blocks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!config.shouldPreventPistons()) {
            return;
        }

        for (Block block : event.getBlocks()) {
            if (shopManager.getShopAt(block.getLocation()).isPresent()) {
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!config.shouldPreventPistons()) {
            return;
        }

        for (Block block : event.getBlocks()) {
            if (shopManager.getShopAt(block.getLocation()).isPresent()) {
                event.setCancelled(true);
                break;
            }
        }
    }

    /**
     * Prevent editing shop signs.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(org.bukkit.event.block.SignChangeEvent event) {
        Block block = event.getBlock();

        // Check if this sign is near a shop
        for (Block adjacent : getAdjacentBlocks(block)) {
            if (shopManager.getShopAt(adjacent.getLocation()).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Update shop visuals when chest is closed (stock may have changed).
     * Also detects and sets item type for empty SELL shops.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof org.bukkit.block.Chest chest)) {
            return;
        }

        Optional<Shop> shopOpt = shopManager.getShopAt(chest.getLocation());
        if (shopOpt.isEmpty()) {
            return;
        }

        Shop shop = shopOpt.get();

        // If this is an empty SELL shop and owner just added items, detect and set the item type
        if (shop.getItem() == null && shop.getType() == dev.ked.bettershop.shop.ShopType.SELL) {
            if (event.getPlayer() instanceof Player player && shop.getOwner().equals(player.getUniqueId())) {
                // Look for the first item in the chest
                for (org.bukkit.inventory.ItemStack item : chest.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        org.bukkit.inventory.ItemStack detectedItem = item.clone();
                        detectedItem.setAmount(1);
                        shop.setItem(detectedItem);

                        player.sendMessage(miniMessage.deserialize(
                            config.getMessage("prefix") + "<green>Shop activated! Now selling <white>" +
                            item.getType().name().toLowerCase().replace('_', ' ') + "</white>"));
                        break;
                    }
                }
            }
        }

        // Update visuals
        if (signRenderer != null) {
            Block block = chest.getBlock();
            if (block.getState() instanceof org.bukkit.block.Chest) {
                // Find and update sign
                for (Block adjacent : getAdjacentBlocks(block)) {
                    if (adjacent.getState() instanceof Sign sign) {
                        signRenderer.updateSignText(sign, shop);
                        break;
                    }
                }
            }
        }

        if (hologramManager != null) {
            hologramManager.updateHologram(shop);
        }
    }

    /**
     * Get blocks adjacent to a block (6 directions).
     */
    private Block[] getAdjacentBlocks(Block block) {
        return new Block[]{
                block.getRelative(1, 0, 0),
                block.getRelative(-1, 0, 0),
                block.getRelative(0, 1, 0),
                block.getRelative(0, -1, 0),
                block.getRelative(0, 0, 1),
                block.getRelative(0, 0, -1)
        };
    }
}