package com.leekwater.homestorage.storage;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player the owner has allowed to use a Home. The UUID is the identity (names can change); the name is
 * only the last one we saw, kept so the member list can be shown without looking players up.
 */
public record HomeMember(UUID id, String name) {
    public static final Codec<HomeMember> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(HomeMember::id),
            Codec.STRING.fieldOf("name").forGetter(HomeMember::name)
    ).apply(instance, HomeMember::new));

    /** Network form; player names are at most 16 characters. */
    public static final StreamCodec<ByteBuf, HomeMember> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HomeMember::id,
            ByteBufCodecs.PLAYER_NAME, HomeMember::name,
            HomeMember::new);
}
