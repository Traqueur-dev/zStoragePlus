package fr.traqueur.storageplus.api.gui.buttons;

import fr.groupez.api.MainConfiguration;
import fr.groupez.api.configurations.Configuration;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.menu.button.ZButton;
import fr.maxlego08.menu.inventory.inventories.InventoryDefault;
import fr.traqueur.storageplus.api.StoragePlusManager;
import fr.traqueur.storageplus.api.StoragePlusPlugin;
import fr.traqueur.storageplus.api.config.PlaceholdersConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class ZToggleVacuumButton extends ZButton {

    private final StoragePlusPlugin plugin;

    public ZToggleVacuumButton(Plugin plugin) {
        this.plugin = ((StoragePlusPlugin) plugin);
    }

    @Override
    public ItemStack getCustomItemStack(Player player) {

        Placeholders placeholders = new Placeholders();
        PlaceholdersConfig config = Configuration.get(MainConfiguration.class).getPlaceholders();
        String vacuumState = this.plugin
                .getManager(StoragePlusManager.class)
                .getOpenedChest(player)
                .isVacuum() ? config.stateOn() : config.stateOff();

        placeholders.register("vacuum_state", vacuumState);

        return this.getItemStack().build(player, true, placeholders);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryDefault inventory, int slot, Placeholders placeholders) {
        var manager = this.plugin.getManager(StoragePlusManager.class);
        var chest = manager.getOpenedChest(player);
        chest.setVacuum(!chest.isVacuum());
    }
}