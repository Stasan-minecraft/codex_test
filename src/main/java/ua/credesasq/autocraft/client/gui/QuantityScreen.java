package ua.credesasq.autocraft.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.text.LiteralText;
import ua.credesasq.autocraft.client.AutoCraftManager;

public final class QuantityScreen extends Screen {
    private final Screen craftingScreen;
    private final Screen selectionScreen;
    private final Recipe<?> recipe;
    private TextFieldWidget amountField;
    private boolean unlimited;

    public QuantityScreen(Screen craftingScreen, Screen selectionScreen, Recipe<?> recipe) {
        super(new LiteralText("Автокрафт — кількість"));
        this.craftingScreen = craftingScreen;
        this.selectionScreen = selectionScreen;
        this.recipe = recipe;
    }

    @Override
    protected void init() {
        int center = width / 2;
        amountField = new TextFieldWidget(textRenderer, center - 50, 88, 100, 20, new LiteralText("Кількість"));
        amountField.setText("64");
        amountField.setMaxLength(7);
        addChild(amountField);
        setInitialFocus(amountField);

        addButton(new ButtonWidget(center - 110, 114, 50, 20, new LiteralText("-1"), button -> changeAmount(-1)));
        addButton(new ButtonWidget(center - 55, 114, 50, 20, new LiteralText("+1"), button -> changeAmount(1)));
        addButton(new ButtonWidget(center, 114, 50, 20, new LiteralText("+64"), button -> changeAmount(64)));
        addButton(new ButtonWidget(center + 55, 114, 55, 20, new LiteralText("∞"), button -> {
            unlimited = !unlimited;
            button.setMessage(new LiteralText(unlimited ? "∞ УВІМК" : "∞"));
        }));

        addButton(new ButtonWidget(center - 75, 145, 150, 20, new LiteralText("КРАФТ"), button -> startCrafting()));
        addButton(new ButtonWidget(center - 75, 170, 150, 20, new LiteralText("Назад"), button -> {
            if (client != null) client.openScreen(selectionScreen);
        }));
    }

    private void changeAmount(int delta) {
        int current = parseAmount();
        current = Math.max(1, Math.min(9999999, current + delta));
        amountField.setText(Integer.toString(current));
    }

    private int parseAmount() {
        try {
            return Math.max(1, Math.min(9999999, Integer.parseInt(amountField.getText().trim())));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void startCrafting() {
        AutoCraftManager.INSTANCE.start(recipe, unlimited ? 0 : parseAmount());
        if (client != null) {
            client.openScreen(craftingScreen);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        int center = width / 2;
        fill(matrices, center - 135, 25, center + 135, 202, 0xCC101820);
        drawCenteredText(matrices, textRenderer, title, center, 34, 0xFFFFFF);

        ItemStack output = recipe.getOutput();
        itemRenderer.renderInGuiWithOverrides(output, center - 8, 51);
        drawCenteredText(matrices, textRenderer, output.getName(), center, 70, 0xD5E9FF);
        drawCenteredText(matrices, textRenderer,
                new LiteralText(unlimited ? "Режим: крафтити до натискання Стоп або кінця ресурсів" : "Вкажи кількість готових предметів"),
                center, 78, unlimited ? 0x66FF99 : 0xAFC9E8);

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (client != null) {
            client.openScreen(selectionScreen);
        }
    }
}
