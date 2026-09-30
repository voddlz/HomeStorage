package com.leekwater.homestorage.block;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.leekwater.homestorage.blockentity.InfinityAccessChestBlockEntity;
import com.leekwater.homestorage.blockentity.ModBlockEntities;
import com.leekwater.homestorage.permissions.StoragePermissions;
import com.leekwater.homestorage.screen.InfinityStorageMenu;
import com.leekwater.homestorage.storage.InfinityStorage;
import com.leekwater.homestorage.storage.StorageHome;
import com.leekwater.homestorage.storage.StorageManager;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class InfinityAccessChestBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Same footprint as a vanilla chest: 14 wide, 14 tall. */
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 14.0);

    public InfinityAccessChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Same server-only check as the Home Chest: the client can't know where Homes are.
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            Optional<StorageHome> home = StorageManager.get(serverLevel.getServer())
                    .findContaining(serverLevel.dimension(), context.getClickedPos());
            Player player = context.getPlayer();
            String denial = null;
            if (home.isEmpty()) {
                denial = "message.homestorage.access_outside_home";
            } else if (!home.get().active()) {
                denial = "message.homestorage.access_home_inactive";
            } else if (player == null || !StoragePermissions.canUse(home.get(), player)) {
                denial = "message.homestorage.no_access"; // only the owner and members may add chests to a Home
            }
            if (denial != null) {
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable(denial, home.map(StorageHome::ownerName).orElse("")));
                }
                return null;
            }
        }
        // The lid faces the player, like a vanilla chest.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfinityAccessChestBlockEntity(pos, state);
    }

    /** Only the client ticks: the tick just advances the lid animation. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntities.INFINITY_ACCESS_CHEST, InfinityAccessChestBlockEntity::lidAnimateTick)
                : null;
    }

    /** The open counter schedules this every few ticks while the chest is open, to catch players who vanished. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof InfinityAccessChestBlockEntity chest) {
            chest.recheckOpen();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS; // the client just plays the swing; the server does the real work
        }

        // The Home is resolved from the position each time, so it can never go stale.
        StorageManager manager = StorageManager.get(serverLevel.getServer());
        Optional<StorageHome> home = manager.findContaining(serverLevel.dimension(), pos);
        if (home.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.homestorage.access_no_home"));
            return InteractionResult.SUCCESS_SERVER;
        }

        UUID homeId = home.get().id();
        if (!home.get().active()) {
            // The storage is safe; it just can't be reached until the Home Chest is back.
            player.sendOverlayMessage(Component.translatable("message.homestorage.access_home_inactive"));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (!StoragePermissions.canUse(home.get(), player)) {
            player.sendOverlayMessage(Component.translatable("message.homestorage.no_access", home.get().ownerName()));
            return InteractionResult.SUCCESS_SERVER;
        }

        InfinityStorage storage = manager.storageFor(homeId);
        InfinityAccessChestBlockEntity chest = level.getBlockEntity(pos) instanceof InfinityAccessChestBlockEntity be ? be : null;

        // Asked every tick by the open menu: it closes itself if the Home is switched off or the player is removed.
        BooleanSupplier stillAllowed = () -> manager.find(homeId)
                .map(h -> h.active() && StoragePermissions.canUse(h, player))
                .orElse(false);

        player.openMenu(new StorageMenuProvider(pos, storage, chest, stillAllowed));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Creates the menu on the server and tells the client which chest it belongs to. */
    private record StorageMenuProvider(BlockPos chestPos, InfinityStorage storage, @Nullable InfinityAccessChestBlockEntity chest,
                                       BooleanSupplier homeActive)
            implements ExtendedMenuProvider<BlockPos> {
        @Override
        public BlockPos getScreenOpeningData(ServerPlayer player) {
            return chestPos;
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("container.homestorage.infinity_storage");
        }

        @Override
        public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
            return new InfinityStorageMenu(containerId, inventory, chestPos, storage, chest, homeActive);
        }
    }
}
