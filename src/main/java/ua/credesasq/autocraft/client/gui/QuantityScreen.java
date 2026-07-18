package ua.credesasq.autocraft.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.text.LiteralText;
import net.minecraft.util.collection.DefaultedList;
import ua.credesasq.autocraft.client.AutoCraftManager;

public final class QuantityScreen extends Screen {
    private final Screen craftingScreen;
    private final Screen selectionScreen;
    private final Recipe<?> recipe;
    private TextFieldWidget amountField;
    private ButtonWidget unlimitedButton;
    private ButtonWidget craftButton;
    private boolean unlimited;
    private int panelLeft;
    private int panelTop;

    public QuantityScreen(Screen craftingScreen, Screen selectionScreen, Recipe<?> recipe) {
        super(new LiteralText("AutoCraft 1.3 — кількість"));
        this.craftingScreen = craftingScreen;
        this.selectionScreen = selectionScreen;
        this.recipe = recipe;
    }

    @Override
    protected void init() {
        int center = width / 2;
        panelLeft = center - 164;
        panelTop = Math.max(7, height / 2 - 142);

        int controlsY = panelTop + 139;
        amountField = new TextFieldWidget(textRenderer, center - 72, controlsY, 118, 20,
                new LiteralText("Кількість"));
        amountField.setText("64");
        amountField.setMaxLength(7);
        amountField.setTextPredicate(value -> value.isEmpty() || value.matches("[0-9]{0,7}"));
        amountField.setChangedListener(value -> updateButtons());
        addChild(amountField);
        setInitialFocus(amountField);

        addButton(new ButtonWidget(center + 50, controlsY, 22, 20, new LiteralText("×"), button -> {
            amountField.setText("");
            amountField.setTextFieldFocused(true);
        }));

        addButton(new ButtonWidget(center - 112, controlsY + 27, 52, 20, new LiteralText("-64"), button -> changeAmount(-64)));
        addButton(new ButtonWidget(center - 56, controlsY + 27, 52, 20, new LiteralText("-1"), button -> changeAmount(-1)));
        addButton(new ButtonWidget(center, controlsY + 27, 52, 20, new LiteralText("+1"), button -> changeAmount(1)));
        addButton(new ButtonWidget(center + 56, controlsY + 27, 56, 20, new LiteralText("+64"), button -> changeAmount(64)));

        addButton(new ButtonWidget(center - 112, controlsY + 52, 52, 20, new LiteralText("1"), button -> setAmount(1)));
        addButton(new ButtonWidget(center - 56, controlsY + 52, 52, 20, new LiteralText("16"), button -> setAmount(16)));
        addButton(new ButtonWidget(center, controlsY + 52, 52, 20, new LiteralText("64"), button -> setAmount(64)));
        unlimitedButton = addButton(new ButtonWidget(center + 56, controlsY + 52, 56, 20,
                new LiteralText("∞"), button -> toggleUnlimited()));

        craftButton = addButton(new ButtonWidget(center - 100, controlsY + 83, 200, 20,
                new LiteralText("ПОЧАТИ АВТОКРАФТ"), button -> startCrafting()));
        addButton(new ButtonWidget(center - 100, controlsY + 108, 200, 20,
                new LiteralText("Назад до предметів"), button -> {
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
        if (client != null) client.openScreen(craftingScreen);
    }

    @Override
    public void tick() {
        if (amountField != null) amountField.tick();
        updateButtons();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        int center = width / 2;
        int panelRight = center + 164;
        int panelBottom = Math.min(height - 7, panelTop + 279);

        fill(matrices, panelLeft, panelTop, panelRight, panelBottom, 0xEE0B121B);
        fill(matrices, panelLeft + 1, panelTop + 1, panelRight - 1, panelTop + 29, 0xFF173247);
        drawCenteredText(matrices, textRenderer, title, center, panelTop + 10, 0xFFFFFF);

        ItemStack output = recipe.getOutput();
        drawCenteredText(matrices, textRenderer, output.getName(), center, panelTop + 36, 0xFFD5E9FF);
        drawRecipePreview(matrices, center, panelTop + 49);

        drawCenteredText(matrices, textRenderer,
                new LiteralText("За один крафт: " + output.getCount() + " шт. • інгредієнтів: " + ingredientCount()),
                center, panelTop + 126, 0xFF91BFD8);

        super.render(matrices, mouseX, mouseY, delta);
        if (amountField != null) amountField.render(matrices, mouseX, mouseY, delta);

        String mode = unlimited
                ? "Без ліміту: до Стоп або кінця ресурсів"
                : "Ти написав кількість: " + (amountField.getText().isEmpty() ? "—" : amountField.getText());
        drawCenteredText(matrices, textRenderer, new LiteralText(mode), center, panelTop + 214,
                unlimited ? 0xFF66FF99 : 0xFFD5E9FF);
    }

    private void drawRecipePreview(MatrixStack matrices, int center, int y) {
        Ingredient[] grid = recipeGrid();
        int gx = center - 91;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = gx + col * 20;
                int sy = y + row * 20;
                fill(matrices, sx, sy, sx + 19, sy + 19, 0xFF4D5960);
                fill(matrices, sx + 1, sy + 1, sx + 18, sy + 18, 0xFFA6B0B5);
                Ingredient ingredient = grid[row * 3 + col];
                if (ingredient != null && !ingredient.isEmpty()) {
                    ItemStack[] choices = ingredient.getMatchingStacks();
                    if (choices.length > 0) {
                        int cycle = (int) ((System.currentTimeMillis() / 900L) % choices.length);
                        itemRenderer.renderInGuiWithOverrides(choices[cycle], sx + 2, sy + 2);
                    }
                }
            }
        }

        textRenderer.drawWithShadow(matrices, "→", center - 13, y + 25, 0xFFFFFFFF);
        int ox = center + 32;
        int oy = y + 20;
        fill(matrices, ox - 4, oy - 4, ox + 24, oy + 24, 0xFF355E75);
        fill(matrices, ox - 3, oy - 3, ox + 23, oy + 23, 0xFF9EC5D8);
        ItemStack output = recipe.getOutput();
        itemRenderer.renderInGuiWithOverrides(output, ox + 1, oy + 1);
        itemRenderer.renderGuiItemOverlay(textRenderer, output, ox + 1, oy + 1);
    }

    private Ingredient[] recipeGrid() {
        Ingredient[] grid = new Ingredient[9];
        DefaultedList<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            int recipeWidth = shaped.getWidth();
            int recipeHeight = shaped.getHeight();
            int offsetX = (3 - recipeWidth) / 2;
            int offsetY = (3 - recipeHeight) / 2;
            for (int row = 0; row < recipeHeight; row++) {
                for (int col = 0; col < recipeWidth; col++) {
                    int source = row * recipeWidth + col;
                    if (source < ingredients.size()) {
                        grid[(row + offsetY) * 3 + col + offsetX] = ingredients.get(source);
                    }
                }
            }
        } else {
            int target = 0;
            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty() && target < 9) grid[target++] = ingredient;
            }
        }
        return grid;
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
        if (client != null) client.openScreen(selectionScreen);
    }
}
