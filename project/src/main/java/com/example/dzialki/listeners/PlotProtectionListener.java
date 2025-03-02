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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.Action;

import java.util.*;

public class PlotProtectionListener implements Listener {
    private final Dzialki plugin;
    private final PlotManager plotManager;

    // Struktura przechowująca ostatnie interakcje z mechanizmami redstone
    private final Map<String, PlayerInteractionData> lastRedstoneInteraction = new HashMap<>();
    // Struktura przechowująca ostatnią aktywność graczy z redstone
    private final Map<UUID, Long> playerLastRedstoneActivity = new HashMap<>();
    private final long INTERACTION_TIMEOUT = 300000; // 5 minut w milisekundach

    // Cooldown dla wiadomości aby zapobiec duplikatom
    private final Map<UUID, Long> interactionMessageCooldown = new HashMap<>();
    private final Map<UUID, Long> breakMessageCooldown = new HashMap<>();
    private final Map<UUID, Long> placeMessageCooldown = new HashMap<>();
    private final long MESSAGE_COOLDOWN = 500; // 500 ms cooldown

    // Śledzenie źródeł cieczy
    private final Map<String, UUID> liquidSources = new HashMap<>();
    private final long LIQUID_SOURCE_TIMEOUT = 300000; // 5 minut

    public PlotProtectionListener(Dzialki plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();

        // Planujemy zadanie czyszczące stare interakcje
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            lastRedstoneInteraction.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue().timestamp) > INTERACTION_TIMEOUT);

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
        }, 6000L, 6000L); // Uruchom co 5 minut (6000 ticków)
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Plot plot = plotManager.getPlotAt(block.getLocation());

        // Śledzenie umieszczonych źródeł cieczy
        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            liquidSources.put(getBlockKey(block), player.getUniqueId());
        }

        // Sprawdź, czy to wiadro z wodą/lawą (dodatkowe śledzenie)
        ItemStack item = event.getItemInHand();
        if (item.getType() == Material.WATER_BUCKET || item.getType() == Material.LAVA_BUCKET) {
            // Zapisz również informację o cieczy w miejscu wylania
            liquidSources.put(getBlockKey(block), player.getUniqueId());
        }

        if (plot != null && !plot.isMember(player.getUniqueId()) && !player.hasPermission("dzialka.admin.bypass")) {
            event.setCancelled(true);

            // Sprawdź cooldown przed wysłaniem wiadomości
            long currentTime = System.currentTimeMillis();
            Long lastMessageTime = placeMessageCooldown.get(player.getUniqueId());

            if (lastMessageTime == null || (currentTime - lastMessageTime) > MESSAGE_COOLDOWN) {
                player.sendMessage(ChatColor.RED + "Nie możesz stawiać bloków na tej działce!");
                placeMessageCooldown.put(player.getUniqueId(), currentTime);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
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

        // Zapisz interakcję z mechanizmami redstone
        if (isRedstoneComponent(block.getType()) || block.getType().name().contains("DISPENSER")) {
            // Utwórz dane interakcji z UUID gracza
            PlayerInteractionData interactionData = new PlayerInteractionData(player.getUniqueId());

            // Użyj współrzędnych bloku jako klucza
            String blockKey = getBlockKey(block);
            lastRedstoneInteraction.put(blockKey, interactionData);

            // Rozpocznij śledzenie wszystkich połączonych komponentów redstone
            trackRedstoneWireConnections(block, interactionData);

            // Specjalne przetwarzanie dla elementów aktywowalnych przez redstone
            // Sprawdź również bloki w promieniu 3 bloków (dla pewności)
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Block neighbor = block.getRelative(x, y, z);
                        if (neighbor.getType().name().contains("DISPENSER") ||
                                neighbor.getType().name().contains("PISTON") ||
                                neighbor.getType().name().contains("DROPPER")) {

                            // Dodaj te bloki do sieci redstone
                            lastRedstoneInteraction.put(getBlockKey(neighbor), interactionData);
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

    @EventHandler(priority = EventPriority.HIGH)
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
            // Blokuj przepływ między różnymi działkami
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
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
                        }
                    }
                }
            }
        }

        if (processedBlocks >= maxBlocks) {
            plugin.getLogger().warning("[Debug] Przerwano przeszukiwanie sieci redstone - zbyt duża sieć!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.isCancelled()) return;

        Block pistonBlock = event.getBlock();

        // Pobierz UUID gracza, który aktywował piston
        UUID playerUUID = getLastInteractionPlayer(pistonBlock);

        Plot pistonPlot = plotManager.getPlotAt(pistonBlock.getLocation());

        // Sprawdź, czy jakieś bloki są wpychane na działkę
        for (Block block : event.getBlocks()) {
            // Oblicz docelową lokalizację, gdzie blok zostanie wepchnięty
            Location targetLoc = block.getLocation().clone().add(
                    event.getDirection().getModX(),
                    event.getDirection().getModY(),
                    event.getDirection().getModZ()
            );

            Plot targetPlot = plotManager.getPlotAt(targetLoc);

            // Jeśli wpychamy na działkę z zewnątrz lub z jednej działki na drugą
            if ((pistonPlot == null && targetPlot != null) ||
                    (pistonPlot != null && targetPlot != null && !pistonPlot.getTag().equals(targetPlot.getTag()))) {

                // Jeśli wiemy kto to aktywował i jest członkiem działki docelowej, pozwól na to
                if (playerUUID != null && targetPlot.isMember(playerUUID)) {
                    continue;
                }

                // W przeciwnym razie, anuluj wydarzenie
                event.setCancelled(true);
                return;
            }
        }

        // Sprawdź, czy jakieś bloki z działki są przesuwane
        for (Block block : event.getBlocks()) {
            Plot blockPlot = plotManager.getPlotAt(block.getLocation());

            // Jeśli przesuwamy bloki z działki pistonem spoza działki
            if (pistonPlot == null && blockPlot != null) {
                // Jeśli wiemy kto to aktywował i jest członkiem działki bloku, pozwól na to
                if (playerUUID != null && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // W przeciwnym razie, anuluj wydarzenie
                event.setCancelled(true);
                return;
            }

            // Jeśli przesuwamy bloki z jednej działki na drugą
            if (pistonPlot != null && blockPlot != null && !pistonPlot.getTag().equals(blockPlot.getTag())) {
                // Jeśli wiemy kto to aktywował i jest członkiem obu działek, pozwól na to
                if (playerUUID != null && pistonPlot.isMember(playerUUID) && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // W przeciwnym razie, anuluj wydarzenie
                event.setCancelled(true);
                return;
            }
        }

        // Sprawdź, czy głowica pistonu będzie wchodzić na działkę
        Location pistonHeadLoc = pistonBlock.getLocation().clone().add(
                event.getDirection().getModX(),
                event.getDirection().getModY(),
                event.getDirection().getModZ()
        );

        Plot pistonHeadPlot = plotManager.getPlotAt(pistonHeadLoc);

        // Jeśli głowica pistonu wchodzi na inną działkę
        if ((pistonPlot == null && pistonHeadPlot != null) ||
                (pistonPlot != null && pistonHeadPlot != null && !pistonPlot.getTag().equals(pistonHeadPlot.getTag()))) {

            // Jeśli wiemy kto to aktywował i jest członkiem działki, na którą wchodzi głowica, pozwól na to
            if (playerUUID != null && pistonHeadPlot.isMember(playerUUID)) {
                return;
            }

            // W przeciwnym razie, anuluj wydarzenie
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.isCancelled()) return;

        Block pistonBlock = event.getBlock();
        UUID playerUUID = getLastInteractionPlayer(pistonBlock);
        Plot pistonPlot = plotManager.getPlotAt(pistonBlock.getLocation());

        // Sprawdź, czy jakieś bloki są wciągane z działki
        for (Block block : event.getBlocks()) {
            Plot blockPlot = plotManager.getPlotAt(block.getLocation());

            // Jeśli wciągamy bloki z działki pistonem spoza działki
            if (pistonPlot == null && blockPlot != null) {
                // Jeśli wiemy kto to aktywował i jest członkiem działki bloku, pozwól na to
                if (playerUUID != null && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // W przeciwnym razie, anuluj wydarzenie
                event.setCancelled(true);
                return;
            }

            // Jeśli wciągamy bloki z jednej działki na drugą
            if (pistonPlot != null && blockPlot != null && !pistonPlot.getTag().equals(blockPlot.getTag())) {
                // Jeśli wiemy kto to aktywował i jest członkiem obu działek, pozwól na to
                if (playerUUID != null && pistonPlot.isMember(playerUUID) && blockPlot.isMember(playerUUID)) {
                    continue;
                }

                // W przeciwnym razie, anuluj wydarzenie
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (event.isCancelled()) return;

        Block dispenserBlock = event.getBlock();

        // Bardziej dogłębne szukanie gracza - najpierw sprawdźmy sam dyspenser
        UUID playerUUID = getLastInteractionPlayer(dispenserBlock);
        Plot dispenserPlot = plotManager.getPlotAt(dispenserBlock.getLocation());

        // Uzyskaj kierunek, w którym dyspenser jest skierowany
        BlockFace face = ((org.bukkit.block.data.Directional) dispenserBlock.getBlockData()).getFacing();

        // Oblicz docelową lokalizację, gdzie przedmiot zostanie wyrzucony
        Location targetLoc = dispenserBlock.getLocation().clone().add(
                face.getModX(),
                face.getModY(),
                face.getModZ()
        );

        Plot targetPlot = plotManager.getPlotAt(targetLoc);

        // Jeśli znamy gracza i jest on członkiem docelowej działki, zawsze pozwól na to
        if (playerUUID != null && targetPlot != null && targetPlot.isMember(playerUUID)) {
            return;  // Pozwól działać dyspenerowi
        }

        // Jeśli dyspenser jest poza działką i wyrzuca na działkę
        if (dispenserPlot == null && targetPlot != null) {
            // Jeśli nie znamy kto aktywował lub nie jest członkiem działki docelowej
            event.setCancelled(true);
            return;
        }

        // Jeśli dyspenser jest na jednej działce i wyrzuca na inną działkę
        if (dispenserPlot != null && targetPlot != null && !dispenserPlot.getTag().equals(targetPlot.getTag())) {
            // Jeśli nie znamy kto aktywował lub nie jest członkiem obu działek
            event.setCancelled(true);
            return;
        }

        // Specjalne traktowanie dla wiaderek z wodą i lawą
        ItemStack item = event.getItem();
        if (item.getType().toString().contains("BUCKET")) {
            // Sprawdź kilka bloków w kierunku wyrzucania, aby złapać przepływ wody/lawy
            for (int i = 1; i <= 8; i++) {
                Location flowLoc = dispenserBlock.getLocation().clone().add(
                        face.getModX() * i,
                        face.getModY() * i,
                        face.getModZ() * i
                );

                Plot flowPlot = plotManager.getPlotAt(flowLoc);

                // Jeśli przepływ wejdzie na inną działkę
                if ((dispenserPlot == null && flowPlot != null) ||
                        (dispenserPlot != null && flowPlot != null && !dispenserPlot.getTag().equals(flowPlot.getTag()))) {

                    // Jeśli wiemy kto to aktywował i jest członkiem działki przepływu, pozwól na to
                    if (playerUUID != null && flowPlot.isMember(playerUUID)) {
                        continue;
                    }

                    // W przeciwnym razie, anuluj wydarzenie
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
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

    @EventHandler(priority = EventPriority.HIGH)
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

        // Jeśli nie znaleziono bezpośredniej interakcji, znajdź ostatnio aktywnego gracza
        // Sprawdzamy wszystkie aktywności graczy i zwracamy tego, który ostatnio używał redstone
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
    private boolean isRedstoneComponent(org.bukkit.Material material) {
        String name = material.name();
        return name.contains("BUTTON") ||
                name.contains("LEVER") ||
                name.contains("PRESSURE_PLATE") ||
                name.contains("REDSTONE") ||
                name.equals("REPEATER") ||
                name.equals("COMPARATOR") ||
                name.equals("OBSERVER") ||
                name.equals("DISPENSER") ||
                name.equals("DROPPER") ||
                name.equals("HOPPER") ||
                name.contains("PISTON") ||
                name.contains("DAYLIGHT_DETECTOR") ||
                name.contains("TRIPWIRE") ||
                name.contains("DETECTOR_RAIL") ||
                name.contains("TARGET");
    }

    /**
     * Sprawdza, czy materiał to przewodnik redstone
     * @param material Materiał do sprawdzenia
     * @return True jeśli to przewodnik redstone, false w przeciwnym razie
     */
    private boolean isRedstoneWire(org.bukkit.Material material) {
        String name = material.name();
        return name.equals("REDSTONE_WIRE") ||
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
    private boolean isInteractiveBlock(org.bukkit.Material material) {
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

    /**
     * Próbuje znaleźć gracza, który postawił ciecz, poprzez przeanalizowanie źródła
     * @param block Blok z cieczą
     * @return UUID gracza lub null, jeśli nie udało się ustalić
     */
    // Poniższa metoda może być usunięta, ponieważ nie jest już używana
    private UUID findLiquidPlacer(Block block) {
        // Sprawdź, czy znamy źródło tego konkretnego bloku
        String blockKey = getBlockKey(block);
        UUID directPlacer = liquidSources.get(blockKey);
        if (directPlacer != null) {
            return directPlacer;
        }

        // Jeśli nie znamy bezpośredniego źródła, spróbuj znaleźć źródło cieczy w okolicy
        Material type = block.getType();

        // Najpierw sprawdźmy najbliższe bloki (może tam jest źródło)
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y >= -1; y--) { // Priorytetyzuj sprawdzanie na tym samym poziomie i powyżej
                for (int z = -1; z <= 1; z++) {
                    Block nearby = block.getRelative(x, y, z);

                    // Sprawdź, czy to źródło cieczy
                    if (nearby.getType() == type && nearby.getBlockData() instanceof org.bukkit.block.data.Levelled) {
                        org.bukkit.block.data.Levelled levelled = (org.bukkit.block.data.Levelled) nearby.getBlockData();
                        if (levelled.getLevel() == 0) {  // To źródło cieczy
                            String sourceKey = getBlockKey(nearby);
                            UUID sourcePlacer = liquidSources.get(sourceKey);
                            if (sourcePlacer != null) {
                                return sourcePlacer;
                            }
                        }
                    }
                }
            }
        }

        // Jeśli nadal nie znaleźliśmy, wykonaj szersze wyszukiwanie
        int radius = 8; // Maksymalny promień przepływu cieczy

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Pomijamy bloki, które są zbyt daleko (używamy odległości Manhattan)
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > radius) continue;

                    Block potentialSource = block.getRelative(x, y, z);

                    // Sprawdź, czy to ten sam typ cieczy
                    if (potentialSource.getType() == type) {
                        // Sprawdź, czy to źródło cieczy
                        if (potentialSource.getBlockData() instanceof org.bukkit.block.data.Levelled) {
                            org.bukkit.block.data.Levelled levelled = (org.bukkit.block.data.Levelled) potentialSource.getBlockData();
                            if (levelled.getLevel() == 0) {  // To źródło cieczy
                                String sourceKey = getBlockKey(potentialSource);
                                UUID sourcePlacer = liquidSources.get(sourceKey);
                                if (sourcePlacer != null) {
                                    return sourcePlacer;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Jeśli nie udało się znaleźć źródła, zwróć null
        return null;
    }
}