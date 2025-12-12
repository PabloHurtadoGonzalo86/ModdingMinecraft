package com.moddersapptolast.component;


import com.moddersapptolast.VillagersCatch;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class ModComponents {

    // Componente que guarda una LISTA de aldeanos (hasta 64)
    public static final DataComponentType<List<CompoundTag>> CAPTURED_VILLAGERS = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(VillagersCatch.MOD_ID, "captured_villagers"),
            DataComponentType.<List<CompoundTag>>builder()
                    .persistent(CompoundTag.CODEC.listOf())
                    .build()
    );

    // Componente que guarda una LISTA de zombies (hasta 64)
    public static final DataComponentType<List<CompoundTag>> CAPTURED_ZOMBIES = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(VillagersCatch.MOD_ID, "captured_zombies"),
            DataComponentType.<List<CompoundTag>>builder()
                    .persistent(CompoundTag.CODEC.listOf())
                    .build()
    );

    public static void initialize() {
        // Registrar componentes con Polymer para excluirlos del registry sync
        PolymerComponent.registerDataComponent(CAPTURED_VILLAGERS, CAPTURED_ZOMBIES);
        VillagersCatch.LOGGER.info("Registering {} components with Polymer", VillagersCatch.MOD_ID);
    }
}