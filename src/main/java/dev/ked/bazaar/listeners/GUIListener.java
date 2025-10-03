package dev.ked.bazaar.listeners;

import dev.ked.bazaar.BazaarPlugin;
import dev.ked.bazaar.ui.ShopDirectoryGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles clicks in BetterShop GUIs.
 */
public class GUIListener implements Listener {

    private final BazaarPlugin plugin;
    private final Map<UUID, ShopDirectoryGUI> activeGUIs = new HashMap<>();

    public GUIListener(BazaarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().title().toString();

        // Check if this is a shop directory GUI
        if (title.contains("Shop Directory") || title.contains("Silk Road Shops")) {
            event.setCancelled(true);

            ShopDirectoryGUI gui = activeGUIs.get(player.getUniqueId());
            if (gui != null && gui.getInventory().equals(inventory)) {
                gui.handleClick(event.getRawSlot());
            }
        }
    }

    /**
     * Register an active GUI for a player.
     */
    public void registerGUI(UUID playerId, ShopDirectoryGUI gui) {
        activeGUIs.put(playerId, gui);
    }

    /**
     * Unregister a player's active GUI.
     */
    public void unregisterGUI(UUID playerId) {
        activeGUIs.remove(playerId);
    }
}
