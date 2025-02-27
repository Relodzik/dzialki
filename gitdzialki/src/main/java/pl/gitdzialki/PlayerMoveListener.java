package pl.gitdzialki;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {

    private final Main plugin;

    public PlayerMoveListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            PlotManager plotManager = plugin.getPlotManager();
            boolean wasInPlot = plotManager.isPlot(from);
            boolean isInPlot = plotManager.isPlot(to);

            if (!wasInPlot && isInPlot) {
                String ownerName = plotManager.getPlotOwnerName(to);
                player.sendMessage(ChatColor.GREEN + "Wszedłeś na teren działki " + ownerName + "!");
            } else if (wasInPlot && !isInPlot) {
                String ownerName = plotManager.getPlotOwnerName(from);
                player.sendMessage(ChatColor.RED + "Wyszedłeś poza teren działki " + ownerName + "!");
            }
        }
    }
}