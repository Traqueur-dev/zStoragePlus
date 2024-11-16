package fr.traqueur.storageplus.domains;

import fr.traqueur.storageplugs.api.StoragePlusManager;
import fr.traqueur.storageplugs.api.domains.ChestTemplate;
import fr.traqueur.storageplugs.api.domains.PlacedChest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class ZPlacedChest implements PlacedChest {

    private final UUID owner;
    private final Location location;
    private final ChestTemplate chestTemplate;
    private long time;

    public ZPlacedChest(UUID owner, Location location, ChestTemplate chestTemplate) {
        this(owner, location, chestTemplate, 0);
    }

    public ZPlacedChest(UUID owner, Location location, ChestTemplate chestTemplate, long time) {
        this.owner = owner;
        this.location = location;
        this.chestTemplate = chestTemplate;
        this.time = time;
    }

    @Override
    public String serialize() {
        return (this.location.getWorld() == null ? "null" : this.location.getWorld().getUID()) + ";"
                + this.location.getBlockX() + ";"
                + this.location.getBlockY() + ";"
                + this.location.getBlockZ() + ";"
                + this.chestTemplate.getName() + ";"
                + this.time + ";"
                + this.owner.toString();
    }

    @Override
    public void tick() {
        this.time++;
        if(this.time == Long.MAX_VALUE) {
            this.time = 0;
        }
    }
    @Override
    public long getTime() {
        return this.time;
    }

    @Override
    public ChestTemplate getChestTemplate() {
        return chestTemplate;
    }

    @Override
    public Location getLocation() {
        return location;
    }
}