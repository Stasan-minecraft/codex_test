package ua.credesasq.autocraft.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
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
        int buttonX = x + (backgroundWidth - 96) / 2;
        int buttonY = y + backgroundHeight + 3;
        autocraft$button = addButton(new ButtonWidget(
                buttonX,
                buttonY,
                96,
                20,
                new LiteralText(autocraft$getButtonText()),
                button -> {
                    if (AutoCraftManager.INSTANCE.isActive()) {
                        AutoCraftManager.INSTANCE.requestStop("Зупинено вручну");
                    } else {
                        MinecraftClient.getInstance().openScreen(new ItemSelectScreen((CraftingScreen) (Object) this));
                    }
                }
        ));
    }

    @Unique
    private String autocraft$getButtonText() {
        if (AutoCraftManager.INSTANCE.isStopping()) {
            return "ЗУПИНЯЮ...";
        }
        return AutoCraftManager.INSTANCE.isActive() ? "СТОП" : "АВТОКРАФТ";
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void autocraft$updateButton(CallbackInfo ci) {
        if (autocraft$button != null) {
            autocraft$button.setMessage(new LiteralText(autocraft$getButtonText()));
            autocraft$button.active = !AutoCraftManager.INSTANCE.isStopping();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void autocraft$renderStatus(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String status = AutoCraftManager.INSTANCE.getStatus();
        int statusY = y + backgroundHeight + 27;
        if (status != null && !status.isEmpty()) {
            textRenderer.drawWithShadow(matrices, status, x, statusY,
                    AutoCraftManager.INSTANCE.isActive() ? 0x66FF99 : 0xD7E7F7);
        }

        ItemStack selected = AutoCraftManager.INSTANCE.getSelectedOutput();
        if (AutoCraftManager.INSTANCE.isActive() && !selected.isEmpty()) {
            itemRenderer.renderInGuiWithOverrides(selected, x + backgroundWidth - 17, statusY - 5);
        }
    }
}
