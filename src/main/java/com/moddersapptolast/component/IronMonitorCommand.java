package com.moddersapptolast.component;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command registration for Iron Farm Monitor.
 * Compatible with Polymer - all messages sent only to the player (not to server console).
 * 
 * Uses Fabric Command API v2 (verified from official fabric-command-api-v2):
 * - CommandRegistrationCallback.EVENT for command registration
 * - Commands.literal() for subcommands
 * - IntegerArgumentType for numeric arguments
 * 
 * Based on official Fabric API and Minecraft 1.21.10 mechanics.
 */
public class IronMonitorCommand {
    
    private static final int DEFAULT_RADIUS = 32;
    
    /**
     * Registers all /ironmonitor commands.
     * Called from main mod initializer.
     */
    public static void register() {
        // Based on CommandRegistrationCallback from fabric-command-api-v2
        CommandRegistrationCallback.EVENT.register(IronMonitorCommand::registerCommands);
    }
    
    /**
     * Registers command structure with the dispatcher.
     * Pattern based on official Fabric CommandTest.java
     */
    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment) {
        
        dispatcher.register(
            Commands.literal("ironmonitor")
                // /ironmonitor start [radius]
                .then(Commands.literal("start")
                    .executes(ctx -> executeStart(ctx, DEFAULT_RADIUS, false))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> executeStart(ctx, 
                            IntegerArgumentType.getInteger(ctx, "radius"), false))))
                
                // /ironmonitor follow [radius]
                .then(Commands.literal("follow")
                    .executes(ctx -> executeStart(ctx, DEFAULT_RADIUS, true))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> executeStart(ctx, 
                            IntegerArgumentType.getInteger(ctx, "radius"), true))))
                
                // /ironmonitor stop
                .then(Commands.literal("stop")
                    .executes(IronMonitorCommand::executeStop))
                
                // /ironmonitor stats
                .then(Commands.literal("stats")
                    .executes(IronMonitorCommand::executeStats))
                
                // /ironmonitor analyze - Re-analyze the farm structure
                .then(Commands.literal("analyze")
                    .executes(IronMonitorCommand::executeAnalyze))
                
                // /ironmonitor reset
                .then(Commands.literal("reset")
                    .executes(IronMonitorCommand::executeReset))
                
                // /ironmonitor help
                .then(Commands.literal("help")
                    .executes(IronMonitorCommand::executeHelp))
                
                // Default: show help
                .executes(IronMonitorCommand::executeHelp)
        );
    }
    
    /**
     * /ironmonitor start [radius] - Start monitoring at current position
     * /ironmonitor follow [radius] - Start monitoring following player
     * 
     * Automatically analyzes the iron farm structure and reports status.
     */
    private static int executeStart(CommandContext<CommandSourceStack> ctx, int radius, boolean follow) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        // Start monitoring and get farm analysis
        IronFarmAnalyzer.FarmAnalysis analysis;
        if (follow) {
            analysis = IronFarmMonitor.startMonitoringFollow(player, radius);
            source.sendSuccess(() -> Component.literal(
                "§a✓ §fMonitoreo iniciado §e(siguiendo tu posición)§f con radio de §6" + radius + " bloques§f."
            ), false);
        } else {
            analysis = IronFarmMonitor.startMonitoring(player, radius);
            source.sendSuccess(() -> Component.literal(
                "§a✓ §fMonitoreo iniciado en §6" + formatPos(player) + " §fcon radio de §6" + radius + " bloques§f."
            ), false);
        }
        
        // Display farm analysis results
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§6═══ Análisis de Granja de Hierro ═══"), false);
        displayFarmAnalysis(source, analysis);
        source.sendSuccess(() -> Component.literal("§6═════════════════════════════════"), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor stop - Stop monitoring
     */
    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        if (!IronFarmMonitor.isMonitoring(player)) {
            source.sendFailure(Component.literal("§cNo estás monitoreando ninguna granja."));
            return 0;
        }
        
        // Get final stats before stopping
        PlayerMonitorData data = IronFarmMonitor.getData(player);
        if (data != null && data.getGolemCount() > 0) {
            source.sendSuccess(() -> Component.literal("§6═══ Resumen Final ═══"), false);
            source.sendSuccess(() -> Component.literal("§eGolems detectados: §f" + data.getGolemCount()), false);
            
            double avgInterval = data.getAverageSpawnInterval();
            if (avgInterval > 0) {
                source.sendSuccess(() -> Component.literal(
                    "§eIntervalo promedio: §f" + String.format("%.1f", avgInterval) + "s"
                ), false);
                source.sendSuccess(() -> Component.literal(
                    "§eProyección: §f" + String.format("%.0f", data.getProjectedGolemsPerHour()) + " golems/hora"
                ), false);
            }
            source.sendSuccess(() -> Component.literal("§6═══════════════════"), false);
        }
        
        IronFarmMonitor.stopMonitoring(player);
        source.sendSuccess(() -> Component.literal("§c✗ §fMonitoreo detenido."), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor stats - Show current statistics (real-time data)
     */
    private static int executeStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        PlayerMonitorData data = IronFarmMonitor.getData(player);
        
        if (data == null || !data.isMonitoring()) {
            source.sendFailure(Component.literal("§cNo estás monitoreando ninguna granja. Usa §e/ironmonitor start"));
            return 0;
        }
        
        // Calculate elapsed time
        long elapsedMs = System.currentTimeMillis() - data.getStartTime();
        long elapsedSec = elapsedMs / 1000;
        long minutes = elapsedSec / 60;
        long seconds = elapsedSec % 60;
        
        source.sendSuccess(() -> Component.literal("§6═══ Iron Farm Monitor - Estadísticas ═══"), false);
        source.sendSuccess(() -> Component.literal("§eGolems detectados: §f" + data.getGolemCount()), false);
        source.sendSuccess(() -> Component.literal("§eTiempo activo: §f" + minutes + "m " + seconds + "s"), false);
        
        // Real-time rate
        source.sendSuccess(() -> Component.literal("§eRate actual: §f" + String.format("%.1f", data.getGolemsPerMinute()) + "/min"), false);
        
        // Spawn interval stats (real data)
        double avgInterval = data.getAverageSpawnInterval();
        if (avgInterval > 0) {
            source.sendSuccess(() -> Component.literal("§eIntervalo promedio: §f" + String.format("%.1f", avgInterval) + "s"), false);
            source.sendSuccess(() -> Component.literal("§eProyección: §f" + String.format("%.0f", data.getProjectedGolemsPerHour()) + " golems/hora"), false);
        }
        
        double lastInterval = data.getLastSpawnInterval();
        if (lastInterval > 0) {
            source.sendSuccess(() -> Component.literal("§eÚltimo intervalo: §f" + String.format("%.1f", lastInterval) + "s"), false);
        }
        
        double timeSinceLast = data.getSecondsSinceLastSpawn();
        if (data.getGolemCount() > 0) {
            source.sendSuccess(() -> Component.literal("§eTiempo desde último spawn: §f" + String.format("%.0f", timeSinceLast) + "s"), false);
        }
        
        // Monitoring area info
        if (data.isFollowPlayer()) {
            source.sendSuccess(() -> Component.literal("§eModo: §fSiguiendo jugador"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§eCentro: §f" + formatBlockPos(data.getCenterPos())), false);
        }
        source.sendSuccess(() -> Component.literal("§eRadio: §f" + data.getRadius() + " bloques"), false);
        
        source.sendSuccess(() -> Component.literal("§6═══════════════════════════════════════"), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor analyze - Re-analyze the farm structure
     */
    private static int executeAnalyze(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        if (!IronFarmMonitor.isMonitoring(player)) {
            source.sendFailure(Component.literal("§cNo estás monitoreando ninguna granja. Usa §e/ironmonitor start"));
            return 0;
        }
        
        // Re-analyze the farm
        IronFarmAnalyzer.FarmAnalysis analysis = IronFarmMonitor.reanalyzeFarm(player);
        
        if (analysis == null) {
            source.sendFailure(Component.literal("§cError al analizar la granja."));
            return 0;
        }
        
        source.sendSuccess(() -> Component.literal("§6═══ Análisis de Granja de Hierro ═══"), false);
        displayFarmAnalysis(source, analysis);
        source.sendSuccess(() -> Component.literal("§6═════════════════════════════════"), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor reset - Reset statistics
     */
    private static int executeReset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        if (!IronFarmMonitor.isMonitoring(player)) {
            source.sendFailure(Component.literal("§cNo estás monitoreando ninguna granja."));
            return 0;
        }
        
        IronFarmMonitor.resetStats(player);
        source.sendSuccess(() -> Component.literal("§a✓ §fEstadísticas reiniciadas."), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor help - Show help message
     */
    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("§6═══ Iron Farm Monitor - Ayuda ═══"), false);
        source.sendSuccess(() -> Component.literal("§eDetección en tiempo real de golems spawneados por aldeanos"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor start [radio]"), false);
        source.sendSuccess(() -> Component.literal("  §7Inicia monitoreo + analiza granja"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor follow [radio]"), false);
        source.sendSuccess(() -> Component.literal("  §7Inicia monitoreo siguiendo tu posición"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor stop"), false);
        source.sendSuccess(() -> Component.literal("  §7Detiene el monitoreo y muestra resumen"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor stats"), false);
        source.sendSuccess(() -> Component.literal("  §7Muestra estadísticas en tiempo real"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor analyze"), false);
        source.sendSuccess(() -> Component.literal("  §7Re-analiza la estructura de la granja"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor reset"), false);
        source.sendSuccess(() -> Component.literal("  §7Reinicia las estadísticas"), false);
        source.sendSuccess(() -> Component.literal("§6══════════════════════════════════"), false);
        
        return 1;
    }
    
    /**
     * Displays detailed farm analysis results.
     */
    private static void displayFarmAnalysis(CommandSourceStack source, IronFarmAnalyzer.FarmAnalysis analysis) {
        // Basic counts
        source.sendSuccess(() -> Component.literal(
            "§eAldeanos encontrados: §f" + analysis.totalVillagers
        ), false);
        
        if (analysis.totalVillagers > 0) {
            source.sendSuccess(() -> Component.literal(
                "  §7Con cama: §f" + analysis.villagersWithBeds + 
                " §7| Con trabajo: §f" + analysis.villagersWithJobs
            ), false);
            
            source.sendSuccess(() -> Component.literal(
                "  §7Durmieron recientemente: §f" + analysis.villagersWhoSleptRecently
            ), false);
            
            source.sendSuccess(() -> Component.literal(
                "  §7Listos para spawn: §f" + analysis.villagersReadyToSpawn + 
                " §7(sin golem en cooldown)"
            ), false);
        }
        
        source.sendSuccess(() -> Component.literal(
            "§eGolems existentes en área: §f" + analysis.existingGolems
        ), false);
        
        source.sendSuccess(() -> Component.literal(""), false);
        
        // Status message with validation results
        for (String line : analysis.statusMessage.split("\n")) {
            final String finalLine = line;
            source.sendSuccess(() -> Component.literal(finalLine), false);
        }
        
        // Spawn mode info
        if (analysis.canSpawnByGossip) {
            source.sendSuccess(() -> Component.literal(
                "§aModo de spawn: §fGOSSIP (automático cada ~35s)"
            ), false);
        } else if (analysis.canSpawnByPanic) {
            source.sendSuccess(() -> Component.literal(
                "§eModo de spawn: §fPÁNICO (requiere amenaza cercana)"
            ), false);
        }
    }
    
    // Helper methods
    private static String formatPos(ServerPlayer player) {
        return String.format("(%d, %d, %d)", 
            player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }
    
    private static String formatBlockPos(net.minecraft.core.BlockPos pos) {
        if (pos == null) return "N/A";
        return String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
}
