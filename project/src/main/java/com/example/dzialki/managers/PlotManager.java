package com.example.dzialki.managers;

import com.example.dzialki.Dzialki;
import com.example.dzialki.models.Plot;
import com.example.dzialki.utils.TagGenerator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlotManager {
    private final Dzialki plugin;
    private final Map<String, Plot> plots;
    private final Map<UUID, String> playerPlots;
    private final Map<UUID, String> lastPlotVisited;
    private final File plotsFile;

    public PlotManager(Dzialki plugin) {
        this.plugin = plugin;
        this.plots = new HashMap<>();
        this.playerPlots = new HashMap<>();
        this.lastPlotVisited = new HashMap<>();
        this.plotsFile = new File(plugin.getDataFolder(), "plots.yml");
    }

    public ItemStack createPlotHeart() {
        ItemStack heart = new ItemStack(Material.RED_SHULKER_BOX);
        ItemMeta meta = heart.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Serce Działki");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Postaw, aby utworzyć działkę");
        lore.add(ChatColor.GRAY + "Chroni obszar 17x17 bloków");
        lore.add(ChatColor.GRAY + "Możesz powiększyć działkę do 81x81 bloków");
        lore.add(ChatColor.GRAY + "używając /dzialka powieksz (max 4 razy)");
        meta.setLore(lore);
        heart.setItemMeta(meta);
        return heart;
    }

    public boolean isPlotHeart(ItemStack item) {
        if (item == null || item.getType() != Material.RED_SHULKER_BOX) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() &&
                meta.getDisplayName().equals(ChatColor.RED + "Serce Działki");
    }

    public Plot createPlot(Player player, Location location) {
        // Check if player already has a plot
        if (hasPlot(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Już posiadasz działkę!");
            return null;
        }

        // Check if location is within another plot
        if (getPlotAt(location) != null) {
            player.sendMessage(ChatColor.RED + "Nie możesz utworzyć działki na terenie innej działki!");
            return null;
        }

        // Generate unique tag
        String tag = TagGenerator.generateTag();
        while (plots.containsKey(tag)) {
            tag = TagGenerator.generateTag();
        }

        // Create plot
        Plot plot = new Plot(tag, player.getUniqueId(), location);
        plots.put(tag, plot);
        playerPlots.put(player.getUniqueId(), tag);

        player.sendMessage(ChatColor.GREEN + "Utworzono działkę o tagu: " + ChatColor.GOLD + tag);
        return plot;
    }

    public boolean removePlot(String tag) {
        Plot plot = plots.get(tag);
        if (plot == null) {
            return false;
        }

        // Remove plot from player's plots
        playerPlots.remove(plot.getOwner());

        // Remove plot from members' plots
        for (UUID member : plot.getMembers()) {
            playerPlots.remove(member);
        }

        // Remove plot
        plots.remove(tag);

        return true;
    }

    public Plot getPlot(String tag) {
        return plots.get(tag);
    }

    public Plot getPlotAt(Location location) {
        for (Plot plot : plots.values()) {
            if (plot.isInPlot(location)) {
                return plot;
            }
        }
        return null;
    }

    public Plot getPlayerPlot(UUID playerId) {
        String tag = playerPlots.get(playerId);
        if (tag == null) {
            return null;
        }
        return plots.get(tag);
    }

    public boolean hasPlot(UUID playerId) {
        return playerPlots.containsKey(playerId);
    }

    public void updateLastVisitedPlot(UUID playerId, String tag) {
        lastPlotVisited.put(playerId, tag);
    }

    public String getLastVisitedPlot(UUID playerId) {
        return lastPlotVisited.get(playerId);
    }

    public void removeLastVisitedPlot(UUID playerId) {
        lastPlotVisited.remove(playerId);
    }

    public void invitePlayer(UUID ownerId, UUID targetId) {
        Plot plot = getPlayerPlot(ownerId);
        if (plot != null) {
            plot.invitePlayer(targetId);
        }
    }

    public boolean joinPlot(UUID playerId, String tag) {
        // Check if player already has a plot
        if (hasPlot(playerId)) {
            return false;
        }

        Plot plot = getPlot(tag);
        if (plot == null || !plot.isInvited(playerId)) {
            return false;
        }

        plot.addMember(playerId);
        playerPlots.put(playerId, tag);
        return true;
    }

    public void removePlayerFromPlot(UUID playerId) {
        playerPlots.remove(playerId);
    }

    public Location findSafeLocationOutsidePlot(Plot plot) {
        Location heartLoc = plot.getHeartLocation();
        World world = heartLoc.getWorld();

        // Try to find a safe location outside the plot
        // Start with a location just outside the plot boundary
        int plotRadius = plot.getRadius(); // Use dynamic radius

        // Try different directions to find a safe spot
        int[][] directions = {
                {1, 0},   // East
                {0, 1},   // South
                {-1, 0},  // West
                {0, -1},  // North
                {1, 1},   // Southeast
                {-1, 1},  // Southwest
                {-1, -1}, // Northwest
                {1, -1}   // Northeast
        };

        // Start with a position just outside the plot
        for (int[] dir : directions) {
            // Start at the edge of the plot + 1 block
            int startX = heartLoc.getBlockX() + (dir[0] * (plotRadius + 1));
            int startZ = heartLoc.getBlockZ() + (dir[1] * (plotRadius + 1));

            // Try up to 10 blocks in this direction
            for (int distance = 0; distance < 10; distance++) {
                int x = startX + (dir[0] * distance);
                int z = startZ + (dir[1] * distance);

                // Find the highest block at this x,z position
                int y = world.getHighestBlockYAt(x, z);

                // Check if this location is safe
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                if (isSafeLocation(loc)) {
                    return loc;
                }
            }
        }

        // If no safe location is found, return the world spawn point
        return world.getSpawnLocation();
    }

    private boolean isSafeLocation(Location location) {
        Block block = location.getBlock();
        Block below = location.clone().subtract(0, 1, 0).getBlock();
        Block above = location.clone().add(0, 1, 0).getBlock();

        // Check if the blocks at and above the location are air (or other safe blocks)
        boolean blockSafe = block.getType() == Material.AIR ||
                block.getType() == Material.SHORT_GRASS ||
                block.getType() == Material.TALL_GRASS;

        boolean aboveSafe = above.getType() == Material.AIR ||
                above.getType() == Material.SHORT_GRASS ||
                above.getType() == Material.TALL_GRASS;

        // Check if the block below is solid
        boolean belowSolid = below.getType().isSolid() &&
                below.getType() != Material.LAVA &&
                below.getType() != Material.WATER;

        return blockSafe && aboveSafe && belowSolid;
    }

    public void savePlots() {
        try {
            if (!plotsFile.exists()) {
                plotsFile.createNewFile();
            }

            FileConfiguration config = new YamlConfiguration();

            // Save plots
            List<Map<String, Object>> plotsList = new ArrayList<>();
            for (Plot plot : plots.values()) {
                plotsList.add(plot.serialize());
            }
            config.set("plots", plotsList);

            config.save(plotsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać działek: " + e.getMessage());
        }
    }

    public void loadPlots() {
        plots.clear();
        playerPlots.clear();

        if (!plotsFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(plotsFile);

        // Load plots
        List<Map<?, ?>> plotsList = config.getMapList("plots");
        for (Map<?, ?> map : plotsList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> plotMap = (Map<String, Object>) map;
            Plot plot = new Plot(plotMap);
            plots.put(plot.getTag(), plot);

            // Add owner to playerPlots
            playerPlots.put(plot.getOwner(), plot.getTag());

            // Add members to playerPlots
            for (UUID member : plot.getMembers()) {
                playerPlots.put(member, plot.getTag());
            }
        }

        plugin.getLogger().info("Załadowano " + plots.size() + " działek");
    }

    public Collection<Plot> getAllPlots() {
        return plots.values();
    }
}