package com.example.dzialki.commands;

import com.example.dzialki.Dzialki;
import com.example.dzialki.managers.PlotManager;
import com.example.dzialki.models.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DzialkaCommand implements CommandExecutor {
    private final Dzialki plugin;
    private final PlotManager plotManager;

    public DzialkaCommand(Dzialki plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda może być użyta tylko przez gracza!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "daj" -> handleGivePlotHeart(player);
            case "usun" -> handleRemovePlot(player, args);
            case "dom" -> handleTeleportHome(player);
            case "tp" -> handleTeleportToPlot(player, args);
            case "ustaw" -> handleSetTeleportLocation(player);
            case "zapros" -> handleInvitePlayer(player, args);
            case "dolacz" -> handleJoinPlot(player, args);
            case "wyrzuc" -> handleKickPlayer(player, args);
            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Komendy Działek ===");
        player.sendMessage(ChatColor.YELLOW + "/dzialka daj" + ChatColor.GRAY + " - Daje Serce Działki (admin)");
        player.sendMessage(ChatColor.YELLOW + "/dzialka usun <TAG>" + ChatColor.GRAY + " - Usuwa działkę (admin)");
        player.sendMessage(ChatColor.YELLOW + "/dzialka dom" + ChatColor.GRAY + " - Teleportuje do domu działki");
        player.sendMessage(ChatColor.YELLOW + "/dzialka tp <TAG>" + ChatColor.GRAY + " - Teleportuje do działki (admin)");
        player.sendMessage(ChatColor.YELLOW + "/dzialka ustaw" + ChatColor.GRAY + " - Ustawia punkt teleportacji działki");
        player.sendMessage(ChatColor.YELLOW + "/dzialka zapros <gracz>" + ChatColor.GRAY + " - Zaprasza gracza do działki");
        player.sendMessage(ChatColor.YELLOW + "/dzialka dolacz <TAG>" + ChatColor.GRAY + " - Dołącza do działki");
        player.sendMessage(ChatColor.YELLOW + "/dzialka wyrzuc <gracz>" + ChatColor.GRAY + " - Wyrzuca gracza z działki");
    }

    private void handleGivePlotHeart(Player player) {
        if (!player.hasPermission("dzialka.admin.daj")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień do użycia tej komendy!");
            return;
        }

        ItemStack plotHeart = plotManager.createPlotHeart();
        player.getInventory().addItem(plotHeart);
        player.sendMessage(ChatColor.GREEN + "Otrzymałeś Serce Działki!");
    }

    private void handleRemovePlot(Player player, String[] args) {
        if (!player.hasPermission("dzialka.admin.usun")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień do użycia tej komendy!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Użycie: /dzialka usun <TAG>");
            return;
        }

        String tag = args[1].toUpperCase();
        Plot plot = plotManager.getPlot(tag);

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Działka o tagu " + tag + " nie istnieje!");
            return;
        }

        // Remove the shulker box
        Location heartLocation = plot.getHeartLocation();
        if (heartLocation.getBlock().getType() == Material.RED_SHULKER_BOX) {
            heartLocation.getBlock().setType(Material.AIR);
        }

        // Remove the plot
        if (plotManager.removePlot(tag)) {
            player.sendMessage(ChatColor.GREEN + "Działka o tagu " + tag + " została usunięta!");
        } else {
            player.sendMessage(ChatColor.RED + "Nie udało się usunąć działki!");
        }
    }

    private void handleTeleportHome(Player player) {
        Plot plot = plotManager.getPlayerPlot(player.getUniqueId());

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
            return;
        }

        player.teleport(plot.getTeleportLocation());
        player.sendMessage(ChatColor.GREEN + "Teleportowano do domu działki!");
    }

    private void handleTeleportToPlot(Player player, String[] args) {
        if (!player.hasPermission("dzialka.admin.tp")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień do użycia tej komendy!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Użycie: /dzialka tp <TAG>");
            return;
        }

        String tag = args[1].toUpperCase();
        Plot plot = plotManager.getPlot(tag);

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Działka o tagu " + tag + " nie istnieje!");
            return;
        }

        player.teleport(plot.getTeleportLocation());
        player.sendMessage(ChatColor.GREEN + "Teleportowano do działki " + tag + "!");
    }

    private void handleSetTeleportLocation(Player player) {
        Plot plot = plotManager.getPlayerPlot(player.getUniqueId());

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
            return;
        }

        if (!plot.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Tylko właściciel działki może zmienić punkt teleportacji!");
            return;
        }

        Location playerLocation = player.getLocation();

        if (!plot.isInPlot(playerLocation)) {
            player.sendMessage(ChatColor.RED + "Musisz znajdować się na terenie swojej działki!");
            return;
        }

        plot.setTeleportLocation(playerLocation);
        player.sendMessage(ChatColor.GREEN + "Punkt teleportacji działki został zaktualizowany!");
    }

    private void handleInvitePlayer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Użycie: /dzialka zapros <gracz>");
            return;
        }

        Plot plot = plotManager.getPlayerPlot(player.getUniqueId());

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
            return;
        }

        if (!plot.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Tylko właściciel działki może zapraszać graczy!");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            player.sendMessage(ChatColor.RED + "Gracz " + args[1] + " nie jest online!");
            return;
        }

        if (plotManager.hasPlot(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Ten gracz już posiada działkę!");
            return;
        }

        if (plot.isMember(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Ten gracz już jest członkiem twojej działki!");
            return;
        }

        plot.invitePlayer(target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Zaprosiłeś gracza " + target.getName() + " do swojej działki!");
        target.sendMessage(ChatColor.GREEN + "Zostałeś zaproszony do działki " + plot.getTag() + " przez " + player.getName() + "!");
        target.sendMessage(ChatColor.GREEN + "Użyj /dzialka dolacz " + plot.getTag() + " aby dołączyć!");
    }

    private void handleJoinPlot(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Użycie: /dzialka dolacz <TAG>");
            return;
        }

        if (plotManager.hasPlot(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Już posiadasz działkę!");
            return;
        }

        String tag = args[1].toUpperCase();
        Plot plot = plotManager.getPlot(tag);

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Działka o tagu " + tag + " nie istnieje!");
            return;
        }

        if (!plot.isInvited(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Nie zostałeś zaproszony do tej działki!");
            return;
        }

        if (plotManager.joinPlot(player.getUniqueId(), tag)) {
            player.sendMessage(ChatColor.GREEN + "Dołączyłeś do działki " + tag + "!");

            // Notify the owner if online
            Player owner = Bukkit.getPlayer(plot.getOwner());
            if (owner != null) {
                owner.sendMessage(ChatColor.GREEN + player.getName() + " dołączył do twojej działki!");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Nie udało się dołączyć do działki!");
        }
    }

    private void handleKickPlayer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Użycie: /dzialka wyrzuc <gracz>");
            return;
        }

        Plot plot = plotManager.getPlayerPlot(player.getUniqueId());

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Nie posiadasz działki!");
            return;
        }

        if (!plot.isOwner(player.getUniqueId()) && !player.hasPermission("dzialka.admin.kick")) {
            player.sendMessage(ChatColor.RED + "Tylko właściciel działki może wyrzucać graczy!");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Gracz " + args[1] + " nie jest online!");
            return;
        }

        // Cannot kick the owner
        if (plot.isOwner(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Nie możesz wyrzucić właściciela działki!");
            return;
        }

        // Check if target is a member of the plot
        if (!plot.isMember(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Ten gracz nie jest członkiem twojej działki!");
            return;
        }

        // Remove player from plot
        plot.removeMember(target.getUniqueId());
        plotManager.removePlayerFromPlot(target.getUniqueId());

        player.sendMessage(ChatColor.GREEN + "Wyrzuciłeś gracza " + target.getName() + " z działki!");
        target.sendMessage(ChatColor.RED + "Zostałeś wyrzucony z działki " + plot.getTag() + "!");

        // Teleport player out of the plot if they are inside
        if (plot.isInPlot(target.getLocation())) {
            // Teleport to spawn or a safe location outside the plot
            Location safeLocation = plotManager.findSafeLocationOutsidePlot(plot);
            target.teleport(safeLocation);
            target.sendMessage(ChatColor.RED + "Zostałeś teleportowany poza teren działki!");
        }
    }
}