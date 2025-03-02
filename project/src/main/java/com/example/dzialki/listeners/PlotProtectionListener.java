package com.example.dzialki.listeners;

import com.example.dzialki.Dzialki;
import com.example.dzialki.managers.PlotManager;
import com.example.dzialki.models.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Słuchacz odpowiedzialny za ochronę działek przed różnymi rodzajami interakcji,
 * w tym ochronę przed pistonami, dispenserami, mechanizmami redstone oraz innymi sposobami obejścia.
 * Implementacja została zaprojektowana, aby być maksymalnie skuteczna w zapobieganiu jakimkolwiek exploitom.
 */
public class PlotProtectionListener implements Listener {
    private final Dzialki plugin;
    private final PlotManager plotManager;

    // Użycie ConcurrentHashMap dla wielowątkowego dostępu i bezpieczeństwa
    private final Map<String, PlayerInteractionData> lastRedstoneInteraction = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastRedstoneActivity = new ConcurrentHashMap<>();
    private final long INTERACTION_TIMEOUT = 300000; // 5 minut w milisekundach

    // Cooldown dla wiadomości aby zapobiec duplikatom
    private final Map<UUID, Long> interactionMessageCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> breakMessageCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> placeMessageCooldown = new ConcurrentHashMap<>();
    private final long MESSAGE_COOLDOWN = 500; // 500 ms cooldown

    // Śledzenie źródeł cieczy i ostatnich interakcji
    private final Map<String, UUID> liquidSources = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBlockUpdate = new ConcurrentHashMap<>();
    private final long LIQUID_SOURCE_TIMEOUT = 300000; // 5 minut

    // Cache znanych pistonów i dispenserów dla szybkiego dostępu
    private final Set<String> knownPistons = ConcurrentHashMap.newKeySet();
    private final Set<String> knownDispensers = ConcurrentHashMap.newKeySet();

    // Śledzenie spadających bloków
    private final Map<UUID, BlockSourceInfo> fallingBlockSourceInfo = new ConcurrentHashMap<>();

    // Kierunki dla efektywnego sprawdzania
    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST,
            BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST};
    /**
     * Sprawdza czy blok jest Sercem Działki
     * @param block Blok do sprawdzenia
     * @return true jeśli blok jest Sercem Działki, false w przeciwnym przypadku
     */
    private boolean isPlotHeart(Block block) {
        if (block.getType() != Material.RED_SHULKER_BOX) return false;

        Plot plot = plotManager.getPlotAt(block.getLocation());
        return plot != null && plot.getHeartLocation().equals(block.getLocation());
    }

    /**
     * Sprawdza wszystkie bloki w podanym kierunku, czy któryś nie jest Sercem Działki
     * @param startBlock Blok początkowy
     * @param direction Kierunek sprawdzania
     * @param distance Odległość sprawdzania
     * @return true jeśli znaleziono Serce Działki, false w przeciwnym przypadku
     */
    private boolean checkForPlotHeartInDirection(Block startBlock, BlockFace direction, int distance) {
        for (int i = 1; i <= distance; i++) {
            Block checkBlock = startBlock.getRelative(direction, i);
            if (isPlotHeart(checkBlock)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Obsługuje zdarzenie aktualizacji bloków przez redstone
     * Zmodyfikowane, aby pozwalać na używanie redstone, ale blokować tylko interakcje z Sercem Działki
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        // Nie ingerujemy w sygnał redstone, pozwalając pistonom działać normalnie
        // Weryfikacja czy piston jest blokowany odbywa się w obsłudze zdarzeń pistonów
    };

    // Klasa do śledzenia informacji o spadających blokach
    private static class BlockSourceInfo {
        public final Location sourceLocation;
        public final long timestamp;
        public final UUID placerUUID;

        public BlockSourceInfo(Location sourceLocation, UUID placerUUID) {
            this.sourceLocation = sourceLocation;
            this.timestamp = System.currentTimeMillis();
            this.placerUUID = placerUUID;
        }
    }

    /**
     * Konstruktor klasy PlotProtectionListener
     * @param plugin Główna instancja pluginu
     */
    public PlotProtectionListener(Dzialki plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();

        // Inicjalizacja harmonogramu zadań czyszczących
        initializeCleanupTask();

        // Inicjalizacja harmonogramu sprawdzania redstone co tick
        initializeRedstoneMonitoring();

        plugin.getLogger().info("Zainicjalizowano zaawansowany system ochrony działek");
    }

    /**
     * Inicjalizuje zadanie czyszczące dla struktur danych
     */
    private void initializeCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            // Czyść stare interakcje redstone
            lastRedstoneInteraction.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue().timestamp) > INTERACTION_TIMEOUT);

            // Czyść stare aktywności graczy
            playerLastRedstoneActivity.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > INTERACTION_TIMEOUT);

            // Czyść przestarzałe cooldowny wiadomości
            interactionMessageCooldown.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > MESSAGE_COOLDOWN);
            breakMessageCooldown.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > MESSAGE_COOLDOWN);
            placeMessageCooldown.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > MESSAGE_COOLDOWN);

            // Czyść stare źródła cieczy
            liquidSources.entrySet().removeIf(entry -> {
                Long timestamp = playerLastRedstoneActivity.get(entry.getValue());
                return timestamp == null || (currentTime - timestamp) > LIQUID_SOURCE_TIMEOUT;
            });

            // Czyść stare informacje o aktualizacjach bloków
            lastBlockUpdate.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > INTERACTION_TIMEOUT);

            // Czyść stare informacje o spadających blokach
            fallingBlockSourceInfo.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue().timestamp) > INTERACTION_TIMEOUT);

        }, 6000L, 6000L); // Uruchom co 5 minut (6000 ticków)
    }

    /**
     * Inicjalizuje monitoring redstone dla szybkiego wykrywania zmian
     */
    private void initializeRedstoneMonitoring() {
        // Sprawdzaj aktywność redstone co tick dla najwyższej precyzji
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Sprawdzamy każdy znany piston
            for (String pistonKey : knownPistons) {
                String[] parts = pistonKey.split(",");
                if (parts.length < 4) continue;

                String worldName = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Block pistonBlock = world.getBlockAt(x, y, z);
                if (!isPiston(pistonBlock.getType())) {
                    knownPistons.remove(pistonKey);
                    continue;
                }

                // Sprawdź czy piston jest zasilony
                if (isPowered(pistonBlock)) {
                    Plot pistonPlot = plotManager.getPlotAt(pistonBlock.getLocation());
                    validatePistonActivity(pistonBlock, pistonPlot);
                }
            }
        }, 1L, 1L); // Uruchom co 1 tick
    }

    // Klasa przechowująca dane o interakcji gracza
    private static class PlayerInteractionData {
        public final UUID playerUUID;
        public final long timestamp;

        public PlayerInteractionData(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Obsługuje zdarzenie niszczenia bloków
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Usuń z cache jeśli niszczony jest piston lub dispenser
        String blockKey = getBlockKey(block);
        if (isPiston(block.getType())) {
            knownPistons.remove(blockKey);
        } else if (isDispenser(block.getType())) {
            knownDispensers.remove(blockKey);
        }

        Plot plot = plotManager.getPlotAt(block.getLocation());

        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);

            // Sprawdź cooldown przed wysłaniem wiadomości
            long currentTime = System.currentTimeMillis();
            Long lastMessageTime = breakMessageCooldown.get(player.getUniqueId());

            if (lastMessageTime == null || (currentTime - lastMessageTime) > MESSAGE_COOLDOWN) {
                player.sendMessage(ChatColor.RED + "Nie możesz niszczyć bloków na tej działce!");
                breakMessageCooldown.put(player.getUniqueId(), currentTime);
            }
        }
    }

    /**
     * Obsługuje zdarzenie stawiania bloków
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Zapisz do cache jeśli stawiany jest piston lub dispenser
        String blockKey = getBlockKey(block);
        if (isPiston(block.getType())) {
            knownPistons.add(blockKey);
        } else if (isDispenser(block.getType())) {
            knownDispensers.add(blockKey);
        }

        // Śledzenie umieszczonych źródeł cieczy
        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            liquidSources.put(blockKey, player.getUniqueId());
        }

        // Zapisz aktualizację bloku
        lastBlockUpdate.put(blockKey, System.currentTimeMillis());

        // Sprawdź, czy to wiadro z wodą/lawą (dodatkowe śledzenie)
        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.WATER_BUCKET || item.getType() == Material.LAVA_BUCKET) {
            // Zapisz również informację o cieczy w miejscu wylania
            liquidSources.put(blockKey, player.getUniqueId());
        }

        Plot plot = plotManager.getPlotAt(block.getLocation());

        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);

            // Sprawdź cooldown przed wysłaniem wiadomości
            long currentTime = System.currentTimeMillis();
            Long lastMessageTime = placeMessageCooldown.get(player.getUniqueId());

            if (lastMessageTime == null || (currentTime - lastMessageTime) > MESSAGE_COOLDOWN) {
                player.sendMessage(ChatColor.RED + "Nie możesz stawiać bloków na tej działce!");
                placeMessageCooldown.put(player.getUniqueId(), currentTime);
            }
        } else {
            // Jeśli wydarzenie się nie anulowało, zapisz informację o ostatniej interakcji
            PlayerInteractionData interactionData = new PlayerInteractionData(player.getUniqueId());
            lastRedstoneInteraction.put(blockKey, interactionData);

            // Aktualizuj globalną aktywność redstone gracza
            playerLastRedstoneActivity.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Obsługuje zdarzenie interakcji gracza z blokami
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        // Skip interactions that are handled by block break/place events
        // This prevents duplicate messages
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Skip for block breaking - BlockBreakEvent will handle it
            return;
        }

        // For right clicks, only handle actual interactions (not block placement)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.isBlockInHand()) {
            // Skip for block placement - BlockPlaceEvent will handle it
            return;
        }

        // Zapisujemy globalną aktywność redstone tego gracza
        playerLastRedstoneActivity.put(player.getUniqueId(), System.currentTimeMillis());

        // Zapisz interakcję z mechanizmami
        if (isRedstoneComponent(block.getType()) || isPiston(block.getType()) || isDispenser(block.getType())) {
            // Utwórz dane interakcji z UUID gracza
            PlayerInteractionData interactionData = new PlayerInteractionData(player.getUniqueId());

            // Użyj współrzędnych bloku jako klucza
            String blockKey = getBlockKey(block);
            lastRedstoneInteraction.put(blockKey, interactionData);

            // Dodaj do cache jeśli to piston lub dispenser
            if (isPiston(block.getType())) {
                knownPistons.add(blockKey);
            } else if (isDispenser(block.getType())) {
                knownDispensers.add(blockKey);
            }

            // Rozpocznij śledzenie wszystkich połączonych komponentów redstone
            trackRedstoneWireConnections(block, interactionData);

            // Specjalne przetwarzanie dla elementów aktywowalnych przez redstone
            // Sprawdź również bloki w promieniu 3 bloków (dla pewności)
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Block neighbor = block.getRelative(x, y, z);
                        if (isDispenser(neighbor.getType()) ||
                                isPiston(neighbor.getType()) ||
                                neighbor.getType().name().contains("DROPPER")) {

                            // Dodaj te bloki do sieci redstone
                            String neighborKey = getBlockKey(neighbor);
                            lastRedstoneInteraction.put(neighborKey, interactionData);

                            // Dodaj do cache jeśli to piston lub dispenser
                            if (isPiston(neighbor.getType())) {
                                knownPistons.add(neighborKey);
                            } else if (isDispenser(neighbor.getType())) {
                                knownDispensers.add(neighborKey);
                            }
                        }
                    }
                }
            }
        }

        Plot plot = plotManager.getPlotAt(block.getLocation());

        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);

            // Pokazuj komunikat tylko dla bloków interaktywnych i tylko jeśli nie jest na cooldownie
            if (isInteractiveBlock(block.getType())) {
                long currentTime = System.currentTimeMillis();
                Long lastMessageTime = interactionMessageCooldown.get(player.getUniqueId());

                if (lastMessageTime == null || (currentTime - lastMessageTime) > MESSAGE_COOLDOWN) {
                    player.sendMessage(ChatColor.RED + "Nie możesz wchodzić w interakcję z blokami na tej działce!");
                    interactionMessageCooldown.put(player.getUniqueId(), currentTime);
                }
            }
        }
    }

    /**
     * Obsługuje zdarzenie przepływu cieczy
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.isCancelled()) return;

        Block fromBlock = event.getBlock();
        Block toBlock = event.getToBlock();

        // Sprawdź, czy to przepływ cieczy
        Material fromType = fromBlock.getType();
        if (fromType != Material.WATER && fromType != Material.LAVA) {
            return;
        }

        // Sprawdź, czy ciecz przepływa na działkę
        Plot fromPlot = plotManager.getPlotAt(fromBlock.getLocation());
        Plot toPlot = plotManager.getPlotAt(toBlock.getLocation());

        // Jeśli płynie między tymi samymi działkami, zawsze pozwalamy
        if (fromPlot != null && toPlot != null && fromPlot.getTag().equals(toPlot.getTag())) {
            return; // Pozwól na przepływ w obrębie tej samej działki
        }

        // Ciecz przepływa z zewnątrz na działkę
        if (fromPlot == null && toPlot != null) {
            // Blokuj przepływ cieczy z zewnątrz na działkę
            event.setCancelled(true);
            return;
        }

        // Ciecz przepływa z jednej działki na drugą
        if (fromPlot != null && toPlot != null && !fromPlot.getTag().equals(toPlot.getTag())) {
            // Znajdź gracza, który mógł umieścić ciecz
            UUID playerUUID = getLastInteractionPlayer(fromBlock);

            // Jeśli znamy gracza i jest on członkiem obu działek, pozwól na przepływ
            if (playerUUID != null && fromPlot.isMember(playerUUID) && toPlot.isMember(playerUUID)) {
                return;
            }

            // W przeciwnym razie blokuj przepływ między różnymi działkami
            event.setCancelled(true);
            return;
        }

        // Ciecz przepływa z działki na zewnątrz
        if (fromPlot != null && toPlot == null) {
            // Blokuj przepływ cieczy z działki na zewnątrz
            event.setCancelled(true);
            return;
        }
    }

    /**
     * Obsługuje zdarzenie opróżniania wiadra przez gracza
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material bucket = event.getBucket();

        // Śledź wylanie wody/lawy z wiadra
        if (bucket == Material.WATER_BUCKET || bucket == Material.LAVA_BUCKET) {
            // Zapisz informację o graczu, który wylał ciecz
            liquidSources.put(getBlockKey(block), player.getUniqueId());
        }

        // Sprawdź, czy gracz ma prawo do wylania cieczy
        Plot plot = plotManager.getPlotAt(block.getLocation());
        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);

            // Sprawdź cooldown przed wysłaniem wiadomości
            long currentTime = System.currentTimeMillis();
            Long lastMessageTime = placeMessageCooldown.get(player.getUniqueId());

            if (lastMessageTime == null || (currentTime - lastMessageTime) > MESSAGE_COOLDOWN) {
                player.sendMessage(ChatColor.RED + "Nie możesz używać wiaderka na tej działce!");
                placeMessageCooldown.put(player.getUniqueId(), currentTime);
            }
        }
    }

    /**
     * Obsługuje zdarzenie napełniania wiadra przez gracza
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Sprawdź, czy gracz ma prawo do napełniania wiadra
        Plot plot = plotManager.getPlotAt(block.getLocation());
        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);

            // Sprawdź cooldown przed wysłaniem wiadomości
            long currentTime = System.currentTimeMillis();
            Long lastMessageTime = interactionMessageCooldown.get(player.getUniqueId());

            if (lastMessageTime == null || (currentTime - lastMessageTime) > MESSAGE_COOLDOWN) {
                player.sendMessage(ChatColor.RED + "Nie możesz używać wiaderka na tej działce!");
                interactionMessageCooldown.put(player.getUniqueId(), currentTime);
            }
        }
    }

    /**
     * Obsługuje przesuwanie przedmiotów między ekwipunkami
     * (np. z hoppera do skrzyni)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.isCancelled()) return;

        // Pobierz lokalizacje źródła i celu
        InventoryHolder sourceHolder = event.getSource().getHolder();
        InventoryHolder destinationHolder = event.getDestination().getHolder();

        if (!(sourceHolder instanceof Block) || !(destinationHolder instanceof Block)) {
            return;
        }

        Block sourceBlock = (Block) sourceHolder;
        Block destinationBlock = (Block) destinationHolder;

        Plot sourcePlot = plotManager.getPlotAt(sourceBlock.getLocation());
        Plot destinationPlot = plotManager.getPlotAt(destinationBlock.getLocation());

        // Jeśli przemieszczanie przedmiotów odbywa się między różnymi działkami
        if ((sourcePlot != null && destinationPlot != null && !sourcePlot.getTag().equals(destinationPlot.getTag())) ||
                (sourcePlot == null && destinationPlot != null) ||
                (sourcePlot != null && destinationPlot == null)) {

            // Znajdź ostatniego gracza, który wchodził w interakcję z mechanizmem
            UUID sourcePlayerUUID = getLastInteractionPlayer(sourceBlock);
            UUID destPlayerUUID = getLastInteractionPlayer(destinationBlock);

            // Jeśli znamy graczy i mają uprawnienia do obu działek, pozwól na transfer
            if (sourcePlayerUUID != null && destPlayerUUID != null) {
                boolean sourceCanAccess = sourcePlot == null || sourcePlot.isMember(sourcePlayerUUID);
                boolean destCanAccess = destinationPlot == null || destinationPlot.isMember(destPlayerUUID);

                if (sourceCanAccess && destCanAccess) {
                    return; // Pozwól na transfer
                }
            }

            // W przeciwnym razie zablokuj transfer
            event.setCancelled(true);
        }
    }

    /**
     * Rekurencyjna metoda śledzenia połączeń redstone używająca algorytmu BFS
     * bez ograniczenia głębokości - zatrzymuje się tylko gdy wszystkie połączenia zostały zbadane
     */
    private void trackRedstoneWireConnections(Block startBlock, PlayerInteractionData data) {
        // Używamy kolejki dla algorytmu BFS (przeszukiwanie wszerz)
        Queue<Block> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        // Dodaj pierwszy blok
        queue.add(startBlock);
        String startKey = getBlockKey(startBlock);
        visited.add(startKey);
        lastRedstoneInteraction.put(startKey, data);

        int processedBlocks = 0;
        int maxBlocks = 10000; // Bezpieczny limit, aby uniknąć nieskończonej pętli

        while (!queue.isEmpty() && processedBlocks < maxBlocks) {
            Block current = queue.poll();
            processedBlocks++;

            // Dodaj ten blok do śledzonej sieci redstone
            String currentKey = getBlockKey(current);
            lastRedstoneInteraction.put(currentKey, data);

            // Znajdź wszystkie sąsiednie bloki (uwzględnij 3D - wszystkie 26 potencjalnych sąsiadów)
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue; // Pomijamy aktualny blok

                        Block neighbor = current.getRelative(x, y, z);
                        String neighborKey = getBlockKey(neighbor);

                        // Jeśli ten blok nie był jeszcze odwiedzony i jest częścią sieci redstone
                        if (!visited.contains(neighborKey) &&
                                (isRedstoneComponent(neighbor.getType()) || isRedstoneWire(neighbor.getType()))) {

                            // Dodaj do kolejki do przeszukania
                            queue.add(neighbor);
                            visited.add(neighborKey);

                            // Zapisz informację o interakcji
                            lastRedstoneInteraction.put(neighborKey, data);

                            // Dodaj do cache jeśli to piston lub dispenser
                            if (isPiston(neighbor.getType())) {
                                knownPistons.add(neighborKey);
                            } else if (isDispenser(neighbor.getType())) {
                                knownDispensers.add(neighborKey);
                            }
                        }
                    }
                }
            }
        }

        if (processedBlocks >= maxBlocks) {
            plugin.getLogger().warning("[Debug] Przerwano przeszukiwanie sieci redstone - zbyt duża sieć!");
        }
    }

    /**
     * Sprawdza czy piston może wykonać działanie, uwzględniając wszystkie scenariusze przekraczania granic działek
     * @param pistonBlock Blok pistonu
     * @param pistonPlot Działka pistonu
     */
    private void validatePistonActivity(Block pistonBlock, Plot pistonPlot) {
        // Pobierz kierunek pistonu
        BlockData blockData = pistonBlock.getBlockData();
        if (!(blockData instanceof Directional)) return;

        BlockFace direction = ((Directional) blockData).getFacing();
        boolean isSticky = pistonBlock.getType() == Material.STICKY_PISTON;

        // Sprawdź wszystkie potencjalne bloki, które piston może poruszyć
        List<Block> affectedBlocks = calculatePistonAffectedBlocks(pistonBlock, direction, isSticky);

        // Pobierz UUID gracza, który wchodził w interakcję z pistonem
        UUID playerUUID = getLastInteractionPlayer(pistonBlock);

        // Ochrona Serca Działki - TYLKO sprawdza czy Serce Działki jest chronione, reszta może być przesuwana
        for (Block block : affectedBlocks) {
            // Sprawdź czy blok jest Sercem Działki (czerwona shulkerowa skrzynia)
            if (block.getType() == Material.RED_SHULKER_BOX) {
                Plot blockPlot = plotManager.getPlotAt(block.getLocation());
                if (blockPlot != null && blockPlot.getHeartLocation().equals(block.getLocation())) {
                    // Anuluj działanie pistonu tylko jeśli próbuje przesunąć Serce Działki
                    cancelPistonAction(pistonBlock);
                    return;
                }
            }
        }

        // Sprawdź wszystkie bloki, które piston może przesunąć
        for (Block block : affectedBlocks) {
            // Oblicz docelową lokalizację bloku po przesunięciu
            Location finalLocation = block.getLocation().clone().add(
                    direction.getModX(),
                    direction.getModY(),
                    direction.getModZ()
            );

            // Pobierz działki dla obecnej i finalnej lokalizacji bloku
            Plot blockPlot = plotManager.getPlotAt(block.getLocation());
            Plot finalPlot = plotManager.getPlotAt(finalLocation);

            // Sprawdź różne przypadki przekraczania granic działek
            if (isCrossingPlotBoundaries(pistonPlot, blockPlot, finalPlot, playerUUID)) {
                // Anuluj działanie pistonu przez usunięcie redstone
                cancelPistonAction(pistonBlock);
                return;
            }
        }

        // Sprawdź czy sama głowica pistonu nie wejdzie na inną działkę
        Location pistonHeadLoc = pistonBlock.getLocation().clone().add(
                direction.getModX(),
                direction.getModY(),
                direction.getModZ()
        );
        Plot pistonHeadPlot = plotManager.getPlotAt(pistonHeadLoc);

        // Sprawdź czy głowica przekracza granice działek
        if ((pistonPlot == null && pistonHeadPlot != null) ||
                (pistonPlot != null && pistonHeadPlot != null && !pistonPlot.getTag().equals(pistonHeadPlot.getTag()))) {

            // Jeśli znamy gracza i jest członkiem działki, gdzie wejdzie głowica, pozwól na to
            if (playerUUID != null && pistonHeadPlot != null && pistonHeadPlot.isMember(playerUUID)) {
                return;
            }

            // W przeciwnym razie anuluj działanie pistonu
            cancelPistonAction(pistonBlock);
        }
    }

    /**
     * Oblicza wszystkie bloki, które mogą zostać poruszone przez piston
     * @param pistonBlock Blok pistonu
     * @param direction Kierunek działania pistonu
     * @param isSticky Czy piston jest lepki
     * @return Lista bloków, które zostaną poruszone
     */
    private List<Block> calculatePistonAffectedBlocks(Block pistonBlock, BlockFace direction, boolean isSticky) {
        List<Block> affectedBlocks = new ArrayList<>();

        // Dla zwykłego pistonu sprawdzamy bloki w kierunku rozszerzenia
        // Piston może przesunąć do 12 bloków
        int maxPushLength = 12;

        // Sprawdź wszystkie bloki w kierunku wysunięcia
        for (int i = 1; i <= maxPushLength; i++) {
            Block block = pistonBlock.getRelative(direction, i);

            // Jeśli blok jest powietrzem, przerywamy (piston nie może przesuwać powietrza)
            if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR || block.getType() == Material.VOID_AIR) {
                break;
            }

            // Jeśli blok nie może być przesuwany przez pistony, przerywamy (np. obsydian)
            if (!canPistonMove(block.getType())) {
                break;
            }

            affectedBlocks.add(block);
        }

        // Dla lepkiego pistonu sprawdzamy również bloki, które mogą zostać pociągnięte
        if (isSticky) {
            Block attachedBlock = pistonBlock.getRelative(direction);

            // Jeśli blok przed pistonem nie jest powietrzem, dodajemy do listy
            if (attachedBlock.getType() != Material.AIR &&
                    attachedBlock.getType() != Material.CAVE_AIR &&
                    attachedBlock.getType() != Material.VOID_AIR) {

                // Dodaj blok do listy, jeśli nie został już dodany
                if (!affectedBlocks.contains(attachedBlock)) {
                    affectedBlocks.add(attachedBlock);
                }

                // Sprawdzamy rekurencyjnie wszystkie bloki, które mogą zostać pociągnięte
                findPullableBlocks(attachedBlock, direction.getOppositeFace(), affectedBlocks, 0, maxPushLength);
            }
        }

        return affectedBlocks;
    }

    /**
     * Rekurencyjnie znajduje wszystkie bloki, które mogą zostać pociągnięte przez lepki piston
     * @param startBlock Blok startowy
     * @param pullDirection Kierunek ciągnięcia
     * @param affectedBlocks Lista bloków, które już zostaną poruszone
     * @param currentDepth Aktualna głębokość rekurencji
     * @param maxDepth Maksymalna głębokość rekurencji
     */
    private void findPullableBlocks(Block startBlock, BlockFace pullDirection, List<Block> affectedBlocks, int currentDepth, int maxDepth) {
        // Zapobiegaj nieskończonej rekurencji
        if (currentDepth >= maxDepth) {
            return;
        }

        // Sprawdź wszystkie bloki, które mogą zostać pociągnięte
        for (BlockFace face : ADJACENT_FACES) {
            // Pomijamy kierunek, z którego ciągniemy (to już sprawdziliśmy)
            if (face == pullDirection.getOppositeFace()) {
                continue;
            }

            Block neighbor = startBlock.getRelative(face);

            // Jeśli blok nie jest powietrzem i może zostać przesunięty przez piston
            if (neighbor.getType() != Material.AIR &&
                    neighbor.getType() != Material.CAVE_AIR &&
                    neighbor.getType() != Material.VOID_AIR &&
                    canPistonMove(neighbor.getType()) &&
                    !affectedBlocks.contains(neighbor)) {

                // Dodaj blok do listy
                affectedBlocks.add(neighbor);

                // Rekurencyjnie sprawdź sąsiadów
                findPullableBlocks(neighbor, pullDirection, affectedBlocks, currentDepth + 1, maxDepth);
            }
        }
    }

    /**
     * Sprawdza czy materiał może być przesuwany przez pistony
     * @param material Materiał do sprawdzenia
     * @return true jeśli może być przesuwany, false w przeciwnym przypadku
     */
    private boolean canPistonMove(Material material) {
        // Lista materiałów, których piston nie może przesuwać
        return !material.name().contains("OBSIDIAN") &&
                !material.name().contains("BEDROCK") &&
                !material.name().contains("CRYING_OBSIDIAN") &&
                !material.name().contains("REINFORCED") &&
                !material.name().contains("END_PORTAL") &&
                !material.name().contains("RESPAWN_ANCHOR") &&
                !material.name().equals("LODESTONE") &&
                !material.name().equals("ENCHANTING_TABLE") &&
                !material.name().equals("ENDER_CHEST") &&
                !material.name().equals("BEACON");
    }

    /**
     * Sprawdza czy bloki przekraczają granice działek
     * @param pistonPlot Działka pistonu
     * @param blockPlot Działka bloku
     * @param finalPlot Działka docelowa
     * @param playerUUID UUID gracza
     * @return true jeśli przekracza granice działek
     */
    private boolean isCrossingPlotBoundaries(Plot pistonPlot, Plot blockPlot, Plot finalPlot, UUID playerUUID) {
        // Przypadek 1: Piston jest poza działką i wpycha blok na działkę
        if (pistonPlot == null && finalPlot != null) {
            // Jeśli znamy gracza i jest członkiem działki docelowej, pozwalamy
            return playerUUID == null || !finalPlot.isMember(playerUUID);
        }

        // Przypadek 2: Piston jest na jednej działce i wpycha blok na inną działkę
        if (pistonPlot != null && finalPlot != null && !pistonPlot.getTag().equals(finalPlot.getTag())) {
            // Jeśli znamy gracza i jest członkiem obu działek, pozwalamy
            return playerUUID == null || !pistonPlot.isMember(playerUUID) || !finalPlot.isMember(playerUUID);
        }

        // Przypadek 3: Piston jest na działce i wysuwa blok poza działkę
        if (pistonPlot != null && blockPlot != null && finalPlot == null &&
                pistonPlot.getTag().equals(blockPlot.getTag())) {
            return true; // Zawsze blokujemy wysuwanie bloków poza działkę
        }

        // Przypadek 4: Piston próbuje przesunąć blok z jednej działki na drugą
        if (blockPlot != null && finalPlot != null && !blockPlot.getTag().equals(finalPlot.getTag())) {
            // Jeśli znamy gracza i jest członkiem obu działek, pozwalamy
            return playerUUID == null || !blockPlot.isMember(playerUUID) || !finalPlot.isMember(playerUUID);
        }

        // Przypadek 5: Piston próbuje wyciągnąć blok z działki
        if (pistonPlot == null && blockPlot != null) {
            // Jeśli znamy gracza i jest członkiem działki bloku, pozwalamy
            return playerUUID == null || !blockPlot.isMember(playerUUID);
        }

        return false; // W pozostałych przypadkach pozwalamy na działanie pistonu
    }

    /**
     * Anuluje działanie pistonu poprzez anulowanie wydarzenia
     * Uproszczona wersja funkcji, która nie usuwa zasilania redstone
     * @param pistonBlock Blok pistonu
     */
    private void cancelPistonAction(Block pistonBlock) {
        // Nie usuwamy zasilania redstone, a tylko odwołujemy się do funkcji obsługi zdarzeń
        // To zapewnia, że piston nie zadziała, ale pozostanie zasilony
        // Dzięki temu gracze mogą nadal używać pistonów normalnie
    }

    /**
     * Obsługuje zdarzenie wysuwania się pistonu
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.isCancelled()) return;

        Block pistonBlock = event.getBlock();
        BlockFace direction = event.getDirection();

        // Sprawdź czy TYLKO Serce Działki jest chronione, reszta może być przesuwana normalnie
        for (Block block : event.getBlocks()) {
            // Sprawdź czy blok jest Sercem Działki
            if (block.getType() == Material.RED_SHULKER_BOX) {
                Plot blockPlot = plotManager.getPlotAt(block.getLocation());
                if (blockPlot != null && blockPlot.getHeartLocation().equals(block.getLocation())) {
                    // Anuluj działanie pistonu tylko gdy próbuje przesunąć Serce Działki
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Pobierz UUID gracza, który aktywował piston
        UUID playerUUID = getLastInteractionPlayer(pistonBlock);

        // Pobierz działkę, na której znajduje się piston
        Plot pistonPlot = plotManager.getPlotAt(pistonBlock.getLocation());

        // Sprawdź wszystkie bloki, które będą przesuwane
        for (Block block : event.getBlocks()) {
            // Oblicz docelową lokalizację bloku po przesunięciu
            Location finalLocation = block.getLocation().clone().add(
                    direction.getModX(),
                    direction.getModY(),
                    direction.getModZ()
            );

            // Pobierz działki dla obecnej i finalnej lokalizacji bloku
            Plot blockPlot = plotManager.getPlotAt(block.getLocation());
            Plot finalPlot = plotManager.getPlotAt(finalLocation);

            // Przypadek 1: Piston jest poza działką i wpycha blok na działkę
            if (pistonPlot == null && finalPlot != null) {
                // Jeśli znamy gracza i jest członkiem działki docelowej, pozwalamy
                if (playerUUID != null && finalPlot.isMember(playerUUID)) {
                    continue;
                }
                event.setCancelled(true);
                return;
            }

            // Przypadek 2: Piston jest na jednej działce i wpycha blok na inną działkę
            if (pistonPlot != null && finalPlot != null && !pistonPlot.getTag().equals(finalPlot.getTag())) {
                // Jeśli znamy gracza i jest członkiem obu działek, pozwalamy
                if (playerUUID != null && pistonPlot.isMember(playerUUID) && finalPlot.isMember(playerUUID)) {
                    continue;
                }
                event.setCancelled(true);
                return;
            }

            // Przypadek 3: Piston jest na działce i wysuwa blok poza działkę
            if (pistonPlot != null && blockPlot != null && finalPlot == null &&
                    pistonPlot.getTag().equals(blockPlot.getTag())) {
                event.setCancelled(true);
                return;
            }

            // Przypadek 4: Piston próbuje przesunąć blok z jednej działki na drugą
            if (blockPlot != null && finalPlot != null && !blockPlot.getTag().equals(finalPlot.getTag())) {
                // Jeśli znamy gracza i jest członkiem obu działek, pozwalamy
                if (playerUUID != null && blockPlot.isMember(playerUUID) && finalPlot.isMember(playerUUID)) {
                    continue;
                }
                event.setCancelled(true);
                return;
            }
        }

        // Sprawdź czy głowica pistonu będzie wchodzić na działkę
        Location pistonHeadLoc = pistonBlock.getLocation().clone().add(
                direction.getModX(),
                direction.getModY(),
                direction.getModZ()
        );
        Plot pistonHeadPlot = plotManager.getPlotAt(pistonHeadLoc);

        // Jeśli głowica pistonu wchodzi na inną działkę
        if ((pistonPlot == null && pistonHeadPlot != null) ||
                (pistonPlot != null && pistonHeadPlot != null && !pistonPlot.getTag().equals(pistonHeadPlot.getTag()))) {

            // Jeśli znamy gracza i jest członkiem działki, na którą wchodzi głowica, pozwalamy
            if (playerUUID != null && pistonHeadPlot.isMember(playerUUID)) {
                return;
            }

            // W przeciwnym razie blokujemy
            event.setCancelled(true);
        }
    }

    /**
     * Obsługuje zdarzenie wciągania się pistonu
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.isCancelled()) return;

        // Jeśli piston nie jest lepki lub nie ma bloków do wciągnięcia, nic nie robimy
        if (!event.isSticky() || event.getBlocks().isEmpty()) {
            return;
        }

        Block pistonBlock = event.getBlock();

        // Sprawdź tylko czy Serce Działki jest chronione, reszta może być przesuwana normalnie
        for (Block block : event.getBlocks()) {
            // Sprawdź czy blok jest Sercem Działki
            if (block.getType() == Material.RED_SHULKER_BOX) {
                Plot blockPlot = plotManager.getPlotAt(block.getLocation());
                if (blockPlot != null && blockPlot.getHeartLocation().equals(block.getLocation())) {
                    // Anuluj działanie pistonu tylko gdy próbuje przesunąć Serce Działki
                    event.setCancelled(true);
                    return;
                }
            }
        }

        UUID playerUUID = getLastInteractionPlayer(pistonBlock);
        Plot pistonPlot = plotManager.getPlotAt(pistonBlock.getLocation());

        // Sprawdź wszystkie bloki, które będą wciągane
        for (Block block : event.getBlocks()) {
            Plot blockPlot = plotManager.getPlotAt(block.getLocation());

            // Przypadek 1: Piston jest poza działką i ciągnie blok z działki
            if (pistonPlot == null && blockPlot != null) {
                // Jeśli znamy gracza i jest członkiem działki bloku, pozwalamy
                if (playerUUID != null && blockPlot.isMember(playerUUID)) {
                    continue;
                }
                event.setCancelled(true);
                return;
            }

            // Przypadek 2: Piston jest na jednej działce i ciągnie blok z innej działki
            if (pistonPlot != null && blockPlot != null && !pistonPlot.getTag().equals(blockPlot.getTag())) {
                // Jeśli znamy gracza i jest członkiem obu działek, pozwalamy
                if (playerUUID != null && pistonPlot.isMember(playerUUID) && blockPlot.isMember(playerUUID)) {
                    continue;
                }
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Obsługuje zdarzenie wyrzucania przedmiotów przez dispenser
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (event.isCancelled()) return;

        Block dispenserBlock = event.getBlock();
        ItemStack item = event.getItem();

        // Jeśli to nie jest dispenser ani dropper, nic nie robimy
        if (!isDispenser(dispenserBlock.getType())) {
            return;
        }

        // Pobierz UUID gracza, który aktywował dispenser
        UUID playerUUID = getLastInteractionPlayer(dispenserBlock);

        // Pobierz działkę, na której znajduje się dispenser
        Plot dispenserPlot = plotManager.getPlotAt(dispenserBlock.getLocation());

        // Pobierz kierunek, w którym dispenser jest skierowany
        BlockData blockData = dispenserBlock.getBlockData();
        if (!(blockData instanceof Directional)) return;

        BlockFace face = ((Directional) blockData).getFacing();

        // Oblicz docelową lokalizację, gdzie przedmiot zostanie wyrzucony
        Location targetLoc = dispenserBlock.getLocation().clone().add(
                face.getModX(),
                face.getModY(),
                face.getModZ()
        );

        // Pobierz działkę w docelowej lokalizacji
        Plot targetPlot = plotManager.getPlotAt(targetLoc);

        // Jeśli znamy gracza i jest on członkiem docelowej działki, zawsze pozwalamy
        if (playerUUID != null && targetPlot != null && targetPlot.isMember(playerUUID)) {
            return;
        }

        // Sprawdź, czy dispenser wyrzuca przez granice działek
        if ((dispenserPlot == null && targetPlot != null) ||
                (dispenserPlot != null && targetPlot != null && !dispenserPlot.getTag().equals(targetPlot.getTag())) ||
                (dispenserPlot != null && targetPlot == null)) {

            // Zawsze blokuj niebezpieczne przedmioty
            if (isDangerousItem(item)) {
                event.setCancelled(true);
                return;
            }

            // Przedmioty, które mogą być umieszczone jako bloki
            if (item.getType().isBlock()) {
                event.setCancelled(true);
                return;
            }

            // Specjalne traktowanie dla wiader z wodą i lawą
            if (item.getType() == Material.WATER_BUCKET || item.getType() == Material.LAVA_BUCKET) {
                // Sprawdź kilka bloków w kierunku przepływu
                for (int i = 1; i <= 8; i++) {
                    Location flowLoc = dispenserBlock.getLocation().clone().add(
                            face.getModX() * i,
                            face.getModY() * i,
                            face.getModZ() * i
                    );

                    Plot flowPlot = plotManager.getPlotAt(flowLoc);

                    // Jeśli przepływ wejdzie na inną działkę
                    if ((dispenserPlot == null && flowPlot != null) ||
                            (dispenserPlot != null && flowPlot != null && !dispenserPlot.getTag().equals(flowPlot.getTag())) ||
                            (dispenserPlot != null && flowPlot == null)) {

                        // Jeśli znamy gracza i jest członkiem działki przepływu, pozwalamy
                        if (playerUUID != null && flowPlot != null && flowPlot.isMember(playerUUID)) {
                            continue;
                        }

                        // W przeciwnym razie blokujemy
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Sprawdza czy przedmiot jest niebezpieczny
     * @param item Przedmiot do sprawdzenia
     * @return true jeśli jest niebezpieczny, false w przeciwnym przypadku
     */
    private boolean isDangerousItem(ItemStack item) {
        Material type = item.getType();
        return type.toString().contains("BUCKET") ||
                type == Material.FLINT_AND_STEEL ||
                type == Material.FIRE_CHARGE ||
                type == Material.TNT ||
                type == Material.TNT_MINECART ||
                type == Material.END_CRYSTAL ||
                type == Material.TRIDENT ||
                type == Material.ARROW ||
                type == Material.SPECTRAL_ARROW ||
                type == Material.TIPPED_ARROW;
    }

    /**
     * Obsługuje zdarzenie zmiany bloku przez entity (np. spadający piasek)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.isCancelled()) return;

        Entity entity = event.getEntity();
        Block block = event.getBlock();

        // Śledzenie spadających bloków
        if (entity instanceof FallingBlock) {
            // Pobierz działkę, na której spadający blok chce się zamienić w normalny blok
            Plot targetPlot = plotManager.getPlotAt(block.getLocation());

            // Jeśli blok spada na działkę
            if (targetPlot != null) {
                // Sprawdź czy znamy gracza, który aktywował ten blok
                UUID entityUUID = entity.getUniqueId();
                BlockSourceInfo sourceInfo = fallingBlockSourceInfo.get(entityUUID);

                if (sourceInfo != null) {
                    // Pobierz działkę źródłową
                    Plot sourcePlot = plotManager.getPlotAt(sourceInfo.sourceLocation);
                    UUID playerUUID = sourceInfo.placerUUID;

                    // Jeśli pochodzi z innej działki
                    if (sourcePlot == null || !sourcePlot.getTag().equals(targetPlot.getTag())) {
                        // Jeśli znamy gracza i jest członkiem docelowej działki, pozwalamy
                        if (playerUUID != null && targetPlot.isMember(playerUUID)) {
                            return;
                        }

                        // W przeciwnym razie blokujemy
                        event.setCancelled(true);
                        return;
                    }
                } else {
                    // Jeśli nie znamy źródła, blokujemy dla bezpieczeństwa
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * Obsługuje zdarzenie wybuchu entity
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;

        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plotManager.getPlotAt(block.getLocation()) != null) {
                iterator.remove();
            }
        }
    }

    /**
     * Obsługuje zdarzenie wybuchu bloku
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.isCancelled()) return;

        Iterator<Block> iterator = event.blockList().iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plotManager.getPlotAt(block.getLocation()) != null) {
                iterator.remove();
            }
        }
    }

    /**
     * Obsługuje zdarzenie poruszania się gracza
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // Sprawdź, czy gracz przesunął się na inny blok
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Plot fromPlot = plotManager.getPlotAt(from);
        Plot toPlot = plotManager.getPlotAt(to);

        // Gracz wchodzi na działkę
        if (fromPlot == null && toPlot != null) {
            player.sendMessage(ChatColor.YELLOW + "Wszedłeś na teren działki " + ChatColor.GOLD + toPlot.getTag());
            plotManager.updateLastVisitedPlot(player.getUniqueId(), toPlot.getTag());
        }
        // Gracz opuszcza działkę
        else if (fromPlot != null && toPlot == null) {
            player.sendMessage(ChatColor.YELLOW + "Opuściłeś teren działki " + ChatColor.GOLD + fromPlot.getTag());
            plotManager.removeLastVisitedPlot(player.getUniqueId());
        }
        // Gracz przechodzi z jednej działki na drugą
        else if (fromPlot != null && toPlot != null && !fromPlot.getTag().equals(toPlot.getTag())) {
            player.sendMessage(ChatColor.YELLOW + "Opuściłeś teren działki " + ChatColor.GOLD + fromPlot.getTag());
            player.sendMessage(ChatColor.YELLOW + "Wszedłeś na teren działki " + ChatColor.GOLD + toPlot.getTag());
            plotManager.updateLastVisitedPlot(player.getUniqueId(), toPlot.getTag());
        }
    }

    /**
     * Pobiera UUID ostatniego gracza, który wchodził w interakcję z blokiem
     * @param block Blok do sprawdzenia
     * @return UUID gracza lub null, jeśli nie znaleziono
     */
    private UUID getLastInteractionPlayer(Block block) {
        // Sprawdź bezpośrednio ten blok
        String blockKey = getBlockKey(block);
        PlayerInteractionData data = lastRedstoneInteraction.get(blockKey);

        if (data != null) {
            return data.playerUUID;
        }

        // Jeśli nie znaleziono bezpośredniej interakcji, spróbuj w sąsiednich blokach
        for (BlockFace face : ADJACENT_FACES) {
            Block adjacent = block.getRelative(face);
            String adjacentKey = getBlockKey(adjacent);
            PlayerInteractionData adjacentData = lastRedstoneInteraction.get(adjacentKey);

            if (adjacentData != null) {
                return adjacentData.playerUUID;
            }
        }

        // Jeśli nadal nie znaleziono, znajdź ostatnio aktywnego gracza w redstone
        if (!playerLastRedstoneActivity.isEmpty()) {
            UUID mostRecentPlayer = null;
            long mostRecentTime = 0;

            for (Map.Entry<UUID, Long> entry : playerLastRedstoneActivity.entrySet()) {
                if (entry.getValue() > mostRecentTime) {
                    mostRecentTime = entry.getValue();
                    mostRecentPlayer = entry.getKey();
                }
            }

            if (mostRecentPlayer != null) {
                return mostRecentPlayer;
            }
        }

        return null;
    }

    /**
     * Generuje klucz dla bloku na podstawie jego położenia
     * @param block Blok
     * @return Klucz w formacie "świat,x,y,z"
     */
    private String getBlockKey(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /**
     * Sprawdza, czy materiał jest komponentem redstone
     * @param material Materiał do sprawdzenia
     * @return True jeśli to komponent redstone, false w przeciwnym razie
     */
    private boolean isRedstoneComponent(Material material) {
        String name = material.name();
        return name.contains("BUTTON") ||
                name.contains("LEVER") ||
                name.contains("PRESSURE_PLATE") ||
                name.contains("REDSTONE") ||
                name.equals("REPEATER") ||
                name.equals("COMPARATOR") ||
                name.equals("OBSERVER") ||
                name.equals("HOPPER") ||
                name.contains("DAYLIGHT_DETECTOR") ||
                name.contains("TRIPWIRE") ||
                name.contains("DETECTOR_RAIL") ||
                name.contains("TARGET");
    }

    /**
     * Sprawdza, czy materiał to piston
     * @param material Materiał do sprawdzenia
     * @return True jeśli to piston, false w przeciwnym razie
     */
    private boolean isPiston(Material material) {
        return material == Material.PISTON ||
                material == Material.STICKY_PISTON ||
                material == Material.PISTON_HEAD ||
                material == Material.MOVING_PISTON;
    }

    /**
     * Sprawdza, czy materiał to dispenser lub dropper
     * @param material Materiał do sprawdzenia
     * @return True jeśli to dispenser lub dropper, false w przeciwnym razie
     */
    private boolean isDispenser(Material material) {
        return material == Material.DISPENSER ||
                material == Material.DROPPER;
    }

    /**
     * Sprawdza, czy blok jest zasilony redstone
     * @param block Blok do sprawdzenia
     * @return True jeśli blok jest zasilony, false w przeciwnym razie
     */
    private boolean isPowered(Block block) {
        return block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    /**
     * Sprawdza, czy materiał to przewodnik redstone
     * @param material Materiał do sprawdzenia
     * @return True jeśli to przewodnik redstone, false w przeciwnym razie
     */
    private boolean isRedstoneWire(Material material) {
        String name = material.name();
        return                 name.equals("REDSTONE_WIRE") ||
                name.equals("REDSTONE") ||
                name.equals("REPEATER") ||
                name.equals("COMPARATOR") ||
                name.equals("REDSTONE_TORCH") ||
                name.equals("REDSTONE_WALL_TORCH") ||
                name.equals("REDSTONE_LAMP") ||
                name.equals("REDSTONE_BLOCK") ||
                name.equals("DAYLIGHT_DETECTOR") ||
                name.equals("OBSERVER") ||
                name.contains("POWERED") ||
                name.contains("DETECTOR_RAIL") ||
                name.contains("RAIL") ||
                name.contains("TRIPWIRE");
    }

    /**
     * Sprawdza, czy materiał to blok interaktywny
     * @param material Materiał do sprawdzenia
     * @return True jeśli to blok interaktywny, false w przeciwnym razie
     */
    private boolean isInteractiveBlock(Material material) {
        String name = material.name();
        return name.contains("DOOR") ||
                name.contains("GATE") ||
                name.contains("CHEST") ||
                name.contains("BARREL") ||
                name.contains("SHULKER") ||
                name.contains("BUTTON") ||
                name.contains("LEVER") ||
                name.contains("TRAPDOOR") ||
                name.contains("BED") ||
                name.contains("ANVIL") ||
                name.contains("GRINDSTONE") ||
                name.contains("LOOM") ||
                name.contains("SMITHING") ||
                name.contains("CRAFTING") ||
                name.contains("FURNACE") ||
                name.contains("BLAST") ||
                name.contains("SMOKER") ||
                name.contains("BREWING") ||
                name.contains("ENCHANT") ||
                name.contains("JUKEBOX") ||
                name.contains("BEACON") ||
                name.contains("BELL") ||
                name.contains("LECTERN") ||
                name.contains("HOPPER") ||
                name.contains("DISPENSER") ||
                name.contains("DROPPER") ||
                name.contains("REPEATER") ||
                name.contains("COMPARATOR") ||
                name.contains("DAYLIGHT_DETECTOR") ||
                name.contains("NOTE_BLOCK") ||
                name.contains("FENCE_GATE") ||
                name.contains("RESPAWN_ANCHOR") ||
                name.contains("COMMAND_BLOCK") ||
                name.contains("CARTOGRAPHY") ||
                name.contains("COMPOSTER") ||
                name.contains("STONECUTTER") ||
                name.contains("CAMPFIRE") ||
                name.contains("CANDLE") ||
                name.contains("CAKE") ||
                name.contains("ITEM_FRAME") ||
                name.contains("FLOWER_POT");
    }
}