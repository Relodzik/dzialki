package pl.gitdzialki;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Klasa odpowiedzialna za rozszerzoną ochronę działek
 */
public class EnhancedPlotProtectionListener implements Listener {

    private final Main plugin;
    private final PlotManager plotManager;

    // Typy bytów, które mogą niszczyć bloki
    private final Set<EntityType> destructiveEntities = new HashSet<>();

    public EnhancedPlotProtectionListener(Main plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;

        // Inicjalizacja listy bytów, które mogą niszczyć bloki
        destructiveEntities.add(EntityType.CREEPER);
        destructiveEntities.add(EntityType.WITHER);
        destructiveEntities.add(EntityType.WITHER_SKULL);
        destructiveEntities.add(EntityType.TNT);
        destructiveEntities.add(EntityType.TNT_MINECART);
        destructiveEntities.add(EntityType.FIREBALL);
        destructiveEntities.add(EntityType.SMALL_FIREBALL);
        destructiveEntities.add(EntityType.DRAGON_FIREBALL);
        destructiveEntities.add(EntityType.END_CRYSTAL);
        destructiveEntities.add(EntityType.GHAST);
        destructiveEntities.add(EntityType.ENDER_DRAGON);
    }

    /**
     * Blokuje eksplozje bytów na działkach
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity entity = event.getEntity();
        Location location = entity.getLocation();

        // Jeśli eksplozja jest na czyjejś działce
        if (plotManager.isPlot(location)) {
            UUID plotOwner = plotManager.getPlotOwner(location);

            // Usuń bloki działek z listy do zniszczenia
            Iterator<Block> blockIterator = event.blockList().iterator();
            while (blockIterator.hasNext()) {
                Block block = blockIterator.next();
                if (plotManager.isPlot(block.getLocation())) {
                    UUID blockPlotOwner = plotManager.getPlotOwner(block.getLocation());

                    // Jeśli blok należy do działki, usuń go z listy zniszczenia
                    if (blockPlotOwner != null) {
                        blockIterator.remove();
                    }
                }
            }

            // Opcjonalnie: całkowicie anuluj eksplozję na działce
            // event.setCancelled(true);
        }
    }

    /**
     * Blokuje zmiany bloków przez byty (np. enderman)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        Block block = event.getBlock();

        // Jeśli blok jest na działce
        if (plotManager.isPlot(block.getLocation())) {
            // Endermany nie mogą zabierać bloków z działek
            if (entity.getType() == EntityType.ENDERMAN) {
                event.setCancelled(true);
            }

            // Opadający piasek/żwir nie może zniszczyć bloków na działkach
            if (entity.getType() == EntityType.FALLING_BLOCK) {
                // Pozwól na lądowanie, ale nie niszczenie
                if (block.getType() != Material.AIR) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Blokuje interakcje bytów z blokami na działkach (np. deptanie przez moby)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(EntityInteractEvent event) {
        Block block = event.getBlock();

        // Jeśli blok jest na działce, a byt to nie gracz
        if (plotManager.isPlot(block.getLocation()) && !(event.getEntity() instanceof Player)) {
            // Ogólnie: blokuj deptanie upraw itp.
            if (block.getType() == Material.FARMLAND ||
                    block.getType() == Material.TURTLE_EGG) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Blokuje niszczenie obrazów, ramek, zbroi itp. przez byty
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity hanging = event.getEntity();
        Entity remover = event.getRemover();

        // Jeśli obrazek/ramka jest na działce
        if (plotManager.isPlot(hanging.getLocation())) {
            // Jeśli niszczący nie jest graczem lub gracz nie jest właścicielem
            if (!(remover instanceof Player)) {
                event.setCancelled(true);
            } else {
                Player player = (Player) remover;
                UUID plotOwner = plotManager.getPlotOwner(hanging.getLocation());

                if (!player.getUniqueId().equals(plotOwner)) {
                    player.sendMessage(ChatColor.RED + "Nie możesz niszczyć obiektów na cudzej działce!");
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Blokuje łowienie ryb na cudzej działce (można używać do ciągnięcia bytów)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        Location hookLoc = event.getHook().getLocation();

        // Sprawdź, czy haczyk jest na czyjejś działce
        if (plotManager.isPlot(hookLoc)) {
            UUID plotOwner = plotManager.getPlotOwner(hookLoc);

            // Jeśli gracz nie jest właścicielem działki
            if (!player.getUniqueId().equals(plotOwner)) {
                // Anuluj łowienie tylko, gdy coś jest złapane lub w stanie BITE (ugryzione)
                if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY ||
                        event.getState() == PlayerFishEvent.State.CAUGHT_FISH ||
                        event.getState() == PlayerFishEvent.State.BITE) {
                    player.sendMessage(ChatColor.RED + "Nie możesz łowić na cudzej działce!");
                    event.setCancelled(true);
                }
            }
        }
    }
}