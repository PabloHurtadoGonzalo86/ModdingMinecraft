package com.moddersapptolast;

import com.moddersapptolast.component.ModComponents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagersCatch implements ModInitializer {
	public static final String MOD_ID = "villagerscatch";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");

		LOGGER.info("Initialize my item");
		ModItems.initialize();
		LOGGER.info("Finish Initialize.........");

		LOGGER.info("Initialize my Data component");
		ModComponents.initialize();
		LOGGER.info("Finish Initialize My Data Components.........");

		// Registrar evento para capturar aldeanos con prioridad
		registerEvents();
		LOGGER.info("Events registered!");
	}

	private void registerEvents() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			ItemStack stack = player.getItemInHand(hand);

			// Verificar si el jugador tiene la red de aldeanos
			if (stack.is(ModItems.VILLAGER_NET)) {

				// Solo capturar si la red esta vacia
				if (!stack.has(ModComponents.CAPTURED_ENTITY)) {

					// Verificar si es un Aldeano
					if (entity instanceof Villager) {

						// Solo ejecutar en servidor
						if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {

							try (final ProblemReporter.ScopedCollector reporter =
									new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {

								final TagValueOutput output = TagValueOutput.createWithContext(
										reporter, entity.registryAccess());

								entity.saveWithoutId(output);
								CompoundTag nbt = output.buildResult();

								nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());

								stack.set(ModComponents.CAPTURED_ENTITY, nbt);

								entity.discard();

								serverPlayer.sendSystemMessage(Component.literal("Aldeano atrapado!"));

								LOGGER.info("Aldeano capturado exitosamente!");
							}

							return InteractionResult.SUCCESS;
						}

						// En cliente, devolver SUCCESS para cancelar el trading
						return InteractionResult.SUCCESS;
					}
				}
			}

			return InteractionResult.PASS;
		});
	}
}