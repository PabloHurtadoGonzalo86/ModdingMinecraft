package com.moddersapptolast.item;

import com.moddersapptolast.VillagersCatch;
import com.moddersapptolast.component.ModComponents;
import eu.pb4.polymer.core.api.item.PolymerItem;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ZombieNetItem extends Item implements PolymerItem {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZombieNetItem");

    public ZombieNetItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();

        List<CompoundTag> entities = stack.get(ModComponents.CAPTURED_ZOMBIES);
        
        if (entities != null && !entities.isEmpty()) {
            
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                
                List<CompoundTag> newList = new ArrayList<>(entities);
                CompoundTag nbt = newList.remove(newList.size() - 1);

                BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

                EntityType.loadEntityRecursive(nbt, serverLevel, EntitySpawnReason.COMMAND, (entity) -> {
                    entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYRot(), entity.getXRot());
                    serverLevel.addFreshEntity(entity);
                    return entity;
                });

                if (newList.isEmpty()) {
                    stack.remove(ModComponents.CAPTURED_ZOMBIES);
                } else {
                    stack.set(ModComponents.CAPTURED_ZOMBIES, newList);
                }
                
                if (context.getPlayer() instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal(
                            "Zombie liberado! (" + newList.size() + "/" + VillagersCatch.MAX_ENTITIES + ")"));
                }
            }

            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent, Consumer<Component> textConsumer, TooltipFlag type) {
        List<CompoundTag> entities = stack.get(ModComponents.CAPTURED_ZOMBIES);
        
        if (entities != null && !entities.isEmpty()) {
            textConsumer.accept(Component.literal(
                    "Contiene " + entities.size() + " Zombie(s)").withStyle(ChatFormatting.RED));
        } else {
            textConsumer.accept(Component.literal("Vacio").withStyle(ChatFormatting.GRAY));
        }
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        List<CompoundTag> entities = stack.get(ModComponents.CAPTURED_ZOMBIES);
        return entities != null && !entities.isEmpty();
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        // Los clientes vanilla verán este item como una caña de pescar
        return Items.FISHING_ROD;
    }

}
