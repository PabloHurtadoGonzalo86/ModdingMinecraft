package com.moddersapptolast.component;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores monitoring data for each player using the Iron Farm Monitor.
 * Based on Fabric API lifecycle patterns from fabric-lifecycle-events-v1.
 */
public class PlayerMonitorData {
    
    // Monitoring state
    private boolean monitoring = false;
    private boolean followPlayer = false;
    
    // Area configuration
    private BlockPos centerPos = null;
    private int radius = 32;
    
    // Statistics
    private int golemCount = 0;
    private long startTime = 0;
    private final List<Long> spawnTimes = new ArrayList<>();
    
    // Timer configuration
    private boolean timerEnabled = false;
    private int golemsPerHour = 350;
    private long lastSpawnTime = 0;
    
    // Getters and Setters
    public boolean isMonitoring() {
        return monitoring;
    }
    
    public void setMonitoring(boolean monitoring) {
        this.monitoring = monitoring;
    }
    
    public boolean isFollowPlayer() {
        return followPlayer;
    }
    
    public void setFollowPlayer(boolean followPlayer) {
        this.followPlayer = followPlayer;
    }
    
    public BlockPos getCenterPos() {
        return centerPos;
    }
    
    public void setCenterPos(BlockPos centerPos) {
        this.centerPos = centerPos;
    }
    
    public int getRadius() {
        return radius;
    }
    
    public void setRadius(int radius) {
        this.radius = radius;
    }
    
    public int getGolemCount() {
        return golemCount;
    }
    
    public void incrementGolemCount() {
        this.golemCount++;
        this.lastSpawnTime = System.currentTimeMillis();
        this.spawnTimes.add(this.lastSpawnTime);
        
        // Keep only last 100 spawn times for rate calculation
        if (spawnTimes.size() > 100) {
            spawnTimes.remove(0);
        }
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public boolean isTimerEnabled() {
        return timerEnabled;
    }
    
    public void setTimerEnabled(boolean timerEnabled) {
        this.timerEnabled = timerEnabled;
    }
    
    public int getGolemsPerHour() {
        return golemsPerHour;
    }
    
    public void setGolemsPerHour(int golemsPerHour) {
        this.golemsPerHour = golemsPerHour;
    }
    
    public long getLastSpawnTime() {
        return lastSpawnTime;
    }
    
    /**
     * Creates an AABB (Axis-Aligned Bounding Box) for entity detection.
     * Uses Minecraft's native AABB class for area calculations.
     */
    public AABB getMonitoringArea() {
        if (centerPos == null) {
            return null;
        }
        return new AABB(
            centerPos.getX() - radius, centerPos.getY() - radius, centerPos.getZ() - radius,
            centerPos.getX() + radius, centerPos.getY() + radius, centerPos.getZ() + radius
        );
    }
    
    /**
     * Calculates golems per minute based on recent spawn times.
     */
    public double getGolemsPerMinute() {
        if (spawnTimes.size() < 2) {
            return 0.0;
        }
        
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60000;
        
        int countInLastMinute = 0;
        for (Long time : spawnTimes) {
            if (time >= oneMinuteAgo) {
                countInLastMinute++;
            }
        }
        
        return countInLastMinute;
    }
    
    /**
     * Calculates estimated time until next golem spawn based on configured rate.
     * Returns seconds remaining.
     */
    public int getSecondsUntilNextSpawn() {
        if (!timerEnabled || golemsPerHour <= 0) {
            return -1;
        }
        
        // Calculate spawn interval in milliseconds
        double spawnIntervalMs = (3600000.0 / golemsPerHour);
        
        long timeSinceLastSpawn = System.currentTimeMillis() - lastSpawnTime;
        int remainingMs = (int) (spawnIntervalMs - timeSinceLastSpawn);
        
        if (remainingMs < 0) {
            return 0;
        }
        
        return remainingMs / 1000;
    }
    
    /**
     * Resets all statistics.
     */
    public void resetStats() {
        this.golemCount = 0;
        this.startTime = System.currentTimeMillis();
        this.spawnTimes.clear();
        this.lastSpawnTime = 0;
    }
    
    /**
     * Stops monitoring and clears all data.
     */
    public void stopMonitoring() {
        this.monitoring = false;
        this.followPlayer = false;
        this.centerPos = null;
        this.timerEnabled = false;
        resetStats();
    }
}
