package com.moddersapptolast;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class SuspiciousSubstanceItem extends Item {
    public SuspiciousSubstanceItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent, Consumer<Component> textConsumer, TooltipFlag type) {
        // Aquí añadimos la traducción y el color dorado como dice el ejemplo
        textConsumer.accept(Component.translatable("itemTooltip.villagerscatch.suspicious_substance").withStyle(ChatFormatting.GOLD));
    }
}
