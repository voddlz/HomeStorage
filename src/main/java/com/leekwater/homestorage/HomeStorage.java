package com.leekwater.homestorage;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leekwater.homestorage.block.ModBlocks;
import com.leekwater.homestorage.command.HomeCommands;
import com.leekwater.homestorage.blockentity.ModBlockEntities;
import com.leekwater.homestorage.network.ModNetworking;
import com.leekwater.homestorage.permissions.BlockProtection;
import com.leekwater.homestorage.screen.ModMenus;
import com.leekwater.homestorage.storage.StorageManager;

public class HomeStorage implements ModInitializer {
	public static final String MOD_ID = "homestorage";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModBlocks.init();
		ModBlockEntities.init(); // after ModBlocks: the block entity type refers to the blocks
		ModMenus.init();
		ModNetworking.init();
		BlockProtection.init();
		HomeCommands.init();

		// List both chests in the Functional Blocks tab of the creative inventory (next to vanilla's chests).
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(ModBlocks.INFINITY_ACCESS_CHEST);
			output.accept(ModBlocks.INFINITY_HOME_CHEST);
		});

		// Loads the saved Homes at startup so a broken save format shows up in the log immediately.
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				LOGGER.info("Loaded {} Home(s)", StorageManager.get(server).all().size()));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
