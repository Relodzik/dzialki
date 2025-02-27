package pl.gitdzialki;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Klasa śledząca, który gracz aktywował dany dyspenser lub element redstone
 */
public class DispenserActivatorTracker implements Listener {

    private final Main plugin;

    // Mapa ostatnich aktywatorów (klucz lokalizacji -> UUID gracza)
    private final Map<String, UUID> lastActivators = new ConcurrentHashMap<>();

    // Czas przechowywania informacji o aktywacji (w milisekundach)
    private static final long ACTIVATION_TIMEOUT = 10000; // 10 sekund
    private final Map<String, Long> activationTimes = new ConcurrentHashMap<>();

    public DispenserActivatorTracker(Main plugin) {
        this.plugin = plugin;

        // Uruchom zadanie czyszczenia starych aktywacji co minutę
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldActivations, 1200L, 1200L);
    }

    /**
     * Nasłuchuje interakcji gracza z elementami redstone
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Sprawdź, czy to redstone lub dyspenser/dropper
        Material blockType = block.getType();
        if (isRedstoneComponent(blockType) || blockType == Material.DISPENSER || blockType == Material.DROPPER) {
            Player player = event.getPlayer();
            String locationKey = getLocationKey(block.getLocation());

            // Zapisz, kto ostatnio aktywował ten blok
            lastActivators.put(locationKey, player.getUniqueId());
            activationTimes.put(locationKey, System.currentTimeMillis());
        }
    }

    /**
     * Sprawdza, czy dany typ bloku jest elementem redstone
     */
    private boolean isRedstoneComponent(Material material) {
        switch (material) {
            case LEVER:
            case STONE_BUTTON:
            case OAK_BUTTON:
            case SPRUCE_BUTTON:
            case BIRCH_BUTTON:
            case JUNGLE_BUTTON:
            case ACACIA_BUTTON:
            case DARK_OAK_BUTTON:
            case CRIMSON_BUTTON:
            case WARPED_BUTTON:
            case MANGROVE_BUTTON:
            case BAMBOO_BUTTON:
            case CHERRY_BUTTON:
            case POLISHED_BLACKSTONE_BUTTON:
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
            case REDSTONE_BLOCK:
            case OBSERVER:
            case DAYLIGHT_DETECTOR:
            case REPEATER:
            case COMPARATOR:
            case REDSTONE_LAMP:
                return true;
            default:
                return false;
        }
    }

    /**
     * Tworzy unikalny klucz dla lokalizacji
     */
    private String getLocationKey(Location location) {
        return location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();
    }

    /**
     * Czyści stare aktywacje
     */
    private void cleanupOldActivations() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : activationTimes.entrySet()) {
            if (currentTime - entry.getValue() > ACTIVATION_TIMEOUT) {
                String key = entry.getKey();
                activationTimes.remove(key);
                lastActivators.remove(key);
            }
        }
    }

    /**
     * Sprawdza, czy dany gracz był ostatnim, który aktywował dyspenser
     * @param player Gracz do sprawdzenia
     * @param location Lokalizacja dyspensera
     * @return true jeśli gracz był ostatnim aktywatorem, false w przeciwnym razie
     */
    public boolean wasLastActivator(Player player, Location location) {
        String locationKey = getLocationKey(location);
        UUID lastActivator = lastActivators.get(locationKey);

        // Jeśli nie mamy informacji o ostatnim aktywatorze
        if (lastActivator == null) {
            return false;
        }

        // Sprawdź, czy aktywacja nie wygasła
        Long activationTime = activationTimes.get(locationKey);
        if (activationTime == null || System.currentTimeMillis() - activationTime > ACTIVATION_TIMEOUT) {
            return false;
        }

        // Sprawdź, czy gracz był ostatnim aktywatorem
        return lastActivator.equals(player.getUniqueId());
    }
}