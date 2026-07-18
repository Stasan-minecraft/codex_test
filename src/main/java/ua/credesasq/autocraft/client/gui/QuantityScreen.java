package ua.credesasq.autocraft.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.text.LiteralText;
import ua.credesasq.autocraft.client.AutoCraftManager;

public final class QuantityScreen extends Screen {
    private final Screen craftingScreen;
    private final Screen selectionScreen;
    private final Recipe<?> recipe;
    private TextFieldWidget amountField;
    private ButtonWidget unlimitedButton;
    private ButtonWidget craftButton;
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
        amountField = new TextFieldWidget(textRenderer, center - 72, 105, 118, 20, new LiteralText("Кількість"));
        amountField.setText("64");
        amountField.setMaxLength(7);
        amountField.setTextPredicate(value -> value.isEmpty() || value.matches("[0-9]{0,7}"));
        amountField.setChangedListener(value -> updateButtons());
        addChild(amountField);
        setInitialFocus(amountField);

        addButton(new ButtonWidget(center + 50, 105, 22, 20, new LiteralText("×"), button -> {
            amountField.setText("");
            amountField.setTextFieldFocused(true);
        }));

        addButton(new ButtonWidget(center - 110, 132, 50, 20, new LiteralText("-64"), button -> changeAmount(-64)));
        addButton(new ButtonWidget(center - 55, 132, 50, 20, new LiteralText("-1"), button -> changeAmount(-1)));
        addButton(new ButtonWidget(center, 132, 50, 20, new LiteralText("+1"), button -> changeAmount(1)));
        addButton(new ButtonWidget(center + 55, 132, 55, 20, new LiteralText("+64"), button -> changeAmount(64)));

        addButton(new ButtonWidget(center - 110, 157, 50, 20, new LiteralText("1"), button -> setAmount(1)));
        addButton(new ButtonWidget(center - 55, 157, 50, 20, new LiteralText("16"), button -> setAmount(16)));
        addButton(new ButtonWidget(center, 157, 50, 20, new LiteralText("64"), button -> setAmount(64)));
        unlimitedButton = addButton(new ButtonWidget(center + 55, 157, 55, 20, new LiteralText("∞"), button -> toggleUnlimited()));

        craftButton = addButton(new ButtonWidget(center - 90, 188, 180, 20, new LiteralText("ПОЧАТИ КРАФТ"), button -> startCrafting()));
        addButton(new ButtonWidget(center - 90, 213, 180, 20, new LiteralText("Назад до предметів"), button -> {
            if (client != null) client.openScreen(selectionScreen);
        }));
        updateButtons();
    }

    private void toggleUnlimited() {
        unlimited = !unlimited;
        amountField.setEditable(!unlimited);
        unlimitedButton.setMessage(new LiteralText(unlimited ? "∞ ВКЛ" : "∞"));
        updateButtons();
    }

    private void setAmount(int amount) {
        unlimited = false;
        amountField.setEditable(true);
        unlimitedButton.setMessage(new LiteralText("∞"));
        amountField.setText(Integer.toString(amount));
    }

    private void changeAmount(int delta) {
        setAmount(Math.max(1, Math.min(9999999, parseAmount() + delta)));
    }

    private int parseAmount() {
        try {
            return Math.max(1, Math.min(9999999, Integer.parseInt(amountField.getText().trim())));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void updateButtons() {
        if (craftButton != null) {
            craftButton.active = unlimited || (amountField != null && !amountField.getText().trim().isEmpty());
        }
    }

    private void startCrafting() {
        AutoCraftManager.INSTANCE.start(recipe, unlimited ? 0 : parseAmount());
        if (client != null) {
            client.openScreen(craftingScreen);
        }
    }

    @Override
    public void tick() {
        if (amountField != null) {
            amountField.tick();
        }
        updateButtons();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        int center = width / 2;
        fill(matrices, center - 145, 12, center + 145, 242, 0xE8101822);
        fill(matrices, center - 144, 13, center + 144, 38, 0xFF172737);
        drawCenteredText(matrices, textRenderer, title, center, 21, 0xFFFFFF);

        ItemStack output = recipe.getOutput();
        fill(matrices, center - 17, 46, center + 17, 80, 0xFF263C4E);
        itemRenderer.renderInGuiWithOverrides(output, center - 8, 55);
        itemRenderer.renderGuiItemOverlay(textRenderer, output, center - 8, 55);
        drawCenteredText(matrices, textRenderer, output.getName(), center, 84, 0xD5E9FF);
        drawCenteredText(matrices, textRenderer,
                new LiteralText("За один крафт: " + output.getCount() + " шт.  •  інгредієнтів: " + ingredientCount()),
                center, 94, 0x91AFC9);

        super.render(matrices, mouseX, mouseY, delta);
        if (amountField != null) {
            amountField.render(matrices, mouseX, mouseY, delta);
        }

        String mode = unlimited
                ? "Крафтити без ліміту до Стоп або кінця ресурсів"
                : "Ти написав кількість: " + (amountField.getText().isEmpty() ? "—" : amountField.getText());
        drawCenteredText(matrices, textRenderer, new LiteralText(mode), center, 178,
                unlimited ? 0x66FF99 : 0xD5E9FF);
    }

    private int ingredientCount() {
        int count = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) count++;
        }
        return count;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && (unlimited || !amountField.getText().trim().isEmpty())) {
            startCrafting();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (client != null) {
            client.openScreen(selectionScreen);
        }
    }
}
