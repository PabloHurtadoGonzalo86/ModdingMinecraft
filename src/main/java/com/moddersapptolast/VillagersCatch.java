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
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VillagersCatch implements ModInitializer {
	public static final String MOD_ID = "villagerscatch";
	public static final int MAX_ENTITIES = 64;

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

		registerEvents();
		LOGGER.info("Events registered!");
	}

	private void registerEvents() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			ItemStack stack = player.getItemInHand(hand);

			// --- CAPTURA DE ALDEANOS ---
			if (stack.is(ModItems.VILLAGER_NET) && entity instanceof Villager) {
				return captureEntity(player, world, stack, entity, ModComponents.CAPTURED_VILLAGERS, "Aldeano");
			}

			// --- CAPTURA DE ZOMBIES ---
			if (stack.is(ModItems.ZOMBIE_NET) && entity instanceof Zombie) {
				return captureEntity(player, world, stack, entity, ModComponents.CAPTURED_ZOMBIES, "Zombie");
			}

			return InteractionResult.PASS;
		});
	}

	private InteractionResult captureEntity(
			net.minecraft.world.entity.player.Player player,
			net.minecraft.world.level.Level world,
			ItemStack stack,
			net.minecraft.world.entity.Entity entity,
			net.minecraft.core.component.DataComponentType<List<CompoundTag>> componentType,
			String entityName) {

		List<CompoundTag> entities = stack.get(componentType);
		if (entities == null) {
			entities = new ArrayList<>();
		}

		if (entities.size() >= MAX_ENTITIES) {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				sp.sendSystemMessage(Component.literal("La red esta llena! (64/64)"));
			}
			return InteractionResult.FAIL;
		}

		if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			try (final ProblemReporter.ScopedCollector reporter =
					new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {

				final TagValueOutput output = TagValueOutput.createWithContext(
						reporter, entity.registryAccess());

				entity.saveWithoutId(output);
				CompoundTag nbt = output.buildResult();

				nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());

				List<CompoundTag> newList = new ArrayList<>(entities);
				newList.add(nbt);

				stack.set(componentType, newList);

				entity.discard();

				serverPlayer.sendSystemMessage(Component.literal(
						entityName + " atrapado! (" + newList.size() + "/" + MAX_ENTITIES + ")"));

				LOGGER.info("{} capturado! Total: {}", entityName, newList.size());
			}

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.SUCCESS;
	}
}