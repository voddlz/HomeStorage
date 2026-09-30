package com.leekwater.homestorage.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** What the renderer needs from the block entity, copied out once per frame (rendering never reads the world directly). */
public class AccessChestRenderState extends BlockEntityRenderState {
    public Direction facing = Direction.SOUTH;
    /** 0 = closed, 1 = fully open. */
    public float open;
}
