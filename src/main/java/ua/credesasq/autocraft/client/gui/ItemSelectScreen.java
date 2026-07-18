package ua.credesasq.autocraft.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemSelectScreen extends Screen {
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = COLUMNS * ROWS;

    private final Screen parent;
    private final List<Recipe<?>> allRecipes = new ArrayList<>();
    private final List<Recipe<?>> filteredRecipes = new ArrayList<>();
    private TextFieldWidget search;
    private int page;
    private int gridX;
    private int gridY;

    public ItemSelectScreen(Screen parent) {
        super(new LiteralText("Автокрафт — вибір предмета"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        gridX = (width - COLUMNS * 22) / 2;
        gridY = 58;

        search = new TextFieldWidget(textRenderer, width / 2 - 100, 28, 200, 20, new LiteralText("Пошук"));
        search.setMaxLength(80);
        search.setChangedListener(value -> applyFilter());
        addChild(search);
        setInitialFocus(search);

        addButton(new ButtonWidget(width / 2 - 112, height - 28, 44, 20, new LiteralText("<"), button -> {
            if (page > 0) page--;
        }));
        addButton(new ButtonWidget(width / 2 + 68, height - 28, 44, 20, new LiteralText(">"), button -> {
            if (page + 1 < pageCount()) page++;
        }));
        addButton(new ButtonWidget(8, height - 28, 70, 20, new LiteralText("Назад"), button -> onClose()));

        loadRecipes();
        applyFilter();
    }

    private void loadRecipes() {
        allRecipes.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        Collection<Recipe<?>> recipes = client.world.getRecipeManager().values();
        Map<Item, Recipe<?>> bestByOutput = new LinkedHashMap<>();
        for (Recipe<?> recipe : recipes) {
            if (recipe.getType() != RecipeType.CRAFTING) {
                continue;
            }
            ItemStack output = recipe.getOutput();
            if (output.isEmpty()) {
                continue;
            }
            Recipe<?> current = bestByOutput.get(output.getItem());
            if (current == null || ingredientCount(recipe) < ingredientCount(current)) {
                bestByOutput.put(output.getItem(), recipe);
            }
        }
        allRecipes.addAll(bestByOutput.values());
        allRecipes.sort(Comparator.comparing(recipe -> recipe.getOutput().getName().getString().toLowerCase(Locale.ROOT)));
    }

    private static int ingredientCount(Recipe<?> recipe) {
        int count = 0;
        for (net.minecraft.recipe.Ingredient ingredient : recipe.getPreviewInputs()) {
            if (!ingredient.isEmpty()) count++;
        }
        return count;
    }

    private void applyFilter() {
        filteredRecipes.clear();
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        for (Recipe<?> recipe : allRecipes) {
            ItemStack stack = recipe.getOutput();
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            String id = recipe.getId().toString().toLowerCase(Locale.ROOT);
            if (query.isEmpty() || name.contains(query) || id.contains(query)) {
                filteredRecipes.add(recipe);
            }
        }
        page = 0;
    }

    private int pageCount() {
        return Math.max(1, (filteredRecipes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        fill(matrices, gridX - 8, gridY - 8, gridX + COLUMNS * 22 + 8, gridY + ROWS * 22 + 8, 0xAA101820);
        drawCenteredText(matrices, textRenderer, title, width / 2, 8, 0xFFFFFF);
        drawCenteredText(matrices, textRenderer, new LiteralText("Обери предмет, який треба крафтити"), width / 2, 17, 0xAFC9E8);

        int start = page * PAGE_SIZE;
        int end = Math.min(filteredRecipes.size(), start + PAGE_SIZE);
        Recipe<?> hovered = null;
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % COLUMNS;
            int row = local / COLUMNS;
            int x = gridX + col * 22;
            int y = gridY + row * 22;
            boolean isHovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            fill(matrices, x, y, x + 20, y + 20, isHovered ? 0xFF4A78A8 : 0xFF263849);
            ItemStack output = filteredRecipes.get(index).getOutput();
            itemRenderer.renderInGuiWithOverrides(output, x + 2, y + 2);
            if (isHovered) hovered = filteredRecipes.get(index);
        }

        drawCenteredText(matrices, textRenderer,
                new LiteralText("Сторінка " + (page + 1) + " / " + pageCount() + "  •  рецептів: " + filteredRecipes.size()),
                width / 2, height - 22, 0xD5E9FF);

        super.render(matrices, mouseX, mouseY, delta);
        if (hovered != null) {
            renderTooltip(matrices, hovered.getOutput(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int col = (int) ((mouseX - gridX) / 22);
            int row = (int) ((mouseY - gridY) / 22);
            if (col >= 0 && col < COLUMNS && row >= 0 && row < ROWS) {
                int cellX = gridX + col * 22;
                int cellY = gridY + row * 22;
                if (mouseX >= cellX && mouseX < cellX + 20 && mouseY >= cellY && mouseY < cellY + 20) {
                    int index = page * PAGE_SIZE + row * COLUMNS + col;
                    if (index >= 0 && index < filteredRecipes.size() && client != null) {
                        client.openScreen(new QuantityScreen(parent, this, filteredRecipes.get(index)));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount < 0 && page + 1 < pageCount()) {
            page++;
            return true;
        }
        if (amount > 0 && page > 0) {
            page--;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void onClose() {
        if (client != null) {
            client.openScreen(parent);
        }
    }
}
