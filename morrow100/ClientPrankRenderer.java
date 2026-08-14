package com.credesasq.morrow.client;

import com.credesasq.morrow.MorrowMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = MorrowMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientPrankRenderer {
    private static final float MONSTER_RENDER_SCALE = 1.36F;
    private static final float DEVIL_RENDER_SCALE = 2.04F;
    private static MorrowModel model;

    public static void initialize(MorrowModel bakedModel) { model = bakedModel; }

    @SubscribeEvent
    public static void renderPlayer(RenderPlayerEvent.Pre event) {
        if (model == null) return;
        Player player = event.getEntity();
        ClientPrankState.State state = ClientPrankState.get(player.getUUID());
        if (state == null || !state.active()) return;

        event.setCanceled(true);
        if (state.boxed() || state.carried() || (state.monster() && state.invisible())) return;

        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float renderYaw = state.monster() ? Mth.rotLerp(partialTick, player.yRotO, player.getYRot()) : bodyYaw;
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - renderYaw);
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float age = player.tickCount + partialTick;
        float scale = state.devil() ? DEVIL_RENDER_SCALE : (state.monster() ? MONSTER_RENDER_SCALE : MorrowRenderer.stageScale(state.stage()));
        ClientRollState.Roll roll = ClientRollState.sample(player, partialTick, renderYaw,
                0.435F * Math.max(0.68F, scale));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - renderYaw));
        if (player.isCrouching()) poseStack.translate(0.0D, state.monster() ? 0.02D : 0.12D, 0.0D);
        poseStack.scale(scale, scale, scale);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);

        model.setupForPlayer(player, state.stage(), state.face(), state.monster(), state.devil(),
                roll.xRot(), roll.zRot(), roll.motion(), roll.impact(), age, headYaw, headPitch);
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(model.renderType(
                state.monster() ? MorrowRenderer.TEXTURE : MorrowRenderer.colorTexture(state.color())));
        model.renderToBuffer(poseStack, consumer, event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        renderCustomName(player, state, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    private static void renderCustomName(Player player, ClientPrankState.State state, PoseStack pose,
                                         MultiBufferSource buffers, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityRenderDispatcher().distanceToSqr(player) > 4096.0D) return;
        Component name = Component.literal(state.displayName());
        pose.pushPose();
        double y = state.devil() ? 4.45D : (state.monster() ? 3.20D : 1.28D);
        pose.translate(0.0D, y, 0.0D);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        Font font = mc.font;
        float x = -font.width(name) / 2.0F;
        font.drawInBatch(name, x, 0.0F, 0xFFFFFFFF, false, matrix, buffers,
                Font.DisplayMode.NORMAL, 0x50000000, packedLight);
        pose.popPose();
    }

    @SubscribeEvent
    public static void renderCarriedMorrowThirdPerson(RenderPlayerEvent.Post event) {
        if (model == null) return;
        Player carrier = event.getEntity();
        ClientPrankState.CarriedView carried = ClientPrankState.findCarriedBy(carrier.getUUID());
        if (carried == null) return;
        ClientPrankState.State state = carried.state();
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(partialTick, carrier.yBodyRotO, carrier.yBodyRot);
        float swing = Mth.sin((carrier.tickCount + partialTick) * 0.12F) * 0.018F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.translate(-0.43D, (carrier.isCrouching() ? 0.93D : 1.08D) + swing, -0.20D);
        poseStack.mulPose(Axis.XP.rotationDegrees(12.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-18.0F));
        renderCarriedBall(poseStack, event.getMultiBufferSource(), event.getPackedLight(),
                carrier, state, carrier.tickCount + partialTick, 0.31F);
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        if (model == null || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ClientPrankState.State self = ClientPrankState.get(mc.player.getUUID());
        if (self != null && self.active() && self.carried() && self.carrierId() != null && mc.level != null) {
            Player carrier = mc.level.getPlayerByUUID(self.carrierId());
            if (carrier instanceof AbstractClientPlayer remote) {
                event.setCanceled(true);
                renderCarrierArmAndItem(event, remote);
            }
            return;
        }

        ClientPrankState.CarriedView carried = ClientPrankState.findCarriedBy(mc.player.getUUID());
        if (carried == null) return;
        ClientPrankState.State state = carried.state();
        PoseStack poseStack = event.getPoseStack();
        float swing = event.getSwingProgress();
        float swayX = Mth.sin(swing * Mth.PI) * 0.08F;
        float swayY = Mth.sin(Mth.sqrt(swing) * Mth.PI) * 0.05F;
        poseStack.pushPose();
        poseStack.translate(0.48D - swayX, -0.40D + swayY, -0.78D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(7.0F));
        renderCarriedBall(poseStack, event.getMultiBufferSource(), event.getPackedLight(),
                mc.player, state, mc.player.tickCount + event.getPartialTick(), 0.34F);
        poseStack.popPose();
    }

    private static void renderCarrierArmAndItem(RenderHandEvent event, AbstractClientPlayer carrier) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        EntityRenderer<? super AbstractClientPlayer> er = mc.getEntityRenderDispatcher().getRenderer(carrier);
        if (er instanceof PlayerRenderer renderer) {
            pose.pushPose();
            pose.translate(0.72D, -0.70D, -0.70D);
            pose.mulPose(Axis.XP.rotationDegrees(-18.0F));
            pose.mulPose(Axis.YP.rotationDegrees(-14.0F));
            renderer.renderRightHand(pose, buffers, event.getPackedLight(), carrier);
            pose.popPose();
        }
        ItemStack held = carrier.getMainHandItem();
        if (!held.isEmpty()) {
            pose.pushPose();
            pose.translate(0.62D, -0.42D, -0.90D);
            pose.mulPose(Axis.YP.rotationDegrees(-28.0F));
            pose.mulPose(Axis.XP.rotationDegrees(-12.0F));
            mc.getItemRenderer().renderStatic(carrier, held, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    false, pose, buffers, carrier.level(), event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                    carrier.getId());
            pose.popPose();
        }
    }

    private static void renderCarriedBall(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                          Player animationPlayer, ClientPrankState.State state,
                                          float age, float baseScale) {
        float scale = baseScale * MorrowRenderer.stageScale(state.stage());
        poseStack.scale(scale, scale, scale);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);
        model.setupForPlayer(animationPlayer, state.stage(), state.face(), false,
                0.0F, 0.0F, 0.0F, 0.0F, age, 0.0F, 0.0F);
        VertexConsumer consumer = buffers.getBuffer(model.renderType(MorrowRenderer.colorTexture(state.color())));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void renderFirstPersonArm(RenderArmEvent event) {
        ClientPrankState.State state = ClientPrankState.get(event.getPlayer().getUUID());
        if (state != null && state.active()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void resizePlayer(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ClientPrankState.State state = ClientPrankState.get(player.getUUID());
        if (state == null || !state.active()) return;
        if (state.carried()) {
            event.setNewSize(EntityDimensions.scalable(0.10F, 0.10F));
            event.setNewEyeHeight(0.05F);
        } else if (state.boxed()) {
            event.setNewSize(EntityDimensions.scalable(0.55F, 0.95F));
            event.setNewEyeHeight(0.72F);
        } else if (state.devil()) {
            event.setNewSize(EntityDimensions.scalable(1.35F, 2.85F));
            event.setNewEyeHeight(2.45F);
        } else {
            event.setNewSize(EntityDimensions.scalable(0.94F, 0.94F));
            event.setNewEyeHeight(0.78F);
        }
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ClientPrankState.State state = ClientPrankState.get(mc.player.getUUID());
        if (state == null || !state.active()) return;
        GuiGraphics g = event.getGuiGraphics();
        String n = state.displayName();
        Component status;
        Component keys;
        int color;
        if (state.boxed()) {
            status = Component.literal(n + " • INSIDE THE BOX");
            keys = Component.literal("PRESS SPACE • OR ANOTHER PLAYER CLICKS THE BOX");
            color = 0xFF74D8FF;
        } else if (state.carried()) {
            status = Component.literal(n + " • CARRIER POV • FULL INVENTORY LINK");
            keys = Component.literal("1-4 FACE • G CUSTOMIZE • CHAT • LIVE CARRIER HAND");
            color = 0xFFFFC857;
            renderCarrierInventory(g, mc);
        } else if (state.devil()) {
            status = Component.literal(n + " • DEVIL FORM • " + (state.invisible() ? "INVISIBLE" : "VISIBLE"));
            keys = Component.literal("R HELLBURST   F DASH   X ROAR   LMB BREAK   C COPY   V PLACE   5 BALL");
            color = 0xFFFF2424;
        } else if (state.monster()) {
            status = Component.literal(n + " • MONSTER FORM • " + (state.invisible() ? "INVISIBLE" : "VISIBLE"));
            keys = Component.literal("J VISIBILITY   LMB BREAK   C COPY   V PLACE   5 DEVIL");
            color = state.invisible() ? 0xFF9A8CFF : 0xFFFF5050;
        } else {
            String faceName = switch (state.face()) {
                case 1 -> "HAPPY";
                case 2 -> "WOW";
                case 3 -> "GRUMPY";
                default -> "SMILE";
            };
            status = Component.literal(n + " • " + faceName + " • STAGE " + state.stage());
            keys = Component.literal("1-4 FACE   G COLOR+NAME   5 MONSTER   6/7/8/9/0 STAGE");
            color = 0xFF57B9FF;
        }
        int width = Math.max(mc.font.width(status), mc.font.width(keys));
        int x = (mc.getWindow().getGuiScaledWidth() - width) / 2;
        int y = mc.getWindow().getGuiScaledHeight() - 67;
        g.fill(x - 5, y - 4, x + width + 5, y + 20, 0xB0100714);
        g.drawString(mc.font, status, x, y, color, false);
        g.drawString(mc.font, keys, x, y + 10, 0xFFFFE36E, false);
    }

    private static void renderCarrierInventory(GuiGraphics g, Minecraft mc) {
        ClientCarrierInventory.Snapshot snap = ClientCarrierInventory.get();
        if (snap == null) return;
        int slot = 18;
        int left = 8;
        int top = Math.max(8, mc.getWindow().getGuiScaledHeight() - 126);
        g.fill(left - 5, top - 18, left + 9 * slot + 58, top + 4 * slot + 8, 0xC00A0D12);
        g.drawString(mc.font, "CARRIER: " + snap.carrierName() + "  •  ACTIVE SLOT: " + (snap.selected() + 1),
                left, top - 14, 0xFFFFD56A, false);
        for (int i = 0; i < 36; i++) {
            int row = i < 9 ? 3 : (i - 9) / 9;
            int col = i < 9 ? i : (i - 9) % 9;
            int x = left + col * slot;
            int y = top + row * slot;
            int bg = i == snap.selected() ? 0xFFFFFF66 : 0xFF38414C;
            g.fill(x - 1, y - 1, x + 17, y + 17, bg);
            g.fill(x, y, x + 16, y + 16, 0xB0121720);
            ItemStack stack = snap.item(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x, y);
                g.renderItemDecorations(mc.font, stack, x, y);
            }
        }
        int extraX = left + 9 * slot + 8;
        for (int i = 36; i < Math.min(41, snap.items().size()); i++) {
            int y = top + (i - 36) * slot;
            g.fill(extraX - 1, y - 1, extraX + 17, y + 17, 0xFF38414C);
            g.fill(extraX, y, extraX + 16, y + 16, 0xB0121720);
            ItemStack stack = snap.item(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, extraX, y);
                g.renderItemDecorations(mc.font, stack, extraX, y);
            }
        }
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        ClientPrankState.clear();
        ClientCarrierInventory.clear();
        ClientRollState.clear();
    }

    private ClientPrankRenderer() {}
}
