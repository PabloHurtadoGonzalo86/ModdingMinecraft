package com.moddersapptolast.component;


import com.moddersapptolast.VillagersCatch;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class ModComponents {

    public static final DataComponentType<CompoundTag> CAPTURED_ENTITY = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(VillagersCatch.MOD_ID, "captured_entity"),
            DataComponentType.<CompoundTag>builder().persistent(CompoundTag.CODEC).build()
    );



    public static void initialize() {
        VillagersCatch.LOGGER.info("Registering {} components", VillagersCatch.MOD_ID);
        // Technically this method can stay empty, but some developers like to notify
        // the console, that certain parts of the mod have been successfully initialized
    }
}