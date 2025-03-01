package com.example.dzialki;

import com.example.dzialki.commands.DzialkaCommand;
import com.example.dzialki.listeners.PlotHeartListener;
import com.example.dzialki.listeners.PlotProtectionListener;
import com.example.dzialki.managers.PlotManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Dzialki extends JavaPlugin {
    private PlotManager plotManager;

    @Override
    public void onEnable() {
        // Create data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        // Initialize plot manager
        plotManager = new PlotManager(this);
        
        // Register commands
        getCommand("dzialka").setExecutor(new DzialkaCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlotHeartListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotProtectionListener(this), this);
        
        // Load plots from storage
        plotManager.loadPlots();
        
        getLogger().info("Plugin Dzialki został włączony!");
    }

    @Override
    public void onDisable() {
        // Save plots to storage
        if (plotManager != null) {
            plotManager.savePlots();
        }
        
        getLogger().info("Plugin Dzialki został wyłączony!");
    }

    public PlotManager getPlotManager() {
        return plotManager;
    }
}