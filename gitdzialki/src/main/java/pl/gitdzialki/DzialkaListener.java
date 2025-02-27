package pl.gitdzialki;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DzialkaListener implements Listener {

    private final PlotManager plotManager;
    private final Main plugin; // Referencja do Main
    private final Set<Material> interactiveBlocks;
    private final Map<UUID, Long> lastMessageTime;

    public DzialkaListener(PlotManager plotManager, Main plugin) {
        this.plotManager = plotManager;
        this.plugin = plugin; // Inicjalizacja referencji do Main
        this.interactiveBlocks = new HashSet<>();
        this.lastMessageTime = new HashMap<>();

        // Lista interaktywnych bloków (możesz dostosować w zależności od potrzeb)
        interactiveBlocks.add(Material.CHEST);
        interactiveBlocks.add(Material.TRAPPED_CHEST);
        interactiveBlocks.add(Material.SHULKER_BOX);
        interactiveBlocks.add(Material.BLACK_SHULKER_BOX);
        interactiveBlocks.add(Material.BLUE_SHULKER_BOX);
        interactiveBlocks.add(Material.BROWN_SHULKER_BOX);
        interactiveBlocks.add(Material.CYAN_SHULKER_BOX);
        interactiveBlocks.add(Material.GRAY_SHULKER_BOX);
        interactiveBlocks.add(Material.GREEN_SHULKER_BOX);
        interactiveBlocks.add(Material.LIGHT_BLUE_SHULKER_BOX);
        interactiveBlocks.add(Material.LIGHT_GRAY_SHULKER_BOX);
        interactiveBlocks.add(Material.LIME_SHULKER_BOX);
        interactiveBlocks.add(Material.MAGENTA_SHULKER_BOX);
        interactiveBlocks.add(Material.ORANGE_SHULKER_BOX);
        interactiveBlocks.add(Material.PINK_SHULKER_BOX);
        interactiveBlocks.add(Material.PURPLE_SHULKER_BOX);
        interactiveBlocks.add(Material.RED_SHULKER_BOX); // Chroni "Serce Działki"
        interactiveBlocks.add(Material.WHITE_SHULKER_BOX);
        interactiveBlocks.add(Material.YELLOW_SHULKER_BOX);
        interactiveBlocks.add(Material.FURNACE);
        interactiveBlocks.add(Material.BLAST_FURNACE);
        interactiveBlocks.add(Material.SMOKER);
        interactiveBlocks.add(Material.BREWING_STAND);
        interactiveBlocks.add(Material.DISPENSER);
        interactiveBlocks.add(Material.DROPPER);
        interactiveBlocks.add(Material.HOPPER);
        interactiveBlocks.add(Material.BARREL);
        interactiveBlocks.add(Material.OAK_DOOR);
        interactiveBlocks.add(Material.SPRUCE_DOOR);
        interactiveBlocks.add(Material.BIRCH_DOOR);
        interactiveBlocks.add(Material.JUNGLE_DOOR);
        interactiveBlocks.add(Material.ACACIA_DOOR);
        interactiveBlocks.add(Material.DARK_OAK_DOOR);
        interactiveBlocks.add(Material.MANGROVE_DOOR);
        interactiveBlocks.add(Material.CHERRY_DOOR);
        interactiveBlocks.add(Material.BAMBOO_DOOR);
        interactiveBlocks.add(Material.CRIMSON_DOOR);
        interactiveBlocks.add(Material.WARPED_DOOR);
        interactiveBlocks.add(Material.IRON_DOOR);
        interactiveBlocks.add(Material.COPPER_DOOR);
        interactiveBlocks.add(Material.EXPOSED_COPPER_DOOR);
        interactiveBlocks.add(Material.WEATHERED_COPPER_DOOR);
        interactiveBlocks.add(Material.OXIDIZED_COPPER_DOOR);
        interactiveBlocks.add(Material.WAXED_COPPER_DOOR);
        interactiveBlocks.add(Material.WAXED_EXPOSED_COPPER_DOOR);
        interactiveBlocks.add(Material.WAXED_WEATHERED_COPPER_DOOR);
        interactiveBlocks.add(Material.WAXED_OXIDIZED_COPPER_DOOR);
        interactiveBlocks.add(Material.COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.EXPOSED_COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.WEATHERED_COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.OXIDIZED_COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.WAXED_COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.WAXED_EXPOSED_COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.WAXED_WEATHERED_COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.WAXED_OXIDIZED_COPPER_TRAPDOOR);
        interactiveBlocks.add(Material.OAK_TRAPDOOR);
        interactiveBlocks.add(Material.SPRUCE_TRAPDOOR);
        interactiveBlocks.add(Material.BIRCH_TRAPDOOR);
        interactiveBlocks.add(Material.JUNGLE_TRAPDOOR);
        interactiveBlocks.add(Material.ACACIA_TRAPDOOR);
        interactiveBlocks.add(Material.DARK_OAK_TRAPDOOR);
        interactiveBlocks.add(Material.MANGROVE_TRAPDOOR);
        interactiveBlocks.add(Material.CHERRY_TRAPDOOR);
        interactiveBlocks.add(Material.BAMBOO_TRAPDOOR);
        interactiveBlocks.add(Material.CRIMSON_TRAPDOOR);
        interactiveBlocks.add(Material.WARPED_TRAPDOOR);
        interactiveBlocks.add(Material.IRON_TRAPDOOR);
        interactiveBlocks.add(Material.STONE_BUTTON);
        interactiveBlocks.add(Material.OAK_BUTTON);
        interactiveBlocks.add(Material.SPRUCE_BUTTON);
        interactiveBlocks.add(Material.BIRCH_BUTTON);
        interactiveBlocks.add(Material.JUNGLE_BUTTON);
        interactiveBlocks.add(Material.ACACIA_BUTTON);
        interactiveBlocks.add(Material.DARK_OAK_BUTTON);
        interactiveBlocks.add(Material.MANGROVE_BUTTON);
        interactiveBlocks.add(Material.CHERRY_BUTTON);
        interactiveBlocks.add(Material.BAMBOO_BUTTON);
        interactiveBlocks.add(Material.CRIMSON_BUTTON);
        interactiveBlocks.add(Material.WARPED_BUTTON);
        interactiveBlocks.add(Material.POLISHED_BLACKSTONE_BUTTON);
        interactiveBlocks.add(Material.WHITE_BED);
        interactiveBlocks.add(Material.ORANGE_BED);
        interactiveBlocks.add(Material.MAGENTA_BED);
        interactiveBlocks.add(Material.LIGHT_BLUE_BED);
        interactiveBlocks.add(Material.YELLOW_BED);
        interactiveBlocks.add(Material.LIME_BED);
        interactiveBlocks.add(Material.PINK_BED);
        interactiveBlocks.add(Material.GRAY_BED);
        interactiveBlocks.add(Material.LIGHT_GRAY_BED);
        interactiveBlocks.add(Material.CYAN_BED);
        interactiveBlocks.add(Material.PURPLE_BED);
        interactiveBlocks.add(Material.BLUE_BED);
        interactiveBlocks.add(Material.BROWN_BED);
        interactiveBlocks.add(Material.GREEN_BED);
        interactiveBlocks.add(Material.RED_BED);
        interactiveBlocks.add(Material.BLACK_BED);
        interactiveBlocks.add(Material.OAK_FENCE_GATE);
        interactiveBlocks.add(Material.SPRUCE_FENCE_GATE);
        interactiveBlocks.add(Material.BIRCH_FENCE_GATE);
        interactiveBlocks.add(Material.JUNGLE_FENCE_GATE);
        interactiveBlocks.add(Material.ACACIA_FENCE_GATE);
        interactiveBlocks.add(Material.DARK_OAK_FENCE_GATE);
        interactiveBlocks.add(Material.MANGROVE_FENCE_GATE);
        interactiveBlocks.add(Material.CHERRY_FENCE_GATE);
        interactiveBlocks.add(Material.BAMBOO_FENCE_GATE);
        interactiveBlocks.add(Material.CRIMSON_FENCE_GATE);
        interactiveBlocks.add(Material.WARPED_FENCE_GATE);
        interactiveBlocks.add(Material.LEVER);
        interactiveBlocks.add(Material.CRAFTER);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        ItemStack item = event.getItemInHand();

        if (block.getType() == Material.RED_SHULKER_BOX) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getDisplayName().equals(ChatColor.RED + "Serce Działki")) {
                if (!plotManager.hasPlot(player)) {
                    Location location = block.getLocation();
                    plotManager.createPlot(player, location);
                } else {
                    player.sendMessage(ChatColor.RED + "Posiadasz już działkę!");
                    event.setCancelled(true);
                }
            } else if (plotManager.isPlot(block.getLocation())) {
                UUID ownerUUID = plotManager.getPlotOwner(block.getLocation());
                if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nie możesz stawiać bloków na cudzej działce!");
                    event.setCancelled(true);
                }
            }
        } else {
            Location location = block.getLocation();
            if (plotManager.isPlot(location)) {
                UUID ownerUUID = plotManager.getPlotOwner(location);
                if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nie możesz stawiać bloków na cudzej działce!");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location location = block.getLocation();

        if (plotManager.isPlot(location)) {
            UUID ownerUUID = plotManager.getPlotOwner(location);
            if (block.getType() == Material.RED_SHULKER_BOX) {
                if (ownerUUID != null && ownerUUID.equals(player.getUniqueId())) {
                    plotManager.removePlot(player, location);
                } else {
                    player.sendMessage(ChatColor.RED + "Nie możesz zniszczyć tej działki!");
                    event.setCancelled(true);
                }
            } else if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Nie możesz niszczyć bloków na cudzej działce!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            Location location = block.getLocation();

            if (plotManager.isPlot(location)) {
                UUID ownerUUID = plotManager.getPlotOwner(location);
                if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
                    if (interactiveBlocks.contains(block.getType())) {
                        long currentTime = System.currentTimeMillis();
                        Long lastTime = lastMessageTime.get(player.getUniqueId());
                        if (lastTime == null || (currentTime - lastTime) > 500) {
                            player.sendMessage(ChatColor.RED + "Nie możesz używać bloków na cudzej działce!");
                            lastMessageTime.put(player.getUniqueId(), currentTime);
                        }
                        event.setCancelled(true);
                    }

                    // Sprawdź, czy gracz próbuje postawić łódkę
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (isBoatItem(item.getType())) {
                        player.sendMessage(ChatColor.RED + "Nie możesz stawiać łódek na cudzej działce!");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        Location pistonLoc = piston.getLocation();
        BlockFace face = event.getDirection();
        int dx = face.getModX();
        int dy = face.getModY();
        int dz = face.getModZ();

        for (Block block : event.getBlocks()) {
            Location from = block.getLocation();
            Location to = from.clone().add(dx, dy, dz);
            if (!canMoveBlock(pistonLoc, from, to)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        // Obsługujemy tylko sticky pistony, które pobierają blok
        if (!event.isSticky()) return;

        Block piston = event.getBlock();
        Location pistonLoc = piston.getLocation();
        BlockFace face = event.getDirection();
        int dx = face.getModX();
        int dy = face.getModY();
        int dz = face.getModZ();

        for (Block block : event.getBlocks()) {
            Location from = block.getLocation();
            // Przy cofaniu blok przesuwa się w przeciwnym kierunku
            Location to = from.clone().subtract(dx, dy, dz);
            if (!canMoveBlock(pistonLoc, from, to)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Metoda sprawdzająca, czy przesunięcie bloku z lokalizacji "from" do "to" przy pomocy pistońa w "pistonLoc"
     * jest dozwolone według zasad działek.
     */
    private boolean canMoveBlock(Location pistonLoc, Location from, Location to) {
        boolean fromInPlot = plotManager.isPlot(from);
        boolean toInPlot = plotManager.isPlot(to);

        // Jeśli blok ani w punkcie startowym, ani docelowym nie znajduje się na działce – ruch jest dozwolony
        if (!fromInPlot && !toInPlot) {
            return true;
        }

        // Jeżeli którykolwiek punkt dotyczy działki, pistoń musi się w niej znajdować,
        // by ruch mógł być wykonany
        if (!plotManager.isPlot(pistonLoc)) {
            return false;
        }

        // Pobieramy właściciela działki, w której znajduje się pistoń – zakładamy, że pistoń umieszczony na terenie
        // danej działki należy do jej właściciela
        if (fromInPlot) {
            if (!plotManager.getPlotOwner(pistonLoc).equals(plotManager.getPlotOwner(from))) {
                return false;
            }
        }
        if (toInPlot) {
            if (!plotManager.getPlotOwner(pistonLoc).equals(plotManager.getPlotOwner(to))) {
                return false;
            }
        }

        // Dodatkowo – nie pozwalamy na ruch z działki na zewnątrz ani z zewnątrz do działki
        if (fromInPlot && !toInPlot) {
            return false;
        }
        if (!fromInPlot && toInPlot) {
            return false;
        }
        // Jeśli oba punkty są na działce, to muszą należeć do tej samej działki (czyli mieć tego samego właściciela)
        if (fromInPlot && toInPlot) {
            if (!plotManager.getPlotOwner(from).equals(plotManager.getPlotOwner(to))) {
                return false;
            }
        }

        return true;
    }

    @EventHandler
    public void onFluidFlow(BlockFromToEvent event) {
        Block fromBlock = event.getBlock(); // Blok, z którego płynie płyn (woda/lawa)
        Block toBlock = event.getToBlock(); // Blok, do którego płynie płyn

        Location fromLoc = fromBlock.getLocation();
        Location toLoc = toBlock.getLocation();

        // Sprawdź, czy blok startowy to woda, lawa lub proszek śnieżny
        if (fromBlock.getType() == Material.WATER ||
                fromBlock.getType() == Material.LAVA ||
                fromBlock.getType() == Material.POWDER_SNOW) {
            boolean fromInPlot = plotManager.isPlot(fromLoc);
            boolean toInPlot = plotManager.isPlot(toLoc);

            // Zezwól na przepływ z działki na zewnątrz, ale blokuj przepływ z zewnątrz do działki
            if (!fromInPlot && toInPlot) {
                event.setCancelled(true); // Blokuj przepływ z zewnątrz do działki
            }
            // Nie blokuj przepływu z działki na zewnątrz (fromInPlot && !toInPlot)
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockClicked().getLocation(); // Lokalizacja, na którą wylewany jest płyn

        // Sprawdź, czy gracz wylewa którykolwiek z płynów lub substancji w kubełkach
        Material bucketType = event.getBucket();
        if (bucketType == Material.WATER_BUCKET ||
                bucketType == Material.LAVA_BUCKET ||
                bucketType == Material.POWDER_SNOW_BUCKET ||
                bucketType == Material.COD_BUCKET ||
                bucketType == Material.SALMON_BUCKET ||
                bucketType == Material.TROPICAL_FISH_BUCKET ||
                bucketType == Material.PUFFERFISH_BUCKET ||
                bucketType == Material.TADPOLE_BUCKET ||
                bucketType == Material.AXOLOTL_BUCKET) {
            if (plotManager.isPlot(location)) {
                UUID ownerUUID = plotManager.getPlotOwner(location);
                if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Nie możesz wylewać substancji na cudzą działkę!");
                    event.setCancelled(true);
                }
            }
        }
    }

    // Metoda pomocnicza: Sprawdź, czy item to zwykła łódka
    private boolean isBoatItem(Material material) {
        return material == Material.OAK_BOAT ||
                material == Material.BIRCH_BOAT ||
                material == Material.SPRUCE_BOAT ||
                material == Material.JUNGLE_BOAT ||
                material == Material.ACACIA_BOAT ||
                material == Material.DARK_OAK_BOAT ||
                material == Material.MANGROVE_BOAT ||
                material == Material.CHERRY_BOAT ||
                material == Material.BAMBOO_RAFT;
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent event) {
        Block dispenserBlock = event.getBlock(); // Blok dyspensera
        ItemStack item = event.getItem(); // Przedmiot wylatujący z dyspensera

        // Sprawdź, czy dyspenser wylewa kubełek z płynem lub substancją
        if (isBucketItem(item.getType())) {
            Location dispenserLoc = dispenserBlock.getLocation();
            Location targetLoc = dispenserBlock.getRelative(event.getVelocity().getBlockX() != 0 ? BlockFace.valueOf("X" + (event.getVelocity().getBlockX() > 0 ? "POS" : "NEG")) :
                    event.getVelocity().getBlockZ() != 0 ? BlockFace.valueOf("Z" + (event.getVelocity().getBlockZ() > 0 ? "POS" : "NEG")) :
                            BlockFace.UP).getLocation(); // Przybliżona lokalizacja docelowa (w zależności od kierunku)

            boolean dispenserInPlot = plotManager.isPlot(dispenserLoc);
            boolean targetInPlot = plotManager.isPlot(targetLoc);

            // Jeśli dyspenser jest poza działką, a cel jest w działce, zablokuj wylewanie
            if (!dispenserInPlot && targetInPlot) {
                plugin.getLogger().warning("Zablokowano wylewanie " + item.getType() + " z dyspensera w " + dispenserLoc + " na działkę w " + targetLoc);
                event.setCancelled(true);
            }
        }
    }

    // Metoda pomocnicza: Sprawdź, czy item to kubełek z płynem lub substancją
    private boolean isBucketItem(Material material) {
        return material == Material.WATER_BUCKET ||
                material == Material.LAVA_BUCKET ||
                material == Material.POWDER_SNOW_BUCKET ||
                material == Material.MILK_BUCKET ||
                material == Material.COD_BUCKET ||
                material == Material.SALMON_BUCKET ||
                material == Material.TROPICAL_FISH_BUCKET ||
                material == Material.PUFFERFISH_BUCKET;
    }
}