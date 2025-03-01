package com.example.dzialki.listeners;

import com.example.dzialki.Dzialki;
import com.example.dzialki.managers.PlotManager;
import com.example.dzialki.models.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class PlotProtectionListener implements Listener {
    private final Dzialki plugin;
    private final PlotManager plotManager;

    // Maps to track who last interacted with redstone components
    private final Map<String, UUID> lastRedstoneInteraction = new HashMap<>();
    private final long INTERACTION_TIMEOUT = 30000; // 30 seconds in milliseconds

    public PlotProtectionListener(Dzialki plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();

        // Schedule a task to clean up old interactions
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            lastRedstoneInteraction.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue().getMostSignificantBits()) > INTERACTION_TIMEOUT);
        }, 600L, 600L); // Run every 30 seconds (600 ticks)
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Plot plot = plotManager.getPlotAt(block.getLocation());

        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nie możesz niszczyć bloków na tej działce!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Plot plot = plotManager.getPlotAt(block.getLocation());

        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nie możesz stawiać bloków na tej działce!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        // Record player interaction with redstone components
        if (isRedstoneComponent(block.getType())) {
            // Store player UUID with current timestamp in most significant bits
            UUID timeTaggedUUID = new UUID(System.currentTimeMillis(), player.getUniqueId().getLeastSignificantBits());

            // Use block coordinates as key
            String blockKey = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            lastRedstoneInteraction.put(blockKey, timeTaggedUUID);

            // Log for debugging
            plugin.getLogger().info("Player " + player.getName() + " interacted with redstone at " + blockKey);
        }

        Plot plot = plotManager.getPlotAt(block.getLocation());

        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nie możesz wchodzić w interakcję z blokami na tej działce!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.isCancelled()) return;

        Block pistonBlock = event.getBlock();
        String blockKey = pistonBlock.getWorld().getName() + "," + pistonBlock.getX() + "," + pistonBlock.getY() + "," + pistonBlock.getZ();
        UUID playerUUID = getPlayerUUID(lastRedstoneInteraction.get(blockKey));

        Plot pistonPlot = plotManager.getPlotAt(pistonBlock.getLocation());

        // Check if any blocks are being pushed into a plot
        for (Block block : event.getBlocks()) {
            // Calculate the target location where the block will be pushed
            Location targetLoc = block.getLocation().clone().add(
                    event.getDirection().getModX(),
                    event.getDirection().getModY(),
                    event.getDirection().getModZ()
            );

            Plot targetPlot = plotManager.getPlotAt(targetLoc);

            // If pushing into a plot from outside, or from one plot to another
            if ((pistonPlot == null && targetPlot != null) ||
                    (pistonPlot != null && targetPlot != null && !pistonPlot.getTag().equals(targetPlot.getTag()))) {

                // If we know who triggered this and they are a member of the target plot, allow it
                if (playerUUID != null && targetPlot != null && targetPlot.isMember(playerUUID)) {
                    continue;
                }

                // Otherwise, cancel the event
                event.setCancelled(true);
                return;
            }
        }

        // Check if any blocks being moved are in a plot
        for (Block block : event.getBlocks()) {
            Plot blockPlot = plotManager.getPlotAt(block.getLocation());

            // If moving blocks from a plot with a piston outside the plot
            if (pistonPlot == null && blockPlot != null) {
                // If we know who triggered this and they are a member of the block's plot, allow it
                if (playerUUID != null && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // Otherwise, cancel the event
                event.setCancelled(true);
                return;
            }

            // If moving blocks from one plot to another
            if (pistonPlot != null && blockPlot != null && !pistonPlot.getTag().equals(blockPlot.getTag())) {
                // If we know who triggered this and they are a member of both plots, allow it
                if (playerUUID != null && pistonPlot.isMember(playerUUID) && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // Otherwise, cancel the event
                event.setCancelled(true);
                return;
            }
        }

        // Check if the piston head will extend into a plot
        Location pistonHeadLoc = pistonBlock.getLocation().clone().add(
                event.getDirection().getModX(),
                event.getDirection().getModY(),
                event.getDirection().getModZ()
        );

        Plot pistonHeadPlot = plotManager.getPlotAt(pistonHeadLoc);

        // If piston head extends into a different plot
        if ((pistonPlot == null && pistonHeadPlot != null) ||
                (pistonPlot != null && pistonHeadPlot != null && !pistonPlot.getTag().equals(pistonHeadPlot.getTag()))) {

            // If we know who triggered this and they are a member of the head's plot, allow it
            if (playerUUID != null && pistonHeadPlot != null && pistonHeadPlot.isMember(playerUUID)) {
                return;
            }

            // Otherwise, cancel the event
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.isCancelled()) return;

        Block pistonBlock = event.getBlock();
        String blockKey = pistonBlock.getWorld().getName() + "," + pistonBlock.getX() + "," + pistonBlock.getY() + "," + pistonBlock.getZ();
        UUID playerUUID = getPlayerUUID(lastRedstoneInteraction.get(blockKey));

        Plot pistonPlot = plotManager.getPlotAt(pistonBlock.getLocation());

        // Check if any blocks being pulled are in a plot
        for (Block block : event.getBlocks()) {
            Plot blockPlot = plotManager.getPlotAt(block.getLocation());

            // If pulling blocks from a plot with a piston outside the plot
            if (pistonPlot == null && blockPlot != null) {
                // If we know who triggered this and they are a member of the block's plot, allow it
                if (playerUUID != null && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // Otherwise, cancel the event
                event.setCancelled(true);
                return;
            }

            // If pulling blocks from one plot to another
            if (pistonPlot != null && blockPlot != null && !pistonPlot.getTag().equals(blockPlot.getTag())) {
                // If we know who triggered this and they are a member of both plots, allow it
                if (playerUUID != null && pistonPlot.isMember(playerUUID) && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // Otherwise, cancel the event
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (event.isCancelled()) return;

        Block dispenserBlock = event.getBlock();
        String blockKey = dispenserBlock.getWorld().getName() + "," + dispenserBlock.getX() + "," + dispenserBlock.getY() + "," + dispenserBlock.getZ();
        UUID playerUUID = getPlayerUUID(lastRedstoneInteraction.get(blockKey));

        Plot dispenserPlot = plotManager.getPlotAt(dispenserBlock.getLocation());

        // Get the direction the dispenser is facing
        BlockFace face = ((org.bukkit.block.data.Directional) dispenserBlock.getBlockData()).getFacing();

        // Calculate the target location where the item will be dispensed
        Location targetLoc = dispenserBlock.getLocation().clone().add(
                face.getModX(),
                face.getModY(),
                face.getModZ()
        );

        Plot targetPlot = plotManager.getPlotAt(targetLoc);

        // If dispenser is outside a plot and dispensing into a plot
        if (dispenserPlot == null && targetPlot != null) {
            // If we know who triggered this and they are a member of the target plot, allow it
            if (playerUUID != null && targetPlot.isMember(playerUUID)) {
                return;
            }

            // Otherwise, cancel the event
            event.setCancelled(true);
            return;
        }

        // If dispenser is in one plot and dispensing into another plot
        if (dispenserPlot != null && targetPlot != null && !dispenserPlot.getTag().equals(targetPlot.getTag())) {
            // If we know who triggered this and they are a member of both plots, allow it
            if (playerUUID != null && dispenserPlot.isMember(playerUUID) && targetPlot.isMember(playerUUID)) {
                return;
            }

            // Otherwise, cancel the event
            event.setCancelled(true);
            return;
        }

        // Special handling for water and lava buckets
        ItemStack item = event.getItem();
        if (item.getType().toString().contains("BUCKET")) {
            // Check a few blocks in the direction of dispensing to catch water/lava flow
            for (int i = 1; i <= 8; i++) {
                Location flowLoc = dispenserBlock.getLocation().clone().add(
                        face.getModX() * i,
                        face.getModY() * i,
                        face.getModZ() * i
                );

                Plot flowPlot = plotManager.getPlotAt(flowLoc);

                // If the flow would enter a different plot
                if ((dispenserPlot == null && flowPlot != null) ||
                        (dispenserPlot != null && flowPlot != null && !dispenserPlot.getTag().equals(flowPlot.getTag()))) {

                    // If we know who triggered this and they are a member of the flow plot, allow it
                    if (playerUUID != null && flowPlot.isMember(playerUUID)) {
                        continue;
                    }

                    // Otherwise, cancel the event
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;

        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plotManager.getPlotAt(block.getLocation()) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.isCancelled()) return;

        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plotManager.getPlotAt(block.getLocation()) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // Check if the player has moved to a different block
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Plot fromPlot = plotManager.getPlotAt(from);
        Plot toPlot = plotManager.getPlotAt(to);

        // Player is entering a plot
        if (fromPlot == null && toPlot != null) {
            player.sendMessage(ChatColor.YELLOW + "Wszedłeś na teren działki " + ChatColor.GOLD + toPlot.getTag());
            plotManager.updateLastVisitedPlot(player.getUniqueId(), toPlot.getTag());
        }
        // Player is leaving a plot
        else if (fromPlot != null && toPlot == null) {
            player.sendMessage(ChatColor.YELLOW + "Opuściłeś teren działki " + ChatColor.GOLD + fromPlot.getTag());
            plotManager.removeLastVisitedPlot(player.getUniqueId());
        }
        // Player is moving from one plot to another
        else if (fromPlot != null && toPlot != null && !fromPlot.getTag().equals(toPlot.getTag())) {
            player.sendMessage(ChatColor.YELLOW + "Opuściłeś teren działki " + ChatColor.GOLD + fromPlot.getTag());
            player.sendMessage(ChatColor.YELLOW + "Wszedłeś na teren działki " + ChatColor.GOLD + toPlot.getTag());
            plotManager.updateLastVisitedPlot(player.getUniqueId(), toPlot.getTag());
        }
    }

    /**
     * Extracts the player UUID from a time-tagged UUID
     * @param timeTaggedUUID The UUID with timestamp in most significant bits
     * @return The original player UUID, or null if input is null
     */
    private UUID getPlayerUUID(UUID timeTaggedUUID) {
        if (timeTaggedUUID == null) {
            return null;
        }
        return new UUID(0, timeTaggedUUID.getLeastSignificantBits());
    }

    /**
     * Checks if a material is a redstone component
     * @param material The material to check
     * @return True if it's a redstone component, false otherwise
     */
    private boolean isRedstoneComponent(org.bukkit.Material material) {
        String name = material.name();
        return name.contains("BUTTON") ||
                name.contains("LEVER") ||
                name.contains("PRESSURE_PLATE") ||
                name.contains("REDSTONE") ||
                name.equals("REPEATER") ||
                name.equals("COMPARATOR") ||
                name.equals("OBSERVER") ||
                name.equals("DISPENSER") ||
                name.equals("DROPPER") ||
                name.equals("HOPPER") ||
                name.contains("PISTON");
    }
}