package pl.gitdzialki;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DzialkaCommand implements CommandExecutor {

    private final Main plugin;

    public DzialkaCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Tylko gracz może używać tej komendy!");
            return true;
        }

        Player player = (Player) sender;
        PlotManager plotManager = plugin.getPlotManager();

        if (command.getName().equalsIgnoreCase("ddaj")) {
            // Dajemy graczowi przedmiot Serce Działki zamiast tworzyć działkę
            ItemStack serceDzialki = new ItemStack(Material.RED_SHULKER_BOX);
            ItemMeta meta = serceDzialki.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "Serce Działki");
                serceDzialki.setItemMeta(meta);
            }
            player.getInventory().addItem(serceDzialki);
            player.sendMessage(ChatColor.GREEN + "Otrzymałeś Serce Działki! Postaw je, aby utworzyć działkę.");
            return true;
        } else if (command.getName().equalsIgnoreCase("dustaw")) {
            if (!plotManager.hasPlot(player)) {
                player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
                return true;
            }
            Location location = player.getLocation();
            // Zapisuj pełną lokalizację, w tym yaw i pitch
            plotManager.updatePlotLocation(player, location);
            player.sendMessage(ChatColor.GREEN + "Przeniesiono działkę na aktualną pozycję i kierunek patrzenia!");
            return true;
        } else if (command.getName().equalsIgnoreCase("ddom")) {
            if (!plotManager.hasPlot(player)) {
                player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
                return true;
            }
            Location plotLocation = plotManager.getPlotLocation(player);
            if (plotLocation != null) {
                player.teleport(plotLocation);
                player.sendMessage(ChatColor.GREEN + "Przeniesiono do Twojej działki!");
            } else {
                player.sendMessage(ChatColor.RED + "Nie znaleziono pozycji Twojej działki!");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("dusun")) {
            if (!plotManager.hasPlot(player)) {
                player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
                return true;
            }
            Location location = player.getLocation();
            plotManager.removePlot(player, location);
            return true;
        }
        return false;
    }
}