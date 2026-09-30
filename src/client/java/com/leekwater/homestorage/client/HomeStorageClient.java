package com.leekwater.homestorage.client;

import com.leekwater.homestorage.blockentity.ModBlockEntities;
import com.leekwater.homestorage.network.HomeDeletedPayload;
import com.leekwater.homestorage.network.HomeMembersPayload;
import com.leekwater.homestorage.network.StorageContentsPayload;
import com.leekwater.homestorage.screen.InfinityStorageMenu;
import com.leekwater.homestorage.screen.ModMenus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class HomeStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Tells the client which screen to open for a given menu type.
		MenuScreens.register(ModMenus.INFINITY_STORAGE, InfinityStorageScreen::new);

		// The server sends the member list when the owner right-clicks the Home Chest, and after every change.
		ClientPlayNetworking.registerGlobalReceiver(HomeMembersPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof HomeMembersScreen open && open.homeId().equals(payload.homeId())) {
				open.update(payload);
			} else {
				context.client().gui.setScreen(new HomeMembersScreen(payload));
			}
		});

		// The Home was deleted (by the owner's button): close the members screen if it is showing that Home.
		ClientPlayNetworking.registerGlobalReceiver(HomeDeletedPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof HomeMembersScreen open && open.homeId().equals(payload.homeId())) {
				open.onClose();
			}
		});

		// The chest is drawn by a block entity renderer (its lid moves), not by a normal block model.
		BlockEntityRenderers.register(ModBlockEntities.INFINITY_ACCESS_CHEST, AccessChestRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(StorageContentsPayload.TYPE, (payload, context) -> {
			// Ignore a packet for a menu that is no longer the open one (closed, or replaced by another).
			if (context.player().containerMenu instanceof InfinityStorageMenu menu
					&& menu.containerId == payload.containerId()) {
				menu.applyContents(payload.page(), payload.totalMatches(), payload.entries());
			}
		});
	}
}