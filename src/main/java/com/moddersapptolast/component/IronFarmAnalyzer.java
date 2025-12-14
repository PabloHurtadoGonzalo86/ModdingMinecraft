package com.moddersapptolast.component;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Analyzes iron farm structures and villager states.
 * Compatible with Polymer - no server console logging.
 * 
 * Iron Golem spawning requirements (Minecraft 1.21.10):
 * - Villagers need to have slept in the last 20 minutes (24000 ticks)
 * - Villagers must not have detected an iron golem in the last 30 seconds (600 ticks)
 * - For GOSSIP spawning: 5+ villagers needed
 * - For PANIC spawning: 3+ villagers needed (when scared by hostile mob)
 * - Spawn area: 17x13x17 box centered on the triggering villager
 * 
 * Based on official Minecraft Wiki mechanics and Mojang mappings for 1.21.10.
 */
public class IronFarmAnalyzer {
    
    // Minecraft mechanics constants
    private static final int TICKS_20_MINUTES = 24000;  // Villagers must have slept within this time
    private static final int TICKS_30_SECONDS = 600;    // Golem detection cooldown
    private static final int MIN_VILLAGERS_GOSSIP = 5;  // Minimum villagers for gossip spawning
    private static final int MIN_VILLAGERS_PANIC = 3;   // Minimum villagers for panic spawning
    private static final int GOLEM_DETECTION_RANGE = 16; // Blocks - villagers check for golems in this range
    
    /**
     * Result of analyzing an iron farm area.
     */
    public static class FarmAnalysis {
        public final int totalVillagers;
        public final int villagersWithBeds;
        public final int villagersWithJobs;
        public final int villagersWhoSleptRecently;
        public final int villagersReadyToSpawn;  // Haven't detected golem recently
        public final int existingGolems;
        public final boolean canSpawnByGossip;
        public final boolean canSpawnByPanic;
        public final boolean isValidFarm;
        public final String statusMessage;
        public final List<VillagerInfo> villagerDetails;
        
        public FarmAnalysis(int totalVillagers, int villagersWithBeds, int villagersWithJobs,
                          int villagersWhoSleptRecently, int villagersReadyToSpawn, int existingGolems,
                          boolean canSpawnByGossip, boolean canSpawnByPanic, boolean isValidFarm,
                          String statusMessage, List<VillagerInfo> villagerDetails) {
            this.totalVillagers = totalVillagers;
            this.villagersWithBeds = villagersWithBeds;
            this.villagersWithJobs = villagersWithJobs;
            this.villagersWhoSleptRecently = villagersWhoSleptRecently;
            this.villagersReadyToSpawn = villagersReadyToSpawn;
            this.existingGolems = existingGolems;
            this.canSpawnByGossip = canSpawnByGossip;
            this.canSpawnByPanic = canSpawnByPanic;
            this.isValidFarm = isValidFarm;
            this.statusMessage = statusMessage;
            this.villagerDetails = villagerDetails;
        }
    }
    
    /**
     * Information about a single villager.
     */
    public static class VillagerInfo {
        public final BlockPos position;
        public final String profession;
        public final boolean hasBed;
        public final boolean hasJobSite;
        public final boolean sleptRecently;
        public final boolean detectedGolemRecently;
        public final boolean canTriggerSpawn;
        
        public VillagerInfo(BlockPos position, String profession, boolean hasBed, boolean hasJobSite,
                          boolean sleptRecently, boolean detectedGolemRecently) {
            this.position = position;
            this.profession = profession;
            this.hasBed = hasBed;
            this.hasJobSite = hasJobSite;
            this.sleptRecently = sleptRecently;
            this.detectedGolemRecently = detectedGolemRecently;
            this.canTriggerSpawn = sleptRecently && !detectedGolemRecently;
        }
    }
    
    /**
     * Analyzes the iron farm structure around a player's position.
     * 
     * @param player The player who initiated the analysis
     * @param radius The radius to search for villagers
     * @return FarmAnalysis containing detailed information about the farm
     */
    public static FarmAnalysis analyzeFarm(ServerPlayer player, int radius) {
        Level level = player.level();
        if (!(level instanceof ServerLevel world)) {
            return createEmptyAnalysis();
        }
        
        BlockPos center = player.blockPosition();
        long currentGameTime = world.getGameTime();
        
        // Create search area
        AABB searchArea = new AABB(
            center.getX() - radius, center.getY() - radius, center.getZ() - radius,
            center.getX() + radius, center.getY() + radius, center.getZ() + radius
        );
        
        // Find all villagers in the area
        List<Villager> villagers = world.getEntitiesOfClass(Villager.class, searchArea);
        
        // Find existing iron golems in the area
        int existingGolems = world.getEntitiesOfClass(
            net.minecraft.world.entity.animal.IronGolem.class, searchArea
        ).size();
        
        // Analyze each villager
        java.util.ArrayList<VillagerInfo> villagerDetails = new java.util.ArrayList<>();
        int villagersWithBeds = 0;
        int villagersWithJobs = 0;
        int villagersWhoSleptRecently = 0;
        int villagersReadyToSpawn = 0;
        
        for (Villager villager : villagers) {
            // Check if villager has a bed
            boolean hasBed = villager.getBrain()
                .getMemory(MemoryModuleType.HOME)
                .isPresent();
            
            // Check if villager has a job site (has a profession other than none/nitwit)
            Holder<VillagerProfession> professionHolder = villager.getVillagerData().profession();
            String professionName = getProfessionName(professionHolder);
            boolean hasJobSite = !professionName.equals("none") && !professionName.equals("nitwit");
            
            // Check if villager slept recently (within last 20 minutes)
            boolean sleptRecently = checkIfSleptRecently(villager, currentGameTime);
            
            // Check if villager detected a golem recently (within last 30 seconds)
            boolean detectedGolemRecently = villager.getBrain()
                .getMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY)
                .isPresent();
            
            if (hasBed) villagersWithBeds++;
            if (hasJobSite) villagersWithJobs++;
            if (sleptRecently) villagersWhoSleptRecently++;
            if (sleptRecently && !detectedGolemRecently) villagersReadyToSpawn++;
            
            villagerDetails.add(new VillagerInfo(
                villager.blockPosition(),
                professionName,
                hasBed,
                hasJobSite,
                sleptRecently,
                detectedGolemRecently
            ));
        }
        
        // Determine if farm can spawn golems
        boolean canSpawnByGossip = villagersReadyToSpawn >= MIN_VILLAGERS_GOSSIP;
        boolean canSpawnByPanic = villagersReadyToSpawn >= MIN_VILLAGERS_PANIC;
        boolean isValidFarm = canSpawnByGossip || canSpawnByPanic;
        
        // Build status message
        String statusMessage = buildStatusMessage(
            villagers.size(), villagersWithBeds, villagersWhoSleptRecently,
            villagersReadyToSpawn, existingGolems, canSpawnByGossip, canSpawnByPanic
        );
        
        return new FarmAnalysis(
            villagers.size(),
            villagersWithBeds,
            villagersWithJobs,
            villagersWhoSleptRecently,
            villagersReadyToSpawn,
            existingGolems,
            canSpawnByGossip,
            canSpawnByPanic,
            isValidFarm,
            statusMessage,
            villagerDetails
        );
    }
    
    /**
     * Gets the profession name from a Holder<VillagerProfession>.
     * Works with Minecraft 1.21.10 Mojang mappings.
     */
    private static String getProfessionName(Holder<VillagerProfession> professionHolder) {
        // Get the registry key name from the holder
        return professionHolder.unwrapKey()
            .map(key -> key.location().getPath())
            .orElse("unknown");
    }
    
    /**
     * Creates an empty analysis result for error cases.
     */
    private static FarmAnalysis createEmptyAnalysis() {
        return new FarmAnalysis(0, 0, 0, 0, 0, 0, false, false, false,
            "§c✗ Error al analizar", new java.util.ArrayList<>());
    }
    
    /**
     * Checks if a villager has slept within the last 20 minutes (24000 ticks).
     * Uses the LAST_SLEPT memory module from the villager's brain.
     */
    private static boolean checkIfSleptRecently(Villager villager, long currentGameTime) {
        Optional<Long> lastSlept = villager.getBrain().getMemory(MemoryModuleType.LAST_SLEPT);
        
        if (lastSlept.isPresent()) {
            long ticksSinceSlept = currentGameTime - lastSlept.get();
            return ticksSinceSlept <= TICKS_20_MINUTES;
        }
        
        // If no sleep memory, villager hasn't slept since loaded
        return false;
    }
    
    /**
     * Builds a human-readable status message for the farm analysis.
     */
    private static String buildStatusMessage(int totalVillagers, int villagersWithBeds,
            int villagersWhoSleptRecently, int villagersReadyToSpawn, int existingGolems,
            boolean canSpawnByGossip, boolean canSpawnByPanic) {
        
        StringBuilder sb = new StringBuilder();
        
        if (totalVillagers == 0) {
            return "§c✗ No hay aldeanos en el área";
        }
        
        if (villagersWithBeds == 0) {
            sb.append("§c⚠ Ningún aldeano tiene cama vinculada\n");
        }
        
        if (villagersWhoSleptRecently == 0) {
            sb.append("§c⚠ Ningún aldeano ha dormido recientemente\n");
        }
        
        if (existingGolems > 0) {
            sb.append("§e⚠ Hay ").append(existingGolems).append(" golem(s) en el área - ");
            sb.append("aldeanos pueden estar en cooldown\n");
        }
        
        if (canSpawnByGossip) {
            sb.append("§a✓ Granja válida para spawn por GOSSIP (").append(villagersReadyToSpawn).append("/5+)\n");
        } else if (canSpawnByPanic) {
            sb.append("§e✓ Granja válida para spawn por PÁNICO (").append(villagersReadyToSpawn).append("/3+)\n");
            sb.append("§7  (Necesita 5+ aldeanos para gossip automático)\n");
        } else {
            sb.append("§c✗ Granja NO válida - necesitas más aldeanos listos\n");
            sb.append("§7  Listos: ").append(villagersReadyToSpawn);
            sb.append(" (mínimo 3 para pánico, 5 para gossip)\n");
        }
        
        return sb.toString().trim();
    }
    
    /**
     * Gets a quick summary suitable for action bar display.
     */
    public static String getQuickSummary(FarmAnalysis analysis) {
        if (analysis.totalVillagers == 0) {
            return "§c⚠ Sin aldeanos";
        }
        
        String status = analysis.isValidFarm ? "§a✓" : "§c✗";
        return String.format("%s §eAldeanos: §f%d §7| §eListos: §f%d §7| §eGolems: §f%d",
            status, analysis.totalVillagers, analysis.villagersReadyToSpawn, analysis.existingGolems);
    }
}
