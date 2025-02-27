package pl.gitdzialki;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlotManager {

    private final Main plugin;
    private final Map<UUID, Location> plots;
    private final Map<UUID, Boolean> isCustomLocation;

    public PlotManager(Main plugin) {
        this.plugin = plugin;
        this.plots = new HashMap<>();
        this.isCustomLocation = new HashMap<>();
    }

    public void createPlot(Player player, Location location) {
        if (plots.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Posiadasz już działkę!");
            return;
        }

        // Używamy getBlockX() i getBlockZ(), aby zapisać dokładną pozycję bloku, ale zachowujemy yaw i pitch
        Location blockLocation = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getYaw(), location.getPitch());
        plots.put(player.getUniqueId(), blockLocation);
        isCustomLocation.put(player.getUniqueId(), false);
        player.sendMessage(ChatColor.GREEN + "Utworzono działkę!");
        savePlots();
    }

    public void removePlot(Player player, Location location) {
        if (!plots.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
            return;
        }

        plots.remove(player.getUniqueId());
        isCustomLocation.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Usunięto działkę!");
        savePlots();
    }

    public boolean isPlot(Location location) {
        for (Map.Entry<UUID, Location> entry : plots.entrySet()) {
            Location plotLocation = entry.getValue();
            int heartX = plotLocation.getBlockX();
            int heartZ = plotLocation.getBlockZ();
            int checkX = location.getBlockX();
            int checkZ = location.getBlockZ();

            // Sprawdzenie, czy lokalizacja jest w granicach 17x17 (8 bloków w każdą stronę)
            if (Math.abs(checkX - heartX) <= 8 && Math.abs(checkZ - heartZ) <= 8) {
                return true;
            }
        }
        return false;
    }

    public String getPlotOwnerName(Location location) {
        UUID ownerUUID = getPlotOwner(location);
        if (ownerUUID != null) {
            Player owner = plugin.getServer().getPlayer(ownerUUID);
            if (owner != null) {
                return owner.getName();
            }
            return plugin.getServer().getOfflinePlayer(ownerUUID).getName();
        }
        return null;
    }

    public UUID getPlotOwner(Location location) {
        for (Map.Entry<UUID, Location> entry : plots.entrySet()) {
            Location plotLocation = entry.getValue();
            int heartX = plotLocation.getBlockX();
            int heartZ = plotLocation.getBlockZ();
            int checkX = location.getBlockX();
            int checkZ = location.getBlockZ();

            // Sprawdzenie, czy lokalizacja jest w granicach 17x17 (8 bloków w każdą stronę)
            if (Math.abs(checkX - heartX) <= 8 && Math.abs(checkZ - heartZ) <= 8) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean hasPlot(Player player) {
        return plots.containsKey(player.getUniqueId());
    }

    public Location getPlotLocation(Player player) {
        return plots.get(player.getUniqueId());
    }

    public void updatePlotLocation(Player player, Location newLocation) {
        // Zapisuj pełną lokalizację, w tym yaw i pitch
        Location fullLocation = new Location(newLocation.getWorld(), newLocation.getBlockX(), newLocation.getBlockY(), newLocation.getBlockZ(), newLocation.getYaw(), newLocation.getPitch());
        plots.put(player.getUniqueId(), fullLocation);
        isCustomLocation.put(player.getUniqueId(), true);
        savePlots();
    }

    public boolean isCustomLocation(Player player) {
        return isCustomLocation.getOrDefault(player.getUniqueId(), false);
    }

    public void savePlots() {
        plugin.getConfig().set("plots", null);
        for (Map.Entry<UUID, Location> entry : plots.entrySet()) {
            UUID uuid = entry.getKey();
            Location location = entry.getValue();
            String path = "plots." + uuid.toString();
            plugin.getConfig().set(path + ".world", location.getWorld().getName());
            plugin.getConfig().set(path + ".x", location.getBlockX()); // Zapisz jako liczbę całkowitą
            plugin.getConfig().set(path + ".y", location.getBlockY());
            plugin.getConfig().set(path + ".z", location.getBlockZ()); // Zapisz jako liczbę całkowitą
            plugin.getConfig().set(path + ".yaw", location.getYaw());
            plugin.getConfig().set(path + ".pitch", location.getPitch());
            plugin.getConfig().set(path + ".custom", isCustomLocation.getOrDefault(uuid, false));
        }
        plugin.saveConfig();
    }

    public void loadPlots() {
        plots.clear();
        isCustomLocation.clear();
        if (plugin.getConfig().contains("plots")) {
            for (String uuidString : plugin.getConfig().getConfigurationSection("plots").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                String path = "plots." + uuidString;
                String worldName = plugin.getConfig().getString(path + ".world");
                int x = plugin.getConfig().getInt(path + ".x"); // Wczytaj jako liczbę całkowitą
                int y = plugin.getConfig().getInt(path + ".y");
                int z = plugin.getConfig().getInt(path + ".z"); // Wczytaj jako liczbę całkowitą
                float yaw = (float) plugin.getConfig().getDouble(path + ".yaw", 0.0);
                float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 0.0);
                boolean custom = plugin.getConfig().getBoolean(path + ".custom", false);
                Location location = new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
                plots.put(uuid, location);
                isCustomLocation.put(uuid, custom);
            }
        }
    }
}