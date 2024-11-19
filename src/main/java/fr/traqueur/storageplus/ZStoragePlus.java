package fr.traqueur.storageplus;

import fr.groupez.api.MainConfiguration;
import fr.groupez.api.configurations.Configuration;
import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.button.loader.NoneLoader;
import fr.traqueur.storageplugs.api.StoragePlusManager;
import fr.traqueur.storageplugs.api.StoragePlusPlugin;
import fr.traqueur.storageplugs.api.domains.ChestTemplate;
import fr.traqueur.storageplugs.api.gui.buttons.ZChestContentButton;
import fr.traqueur.storageplugs.api.gui.buttons.ZToggleAutoSellButton;
import fr.traqueur.storageplugs.api.gui.buttons.ZToggleVacuumButton;
import fr.traqueur.storageplugs.api.gui.loaders.CompressorButtonLoader;
import fr.traqueur.storageplus.commands.StoragePlusCommand;
import fr.traqueur.storageplus.commands.converters.SmartChestConverter;

import java.io.File;

public final class ZStoragePlus extends StoragePlusPlugin {

    private InventoryManager inventoryManager;

    @Override
    public void enable() {

        MainConfiguration configuration = Configuration.register(MainConfiguration.class, new ZMainConfiguration());
        configuration.load();

        File folder = new File(this.getDataFolder(), "chests/");
        if(!folder.exists()) {
            folder.mkdirs();
            this.saveResource("chests/autosell_chest.yml", false);
        }

        Configuration.REGISTRY.values().forEach(config -> {
            if(!config.isLoad()) {
                config.load();
            }
        });

        ButtonManager buttonManager = this.getProvider(ButtonManager.class);
        this.inventoryManager = this.getProvider(InventoryManager.class);

        if(buttonManager == null || this.inventoryManager == null) {
            this.getLogger().severe("ButtonManager or InventoryManager not found.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        buttonManager.unregisters(this);
        buttonManager.register(new NoneLoader(this, ZChestContentButton.class, "ZSTORAGEPLUS_CONTENT"));
        buttonManager.register(new NoneLoader(this, ZToggleAutoSellButton.class, "ZSTORAGEPLUS_TOGGLE_AUTOSELL"));
        buttonManager.register(new NoneLoader(this, ZToggleVacuumButton.class, "ZSTORAGEPLUS_TOGGLE_VACUUM"));
        buttonManager.register(new CompressorButtonLoader(this, "ZSTORAGEPLUS_COMPRESSOR"));

        var manager = this.registerManager(StoragePlusManager.class, new ZStoragePlusManager());

        this.commandManager.setDebug(configuration.isDebug());
        this.commandManager.registerConverter(ChestTemplate.class, new SmartChestConverter(manager));
        this.loadCommands();
    }

    @Override
    public void disable() {

    }

    public void loadCommands() {
        var command = new StoragePlusCommand(this);
        this.commandManager.unregisterCommand(command);
        this.commandManager.registerCommand(command);
    }

    @Override
    public boolean isDebug() {
        return Configuration.get(MainConfiguration.class).isDebug();
    }

    @Override
    public InventoryManager getInventoryManager() {
        return this.inventoryManager;
    }
}
