package fr.traqueur.storageplus.api.gui;

import fr.groupez.api.zcore.CompatibilityUtil;
import fr.maxlego08.menu.api.dupe.DupeManager;
import fr.traqueur.storageplus.api.StoragePlusManager;
import fr.traqueur.storageplus.api.StoragePlusPlugin;
import fr.traqueur.storageplus.api.domains.PlacedChest;
import fr.traqueur.storageplus.api.domains.PlacedChestContent;
import fr.traqueur.storageplus.api.domains.StorageItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.List;
import java.util.stream.Collectors;

public class ClickHolder {

    private final StoragePlusPlugin plugin;

    public ClickHolder(StoragePlusPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleLeftClick(InventoryClickEvent event, Player player, ItemStack cursor, int slot, PlacedChest chest, PlacedChestContent vault) {
        StorageItem vaultItem = vault.content().stream().filter(item -> item.slot() == slot).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slot));
        if(chest.getChestTemplate().isInfinite()) {
            ItemStack current = vaultItem.item();
            if(cursor == null || cursor.getType().isAir() && !vaultItem.isEmpty()) {
                int amountToRemove = Math.min(vaultItem.amount(), current.getMaxStackSize());
                this.removeItem(event, player, slot, vault, chest, vaultItem, amountToRemove);
            } else if(!cursor.getType().isAir()) {
                int slotToAdd = this.findCorrespondingSlot(event.getInventory(), cursor, vault, chest);
                if(slotToAdd == -1) {
                    return;
                }
                vaultItem = vault.content().stream().filter(item -> item.slot() == slotToAdd).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slotToAdd));
                this.addItem(event, player, slotToAdd, vault, chest, vaultItem, cursor, cursor.getAmount());
            }
        } else {
            InventoryAction action = event.getAction();
            switch (action) {
                case PLACE_ALL -> {
                    var newStorageItem = this.addToStorageItem(player, chest, vault, vaultItem, this.cloneItemStack(cursor), cursor.getAmount(), event);
                    event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                    CompatibilityUtil.setCursor(event, new ItemStack(Material.AIR));
                }

                case PLACE_SOME -> {
                    int amountToAdd = cursor.getAmount();
                    int newAmount = Math.min(vaultItem.item().getMaxStackSize(), vaultItem.amount() + amountToAdd);
                    int restInCursor = amountToAdd - (newAmount - vaultItem.amount());
                    var newStorageItem = this.addToStorageItem(player, chest, vault, vaultItem, this.cloneItemStack(cursor), newAmount - vaultItem.amount(), event);
                    event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                    ItemStack newCursor = this.cloneItemStack(cursor);
                    if(restInCursor == 0) {
                        newCursor = new ItemStack(Material.AIR);
                    } else {
                        newCursor.setAmount(restInCursor);
                    }
                    CompatibilityUtil.setCursor(event,newCursor);
                }

                case SWAP_WITH_CURSOR -> {
                    this.switchWithCursor(event, player, cursor, slot, chest, vault, vaultItem);
                }

                case PICKUP_ALL -> {
                    var newStorageItem = this.removeFromStorageItem(player, vault, vaultItem, vaultItem.amount());
                    event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                    ItemStack toAdd = this.cloneItemStack(vaultItem.item());
                    toAdd.setAmount(vaultItem.amount());
                    CompatibilityUtil.setCursor(event,toAdd);
                }
            }
        }
    }

    
    public void handleRightClick(InventoryClickEvent event, Player player, ItemStack cursor, ItemStack current, int slot, int inventorySize, PlacedChestContent vault, PlacedChest chest) {
        StorageItem vaultItem = vault.content().stream().filter(item -> item.slot() == slot).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slot));
        if (chest.getChestTemplate().isInfinite()) {
            if(cursor == null || cursor.getType().isAir() && !vaultItem.isEmpty()) {
                int amountToRemove = Math.min(vaultItem.amount() / 2, vaultItem.item().getMaxStackSize() / 2);
                if(amountToRemove == 0) {
                    amountToRemove = 1;
                }
                this.removeItem(event, player, slot, vault, chest, vaultItem, amountToRemove);
            } else if(!cursor.getType().isAir()) {
                int slotToAdd = this.findCorrespondingSlot(event.getInventory(), cursor, vault, chest);
                if(slotToAdd == -1) {
                    return;
                }
                vaultItem = vault.content().stream().filter(item -> item.slot() == slotToAdd).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slotToAdd));
                this.addItem(event, player, slotToAdd, vault, chest, vaultItem, cursor, 1);
            }
        } else {
            InventoryAction action = event.getAction();
            switch (action) {
                case SWAP_WITH_CURSOR -> {
                    this.switchWithCursor(event, player, cursor, slot, chest,vault, vaultItem);
                }

                case PICKUP_HALF -> {
                    int halfAmount = vaultItem.amount() / 2;
                    if(halfAmount == 0) {
                        halfAmount = 1;
                    }
                    var newStorageItem = this.removeFromStorageItem(player, vault, vaultItem, halfAmount);
                    event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                    ItemStack toAdd = this.cloneItemStack(vaultItem.item());
                    toAdd.setAmount(halfAmount);
                    CompatibilityUtil.setCursor(event,toAdd);
                }
                case PLACE_ONE -> {
                    var newStorageItem = this.addToStorageItem(player, chest, vault, vaultItem, this.cloneItemStack(cursor), 1, event);
                    event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                    ItemStack newCursor = this.cloneItemStack(cursor);
                    if(cursor.getAmount() - 1 == 0) {
                        newCursor = new ItemStack(Material.AIR);
                    } else {
                        newCursor.setAmount(cursor.getAmount() - 1);
                    }
                    CompatibilityUtil.setCursor(event,newCursor);
                }
            }
        }
    }

    
    public void handleShift(InventoryClickEvent event, Player player, ItemStack cursor, ItemStack current, int slot, int inventorySize, PlacedChestContent vault, PlacedChest chest, List<Integer> slots) {
        if(chest.getChestTemplate().isInfinite()) {
            if (slot >= inventorySize) {
                if(cursor == null || cursor.getType().isAir() && current == null || current.getType().isAir()) {
                    return;
                }
                int slotToAdd = this.findCorrespondingSlot(event.getInventory(), current, vault, chest);
                if(slotToAdd == -1) {
                    return;
                }
                StorageItem vaultItem = vault.content().stream().filter(item -> item.slot() == slotToAdd).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slotToAdd));
                var newStorageItem = this.addToStorageItem(player, chest, vault, vaultItem, current, current.getAmount(), event);
                event.getInventory().setItem(slotToAdd, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                event.setCurrentItem(new ItemStack(Material.AIR));
            } else {
                this.shiftClickFromPlacedChestContent(event, player, cursor, current, slot, vault, chest);
            }
        } else {
            if (slot >= inventorySize) {
                if(cursor == null || cursor.getType().isAir() && current == null || current.getType().isAir()) {
                    return;
                }
                var virtualInv = Bukkit.createInventory(null, inventorySize, "virtual_inv");
                virtualInv.setContents(event.getInventory().getContents());
                var rest = virtualInv.addItem(current);
                for (int i : slots) {
                    ItemStack virtual = virtualInv.getItem(i);
                    ItemStack real = event.getInventory().getItem(i);
                    if(this.isDifferent(virtual, real, true)) {
                        var newStorageItem = this.addToStorageItem(player, chest, vault, new StorageItem(new ItemStack(Material.AIR), 1, i), virtual, virtual.getAmount(), event);
                        event.getInventory().setItem(i, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                    }
                }
                int newCurrentAmount = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
                ItemStack newCurrent = this.cloneItemStack(current);
                if(newCurrentAmount == 0) {
                    newCurrent = new ItemStack(Material.AIR);
                } else {
                    newCurrent.setAmount(newCurrentAmount);
                }
                player.getInventory().setItem(event.getSlot(), newCurrent);
            } else {
                this.shiftClickFromPlacedChestContent(event, player, cursor, current, slot, vault, chest);
            }

        }
    }

    
    public void handleDrop(InventoryClickEvent event, Player player, ItemStack cursor, ItemStack current, int slot, int inventorySize, PlacedChestContent vault, PlacedChest chest, boolean controlDrop) {
        StorageItem vaultItem = vault.content().stream().filter(item -> item.slot() == slot).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slot));
        if(vaultItem.isEmpty()) {
            return;
        }
        int amountToDrop;
        if(controlDrop) {
            amountToDrop = Math.min(vaultItem.amount(), vaultItem.item().getMaxStackSize());
        } else {
            amountToDrop = 1;
        }

        StorageItem newStorageItem = this.removeFromStorageItem(player, vault, vaultItem, amountToDrop);
        event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
        ItemStack item = this.cloneItemStack(vaultItem.item());
        item.setAmount(amountToDrop);
        player.getWorld().dropItemNaturally(player.getLocation(), item);
    }

    
    public void handleNumberKey(InventoryClickEvent event, Player player, ItemStack cursor, ItemStack current, int slot, int inventorySize, PlacedChestContent vault, PlacedChest chest) {
        ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
        StorageItem vaultItem = vault.content().stream().filter(item -> item.slot() == slot).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slot));

        if(chest.getChestTemplate().isInfinite()) {
            if(vaultItem.isEmpty() && hotbarItem != null && !hotbarItem.getType().isAir()) {
                this.addFromHotbar(event, player, chest, vault, hotbarItem);
            } else if(!vaultItem.isEmpty() && (hotbarItem == null || hotbarItem.getType().isAir())) {
                int amount = Math.min(vaultItem.amount(), vaultItem.item().getMaxStackSize());
                ItemStack toAdd = vaultItem.item().clone();
                toAdd.setAmount(amount);
                player.getInventory().setItem(event.getHotbarButton(), toAdd);
                var newStorageItem = this.removeFromStorageItem(player, vault, vaultItem, amount);
                event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
            } else if (hotbarItem != null && vaultItem.item().isSimilar(hotbarItem)) {
                this.addFromHotbar(event, player, chest, vault, hotbarItem);
            }
        } else {
            if(vaultItem.isEmpty() && hotbarItem != null && !hotbarItem.getType().isAir()) {
                var newStorageItem = this.addToStorageItem(player, chest, vault, new StorageItem(new ItemStack(Material.AIR), 1, slot), hotbarItem, hotbarItem.getAmount(),event);
                event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                player.getInventory().setItem(event.getHotbarButton(), new ItemStack(Material.AIR));
            } else if(!vaultItem.isEmpty() && (hotbarItem == null || hotbarItem.getType().isAir())) {
                ItemStack newHotbarItem = this.cloneItemStack(vaultItem.item());
                newHotbarItem.setAmount(vaultItem.amount());
                player.getInventory().setItem(event.getHotbarButton(), newHotbarItem);
                var newStorageItem = this.removeFromStorageItem(player, vault, vaultItem, vaultItem.amount());
                event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
            } else if (hotbarItem != null && !this.isDifferent(this.cloneItemStack(vaultItem.item()), hotbarItem, false)) {
                int newAmount = Math.min(vaultItem.amount() + hotbarItem.getAmount(), vaultItem.item().getMaxStackSize());
                int rest = hotbarItem.getAmount() - (newAmount - vaultItem.amount());
                var newStorageItem = this.addToStorageItem(player, chest, vault, vaultItem, this.cloneItemStack(hotbarItem), newAmount - vaultItem.amount(), event);
                event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                ItemStack newHotbarItem = this.cloneItemStack(hotbarItem);
                if(rest == 0) {
                    newHotbarItem = new ItemStack(Material.AIR);
                } else {
                    newHotbarItem.setAmount(rest);
                }
                player.getInventory().setItem(event.getHotbarButton(), newHotbarItem);
            }
        }

    }

    private void shiftClickFromPlacedChestContent(InventoryClickEvent event, Player player, ItemStack cursor, ItemStack current, int slot, PlacedChestContent vault, PlacedChest chest) {
        if(cursor == null || cursor.getType().isAir() && current == null || current.getType().isAir()) {
            return;
        }

        StorageItem vaultItem = vault.content().stream().filter(item -> item.slot() == slot).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slot));
        int removeAmount = Math.min(vaultItem.amount(), current.getMaxStackSize());
        ItemStack toAdd = this.cloneItemStack(vaultItem.item());
        toAdd.setAmount(removeAmount);
        var rest = player.getInventory().addItem(toAdd);
        if(!rest.isEmpty()) {
            removeAmount -= rest.values().stream().mapToInt(ItemStack::getAmount).sum();
        }
        var newStorageItem = this.removeFromStorageItem(player, vault, vaultItem, removeAmount);
        event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
    }

    private void switchWithCursor(InventoryClickEvent event, Player player, ItemStack cursor, int slot, PlacedChest chest, PlacedChestContent vault, StorageItem vaultItem) {
        var newStorageItem = this.addToStorageItem(player, chest, vault, new StorageItem(new ItemStack(Material.AIR), 1, slot), cursor, cursor.getAmount(), event);
        event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
        ItemStack toAdd = this.cloneItemStack(vaultItem.item());
        toAdd.setAmount(vaultItem.amount());
        CompatibilityUtil.setCursor(event,toAdd);
    }

    private void addFromHotbar(InventoryClickEvent event, Player player, PlacedChest chest, PlacedChestContent vault, ItemStack hotbarItem) {
        StorageItem vaultItem;
        int slotToAdd = this.findCorrespondingSlot(event.getInventory(), hotbarItem, vault, chest);
        vaultItem = vault.content().stream().filter(item -> item.slot() == slotToAdd).findFirst().orElse(new StorageItem(new ItemStack(Material.AIR), 1, slotToAdd));
        var newStorageItem = this.addToStorageItem(player, chest, vault, vaultItem, hotbarItem, hotbarItem.getAmount(), event);
        event.getInventory().setItem(slotToAdd, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
        player.getInventory().setItem(event.getHotbarButton(), new ItemStack(Material.AIR));
    }

    private void addItem(InventoryClickEvent event, Player player, int slot, PlacedChestContent vault, PlacedChest chest, StorageItem vaultItem, ItemStack cursor, int amountToAdd) {
        var newStorageItem = this.addToStorageItem(player, chest, vault, vaultItem, cursor, amountToAdd, event);
        event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
        int newAmount = cursor.getAmount() - amountToAdd;
        if(newAmount == 0) {
            CompatibilityUtil.setCursor(event,new ItemStack(Material.AIR));
            return;
        }
        cursor.setAmount(newAmount);
        CompatibilityUtil.setCursor(event,cursor);
    }

    private void removeItem(InventoryClickEvent event, Player player, int slot, PlacedChestContent vault, PlacedChest chest, StorageItem vaultItem, int amountToRemove) {
        var newStorageItem = this.removeFromStorageItem(player, vault, vaultItem, amountToRemove);
        event.getInventory().setItem(slot, newStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
        ItemStack newCursor = newStorageItem.isEmpty() ? vaultItem.item().clone() : newStorageItem.item().clone();
        newCursor.setAmount(amountToRemove);
        CompatibilityUtil.setCursor(event,newCursor);
    }

    private int findCorrespondingSlot(Inventory inventory, ItemStack correspond, PlacedChestContent vault, PlacedChest chest) {
        for (StorageItem vaultItem : vault.content()) {
            if(correspond.isSimilar(vaultItem.item()) && vaultItem.amount() < this.getMaxStackSize(chest, vaultItem.item())) {
                return vaultItem.slot();
            }
        }
        return inventory.firstEmpty();
    }

    private StorageItem removeFromStorageItem(Player player, PlacedChestContent vault, StorageItem vaultItem, int amount) {
        int currentAmount = vaultItem.amount();
        StorageItem newStorageItem;
        if(currentAmount - amount == 0) {
            newStorageItem = new StorageItem(new ItemStack(Material.AIR), 1, vaultItem.slot());
        } else {
            newStorageItem = new StorageItem(vaultItem.item(), currentAmount - amount, vaultItem.slot());
        }
        vault.setContent(vault.content().stream().map(item -> item.slot() == newStorageItem.slot() ? newStorageItem : item).collect(Collectors.toList()));
        return newStorageItem;
    }

    private StorageItem addToStorageItem(Player player, PlacedChest chest, PlacedChestContent content, StorageItem vaultItem, ItemStack cursor, int amount, InventoryClickEvent event) {
        int maxStackSize = this.getMaxStackSize(chest, cursor);
        int remainingAmount = amount;
        int currentAmount = vaultItem.isEmpty() ? 0 : vaultItem.amount();

        // Add to current slot up to max stack size
        int amountToAddInCurrent = Math.min(maxStackSize - currentAmount, remainingAmount);
        StorageItem newStorageItem = new StorageItem(vaultItem.isEmpty() ? cursor : vaultItem.item(), currentAmount + amountToAddInCurrent, vaultItem.slot());
        plugin.getManager(StoragePlusManager.class).setContent(chest, content.content().stream().map(v -> v.slot() == newStorageItem.slot() ? newStorageItem : v).collect(Collectors.toList()));
        remainingAmount -= amountToAddInCurrent;
        if (remainingAmount <= 0) {
            return newStorageItem;
        }

        // Add remaining amount in other empty slots
        for (StorageItem slot : content.content()) {
            if (slot.isEmpty()) {
                int amountToAdd = Math.min(maxStackSize, remainingAmount);
                StorageItem additionalStorageItem = new StorageItem(cursor, amountToAdd, slot.slot());
                plugin.getManager(StoragePlusManager.class).setContent(chest,content.content().stream().map(v -> v.slot() == additionalStorageItem.slot() ? additionalStorageItem : v).collect(Collectors.toList()));
                event.getInventory().setItem(slot.slot(), additionalStorageItem.toItem(player, chest.getChestTemplate().isInfinite()));
                remainingAmount -= amountToAdd;
                if (remainingAmount <= 0) break;
            }
        }

        return newStorageItem;
    }

    private int getMaxStackSize(PlacedChest chest, ItemStack item) {
        return chest.getChestTemplate().isInfinite() ? (chest.getChestTemplate().getMaxStackSize() == -1 ? Integer.MAX_VALUE : chest.getChestTemplate().getMaxStackSize())  : item.getMaxStackSize();
    }

    private ItemStack cloneItemStack(ItemStack itemStack) {
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

    private boolean isDifferent(ItemStack item1, ItemStack item2, boolean checkAmount) {
        if (item1 == null && item2 == null) {
            return false;
        }

        if (item1 == null || item2 == null) {
            return true;
        }

        if (item1.getType() != item2.getType()) {
            return true;
        }

        if(checkAmount) {
            if (item1.getAmount() != item2.getAmount()) {
                return true;
            }
        }

        if (!item1.hasItemMeta() && !item2.hasItemMeta()) {
            return false;
        }

        if (item1.hasItemMeta() != item2.hasItemMeta()) {
            return true;
        }

        if (item1.hasItemMeta() && item2.hasItemMeta()) {
            if (!item1.getItemMeta().equals(item2.getItemMeta())) {
                return true;
            }
        }
        return false;
    }

}