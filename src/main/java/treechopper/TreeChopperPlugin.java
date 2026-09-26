package treechopper;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class TreeChopperPlugin extends JavaPlugin implements Listener {

    // Hard limit to prevent the server from freezing if someone chops an infinite jungle canopy
    private final int MAX_BLOCKS = 2048; 

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTreeChop(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        // 1. Must be shifting
        if (!player.isSneaking()) return;

        // 2. Must be holding an axe
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType() == Material.AIR || !tool.getType().name().endsWith("_AXE")) return;

        // 3. Must be breaking a log
        Block startBlock = event.getBlock();
        if (!Tag.LOGS.isTagged(startBlock.getType())) return;

        // Run a 3D scanner (BFS) to find all connected tree blocks
        Set<Block> blocksToBreak = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        
        queue.add(startBlock);
        blocksToBreak.add(startBlock);

        int leafCount = 0;
        int logCount = 0;

        while (!queue.isEmpty() && blocksToBreak.size() < MAX_BLOCKS) {
            Block current = queue.poll();

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        
                        Block adj = current.getRelative(x, y, z);
                        if (!blocksToBreak.contains(adj)) {
                            // Only crawl across logs and leaves
                            if (Tag.LOGS.isTagged(adj.getType()) || Tag.LEAVES.isTagged(adj.getType())) {
                                blocksToBreak.add(adj);
                                queue.add(adj);
                                
                                if (Tag.LEAVES.isTagged(adj.getType())) leafCount++;
                                if (Tag.LOGS.isTagged(adj.getType())) logCount++;
                            }
                        }
                    }
                }
            }
        }

        // 4. SMART CHECK: Must have at least 3 leaves to be considered a real tree
        if (leafCount < 3) return; 

        List<Block> baseLogs = new ArrayList<>();
        Map<Block, Material> saplingReplacements = new HashMap<>();

        for (Block b : blocksToBreak) {
            if (Tag.LOGS.isTagged(b.getType())) {
                Block below = b.getRelative(BlockFace.DOWN);
                // 5. SMART CHECK: Look for logs touching the ground (Dirt, Grass, Podzol, etc.)
                if (Tag.DIRT.isTagged(below.getType()) || below.getType() == Material.GRASS_BLOCK) {
                    baseLogs.add(b);
                    saplingReplacements.put(b, getSaplingType(b.getType()));
                }
            }
        }

        // If no log in the entire scan is touching the ground, it's a player-build. Abort.
        if (baseLogs.isEmpty()) return;

        // Passed all checks! Cancel the vanilla break event so the plugin takes full control.
        event.setCancelled(true);

        // Sort blocks from top to bottom (highest Y to lowest Y) so falling blocks/items look clean
        List<Block> sortedBlocks = new ArrayList<>(blocksToBreak);
        sortedBlocks.sort((b1, b2) -> Integer.compare(b2.getY(), b1.getY()));

        for (Block b : sortedBlocks) {
            boolean isLog = Tag.LOGS.isTagged(b.getType());

            // Break the block naturally so leaves drop apples/saplings, and logs drop themselves
            b.breakNaturally(tool);

            // Apply durability damage to the axe ONLY for logs, just like vanilla
            if (isLog) {
                tool.damage(1, player);
                
                // If the axe breaks mid-chop, stop breaking the rest of the tree
                if (tool.getType() == Material.AIR || tool.getAmount() == 0) {
                    break;
                }
            }
        }

        // Finally, plant the saplings at the exact locations where the trees touched the dirt
        for (Map.Entry<Block, Material> entry : saplingReplacements.entrySet()) {
            Block base = entry.getKey();
            Material sapling = entry.getValue();
            
            if (base.getType() == Material.AIR || base.getType() == Material.CAVE_AIR) {
                base.setType(sapling);
            }
        }
    }

    private Material getSaplingType(Material log) {
        String name = log.name();
        if (name.startsWith("SPRUCE")) return Material.SPRUCE_SAPLING;
        if (name.startsWith("BIRCH")) return Material.BIRCH_SAPLING;
        if (name.startsWith("JUNGLE")) return Material.JUNGLE_SAPLING;
        if (name.startsWith("ACACIA")) return Material.ACACIA_SAPLING;
        if (name.startsWith("DARK_OAK")) return Material.DARK_OAK_SAPLING;
        if (name.startsWith("MANGROVE")) return Material.MANGROVE_PROPAGULE;
        if (name.startsWith("CHERRY")) return Material.CHERRY_SAPLING;
        if (name.startsWith("CRIMSON")) return Material.CRIMSON_FUNGUS;
        if (name.startsWith("WARPED")) return Material.WARPED_FUNGUS;
        return Material.OAK_SAPLING; // Fallback for standard Oak or custom woods
    }
}
