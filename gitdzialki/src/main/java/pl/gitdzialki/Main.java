package pl.gitdzialki;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private PlotManager plotManager;
    private DispenserActivatorTracker activatorTracker;

    @Override
    public void onEnable() {
        // Inicjalizacja PlotManager
        plotManager = new PlotManager(this);
        plotManager.loadPlots(); // Wczytanie zapisanych działek

        // Inicjalizacja DispenserActivatorTracker
        activatorTracker = new DispenserActivatorTracker(this);

        // Rejestracja komend
        getCommand("ddaj").setExecutor(new DzialkaCommand(this));
        getCommand("dustaw").setExecutor(new DzialkaCommand(this));
        getCommand("ddom").setExecutor(new DzialkaCommand(this));
        getCommand("dusun").setExecutor(new DzialkaCommand(this));

        // Rejestracja listenerów
        getServer().getPluginManager().registerEvents(new DzialkaListener(plotManager, this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // Rejestracja trackera dyspenserów
        getServer().getPluginManager().registerEvents(activatorTracker, this);

        // Dodajemy nowe listenery do rozszerzonej ochrony działek
        getServer().getPluginManager().registerEvents(
                new DispenserProtectionListener(this, plotManager, activatorTracker), this);
        getServer().getPluginManager().registerEvents(
                new EnhancedPlotProtectionListener(this, plotManager), this);

        getLogger().info("Plugin GitDzialki włączony!");
    }

    @Override
    public void onDisable() {
        // Zapisanie działek przed wyłączeniem pluginu
        if (plotManager != null) {
            plotManager.savePlots();
        }
        getLogger().info("Plugin GitDzialki wyłączony!");
    }

    public PlotManager getPlotManager() {
        return plotManager;
    }

    public DispenserActivatorTracker getActivatorTracker() {
        return activatorTracker;
    }
}