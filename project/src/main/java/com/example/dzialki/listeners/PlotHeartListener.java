package com.example.dzialki.listeners;

import com.example.dzialki.Dzialki;
import com.example.dzialki.managers.PlotManager;
import com.example.dzialki.models.Plot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlotHeartListener implements Listener {
    private final Dzialki plugin;
    private final PlotManager plotManager;

    public PlotHeartListener(Dzialki plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Check if the player is placing a Plot Heart
        if (block.getType() == Material.RED_SHULKER_BOX && plotManager.isPlotHeart(event.getItemInHand())) {
            // Check if the location is within another plot
            if (plotManager.getPlotAt(block.getLocation()) != null) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nie możesz postawić Serca Działki na terenie innej działki!");
                return;
            }
            
            // Create a new plot
            Plot plot = plotManager.createPlot(player, block.getLocation());
            if (plot == null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Check if the player is breaking a Plot Heart
        if (block.getType() == Material.RED_SHULKER_BOX) {
            Plot plot = plotManager.getPlotAt(block.getLocation());
            
            // Check if the block is a Plot Heart
            if (plot != null && plot.getHeartLocation().equals(block.getLocation())) {
                // Check if the player is the owner
                if (!plot.isOwner(player.getUniqueId()) && !player.hasPermission("dzialka.admin.usun")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Tylko właściciel działki może zniszczyć Serce Działki!");
                    return;
                }
                
                // Remove the plot
                if (plotManager.removePlot(plot.getTag())) {
                    player.sendMessage(ChatColor.GREEN + "Działka została usunięta!");
                }
            }
        }
    }
}