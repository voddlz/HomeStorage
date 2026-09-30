package com.leekwater.homestorage.screen;

import com.leekwater.homestorage.HomeStorage;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    /**
     * Extended = the server can send extra data when opening it. We send the Access Chest's position,
     * so the client-side menu knows which chest it belongs to.
     */
    public static final MenuType<InfinityStorageMenu> INFINITY_STORAGE = Registry.register(
            BuiltInRegistries.MENU,
            HomeStorage.id("infinity_storage"),
            new ExtendedMenuType<>(InfinityStorageMenu::new, BlockPos.STREAM_CODEC));

    private ModMenus() {}

    /** Forces this class to load, which registers the menu types. */
    public static void init() {}
}
