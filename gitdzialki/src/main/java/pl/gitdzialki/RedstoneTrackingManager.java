package pl.gitdzialki;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Klasa odpowiedzialna za śledzenie przepływu sygnału redstone i ustalanie,
 * który gracz zainicjował sygnał prowadzący do dyspensera
 */
public class RedstoneTrackingManager implements Listener {
    private final Main plugin;
    private final ProtocolManager protocolManager;

    // Mapa przechowująca (świat:x:y:z -> UUID gracza) dla aktywacji elementów redstone
    private final Map<String, UUID> redstoneInitiators = new ConcurrentHashMap<>();

    // Mapa przechowująca (świat:x:y:z -> zbiór lokalizacji) dla śledzenia przepływu sygnału
    private final Map<String, Set<String>> redstoneFlowMap = new ConcurrentHashMap<>();

    // Czas wygasania danych śledzenia (w milisekundach)
    private static final long TRACKING_TIMEOUT = 30000; // 30 sekund
    private final Map<String, Long> trackingTimestamps = new ConcurrentHashMap<>();

    public RedstoneTrackingManager(Main plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        registerPacketListeners();

        // Uruchom zadanie czyszczenia starych danych co 5 minut
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldTracking, 6000L, 6000L);
    }

    /**
     * Rejestruje listenery pakietów dla ProtocolLib
     */
    private void registerPacketListeners() {
        // Nasłuchuj pakietów interakcji gracza z blokami
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ITEM) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                PacketContainer packet = event.getPacket();

                // Pobierz pozycję bloku, z którym gracz wchodzi w interakcję
                BlockPosition blockPos = packet.getBlockPositionModifier().read(0);

                // Sprawdź, czy to element redstone
                Block block = player.getWorld().getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                if (isRedstoneComponent(block.getType())) {
                    // Zapisz, że ten gracz aktywował element redstone w tej lokalizacji
                    String blockKey = getLocationKey(block.getLocation());
                    redstoneInitiators.put(blockKey, player.getUniqueId());
                    trackingTimestamps.put(blockKey, System.currentTimeMillis());
                }
            }
        });
    }

    /**
     * Listener dla standardowego API Bukkit do śledzenia zmian redstone
     */
    @EventHandler
    public void onRedstoneChange(BlockRedstoneEvent event) {
        // Śledź tylko gdy zmienia się z niskiego na wysoki poziom (aktywacja)
        if (event.getOldCurrent() == 0 && event.getNewCurrent() > 0) {
            Block changedBlock = event.getBlock();
            String blockKey = getLocationKey(changedBlock.getLocation());

            // Jeśli znamy inicjatora dla tego bloku, to aktualizujemy czas
            if (redstoneInitiators.containsKey(blockKey)) {
                trackingTimestamps.put(blockKey, System.currentTimeMillis());

                // Rekurencyjne śledzenie rozprzestrzeniania się sygnału redstone
                Bukkit.getScheduler().runTask(plugin, () -> trackRedstoneFlow(changedBlock, redstoneInitiators.get(blockKey)));
            }
        }
    }

    /**
     * Sprawdza, czy podany typ materiału jest komponentem redstone
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
            case REDSTONE_WIRE:
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
            case REPEATER:
            case COMPARATOR:
            case DISPENSER:
            case DROPPER:
            case OBSERVER:
            case DAYLIGHT_DETECTOR:
            case TARGET:
            case TRIPWIRE_HOOK:
            case TRAPPED_CHEST:
            case STONE_PRESSURE_PLATE:
            case OAK_PRESSURE_PLATE:
            case SPRUCE_PRESSURE_PLATE:
            case BIRCH_PRESSURE_PLATE:
            case JUNGLE_PRESSURE_PLATE:
            case ACACIA_PRESSURE_PLATE:
            case DARK_OAK_PRESSURE_PLATE:
            case CRIMSON_PRESSURE_PLATE:
            case WARPED_PRESSURE_PLATE:
            case MANGROVE_PRESSURE_PLATE:
            case BAMBOO_PRESSURE_PLATE:
            case CHERRY_PRESSURE_PLATE:
            case POLISHED_BLACKSTONE_PRESSURE_PLATE:
            case LIGHT_WEIGHTED_PRESSURE_PLATE:
            case HEAVY_WEIGHTED_PRESSURE_PLATE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Śledzi przepływ sygnału redstone z danego bloku
     */
    private void trackRedstoneFlow(Block sourceBlock, UUID initiatorUUID) {
        String sourceKey = getLocationKey(sourceBlock.getLocation());

        // Kierunki, w których może płynąć sygnał redstone
        BlockFace[] faces = {
                BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN
        };

        for (BlockFace face : faces) {
            Block targetBlock = sourceBlock.getRelative(face);

            // Jeśli blok docelowy może przewodzić redstone
            if (canConductRedstone(targetBlock.getType())) {
                String targetKey = getLocationKey(targetBlock.getLocation());

                // Dodaj informację o przepływie
                redstoneFlowMap.computeIfAbsent(sourceKey, k -> new HashSet<>()).add(targetKey);

                // Ustaw inicjatora dla bloku docelowego
                if (!redstoneInitiators.containsKey(targetKey)) {
                    redstoneInitiators.put(targetKey, initiatorUUID);
                    trackingTimestamps.put(targetKey, System.currentTimeMillis());
                }
            }
        }
    }

    /**
     * Sprawdza, czy podany typ materiału może przewodzić sygnał redstone
     */
    private boolean canConductRedstone(Material material) {
        switch (material) {
            case REDSTONE_WIRE:
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
            case REPEATER:
            case COMPARATOR:
            case DISPENSER:
            case DROPPER:
            case OBSERVER:
            case PISTON:
            case STICKY_PISTON:
            case REDSTONE_LAMP:
            case NOTE_BLOCK:
            case POWERED_RAIL:
            case ACTIVATOR_RAIL:
            case DETECTOR_RAIL:
            case IRON_DOOR:
            case IRON_TRAPDOOR:
            case LECTERN:
            case BELL:
            case HOPPER:
                return true;
            default:
                return material.toString().contains("DOOR") ||
                        material.toString().contains("TRAPDOOR") ||
                        material.toString().contains("GATE");
        }
    }

    /**
     * Tworzy unikalny klucz dla lokalizacji bloku
     */
    private String getLocationKey(Location location) {
        return location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();
    }

    /**
     * Czyści stare dane śledzenia
     */
    private void cleanupOldTracking() {
        long currentTime = System.currentTimeMillis();

        // Usuń stare wpisy czasowe
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, Long> entry : trackingTimestamps.entrySet()) {
            if (currentTime - entry.getValue() > TRACKING_TIMEOUT) {
                keysToRemove.add(entry.getKey());
            }
        }

        // Usuń odpowiednie wpisy z innych map
        for (String key : keysToRemove) {
            trackingTimestamps.remove(key);
            redstoneInitiators.remove(key);
            redstoneFlowMap.remove(key);
        }
    }

    /**
     * Znajduje gracza, który zainicjował sygnał redstone prowadzący do danego dyspensera
     * @param dispenserLocation Lokalizacja dyspensera
     * @return UUID gracza, który zainicjował sygnał lub null jeśli nie znaleziono
     */
    public UUID getRedstoneInitiator(Location dispenserLocation) {
        String dispenserKey = getLocationKey(dispenserLocation);

        // Sprawdź bezpośrednio, czy znamy inicjatora dla tego dyspensera
        UUID directInitiator = redstoneInitiators.get(dispenserKey);
        if (directInitiator != null) {
            return directInitiator;
        }

        // Jeśli nie, spróbuj znaleźć inicjatora, który mógł zainicjować sygnał
        // poprzez śledzenie przepływu sygnału redstone
        Set<String> visited = new HashSet<>();
        return findInitiatorRecursively(dispenserKey, visited);
    }

    /**
     * Rekurencyjnie szuka inicjatora sygnału redstone poprzez graf przepływu
     */
    private UUID findInitiatorRecursively(String blockKey, Set<String> visited) {
        if (visited.contains(blockKey)) {
            return null; // Unikaj cykli
        }

        visited.add(blockKey);

        // Sprawdź, czy znamy inicjatora dla tego bloku
        UUID initiator = redstoneInitiators.get(blockKey);
        if (initiator != null) {
            return initiator;
        }

        // Sprawdź wszystkie bloki, które mogły dostarczyć sygnał do tego bloku
        for (Map.Entry<String, Set<String>> entry : redstoneFlowMap.entrySet()) {
            if (entry.getValue().contains(blockKey)) {
                UUID foundInitiator = findInitiatorRecursively(entry.getKey(), visited);
                if (foundInitiator != null) {
                    return foundInitiator;
                }
            }
        }

        return null;
    }

    /**
     * Sprawdza, czy podany gracz jest inicjatorem sygnału redstone dla danego dyspensera
     */
    public boolean isPlayerRedstoneInitiator(Player player, Location dispenserLocation) {
        UUID initiator = getRedstoneInitiator(dispenserLocation);
        return initiator != null && initiator.equals(player.getUniqueId());
    }
}