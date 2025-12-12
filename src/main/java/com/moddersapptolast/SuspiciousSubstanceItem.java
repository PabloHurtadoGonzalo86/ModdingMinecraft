package com.moddersapptolast;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.Consumer;

public class SuspiciousSubstanceItem extends Item implements PolymerItem {
    public SuspiciousSubstanceItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent, Consumer<Component> textConsumer, TooltipFlag type) {
        // Aquí añadimos la traducción y el color dorado como dice el ejemplo
        textConsumer.accept(Component.translatable("itemTooltip.villagerscatch.suspicious_substance").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        // Los clientes vanilla verán este item como polvo de piedra luminosa
        return Items.GLOWSTONE_DUST;
    }
}
