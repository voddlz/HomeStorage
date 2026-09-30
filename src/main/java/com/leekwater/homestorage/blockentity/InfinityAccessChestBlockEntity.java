package com.leekwater.homestorage.blockentity;

import com.leekwater.homestorage.block.ModBlocks;
import com.leekwater.homestorage.screen.InfinityStorageMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds no items (they live in the Home's storage). It exists only so the chest can animate its lid and
 * make sounds, exactly like an ender chest: it counts who has it open and tells clients to open/close.
 */
public class InfinityAccessChestBlockEntity extends BlockEntity implements LidBlockEntity {
    /** Block event id understood by {@link #triggerEvent}; param is the number of players with it open. */
    private static final int EVENT_OPENERS_CHANGED = 1;

    private final ChestLidController lidController = new ChestLidController();

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            playSound(level, pos, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            playSound(level, pos, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
            // Sends the new count to every client tracking this chunk, which drives their lid animation.
            level.blockEvent(pos, ModBlocks.INFINITY_ACCESS_CHEST, EVENT_OPENERS_CHANGED, current);
        }

        @Override
        public boolean isOwnContainer(Player player) {
            // "Has this chest open" = has our storage menu open for this exact chest.
            return player.containerMenu instanceof InfinityStorageMenu menu
                    && menu.getChestPos().equals(InfinityAccessChestBlockEntity.this.getBlockPos());
        }
    };

    public InfinityAccessChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITY_ACCESS_CHEST, pos, state);
    }

    private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS,
                0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    /** Client tick: moves the lid a step toward open or closed. */
    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, InfinityAccessChestBlockEntity chest) {
        chest.lidController.tickLid();
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_OPENERS_CHANGED) {
            lidController.shouldBeOpen(param > 0);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public void startOpen(ContainerUser user) {
        if (!isRemoved() && !user.getLivingEntity().isSpectator()) {
            openersCounter.incrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState(),
                    user.getContainerInteractionRange());
        }
    }

    public void stopOpen(ContainerUser user) {
        if (!isRemoved() && !user.getLivingEntity().isSpectator()) {
            openersCounter.decrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState());
        }
    }

    /** Safety net (called from a scheduled tick): fixes the count if a player vanished without closing. */
    public void recheckOpen() {
        if (!isRemoved()) {
            openersCounter.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
        }
    }

    @Override
    public float getOpenNess(float partialTick) {
        return lidController.getOpenness(partialTick);
    }
}
