package com.leekwater.homestorage.block;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.leekwater.homestorage.HomeStorage;
import com.leekwater.homestorage.permissions.MemberManagement;
import com.leekwater.homestorage.permissions.StoragePermissions;
import com.leekwater.homestorage.storage.StorageHome;
import com.leekwater.homestorage.storage.StorageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class InfinityHomeChestBlock extends Block {
    public InfinityHomeChestBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Returning null cancels the placement. Only the server knows the Homes, so the client skips the check
        // and the server's answer corrects it (the block just doesn't appear).
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            Player player = context.getPlayer();
            UUID placer = player == null ? null : player.getUUID();

            Optional<StorageHome> conflict = StorageManager.get(serverLevel.getServer())
                    .findConflicting(serverLevel.dimension(), context.getClickedPos(), placer);
            if (conflict.isPresent()) {
                if (player != null) {
                    StorageHome blocking = conflict.get();
                    // An inactive Home explains itself: it is reserved, and where its chest used to be.
                    player.sendOverlayMessage(blocking.active()
                            ? Component.translatable("message.homestorage.home_overlap")
                            : Component.translatable("message.homestorage.home_inactive_nearby", blocking.chestPos().toShortString()));
                }
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        // Only the server decides game state; the client also runs this method for the same placement.
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof Player player)) {
            return;
        }

        StorageManager manager = StorageManager.get(serverLevel.getServer());
        String placerName = player.getGameProfile().name();

        // Placing on the spot of the owner's own inactive Home brings that Home (and its items) back.
        Optional<StorageHome> reclaimable = manager.findReclaimable(serverLevel.dimension(), pos, player.getUUID());
        if (reclaimable.isPresent()) {
            manager.setActive(reclaimable.get().id(), true);
            manager.updateOwnerName(reclaimable.get().id(), placerName);
            HomeStorage.LOGGER.info("Reactivated Home {} at {} in {}", reclaimable.get().id(), pos, serverLevel.dimension().identifier());
            player.sendOverlayMessage(Component.translatable("message.homestorage.home_restored"));
            return;
        }

        StorageHome home = manager.createHome(player.getUUID(), placerName, serverLevel.dimension(), pos);
        HomeStorage.LOGGER.info("Created Home {} at {} in {}", home.id(), pos, serverLevel.dimension().identifier());
    }

    /** Right-click: the owner gets the members screen, everyone else is told who owns the Home. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS; // the client just plays the swing; the server does the real work
        }

        Optional<StorageHome> home = StorageManager.get(serverLevel.getServer())
                .findByChest(serverLevel.dimension(), pos)
                .filter(StorageHome::active);
        if (home.isEmpty()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (StoragePermissions.canManage(home.get(), player)) {
            MemberManagement.open(serverPlayer, home.get());
        } else {
            player.sendOverlayMessage(Component.translatable("message.homestorage.not_owner", home.get().ownerName()));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Safety net for the rare ways the Home Chest can still disappear without the Delete Home button (a command
     * like /setblock, another mod). Players can't mine it and explosions can't break it, so this is not the normal path.
     * The Home is switched off, never deleted, so no item can be lost by accident; the owner can reclaim it by
     * placing a Home Chest on the same spot.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        StorageManager.get(level.getServer())
                .deactivateAt(level.dimension(), pos)
                .ifPresent(home -> HomeStorage.LOGGER.info("Deactivated Home {} (Home Chest removed at {})", home.id(), pos));
    }
}
