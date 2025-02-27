package pl.gitdzialki;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Klasa odpowiedzialna za rozszerzoną ochronę działek przed dyspenserami
 */
public class DispenserProtectionListener implements Listener {

    private final Main plugin;
    private final PlotManager plotManager;
    private final DispenserActivatorTracker activatorTracker;
    private final RedstoneTrackingManager redstoneTracker;

    // Przedmioty, które są potencjalnie niebezpieczne
    private final Set<Material> dangerousItems = new HashSet<>(Arrays.asList(
            // Kubełki z płynami
            Material.WATER_BUCKET,
            Material.LAVA_BUCKET,
            Material.POWDER_SNOW_BUCKET,
            Material.MILK_BUCKET,
            // Kubełki z mobami
            Material.COD_BUCKET,
            Material.SALMON_BUCKET,
            Material.TROPICAL_FISH_BUCKET,
            Material.PUFFERFISH_BUCKET,
            Material.AXOLOTL_BUCKET,
            Material.TADPOLE_BUCKET,
            // Łódki i wagoniki
            Material.OAK_BOAT,
            Material.BIRCH_BOAT,
            Material.SPRUCE_BOAT,
            Material.JUNGLE_BOAT,
            Material.ACACIA_BOAT,
            Material.DARK_OAK_BOAT,
            Material.MANGROVE_BOAT,
            Material.CHERRY_BOAT,
            Material.BAMBOO_RAFT,
            Material.MINECART,
            Material.CHEST_MINECART,
            Material.HOPPER_MINECART,
            Material.TNT_MINECART,
            Material.FURNACE_MINECART,
            // Materiały wybuchowe
            Material.TNT,
            Material.FIRE_CHARGE,
            Material.FIREWORK_ROCKET,
            // Jaja spawnujące
            Material.ALLAY_SPAWN_EGG,
            Material.AXOLOTL_SPAWN_EGG,
            Material.BAT_SPAWN_EGG,
            Material.BEE_SPAWN_EGG,
            Material.BLAZE_SPAWN_EGG,
            Material.CAT_SPAWN_EGG,
            Material.BREEZE_SPAWN_EGG,
            Material.CAMEL_SPAWN_EGG,
            Material.CAVE_SPIDER_SPAWN_EGG,
            Material.CHICKEN_SPAWN_EGG,
            Material.COD_SPAWN_EGG,
            Material.COW_SPAWN_EGG,
            Material.CREEPER_SPAWN_EGG,
            Material.DOLPHIN_SPAWN_EGG,
            Material.DONKEY_SPAWN_EGG,
            Material.DROWNED_SPAWN_EGG,
            Material.ELDER_GUARDIAN_SPAWN_EGG,
            Material.ENDER_DRAGON_SPAWN_EGG,
            Material.ENDERMAN_SPAWN_EGG,
            Material.ENDERMITE_SPAWN_EGG,
            Material.EVOKER_SPAWN_EGG,
            Material.FOX_SPAWN_EGG,
            Material.FROG_SPAWN_EGG,
            Material.GHAST_SPAWN_EGG,
            Material.GLOW_SQUID_SPAWN_EGG,
            Material.GOAT_SPAWN_EGG,
            Material.GUARDIAN_SPAWN_EGG,
            Material.HOGLIN_SPAWN_EGG,
            Material.HORSE_SPAWN_EGG,
            Material.HUSK_SPAWN_EGG,
            Material.IRON_GOLEM_SPAWN_EGG,
            Material.LLAMA_SPAWN_EGG,
            Material.MAGMA_CUBE_SPAWN_EGG,
            Material.MOOSHROOM_SPAWN_EGG,
            Material.MULE_SPAWN_EGG,
            Material.OCELOT_SPAWN_EGG,
            Material.PANDA_SPAWN_EGG,
            Material.PARROT_SPAWN_EGG,
            Material.PHANTOM_SPAWN_EGG,
            Material.PIG_SPAWN_EGG,
            Material.PIGLIN_BRUTE_SPAWN_EGG,
            Material.PIGLIN_SPAWN_EGG,
            Material.PILLAGER_SPAWN_EGG,
            Material.POLAR_BEAR_SPAWN_EGG,
            Material.PUFFERFISH_SPAWN_EGG,
            Material.RABBIT_SPAWN_EGG,
            Material.RAVAGER_SPAWN_EGG,
            Material.SALMON_SPAWN_EGG,
            Material.SHEEP_SPAWN_EGG,
            Material.SHULKER_SPAWN_EGG,
            Material.SILVERFISH_SPAWN_EGG,
            Material.SKELETON_HORSE_SPAWN_EGG,
            Material.SKELETON_SPAWN_EGG,
            Material.SLIME_SPAWN_EGG,
            Material.SNIFFER_SPAWN_EGG,
            Material.SNOW_GOLEM_SPAWN_EGG,
            Material.SPIDER_SPAWN_EGG,
            Material.SQUID_SPAWN_EGG,
            Material.STRAY_SPAWN_EGG,
            Material.STRIDER_SPAWN_EGG,
            Material.TADPOLE_SPAWN_EGG,
            Material.TRADER_LLAMA_SPAWN_EGG,
            Material.TROPICAL_FISH_SPAWN_EGG,
            Material.TURTLE_SPAWN_EGG,
            Material.VEX_SPAWN_EGG,
            Material.VILLAGER_SPAWN_EGG,
            Material.VINDICATOR_SPAWN_EGG,
            Material.WANDERING_TRADER_SPAWN_EGG,
            Material.WARDEN_SPAWN_EGG,
            Material.WITCH_SPAWN_EGG,
            Material.WITHER_SKELETON_SPAWN_EGG,
            Material.WITHER_SPAWN_EGG,
            Material.WOLF_SPAWN_EGG,
            Material.ZOGLIN_SPAWN_EGG,
            Material.ZOMBIE_HORSE_SPAWN_EGG,
            Material.ZOMBIE_SPAWN_EGG,
            Material.ZOMBIE_VILLAGER_SPAWN_EGG,
            Material.ZOMBIFIED_PIGLIN_SPAWN_EGG,
            // Bloki, które mogą być postawione
            Material.COBBLESTONE,
            Material.STONE,
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.SAND,
            Material.GRAVEL,
            Material.OBSIDIAN,
            Material.PUMPKIN,
            Material.MELON,
            Material.CARVED_PUMPKIN,
            Material.HAY_BLOCK,
            Material.FARMLAND,
            // Pociski
            Material.ARROW,
            Material.SPECTRAL_ARROW,
            Material.TIPPED_ARROW,
            // Inne
            Material.FLINT_AND_STEEL,
            Material.SHEARS,
            Material.ARMOR_STAND,
            Material.ITEM_FRAME,
            Material.GLOW_ITEM_FRAME,
            Material.PAINTING
    ));

    // Bloki, które mogą być używane razem z dyspenserami
    private final Set<Material> dispenserBlocks = new HashSet<>(Arrays.asList(
            Material.DISPENSER,
            Material.DROPPER
    ));

    public DispenserProtectionListener(Main plugin, PlotManager plotManager, RedstoneTrackingManager redstoneTracker) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.redstoneTracker = redstoneTracker;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenserBlock = event.getBlock();
        ItemStack item = event.getItem();

        // Sprawdź, czy blok to dyspenser/dropper
        if (!dispenserBlocks.contains(dispenserBlock.getType())) {
            return;
        }

        // Znajdź dokładną lokalizację docelową na podstawie kierunku dyspensera
        Location dispenserLoc = dispenserBlock.getLocation();
        Location targetLoc = getTargetLocation(dispenserBlock, item.getType());

        // Sprawdź, czy przedmiot jest potencjalnie niebezpieczny
        if (dangerousItems.contains(item.getType()) || isBlockItem(item.getType())) {
            boolean dispenserInPlot = plotManager.isPlot(dispenserLoc);
            boolean targetInPlot = plotManager.isPlot(targetLoc);

            // Jeśli cel jest na działce kogoś
            if (targetInPlot) {
                UUID targetOwner = plotManager.getPlotOwner(targetLoc);

                // Jeśli dyspenser jest również na działce
                if (dispenserInPlot) {
                    UUID dispenserOwner = plotManager.getPlotOwner(dispenserLoc);

                    // Jeśli to różni właściciele, blokuj
                    if (dispenserOwner != null && targetOwner != null && !dispenserOwner.equals(targetOwner)) {
                        event.setCancelled(true);
                        return;
                    }
                }
                // Jeśli dyspenser jest poza działką
                else {
                    // Domyślnie zakładamy, że należy zablokować
                    boolean shouldAllow = false;

                    // Znajdź właściciela działki wśród online graczy
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (player.getUniqueId().equals(targetOwner)) {
                            // Sprawdź, czy właściciel był ostatnim, który aktywował ten dyspenser
                            if (activatorTracker.wasLastActivator(player, dispenserLoc)) {
                                shouldAllow = true;
                                break;
                            }
                        }
                    }

                    // Jeśli nie powinniśmy pozwolić, anuluj zdarzenie
                    if (!shouldAllow) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Sprawdza, czy przedmiot to blok, który może być postawiony
     */
    private boolean isBlockItem(Material material) {
        return material.isBlock() && material.isSolid();
    }

    /**
     * Sprawdza, czy działanie może być wykonane na podstawie właścicieli działek
     */
    private boolean canAffectTarget(Location source, Location target) {
        boolean sourceInPlot = plotManager.isPlot(source);
        boolean targetInPlot = plotManager.isPlot(target);

        // Jeśli oba są poza działkami, pozwól
        if (!sourceInPlot && !targetInPlot) {
            return true;
        }

        // Jeśli tylko jeden jest na działce, zablokuj
        if (sourceInPlot != targetInPlot) {
            return false;
        }

        // Jeśli oba są na działkach, sprawdź właściciela
        return plotManager.getPlotOwner(source).equals(plotManager.getPlotOwner(target));
    }

    /**
     * Uzyskuje dokładną lokalizację docelową na podstawie kierunku dyspensera
     */
    private Location getTargetLocation(Block dispenserBlock, Material itemType) {
        // Pobierz kierunek dyspensera
        if (!(dispenserBlock.getBlockData() instanceof Directional)) {
            // Fallback jeśli nie można uzyskać kierunku
            return dispenserBlock.getLocation().add(0, 0, 1);
        }

        Directional directional = (Directional) dispenserBlock.getBlockData();
        BlockFace face = directional.getFacing();

        // Oblicz wektor kierunku
        Vector direction = new Vector(face.getModX(), face.getModY(), face.getModZ());

        // Specjalne przypadki dla niektórych przedmiotów
        int distance = 1;

        // Kubełki i bloki są umieszczane bezpośrednio przed dyspenserami
        if (itemType.toString().endsWith("_BUCKET") || itemType.isBlock()) {
            distance = 1;
        }
        // Strzały i pociski lecą dalej
        else if (itemType == Material.ARROW || itemType == Material.SPECTRAL_ARROW ||
                itemType == Material.TIPPED_ARROW || itemType == Material.FIRE_CHARGE) {
            distance = 2; // Uwaga: pociski mogą lecieć dalej, ale sprawdzamy tylko 2 bloki
        }

        // Oblicz docelową lokalizację
        return dispenserBlock.getLocation().add(direction.multiply(distance));
    }

    /**
     * Metoda do logowania zablokowania dyspensera (obecnie wyłączona)
     */
    private void logDispenseBlock(ItemStack item, Location dispenserLoc, Location targetLoc, String reason) {
        // Logowanie zostało wyłączone
        // Można włączyć ponownie odkomentowując poniższy kod
        /*
        plugin.getLogger().warning(
            ChatColor.RED + "Zablokowano dyspenserowanie " + item.getType() +
            " z dyspensera w [" + formatLocation(dispenserLoc) +
            "] do [" + formatLocation(targetLoc) +
            "] - przyczyna: " + reason
        );
        */
    }

    /**
     * Formatuje lokalizację do wyświetlenia w logach
     */
    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + ", " +
                loc.getBlockX() + ", " +
                loc.getBlockY() + ", " +
                loc.getBlockZ();
    }
}