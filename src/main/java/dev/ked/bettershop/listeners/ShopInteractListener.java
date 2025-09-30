package dev.ked.bettershop.listeners;

import dev.ked.bettershop.config.ConfigManager;
import dev.ked.bettershop.shop.Shop;
import dev.ked.bettershop.shop.ShopManager;
import dev.ked.bettershop.shop.ShopType;
import dev.ked.bettershop.ui.TradeGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * Handles player interactions with shop chests.
 */
public class ShopInteractListener implements Listener {
    private final ShopManager shopManager;
    private final ConfigManager config;
    private final TradeGUI tradeGUI;
    private final Economy economy;
    private final MiniMessage miniMessage;

    public ShopInteractListener(ShopManager shopManager, ConfigManager config, TradeGUI tradeGUI, Economy economy) {
        this.shopManager = shopManager;
        this.config = config;
        this.tradeGUI = tradeGUI;
        this.economy = economy;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Shop> shopOpt = shopManager.getShopAt(block.getLocation());

        if (shopOpt.isEmpty()) {
            return;
        }

        Shop shop = shopOpt.get();

        // Allow owner to access their own chest directly (don't cancel event)
        if (shop.getOwner().equals(player.getUniqueId())) {
            // Let them open the chest normally
            return;
        }

        // Cancel event for non-owners (they use the shop interface instead)
        event.setCancelled(true);

        // Check if world is enabled
        if (!config.isWorldEnabled(player.getWorld().getName())) {
            player.sendMessage(miniMessage.deserialize(config.getMessage("world-disabled")));
            return;
        }

        // Quick buy with shift-click
        if (player.isSneaking() && config.isQuickBuyOnShiftClick()) {
            handleQuickBuy(player, shop);
        } else if (config.isGuiEnabled()) {
            // Open GUI
            tradeGUI.openGUI(player, shop);
        } else {
            // GUI disabled, fall back to quick buy
            handleQuickBuy(player, shop);
        }
    }

    /**
     * Handle instant transaction of 1 item.
     */
    private void handleQuickBuy(Player player, Shop shop) {
        ShopManager.TransactionResultData resultData;

        // BUY shop = player sells TO the shop
        // SELL shop = player buys FROM the shop
        if (shop.getType() == ShopType.SELL) {
            resultData = shopManager.processBuyTransaction(player, shop, 1);
        } else {
            resultData = shopManager.processSellTransaction(player, shop, 1);
        }

        ShopManager.TransactionResult result = resultData.getResult();

        // Send result message
        String messageKey = switch (result) {
            case SUCCESS -> shop.getType() == ShopType.SELL ? "purchase-success" : "sell-success";
            case INSUFFICIENT_FUNDS -> "purchase-insufficient-funds";
            case INSUFFICIENT_STOCK -> "purchase-insufficient-stock";
            case NO_INVENTORY_SPACE -> "purchase-no-space";
            case SHOP_INSUFFICIENT_FUNDS -> "sell-shop-no-funds";
            case INSUFFICIENT_ITEMS -> "sell-insufficient-items";
            case CHEST_FULL -> "sell-chest-full";
            case WRONG_SHOP_TYPE -> "wrong-shop-type";
            default -> "transaction-failed";
        };

        String itemName = getItemDisplayName(shop);
        String message = config.getMessage(messageKey,
                "quantity", "1",
                "item", itemName,
                "total", String.format("%.2f", shop.getPrice()),
                "price", String.format("%.2f", shop.getPrice()),
                "balance", String.format("%.2f", economy.getBalance(player)),
                "stock", String.valueOf(shopManager.getStock(shop))
        );

        player.sendMessage(miniMessage.deserialize(message));

        // Send tax message if applicable
        if (result == ShopManager.TransactionResult.SUCCESS && resultData.hasTransactionTax()) {
            String taxMessage = config.getMessage("territory-transaction-tax",
                    "amount", String.format("%.2f", resultData.getTransactionTax()),
                    "territory", resultData.getTerritoryName()
            );
            player.sendMessage(miniMessage.deserialize(taxMessage));
        }

        // Play sound on success
        if (result == ShopManager.TransactionResult.SUCCESS) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private String getItemDisplayName(Shop shop) {
        String materialName = shop.getItem().getType().name();
        String[] parts = materialName.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return result.toString();
    }
}