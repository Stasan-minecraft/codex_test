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
        int buttonWidth = 124;
        int buttonX = x + (backgroundWidth - buttonWidth) / 2;
        int buttonY = y + backgroundHeight + 4;
        autocraft$button = addButton(new ButtonWidget(
                buttonX,
                buttonY,
                buttonWidth,
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
            return "ЗУПИНЯЮ ТА ПОВЕРТАЮ РЕЧІ...";
        }
        return AutoCraftManager.INSTANCE.isActive() ? "СТОП АВТОКРАФТ" : "ВІДКРИТИ АВТОКРАФТ";
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
        int panelY = y + backgroundHeight + 27;
        int panelHeight = AutoCraftManager.INSTANCE.isActive() ? 28 : 18;

        fill(matrices, x, panelY - 3, x + backgroundWidth, panelY + panelHeight, 0xC8101822);
        fill(matrices, x + 1, panelY - 2, x + backgroundWidth - 1, panelY - 1,
                AutoCraftManager.INSTANCE.isActive() ? 0xFF45C878 : 0xFF48677A);

        ItemStack selected = AutoCraftManager.INSTANCE.getSelectedOutput();
        int textX = x + 7;
        if (AutoCraftManager.INSTANCE.isActive() && !selected.isEmpty()) {
            fill(matrices, x + 5, panelY + 2, x + 27, panelY + 24, 0xFF27485D);
            itemRenderer.renderInGuiWithOverrides(selected, x + 8, panelY + 5);
            itemRenderer.renderGuiItemOverlay(textRenderer, selected, x + 8, panelY + 5);
            textX = x + 33;
        }

        if (status != null && !status.isEmpty()) {
            textRenderer.drawWithShadow(matrices, status, textX, panelY + 3,
                    AutoCraftManager.INSTANCE.isActive() ? 0xFF89F0A8 : 0xFFD7E7F7);
        }

        if (AutoCraftManager.INSTANCE.isActive()) {
            int crafted = AutoCraftManager.INSTANCE.getCraftedAmount();
            int target = AutoCraftManager.INSTANCE.getTargetAmount();
            String progress = target == 0
                    ? "Готово предметів: " + crafted + " • режим без ліміту"
                    : "Прогрес: " + crafted + " / " + target;
            textRenderer.drawWithShadow(matrices, progress, textX, panelY + 14, 0xFFAFC9D8);
        }
    }
}
