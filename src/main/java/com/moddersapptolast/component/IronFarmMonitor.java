package com.moddersapptolast.component;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Iron Farm Monitor - Server-side golem spawn tracker.
 * 
 * Uses Fabric API events (verified from official fabric-lifecycle-events-v1):
 * - ServerEntityEvents.ENTITY_LOAD: Detects when Iron Golems spawn
 * - ServerTickEvents.END_SERVER_TICK: Updates action bar display
 * 
 * Based on official Fabric test code: ServerEntityLifecycleTests.java
 */
public class IronFarmMonitor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("IronFarmMonitor");
    
    // Store monitoring data per player (by UUID)
    private static final Map<UUID, PlayerMonitorData> playerData = new HashMap<>();
    
    /**
     * Initializes the Iron Farm Monitor event listeners.
     * Called from main mod initializer.
     */
    public static void initialize() {
        LOGGER.info("Initializing Iron Farm Monitor...");
        
        // Register entity load event to detect golem spawns
        // Based on: ServerEntityEvents.ENTITY_LOAD from fabric-lifecycle-events-v1
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof IronGolem) {
                onGolemSpawn(entity, world);
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
        
        LOGGER.info("Iron Farm Monitor initialized!");
    }
    
    /**
     * Called when an Iron Golem is loaded into the world.
     * Checks if the golem spawned within any player's monitoring area.
     */
    private static void onGolemSpawn(Entity entity, ServerLevel world) {
        BlockPos golemPos = entity.blockPosition();
        
        for (Map.Entry<UUID, PlayerMonitorData> entry : playerData.entrySet()) {
            PlayerMonitorData data = entry.getValue();
            
            if (!data.isMonitoring()) {
                continue;
            }
            
            AABB area = data.getMonitoringArea();
            if (area != null && area.contains(golemPos.getX(), golemPos.getY(), golemPos.getZ())) {
                data.incrementGolemCount();
                
                // Find player and notify
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    LOGGER.info("Iron Golem spawned in {}'s monitoring area. Total: {}", 
                        player.getName().getString(), data.getGolemCount());
                }
            }
        }
    }
    
    /**
     * Updates the action bar display for a player if they are monitoring.
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
        
        // Build display message
        StringBuilder message = new StringBuilder();
        message.append("§6⚙ §eGolems: §f").append(data.getGolemCount());
        
        // Show rate if we have data
        double rate = data.getGolemsPerMinute();
        if (rate > 0) {
            message.append(" §7| §eRate: §f").append(String.format("%.1f", rate)).append("/min");
        }
        
        // Show countdown timer if enabled
        if (data.isTimerEnabled()) {
            int seconds = data.getSecondsUntilNextSpawn();
            if (seconds >= 0) {
                message.append(" §7| §ePróximo: §f~").append(seconds).append("s");
            }
        }
        
        // Send action bar packet
        Component text = Component.literal(message.toString());
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }
    
    // ============ Public API for Commands ============
    
    /**
     * Starts monitoring for a player with a fixed position.
     */
    public static void startMonitoring(ServerPlayer player, int radius) {
        PlayerMonitorData data = getOrCreateData(player);
        data.setMonitoring(true);
        data.setFollowPlayer(false);
        data.setCenterPos(player.blockPosition());
        data.setRadius(radius);
        data.setStartTime(System.currentTimeMillis());
        data.resetStats();
        
        LOGGER.info("Started monitoring for {} at {} with radius {}", 
            player.getName().getString(), data.getCenterPos(), radius);
    }
    
    /**
     * Starts monitoring for a player that follows their position.
     */
    public static void startMonitoringFollow(ServerPlayer player, int radius) {
        PlayerMonitorData data = getOrCreateData(player);
        data.setMonitoring(true);
        data.setFollowPlayer(true);
        data.setCenterPos(player.blockPosition());
        data.setRadius(radius);
        data.setStartTime(System.currentTimeMillis());
        data.resetStats();
        
        LOGGER.info("Started follow monitoring for {} with radius {}", 
            player.getName().getString(), radius);
    }
    
    /**
     * Stops monitoring for a player.
     */
    public static void stopMonitoring(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        if (data != null) {
            data.stopMonitoring();
            LOGGER.info("Stopped monitoring for {}", player.getName().getString());
        }
    }
    
    /**
     * Enables the countdown timer with expected golems per hour.
     */
    public static void enableTimer(ServerPlayer player, int golemsPerHour) {
        PlayerMonitorData data = getOrCreateData(player);
        data.setTimerEnabled(true);
        data.setGolemsPerHour(golemsPerHour);
        
        LOGGER.info("Enabled timer for {} with {} golems/hour", 
            player.getName().getString(), golemsPerHour);
    }
    
    /**
     * Disables the countdown timer.
     */
    public static void disableTimer(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        if (data != null) {
            data.setTimerEnabled(false);
        }
    }
    
    /**
     * Resets statistics for a player.
     */
    public static void resetStats(ServerPlayer player) {
        PlayerMonitorData data = playerData.get(player.getUUID());
        if (data != null) {
            data.resetStats();
            LOGGER.info("Reset stats for {}", player.getName().getString());
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
