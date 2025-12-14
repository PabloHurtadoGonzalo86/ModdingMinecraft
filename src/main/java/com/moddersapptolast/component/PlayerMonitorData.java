package com.moddersapptolast.component;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores monitoring data for each player using the Iron Farm Monitor.
 * 
 * Now tracks real spawn times instead of calculated estimates.
 * Based on Fabric API lifecycle patterns from fabric-lifecycle-events-v1.
 */
public class PlayerMonitorData {
    
    // Monitoring state
    private boolean monitoring = false;
    private boolean followPlayer = false;
    
    // Area configuration
    private BlockPos centerPos = null;
    private int radius = 32;
    
    // Real-time statistics (no more theoretical calculations)
    private int golemCount = 0;
    private long startTime = 0;
    private final List<Long> spawnTimes = new ArrayList<>();  // Real spawn timestamps
    private long lastSpawnTime = 0;
    private long previousSpawnTime = 0;  // For calculating interval between spawns
    
    // Farm analysis cache
    private IronFarmAnalyzer.FarmAnalysis lastAnalysis = null;
    
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
    
    /**
     * Records a real golem spawn event with timestamp.
     * Called when a villager-spawned golem is detected in the monitoring area.
     */
    public void recordGolemSpawn() {
        this.golemCount++;
        this.previousSpawnTime = this.lastSpawnTime;
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
    
    public long getLastSpawnTime() {
        return lastSpawnTime;
    }
    
    /**
     * Gets the time in seconds since the last golem spawn.
     * Returns 0 if no golem has spawned yet.
     */
    public double getSecondsSinceLastSpawn() {
        if (lastSpawnTime == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - lastSpawnTime) / 1000.0;
    }
    
    /**
     * Gets the interval in seconds between the last two spawns.
     * Returns 0 if less than 2 golems have spawned.
     */
    public double getLastSpawnInterval() {
        if (previousSpawnTime == 0 || lastSpawnTime == 0) {
            return 0;
        }
        return (lastSpawnTime - previousSpawnTime) / 1000.0;
    }
    
    /**
     * Gets the average spawn interval based on all recorded spawns.
     * Returns 0 if less than 2 golems have spawned.
     */
    public double getAverageSpawnInterval() {
        if (spawnTimes.size() < 2) {
            return 0;
        }
        
        long firstSpawn = spawnTimes.get(0);
        long lastSpawn = spawnTimes.get(spawnTimes.size() - 1);
        long totalTimeMs = lastSpawn - firstSpawn;
        int intervals = spawnTimes.size() - 1;
        
        return (totalTimeMs / 1000.0) / intervals;
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
     * Calculates golems per minute based on recent spawn times (real data).
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
     * Gets the projected golems per hour based on current spawn rate.
     */
    public double getProjectedGolemsPerHour() {
        double avgInterval = getAverageSpawnInterval();
        if (avgInterval <= 0) {
            return 0;
        }
        return 3600.0 / avgInterval;
    }
    
    /**
     * Gets the last farm analysis result.
     */
    public IronFarmAnalyzer.FarmAnalysis getLastAnalysis() {
        return lastAnalysis;
    }
    
    /**
     * Sets the last farm analysis result.
     */
    public void setLastAnalysis(IronFarmAnalyzer.FarmAnalysis analysis) {
        this.lastAnalysis = analysis;
    }
    
    /**
     * Resets all statistics.
     */
    public void resetStats() {
        this.golemCount = 0;
        this.startTime = System.currentTimeMillis();
        this.spawnTimes.clear();
        this.lastSpawnTime = 0;
        this.previousSpawnTime = 0;
    }
    
    /**
     * Stops monitoring and clears all data.
     */
    public void stopMonitoring() {
        this.monitoring = false;
        this.followPlayer = false;
        this.centerPos = null;
        this.lastAnalysis = null;
        resetStats();
    }
}
