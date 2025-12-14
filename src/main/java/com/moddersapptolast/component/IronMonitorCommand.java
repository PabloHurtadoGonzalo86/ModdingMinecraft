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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command registration for Iron Farm Monitor.
 * 
 * Uses Fabric Command API v2 (verified from official fabric-command-api-v2):
 * - CommandRegistrationCallback.EVENT for command registration
 * - Commands.literal() for subcommands
 * - IntegerArgumentType for numeric arguments
 * 
 * Based on official Fabric test code: CommandTest.java
 */
public class IronMonitorCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("IronMonitorCommand");
    private static final int DEFAULT_RADIUS = 32;
    
    /**
     * Registers all /ironmonitor commands.
     * Called from main mod initializer.
     */
    public static void register() {
        // Based on CommandRegistrationCallback from fabric-command-api-v2
        CommandRegistrationCallback.EVENT.register(IronMonitorCommand::registerCommands);
        LOGGER.info("Iron Monitor commands registered!");
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
                
                // /ironmonitor timer <golems_per_hour>
                .then(Commands.literal("timer")
                    .then(Commands.argument("golemsPerHour", IntegerArgumentType.integer(1, 10000))
                        .executes(IronMonitorCommand::executeTimer))
                    .then(Commands.literal("off")
                        .executes(IronMonitorCommand::executeTimerOff)))
                
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
     */
    private static int executeStart(CommandContext<CommandSourceStack> ctx, int radius, boolean follow) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        if (follow) {
            IronFarmMonitor.startMonitoringFollow(player, radius);
            source.sendSuccess(() -> Component.literal(
                "§a✓ §fMonitoreo iniciado §e(siguiendo tu posición)§f con radio de §6" + radius + " bloques§f."
            ), false);
        } else {
            IronFarmMonitor.startMonitoring(player, radius);
            source.sendSuccess(() -> Component.literal(
                "§a✓ §fMonitoreo iniciado en §6" + formatPos(player) + " §fcon radio de §6" + radius + " bloques§f."
            ), false);
        }
        
        source.sendSuccess(() -> Component.literal(
            "§7Usa §e/ironmonitor timer <golems/hora>§7 para activar el countdown."
        ), false);
        
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
        
        IronFarmMonitor.stopMonitoring(player);
        source.sendSuccess(() -> Component.literal("§c✗ §fMonitoreo detenido."), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor stats - Show current statistics
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
        
        // Calculate projected golems per hour - must be final for lambda
        final double golemsPerHourFinal;
        if (elapsedMs > 0) {
            golemsPerHourFinal = (data.getGolemCount() * 3600000.0) / elapsedMs;
        } else {
            golemsPerHourFinal = 0;
        }
        
        source.sendSuccess(() -> Component.literal("§6═══ Iron Farm Monitor ═══"), false);
        source.sendSuccess(() -> Component.literal("§eGolems detectados: §f" + data.getGolemCount()), false);
        source.sendSuccess(() -> Component.literal("§eTiempo activo: §f" + minutes + "m " + seconds + "s"), false);
        source.sendSuccess(() -> Component.literal("§eRate actual: §f" + String.format("%.1f", data.getGolemsPerMinute()) + "/min"), false);
        source.sendSuccess(() -> Component.literal("§eProyección: §f" + String.format("%.0f", golemsPerHourFinal) + " golems/hora"), false);
        
        if (data.isFollowPlayer()) {
            source.sendSuccess(() -> Component.literal("§eModo: §fSiguiendo jugador"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§eCentro: §f" + formatBlockPos(data.getCenterPos())), false);
        }
        
        source.sendSuccess(() -> Component.literal("§eRadio: §f" + data.getRadius() + " bloques"), false);
        
        if (data.isTimerEnabled()) {
            source.sendSuccess(() -> Component.literal("§eTimer: §f" + data.getGolemsPerHour() + " golems/hora (~" + 
                String.format("%.1f", 3600.0 / data.getGolemsPerHour()) + "s/golem)"), false);
        }
        
        source.sendSuccess(() -> Component.literal("§6═══════════════════════"), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor timer <golems_per_hour> - Enable countdown timer
     */
    private static int executeTimer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        int golemsPerHour = IntegerArgumentType.getInteger(ctx, "golemsPerHour");
        double secondsPerGolem = 3600.0 / golemsPerHour;
        
        IronFarmMonitor.enableTimer(player, golemsPerHour);
        
        source.sendSuccess(() -> Component.literal(
            "§a✓ §fTimer activado: §6" + golemsPerHour + " golems/hora §f(~§6" + 
            String.format("%.1f", secondsPerGolem) + "s§f por golem)"
        ), false);
        
        return 1;
    }
    
    /**
     * /ironmonitor timer off - Disable countdown timer
     */
    private static int executeTimerOff(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Este comando solo puede ser ejecutado por un jugador."));
            return 0;
        }
        
        IronFarmMonitor.disableTimer(player);
        source.sendSuccess(() -> Component.literal("§c✗ §fTimer desactivado."), false);
        
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
        source.sendSuccess(() -> Component.literal("§e/ironmonitor start [radio]"), false);
        source.sendSuccess(() -> Component.literal("  §7Inicia monitoreo en tu posición actual"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor follow [radio]"), false);
        source.sendSuccess(() -> Component.literal("  §7Inicia monitoreo siguiendo tu posición"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor stop"), false);
        source.sendSuccess(() -> Component.literal("  §7Detiene el monitoreo"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor stats"), false);
        source.sendSuccess(() -> Component.literal("  §7Muestra estadísticas detalladas"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor timer <golems/hora>"), false);
        source.sendSuccess(() -> Component.literal("  §7Activa countdown (ej: 350 = ~10s/golem)"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor timer off"), false);
        source.sendSuccess(() -> Component.literal("  §7Desactiva el countdown"), false);
        source.sendSuccess(() -> Component.literal("§e/ironmonitor reset"), false);
        source.sendSuccess(() -> Component.literal("  §7Reinicia las estadísticas"), false);
        source.sendSuccess(() -> Component.literal("§6══════════════════════════════"), false);
        
        return 1;
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
