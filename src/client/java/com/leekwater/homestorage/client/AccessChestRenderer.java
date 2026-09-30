package com.leekwater.homestorage.client;

import org.jspecify.annotations.Nullable;

import com.leekwater.homestorage.HomeStorage;
import com.leekwater.homestorage.block.InfinityAccessChestBlock;
import com.leekwater.homestorage.blockentity.InfinityAccessChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the vanilla chest model with our own colour. Vanilla's ChestRenderer can't be reused directly because
 * its texture choice is a fixed list of vanilla materials.
 */
public class AccessChestRenderer implements BlockEntityRenderer<InfinityAccessChestBlockEntity, AccessChestRenderState> {
    /** textures/entity/chest/infinity_access.png, taken from the chest texture atlas. */
    private static final SpriteId SPRITE = Sheets.CHEST_MAPPER.apply(HomeStorage.id("infinity_access"));

    /** textures/entity/chest/infinity_access_glow.png: only the glowing trim, everything else transparent. */
    private static final SpriteId GLOW_SPRITE = Sheets.CHEST_MAPPER.apply(HomeStorage.id("infinity_access_glow"));
    /** How much bigger the glow pass is drawn (0.2%): enough to avoid z-fighting, too little to see. */
    private static final float GLOW_SCALE = 1.002F;

    private final SpriteGetter sprites;
    private final ChestModel model;

    public AccessChestRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.model = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
    }

    @Override
    public AccessChestRenderState createRenderState() {
        return new AccessChestRenderState();
    }

    @Override
    public void extractRenderState(InfinityAccessChestBlockEntity chest, AccessChestRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(chest, state, partialTicks, cameraPosition, breakProgress);
        state.facing = chest.getBlockState().getValue(InfinityAccessChestBlock.FACING);
        state.open = chest.getOpenNess(partialTicks);
    }

    @Override
    public void submit(AccessChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));

        // Same easing as vanilla: the lid swings fast at first, then settles.
        float open = 1.0F - state.open;
        open = 1.0F - open * open * open;

        collector.submitModel(model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, SPRITE, sprites, 0);

        // The glow: the same model again, but only the cyan trim (the mask texture is transparent everywhere else),
        // drawn at full brightness so the lines stay lit in the dark. Slightly bigger so it can't flicker against
        // the identical surface underneath, and ordered after the first pass so it is drawn on top.
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.scale(GLOW_SCALE, GLOW_SCALE, GLOW_SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        collector.order(1).submitModel(model, open, poseStack, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1,
                GLOW_SPRITE, sprites, 0);
        poseStack.popPose();

        if (state.breakProgress != null) {
            collector.order(1).submitCrumblingOverlay(model, open, poseStack, SPRITE.renderType(model.renderType()),
                    state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
        }
        poseStack.popPose();
    }
}
