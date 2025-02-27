package pl.gitdzialki;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private PlotManager plotManager;

    @Override
    public void onEnable() {
        // Inicjalizacja PlotManager
        plotManager = new PlotManager(this);
        plotManager.loadPlots(); // Wczytanie zapisanych działek

        // Rejestracja komend
        getCommand("ddaj").setExecutor(new DzialkaCommand(this));
        getCommand("dustaw").setExecutor(new DzialkaCommand(this));
        getCommand("ddom").setExecutor(new DzialkaCommand(this));
        getCommand("dusun").setExecutor(new DzialkaCommand(this));

        // Rejestracja listenerów
        getServer().getPluginManager().registerEvents(new DzialkaListener(plotManager, this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

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
}