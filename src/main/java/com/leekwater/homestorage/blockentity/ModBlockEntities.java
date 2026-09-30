package com.leekwater.homestorage.blockentity;

import com.leekwater.homestorage.HomeStorage;
import com.leekwater.homestorage.block.ModBlocks;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<InfinityAccessChestBlockEntity> INFINITY_ACCESS_CHEST = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            HomeStorage.id("infinity_access_chest"),
            FabricBlockEntityTypeBuilder.create(InfinityAccessChestBlockEntity::new, ModBlocks.INFINITY_ACCESS_CHEST).build());

    private ModBlockEntities() {}

    /** Forces this class to load, which registers the block entity types. Must run after ModBlocks. */
    public static void init() {}
}
