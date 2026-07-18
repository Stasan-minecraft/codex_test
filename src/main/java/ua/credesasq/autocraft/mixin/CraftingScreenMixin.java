package ua.credesasq.autocraft.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.credesasq.autocraft.client.AutoCraftManager;
import ua.credesasq.autocraft.client.gui.ItemSelectScreen;

@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin extends HandledScreen<CraftingScreenHandler> {
    @Unique
    private ButtonWidget autocraft$button;

    protected CraftingScreenMixin(CraftingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void autocraft$addButton(CallbackInfo ci) {
        autocraft$button = addButton(new ButtonWidget(
                x + 53,
                y + backgroundHeight + 3,
                70,
                20,
                new LiteralText(AutoCraftManager.INSTANCE.isActive() ? "СТОП" : "Автокрафт"),
                button -> {
                    if (AutoCraftManager.INSTANCE.isActive()) {
                        AutoCraftManager.INSTANCE.stop("Зупинено вручну");
                    } else {
                        MinecraftClient.getInstance().openScreen(new ItemSelectScreen((CraftingScreen) (Object) this));
                    }
                }
        ));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void autocraft$updateButton(CallbackInfo ci) {
        if (autocraft$button != null) {
            autocraft$button.setMessage(new LiteralText(AutoCraftManager.INSTANCE.isActive() ? "СТОП" : "Автокрафт"));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void autocraft$renderStatus(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String status = AutoCraftManager.INSTANCE.getStatus();
        if (status != null && !status.isEmpty()) {
            textRenderer.drawWithShadow(matrices, status, x, y + backgroundHeight + 26, AutoCraftManager.INSTANCE.isActive() ? 0x66FF99 : 0xD7E7F7);
        }
    }
}
