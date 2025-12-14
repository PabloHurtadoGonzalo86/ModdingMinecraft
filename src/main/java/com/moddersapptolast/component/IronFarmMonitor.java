package com.moddersapptolast.component;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Iron Farm Monitor - Real-time server-side golem spawn tracker.
 * Compatible with Polymer - all messages sent only to the player.
 * 
 * Uses Fabric API events (verified from official fabric-lifecycle-events-v1):
 * - ServerEntityEvents.ENTITY_LOAD: Detects when Iron Golems spawn
 * - ServerTickEvents.END_SERVER_TICK: Updates action bar display
 * 
 * Detection criteria for villager-spawned golems:
 * - IronGolem.isPlayerCreated() == false (not built by player)
 * - entity.tickCount <= 1 (newly spawned, not loaded from chunk)
 * - Villagers present in the monitoring area
 * 
 * Based on official Fabric API and Minecraft 1.21.10 mechanics.
 */
public class IronFarmMonitor {
    
    // Store monitoring data per player (by UUID)
    private static final Map<UUID, PlayerMonitorData> playerData = new HashMap<>();
    
    /**
     * Initializes the Iron Farm Monitor event listeners.
     * Called from main mod initializer.
     */
    public static void initialize() {
        // Register entity load event to detect golem spawns
        // Based on: ServerEntityEvents.ENTITY_LOAD from fabric-lifecycle-events-v1
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof IronGolem golem) {
                onGolemLoaded(golem, world);
            }
        });
        
        // Register tick event for action bar updates
        // Based on: ServerTickEvents.END_SERVER_TICK from fabric-lifecycle-events-v1
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Update every 10 ticks (0.5 seconds) for performance
            if (server.getTickCount() % 10 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    updatePlayerDisplay(player);
                }
            }
        });
    }
    
    /**
     * Called when an Iron Golem is loaded into the world.
     * Filters to only count golems spawned by villagers (not player-built or chunk-loaded).
     * 
     * Detection logic:
     * 1. isPlayerCreated() == false: Golem was NOT built by a player
     * 2. tickCount <= 1: Entity was just created (not loaded from existing chunk)
     * 3. Villagers nearby: Confirms this is likely a villager-spawned golem
     */
    private static void onGolemLoaded(IronGolem golem, ServerLevel world) {
        // Filter 1: Skip player-created golems (built with iron blocks + pumpkin)
        if (golem.isPlayerCreated()) {
            return;
        }
        
        // Filter 2: Skip golems loaded from chunks (tickCount > 1 means already existed)
        // tickCount is 0 on first tick, 1 on second tick after spawn
        if (golem.tickCount > 1) {
            return;
        }
        
        BlockPos golemPos = golem.blockPosition();
        
        // Filter 3: Verify villagers are nearby (within 17 blocks - spawn range)
        AABB villagerCheckArea = new AABB(
            golemPos.getX() - 17, golemPos.getY() - 13, golemPos.getZ() - 17,
            golemPos.getX() + 17, golemPos.getY() + 13, golemPos.getZ() + 17
        );
        
        List<Villager> nearbyVillagers = world.getEntitiesOfClass(Villager.class, villagerCheckArea);
        if (nearbyVillagers.isEmpty()) {
            return;
        }
        
        // This is a villager-spawned golem! Notify all monitoring players in range
        for (Map.Entry<UUID, PlayerMonitorData> entry : playerData.entrySet()) {
            PlayerMonitorData data = entry.getValue();
            
            if (!data.isMonitoring()) {
                continue;
            }
            
            AABB monitorArea = data.getMonitoringArea();
            if (monitorArea != null && monitorArea.contains(golemPos.getX(), golemPos.getY(), golemPos.getZ())) {
                // Record the spawn with real timestamp
                data.recordGolemSpawn();
                
                // Find player and send notification (only to the player, not to server console)
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    // Send chat notification for the spawn event - only to this player
                    String spawnMsg = String.format(
                        "§a⚙ §fGolem #%d spawneado! §7(%.1fs desde el anterior)",
                        data.getGolemCount(),
                        data.getSecondsSinceLastSpawn()
                    );
                    player.sendSystemMessage(Component.literal(spawnMsg), false);
                }
            }
        }
    }
    
    /**
     * Updates the action bar display for a player if they are monitoring.
     * Shows real-time stats based on actual spawn detections.
     * Uses direct packet sending for Polymer compatibility.
     */
    private static void updatePlayerDisplay(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        
        if (data == null || !data.isMonitoring()) {
            return;
        }
        
        // Update center position if following player
        if (data.isFollowPlayer()) {
            data.setCenterPos(player.blockPosition());
        }
        
        // Build display message with real-time data
        StringBuilder message = new StringBuilder();
        message.append("§6⚙ §eGolems: §f").append(data.getGolemCount());
        
        // Show rate based on actual spawns
        double rate = data.getGolemsPerMinute();
        if (rate > 0) {
            message.append(" §7| §eRate: §f").append(String.format("%.1f", rate)).append("/min");
        }
        
        // Show time since last spawn (real, not calculated)
        double secondsSinceLast = data.getSecondsSinceLastSpawn();
        if (data.getGolemCount() > 0) {
            message.append(" §7| §eÚltimo: §f").append(String.format("%.0f", secondsSinceLast)).append("s");
        } else {
            message.append(" §7| §eEsperando spawn...");
        }
        
        // Show average interval if we have enough data
        double avgInterval = data.getAverageSpawnInterval();
        if (avgInterval > 0) {
            message.append(" §7| §ePromedio: §f").append(String.format("%.1f", avgInterval)).append("s");
        }
        
        // Send action bar packet directly to player (Polymer compatible)
        Component text = Component.literal(message.toString());
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
    
    // ============ Public API for Commands ============
    
    /**
     * Starts monitoring for a player with a fixed position.
     * Automatically analyzes the iron farm structure.
     * 
     * @return FarmAnalysis with information about the detected farm structure
     */
    public static IronFarmAnalyzer.FarmAnalysis startMonitoring(ServerPlayer player, int radius) {
        PlayerMonitorData data = getOrCreateData(player);
        data.setMonitoring(true);
        data.setFollowPlayer(false);
        data.setCenterPos(player.blockPosition());
        data.setRadius(radius);
        data.setStartTime(System.currentTimeMillis());
        data.resetStats();
        
        // Analyze the farm structure
        IronFarmAnalyzer.FarmAnalysis analysis = IronFarmAnalyzer.analyzeFarm(player, radius);
        data.setLastAnalysis(analysis);
        
        return analysis;
    }
    
    /**
     * Starts monitoring for a player that follows their position.
     * Automatically analyzes the iron farm structure.
     * 
     * @return FarmAnalysis with information about the detected farm structure
     */
    public static IronFarmAnalyzer.FarmAnalysis startMonitoringFollow(ServerPlayer player, int radius) {
        PlayerMonitorData data = getOrCreateData(player);
        data.setMonitoring(true);
        data.setFollowPlayer(true);
        data.setCenterPos(player.blockPosition());
        data.setRadius(radius);
        data.setStartTime(System.currentTimeMillis());
        data.resetStats();
        
        // Analyze the farm structure
        IronFarmAnalyzer.FarmAnalysis analysis = IronFarmAnalyzer.analyzeFarm(player, radius);
        data.setLastAnalysis(analysis);
        
        return analysis;
    }
    
    /**
     * Re-analyzes the farm structure for a player.
     * Useful when the player wants to refresh the farm status.
     */
    public static IronFarmAnalyzer.FarmAnalysis reanalyzeFarm(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        if (data == null || !data.isMonitoring()) {
            return null;
        }
        
        // Update center if following player
        if (data.isFollowPlayer()) {
            data.setCenterPos(player.blockPosition());
        }
        
        IronFarmAnalyzer.FarmAnalysis analysis = IronFarmAnalyzer.analyzeFarm(player, data.getRadius());
        data.setLastAnalysis(analysis);
        
        return analysis;
    }
    
    /**
     * Stops monitoring for a player.
     */
    public static void stopMonitoring(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        if (data != null) {
            data.stopMonitoring();
        }
    }
    
    /**
     * Resets statistics for a player.
     */
    public static void resetStats(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        if (data != null) {
            data.resetStats();
        }
    }
    
    /**
     * Gets the monitoring data for a player (for stats display).
     */
    public static PlayerMonitorData getData(ServerPlayer player) {
        return playerData.get(player.getUUID());
    }
    
    /**
     * Checks if a player is currently monitoring.
     */
    public static boolean isMonitoring(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        return data != null && data.isMonitoring();
    }
    
    /**
     * Gets or creates monitoring data for a player.
     */
    private static PlayerMonitorData getOrCreateData(ServerPlayer player) {
        return playerData.computeIfAbsent(player.getUUID(), k -> new PlayerMonitorData());
    }
    
    /**
     * Cleans up data for a player (call on disconnect).
     */
    public static void cleanup(UUID playerUUID) {
        playerData.remove(playerUUID);
    }
}
