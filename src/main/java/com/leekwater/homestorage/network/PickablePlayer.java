package com.leekwater.homestorage.network;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An online player the owner can add to a Home (not the owner, not a member yet). */
public record PickablePlayer(UUID id, String name) {
    public static final StreamCodec<ByteBuf, PickablePlayer> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PickablePlayer::id,
            ByteBufCodecs.PLAYER_NAME, PickablePlayer::name,
            PickablePlayer::new);
}
