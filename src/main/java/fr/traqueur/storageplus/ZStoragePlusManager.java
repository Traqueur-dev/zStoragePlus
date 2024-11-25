package fr.traqueur.storageplus;

import fr.groupez.api.MainConfiguration;
import fr.groupez.api.ZLogger;
import fr.groupez.api.configurations.Configuration;
import fr.groupez.api.zcore.ElapsedTime;
import fr.maxlego08.menu.MenuItemStack;
import fr.maxlego08.menu.api.dupe.DupeManager;
import fr.maxlego08.menu.exceptions.InventoryException;
import fr.maxlego08.menu.loader.MenuItemStackLoader;
import fr.maxlego08.menu.zcore.utils.loader.Loader;
import fr.traqueur.storageplus.api.StoragePlusManager;
import fr.traqueur.storageplus.api.config.DropMode;
import fr.traqueur.storageplus.api.domains.ChestTemplate;
import fr.traqueur.storageplus.api.domains.PlacedChest;
import fr.traqueur.storageplus.api.domains.PlacedChestContent;
import fr.traqueur.storageplus.api.domains.StorageItem;
import fr.traqueur.storageplus.api.gui.ChestMenu;
import fr.traqueur.storageplus.api.gui.buttons.ZChestContentButton;
import fr.traqueur.storageplus.api.hooks.Hook;
import fr.traqueur.storageplus.api.hooks.HooksManager;
import fr.traqueur.storageplus.api.serializers.ChestLocationDataType;
import fr.traqueur.storageplus.api.storage.Service;
import fr.traqueur.storageplus.api.storage.dto.PlacedChestDTO;
import fr.traqueur.storageplus.domains.ZChestTemplate;
import fr.traqueur.storageplus.domains.ZPlacedChest;
import fr.traqueur.storageplus.domains.ZPlacedChestContent;
import fr.traqueur.storageplus.hooks.Hooks;
import fr.traqueur.storageplus.storage.PlacedChestRepository;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZStoragePlusManager implements StoragePlusManager {

    private final Map<String, ChestTemplate> smartChests;
    private final Map<UUID, PlacedChest> openedChests;
    private final Map<Location, Inventory> mapInventoryOpened;
    private final Map<UUID, PlacedChestContent> contents;

    private final Service<PlacedChestContent, PlacedChestDTO> service;

    public ZStoragePlusManager() {
        this.smartChests = new HashMap<>();
        this.openedChests = new HashMap<>();
        this.mapInventoryOpened = new HashMap<>();
        this.contents = new HashMap<>();

        this.service = new Service<>(this.getPlugin(), PlacedChestDTO.class, new PlacedChestRepository(), TABLE_NAME);

        this.registerChests();

        this.service.findAll().forEach(e -> {
            this.contents.put(e.uuid(), e);
        });

        this.getPlugin().getServer().getPluginManager().registerEvents(new ZStoragePlusListener(this), this.getPlugin());
        this.getPlugin().getScheduler().runTimer(new ZStoragePlusAutoSellTask(this), 0, 20);
        this.getPlugin().getScheduler().runTimer(this::saveAll, 0, 20*60*60);
    }

    @Override
    public Map<String, ChestTemplate> getSmartChests() {
        return this.smartChests;
    }

    @Override
    public Optional<PlacedChest> getChestFromBlock(Location location) {
        Chunk chunk = location.getChunk();
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        List<PlacedChest> chests = container.getOrDefault(this.getNamespaceKey(), PersistentDataType.LIST.listTypeFrom(ChestLocationDataType.INSTANCE), new ArrayList<>());
        return chests.stream().filter(e -> this.locationEquals(e.getLocation(), location)).findFirst();
    }

    @Override
    public void placeChest(Player player, Location location, ChestTemplate chest, ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        UUID uuid = null;
        if(meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            String uuidStr = container.getOrDefault(this.getNamespaceKeyUUID(), PersistentDataType.STRING, "error");
            if (!uuidStr.equals("error")) {
                uuid = UUID.fromString(uuidStr);
            }
        }

        PlacedChest chestLocation = uuid == null ? new ZPlacedChest(player.getUniqueId(), location, chest) : new ZPlacedChest(uuid, player.getUniqueId(), location, chest);
        List<PlacedChest> chests = this.getChestsInChunk(location.getChunk());
        chests.add(chestLocation);
        this.saveChestsInChunk(location.getChunk(), chests);

        if(!this.contents.containsKey(chestLocation.getUniqueId())) {
            this.contents.put(chestLocation.getUniqueId(), new ZPlacedChestContent(chestLocation.getUniqueId(), this.generateItemList(getPlugin().getInventoryManager().getInventory(chest.getName()).get().size(), chest.getMaxPages(), getPlugin().getInventoryManager().getInventory(chest.getName()).get().getButtons().stream().filter(button -> button instanceof ZChestContentButton).map(ZChestContentButton.class::cast).findFirst().get().getSlots())));
        }

        if(this.getPlugin().isDebug()) {
            ZLogger.info("Placed chest " + chest.getName() + " at " + location);
        }
    }

    private List<StorageItem> generateItemList(int size, int numberPages, Collection<Integer> slots) {
        List<StorageItem> items = new ArrayList<>();
        if(numberPages == -1) {
            numberPages = 1;
        }
        for (int i = 0; i < numberPages; i++) {
            for (int slot : slots) {
                items.add(StorageItem.empty(slot+(size*(i))));
            }
        }
        return items;
    }

    @Override
    public void breakChest(BlockBreakEvent event, Location location) {
        this.getChestFromBlock(location).ifPresent(chest -> {
            event.setDropItems(false);

            List<PlacedChest> chests = this.getChestsInChunk(location.getChunk());
            chests.removeIf(e -> this.locationEquals(e.getLocation(), location));
            this.saveChestsInChunk(location.getChunk(), chests);

            ItemStack item = chest.getChestTemplate().build(event.getPlayer());

            switch (chest.getChestTemplate().getDropMode()) {
                case KEEP -> {
                    ItemMeta meta = item.getItemMeta();
                    if(meta != null) {
                        PersistentDataContainer container = meta.getPersistentDataContainer();
                        container.set(this.getNamespaceKeyUUID(), PersistentDataType.STRING, chest.getUniqueId().toString());
                        item.setItemMeta(meta);
                    }
                }
                case DROP -> {
                    PlacedChestContent content = this.contents.remove(chest.getUniqueId());
                    if(content != null) {
                        for (StorageItem storageItem : content.content()) {
                            if(storageItem.isEmpty()) {
                                continue;
                            }
                            this.getPlugin().getScheduler().runAtLocation(location, (task) -> this.dropItems(location, storageItem.amount(), storageItem.item()));
                        }
                        this.service.delete(content);
                    }
                }
            }
            this.getPlugin().getScheduler().runAtLocation(location, (task) -> event.getPlayer().getWorld().dropItemNaturally(location, item));
            if(this.getPlugin().isDebug()) {
                ZLogger.info("Broke chest at " + location);
            }
        });
    }

    @Override
    public Optional<ChestTemplate> getChestFromItem(ItemStack item) {
        if(item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if(meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String key = container.get(this.getNamespaceKey(), PersistentDataType.STRING);
        if(key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.getSmartChest(key));
    }

    @Override
    public ChestTemplate getSmartChest(String s) {
        return this.smartChests.getOrDefault(s, null);
    }

    @Override
    public NamespacedKey getNamespaceKey() {
        return new NamespacedKey(this.getPlugin(), "storageplus");
    }

    @Override
    public NamespacedKey getNamespaceKeyUUID() {
        return new NamespacedKey(this.getPlugin(), "storageplus_uuid");
    }

    @Override
    public void give(Player player, ChestTemplate chest) {
        player.getInventory().addItem(chest.build(player));
    }

    @Override
    public void registerChests() {
        this.smartChests.clear();
        File folder = new File(this.getPlugin().getDataFolder(), "chests/");
        try (Stream<Path> s = Files.walk(Paths.get(folder.getPath()))) {
            s.skip(1).map(Path::toFile).filter(File::isFile).filter(e -> e.getName().endsWith(".yml"))
                    .forEach(this::registerChestFromFile);
        } catch (IOException exception) {
            ZLogger.severe("Error while loading chests", exception);
        }
    }

    @Override
    public void handleAutoSell() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk loadedChunk : world.getLoadedChunks()) {
                List<PlacedChest> chests = this.getChestsInChunk(loadedChunk);
                chests.stream().filter(PlacedChest::isAutoSell).forEach(chest -> {
                    chest.tick();
                    if (chest.getTime() % chest.getSellDelay() == 0) {
                        this.sell(chest);
                        var inventory = this.mapInventoryOpened.remove(chest.getLocation());
                        if(inventory != null) {
                            for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                                this.openedChests.remove(viewer.getUniqueId());
                                viewer.closeInventory();
                                this.openChest((Player) viewer, chest, 0, true);
                            }
                        }
                        if(this.getPlugin().isDebug()) {
                            ZLogger.info("Auto selling chest " + chest.getChestTemplate().getName() + " at " + chest.getLocation());
                        }
                    }
                });
                this.saveChestsInChunk(loadedChunk, chests);
            }
        }
    }

    private void sell(PlacedChest chest) {
        PlacedChestContent content = this.getContent(chest);
        Map<ItemStack, Integer> groupedItems = this.groupItems(content.content());
        Map<ItemStack, Integer> notSellable = new HashMap<>();
        groupedItems.forEach((item, amount) -> {
            boolean hasSell = false;
            var hookManager = this.getPlugin().getManager(HooksManager.class);
            for (Hook hook : (chest.getChestTemplate().getShops().isEmpty() ? hookManager.getHooks() : chest.getChestTemplate().getShops())) {
                var opt = hookManager.getProvider(hook);
                if(opt.isEmpty()) {
                    continue;
                }
                if(opt.get().sellItems(Bukkit.getPlayer(chest.getOwner()), item, amount)) {
                    hasSell = true;
                    break;
                }
            }
            if(!hasSell) {
                notSellable.put(item, amount);
            }
        });
        List<Integer> slots = new ArrayList<>();
        this.getPlugin().getInventoryManager()
                .getInventory(chest.getChestTemplate().getName()).flatMap(inventory -> inventory.getButtons().stream().filter(button -> button instanceof ZChestContentButton).findFirst()).ifPresent(button -> {
                    slots.addAll(button.getSlots());
                    this.setContent(chest, this.degroupItems(chest, notSellable, slots));
                });
    }

    @Override
    public PlacedChest deserializeChest(String string) {
        String[] parts = string.split(";");
        String worldName = parts[0];
        World world = worldName.equals("null") ? null : Bukkit.getWorld(UUID.fromString(worldName));
        Location location = new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        return new ZPlacedChest(UUID.fromString(parts[10]), UUID.fromString(parts[6]),location, this.getSmartChest(parts[4]), Long.parseLong(parts[5]), Boolean.parseBoolean(parts[7]), Long.parseLong(parts[8]), Boolean.parseBoolean(parts[9]));
    }

    @Override
    public void openChest(Player player, PlacedChest chest, int page, boolean first) {
        if(first) {
            this.openedChests.put(player.getUniqueId(), chest);
        }
        var inventory = getPlugin().getInventoryManager().getInventory(chest.getChestTemplate().getName()).get();
        var zbutton = inventory.getButtons().stream().filter(button -> button instanceof ZChestContentButton).map(ZChestContentButton.class::cast).findFirst().get();

        this.contents.get(chest.getUniqueId()).generatePage(zbutton.getSlots(), inventory.size(),page);

        chest.getChestTemplate().open(this.getPlugin(), player, page);
        if(!first) {
            this.openedChests.put(player.getUniqueId(), chest);
        }
    }

    @Override
    public void closeChest(Player player) {
        PlacedChest chest = this.openedChests.remove(player.getUniqueId());
        if(chest == null) {
            return;
        }
        if(this.openedChests.values().stream().anyMatch(e -> this.locationEquals(e.getLocation(), chest.getLocation()))) {
            return;
        }
        this.saveChest(chest);
    }

    @Override
    public PlacedChest getOpenedChest(Player player) {
        return this.openedChests.get(player.getUniqueId());
    }

    @Override
    public void saveChest(PlacedChest chest) {
        if (this.getPlugin().isDebug()) {
            ZLogger.info("Saving chest " + chest.getChestTemplate().getName() + " at " + chest.getLocation());
        }
        Chunk chunk = chest.getLocation().getChunk();
        List<PlacedChest> chests = this.getChestsInChunk(chunk);
        chests.removeIf(e -> this.locationEquals(e.getLocation(), chest.getLocation()));
        chests.add(chest);
        this.saveChestsInChunk(chunk, chests);
    }

    @Override
    public List<PlacedChest> getChestsInChunk(Chunk chunk) {
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        var chests = container.getOrDefault(this.getNamespaceKey(), PersistentDataType.LIST.listTypeFrom(ChestLocationDataType.INSTANCE), new ArrayList<>());
        return new ArrayList<>(chests);
    }

    @Override
    public ItemStack addItemsToChest(Chunk chunk, ItemStack itemStack) {
        List<PlacedChest> chests = this.getChestsInChunk(chunk);
        int rest = itemStack.getAmount();
        for (PlacedChest chest : chests) {
            PlacedChestContent content = this.contents.get(chest.getUniqueId());
            StorageItem storageItem = content.content()
                    .stream()
                    .filter(vaultItem1 -> vaultItem1.item().isSimilar(itemStack) && vaultItem1.amount() < this.getMaxStackSize(chest, itemStack))
                    .findFirst().orElseGet(() -> content.content()
                            .stream()
                            .filter(StorageItem::isEmpty)
                            .findFirst()
                            .orElse(null));

            while(storageItem != null) {
                int amount = itemStack.getAmount();
                int baseAmount = storageItem.isEmpty() ? 0 : storageItem.amount();
                int newAmount = Math.min(this.getMaxStackSize(chest, storageItem.item()), baseAmount + amount);
                StorageItem finalVaultItem = new StorageItem(this.cloneItemStack(itemStack), newAmount, storageItem.slot());
                this.setContent(chest, content.content().stream().map(vaultItem1 -> vaultItem1.slot() == finalVaultItem.slot() ? finalVaultItem : vaultItem1).collect(Collectors.toList()));
                rest = Math.max(0, amount - (newAmount - baseAmount));
                if(rest == 0) {
                    return null;
                }
                itemStack.setAmount(rest);
                storageItem = content.content()
                        .stream()
                        .filter(vaultItem1 -> vaultItem1.item().isSimilar(itemStack) && vaultItem1.amount() < this.getMaxStackSize(chest, itemStack))
                        .findFirst().orElseGet(() -> content.content()
                                .stream()
                                .filter(StorageItem::isEmpty)
                                .findFirst()
                                .orElse(null));
            }
        }
        if (rest == 0) {
            return null;
        }
        return itemStack;
    }

    @Override
    public void compress(PlacedChest chest, List<Material> availableMaterials, List<Integer> slots) {
        Map<ItemStack, Integer> groupedItems = this.groupItems(this.getContent(chest).content());
        Map<ItemStack, Integer> compressedItems = new HashMap<>();

        for (Map.Entry<ItemStack, Integer> itemStackIntegerEntry : groupedItems.entrySet()) {
            ItemStack item = itemStackIntegerEntry.getKey();
            int amount = itemStackIntegerEntry.getValue();
            Material compressedType = this.getCompressedType(item.getType());
            if(availableMaterials.contains(item.getType()) && compressedType != item.getType()) {
                int compressedAmount = amount / 9;
                int rest = amount % 9;
                this.addInMap(compressedItems, new ItemStack(compressedType), compressedAmount);
                this.addInMap(compressedItems, item, rest);
            } else {
                this.addInMap(compressedItems, item, amount);
            }
        }
        this.setContent(chest, this.degroupItems(chest, compressedItems, slots));
    }

    @Override
    public void smelt(PlacedChest chest, List<Material> availableMaterials, List<Integer> slots) {
        Map<ItemStack, Integer> groupedItems = this.groupItems(this.getContent(chest).content());
        Map<ItemStack, Integer> smeltItems = new HashMap<>();
        for (Map.Entry<ItemStack, Integer> itemStackIntegerEntry : groupedItems.entrySet()) {
            ItemStack item = itemStackIntegerEntry.getKey();
            int amount = itemStackIntegerEntry.getValue();
            ItemStack result = this.getSmeltedItems(item);
            if(availableMaterials.contains(item.getType()) && !result.isSimilar(item)) {
                int smeltAmount = amount*result.getAmount();
                this.addInMap(smeltItems, result, smeltAmount);
            } else {
                this.addInMap(smeltItems, item, amount);
            }
        }
        this.setContent(chest, this.degroupItems(chest, smeltItems, slots));
    }

    private void addInMap(Map<ItemStack, Integer> compressedItems, ItemStack item, int amount) {
        if(compressedItems.entrySet().stream().anyMatch(e -> e.getKey().isSimilar(item))) {
            ItemStack similarItem = compressedItems.keySet().stream().filter(e -> e.isSimilar(item)).findFirst().get();
            compressedItems.put(similarItem, compressedItems.get(similarItem) + amount);
        } else {
            compressedItems.put(item, amount);
        }
    }

    private Map<ItemStack, Integer> groupItems(List<StorageItem> items) {
        Map<ItemStack, Integer> map = new HashMap<>();
        for (StorageItem item : items) {
            if(item.isEmpty()) {
                continue;
            }
            Optional<ItemStack> similarItem = map.keySet().stream()
                    .filter(existing -> existing.isSimilar(item.item()))
                    .findFirst();
            if (similarItem.isPresent()) {
                map.put(similarItem.get(), map.get(similarItem.get()) + item.amount());
            } else {
                map.put(item.item().clone(), item.amount());
            }
        }
        return map;
    }

    private List<StorageItem> degroupItems(PlacedChest chest, Map<ItemStack, Integer> items, List<Integer> slots) {
        int i = 0;
        slots = this.calculateSlots(chest, slots);
        int slot = slots.get(i);
        List<StorageItem> degroupItems = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> itemStackIntegerEntry : items.entrySet()) {
            ItemStack item = itemStackIntegerEntry.getKey();
            int amount = itemStackIntegerEntry.getValue();
            int maxStackSize = this.getMaxStackSize(chest, item);

            while (amount > 0) {
                int toAdd = Math.min(amount, maxStackSize);
                ItemStack clone = item.clone();
                clone.setAmount(toAdd);
                if(i >= slots.size()) {
                    this.dropItems(chest.getLocation(), amount, clone);
                    break;
                } else {
                    degroupItems.add(new StorageItem(clone, toAdd, slot));
                    slot = slots.get(++i);
                }
                amount -= toAdd;
            }
        }
        return degroupItems;
    }

    private List<Integer> calculateSlots(PlacedChest chest, List<Integer> slots) {
        if(chest.getChestTemplate().getMaxPages() == 1) {
            return slots;
        }
        List<Integer> newSlots = new ArrayList<>();
        int size = this.getPlugin().getInventoryManager().getInventory(chest.getChestTemplate().getName()).get().size();
        int nbIterations;
        if(chest.getChestTemplate().getMaxPages() == -1) {
            int contentSize = this.getContent(chest).content().size();
            int pages = (int) Math.ceil((double) contentSize / slots.size());
            nbIterations = pages+10;
        } else {
            nbIterations = chest.getChestTemplate().getMaxPages();
        }
        for (int i = 0; i < nbIterations; i++) {
            for (int slot : slots) {
                newSlots.add(slot+(size*(i)));
            }
        }
        return newSlots;
    }

    @Override
    public int getMaxStackSize(PlacedChest chest, ItemStack item) {
        return chest.getChestTemplate().isInfinite() ? (chest.getChestTemplate().getMaxStackSize() == -1 ? Integer.MAX_VALUE : chest.getChestTemplate().getMaxStackSize())  : item.getMaxStackSize();
    }

    @Override
    public void dropItems(Location location, int amount, ItemStack itemStack) {
        while (amount > 0) {
            int toAdd = Math.min(amount, itemStack.getMaxStackSize());
            ItemStack item = itemStack.clone();
            item.setAmount(toAdd);
            location.getWorld().dropItemNaturally(location, item);
            amount -= toAdd;
        }
    }

    @Override
    public void purge(Chunk chunk, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Chunk loadedChunk = chunk.getWorld().getChunkAt(chunk.getX() + x, chunk.getZ() + z);
                List<PlacedChest> chests = this.getChestsInChunk(loadedChunk);
                for (PlacedChest chest : chests) {
                    PlacedChestContent content = this.contents.remove(chest.getUniqueId());
                    if(content != null) {
                        this.service.delete(content);
                    }
                }
                this.saveChestsInChunk(loadedChunk, new ArrayList<>());
            }
        }
    }

    @Override
    public ItemStack cloneItemStack(ItemStack itemStack) {
        ItemStack clone = itemStack.clone();
        ItemMeta cloneMeta = clone.getItemMeta();
        if(cloneMeta == null) {
            return clone;
        }
        PersistentDataContainer container = cloneMeta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(Bukkit.getServer().getPluginManager().getPlugin("zMenu"), DupeManager.KEY);
        if(container.has(key)) {
            container.remove(key);
        }
        clone.setItemMeta(cloneMeta);
        return clone;
    }

    @Override
    public void saveAll() {
        ElapsedTime elapsedTime = new ElapsedTime("saveAll");
        elapsedTime.start();
        this.contents.values().forEach(this.service::save);
        elapsedTime.endDisplay();
    }

    @Override
    public PlacedChestContent getContent(PlacedChest chest) {
        return this.contents.get(chest.getUniqueId());
    }

    @Override
    public void setContent(PlacedChest chest, List<StorageItem> items) {
        this.contents.computeIfPresent(chest.getUniqueId(), (k, v) -> {
            v.setContent(items);
            return v;
        });
    }


    private ItemStack getSmeltedItems(ItemStack itemStack) {
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if(recipe instanceof FurnaceRecipe furnaceRecipe) {
                if(furnaceRecipe.getInput().isSimilar(itemStack)) {
                    return furnaceRecipe.getResult();
                }
            }
        }
        return itemStack;
    }

    private Material getCompressedType(Material material) {
        return switch (material) {
            case IRON_INGOT -> Material.IRON_BLOCK;
            case GOLD_INGOT -> Material.GOLD_BLOCK;
            case DIAMOND -> Material.DIAMOND_BLOCK;
            case EMERALD -> Material.EMERALD_BLOCK;
            case LAPIS_LAZULI -> Material.LAPIS_BLOCK;
            case REDSTONE -> Material.REDSTONE_BLOCK;
            case COAL -> Material.COAL_BLOCK;
            case NETHERITE_INGOT -> Material.NETHERITE_BLOCK;
            case RAW_COPPER -> Material.RAW_COPPER_BLOCK;
            case RAW_IRON -> Material.RAW_IRON_BLOCK;
            case RAW_GOLD -> Material.RAW_GOLD_BLOCK;
            default -> material;
        };
    }

    private void saveChestsInChunk(Chunk loadedChunk, List<PlacedChest> chests) {
        PersistentDataContainer container = loadedChunk.getPersistentDataContainer();
        container.set(this.getNamespaceKey(), PersistentDataType.LIST.listTypeFrom(ChestLocationDataType.INSTANCE), chests);
    }

    private void registerChestFromFile(File file) {
        String name = "undefined";
        try {
            String fileName = file.getPath();
            fileName = fileName.replace(this.getPlugin().getDataFolder().getPath(), "");
            this.getPlugin().getInventoryManager().loadInventory(getPlugin(), fileName, ChestMenu.class);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            name = file.getName().replace(".yml", "");
            MenuItemStack menuItemStack;
            Loader<MenuItemStack> loader = new MenuItemStackLoader(this.getPlugin().getInventoryManager());
            menuItemStack = loader.load(config, "settings.item.", file);
            boolean autoSell = config.getBoolean("settings.auto-sell.enabled", false);
            long interval = config.getLong("settings.auto-sell.interval", Configuration.get(MainConfiguration.class).getDefaultAutoSellDelay());
            List<Hook> shops = new ArrayList<>();
            if(config.contains("settings.auto-sell.shops")) {
                shops = config.getStringList("settings.auto-sell.shops").stream().map(str -> Hooks.valueOf(str.toUpperCase())).collect(Collectors.toList());
            }
            boolean vacuum = config.getBoolean("settings.vacuum.enabled", false);
            List<Material> blacklistVacuum = new ArrayList<>();
            if(config.contains("settings.vacuum.black-list")) {
                blacklistVacuum = config.getStringList("settings.vacuum.black-list").stream().map(Material::matchMaterial).collect(Collectors.toList());
            }
            DropMode dropMode = DropMode.valueOf(config.getString("settings.drop-mode", "KEEP"));
            boolean infinite = config.getBoolean("settings.infinite", false);
            int maxStackSize = config.getInt("settings.max-stack-size", -1);
            int maxPages = config.getInt("settings.max-pages", 1);
            this.smartChests.put(name, new ZChestTemplate(getPlugin(), name, menuItemStack, autoSell, interval, shops, vacuum, blacklistVacuum, dropMode, infinite, maxStackSize, maxPages));
            if(this.getPlugin().isDebug()) {
                ZLogger.info("Registered chest " + name);
            }
        } catch (InventoryException e) {
            ZLogger.severe("Error while loading chest " + name, e);
        }
    }

    private boolean locationEquals(Location l1, Location l2) {
        if(l1.getWorld() == null && l2.getWorld() == null) {
            return l1.getBlockX() == l2.getBlockX() && l1.getBlockY() == l2.getBlockY() && l1.getBlockZ() == l2.getBlockZ();
        }
        if((l1.getWorld() == null && l2.getWorld() != null) || (l1.getWorld() != null && l2.getWorld() == null)) {
            return false;
        }

        return l1.getWorld().getUID().equals(l2.getWorld().getUID()) && l1.getBlockX() == l2.getBlockX() && l1.getBlockY() == l2.getBlockY() && l1.getBlockZ() == l2.getBlockZ();
    }

}
