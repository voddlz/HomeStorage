package com.leekwater.homestorage.block;

import java.util.function.Function;

import com.leekwater.homestorage.HomeStorage;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final Block INFINITY_HOME_CHEST = register(
            "infinity_home_chest",
            InfinityHomeChestBlock::new,
            // The Home Chest can't be mined (hardness -1, like bedrock) or blown up (huge blast resistance): it is
            // removed only by the Delete Home button. IMMOVEABLE: a piston pushing it would also remove it.
            BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).mapColor(MapColor.WOOD)
                    .pushReaction(PushReaction.IMMOVEABLE));

    public static final Block INFINITY_ACCESS_CHEST = register(
            "infinity_access_chest",
            InfinityAccessChestBlock::new,
            // lightLevel: the glowing trim also casts a faint light (0-15) on its surroundings
            BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).mapColor(MapColor.WOOD)
                    .lightLevel(state -> 5));

    private ModBlocks() {}

    private static Block register(String name,
                                  Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, HomeStorage.id(name));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, HomeStorage.id(name));

        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

        BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        item.registerBlocks(Item.BY_BLOCK, item);

        return block;
    }

    /** Forces this class to load, which runs the static fields above and registers the blocks. */
    public static void init() {}
}