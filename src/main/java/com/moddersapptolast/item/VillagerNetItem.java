package com.moddersapptolast.item;

import com.moddersapptolast.component.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class VillagerNetItem extends Item {

    private static final Logger LOGGER = LoggerFactory.getLogger("VillagerNetItem");

    public VillagerNetItem(Properties settings) {
        super(settings);
    }

    // --- ACCIÓN: SOLTAR (Click derecho en bloque) ---
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();

        // Solo actuamos si la red está LLENA (tiene el componente)
        if (stack.has(ModComponents.CAPTURED_ENTITY)) {
            
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                CompoundTag nbt = stack.get(ModComponents.CAPTURED_ENTITY);

                BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

                EntityType.loadEntityRecursive(nbt, serverLevel, EntitySpawnReason.COMMAND, (entity) -> {
                    entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYRot(), entity.getXRot());
                    serverLevel.addFreshEntity(entity);
                    return entity;
                });

                stack.remove(ModComponents.CAPTURED_ENTITY);
                
                if (context.getPlayer() instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal("Aldeano liberado!"));
                }
            }

            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    // --- VISUAL (Tooltip) ---
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent, Consumer<Component> textConsumer, TooltipFlag type) {
        if (stack.has(ModComponents.CAPTURED_ENTITY)) {
            textConsumer.accept(Component.literal("Contiene un Aldeano").withStyle(ChatFormatting.GREEN));
        } else {
            textConsumer.accept(Component.literal("Vacio").withStyle(ChatFormatting.GRAY));
        }
    }
    
    // --- BRILLO cuando contiene aldeano ---
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModComponents.CAPTURED_ENTITY);
    }

}
