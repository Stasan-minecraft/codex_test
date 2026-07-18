package ua.credesasq.autocraft.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemSelectScreen extends Screen {
    private final Screen parent;
    private final List<Recipe<?>> allRecipes = new ArrayList<>();
    private final List<Recipe<?>> filteredRecipes = new ArrayList<>();

    private TextFieldWidget search;
    private ButtonWidget clearButton;
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;

    private int page;
    private int columns;
    private int rows;
    private int pageSize;
    private int gridX;
    private int gridY;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int searchX;
    private int searchY;
    private String lastQuery = "";

    public ItemSelectScreen(Screen parent) {
        super(new LiteralText("Автокрафт — вибір предмета"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        columns = clamp((width - 44) / 24, 6, 12);
        rows = clamp((height - 150) / 24, 3, 6);
        pageSize = columns * rows;
        gridX = (width - columns * 22) / 2;
        gridY = 74;

        panelLeft = Math.max(8, gridX - 13);
        panelRight = Math.min(width - 8, gridX + columns * 22 + 13);
        panelTop = 6;
        panelBottom = height - 7;

        int searchWidth = Math.min(250, Math.max(130, width - 150));
        searchX = (width - searchWidth - 25) / 2;
        searchY = 38;
        search = new TextFieldWidget(textRenderer, searchX, searchY, searchWidth, 20, new LiteralText("Пошук предмета"));
        search.setMaxLength(80);
        search.setChangedListener(value -> applyFilter(false));
        search.setText(lastQuery);
        addChild(search);
        setInitialFocus(search);

        clearButton = addButton(new ButtonWidget(searchX + searchWidth + 4, searchY, 21, 20,
                new LiteralText("×"), button -> clearSearch()));

        previousButton = addButton(new ButtonWidget(width / 2 - 74, height - 30, 34, 20,
                new LiteralText("<"), button -> changePage(-1)));
        nextButton = addButton(new ButtonWidget(width / 2 + 40, height - 30, 34, 20,
                new LiteralText(">"), button -> changePage(1)));
        addButton(new ButtonWidget(panelLeft + 7, height - 30, 68, 20,
                new LiteralText("Назад"), button -> onClose()));
        addButton(new ButtonWidget(panelRight - 75, height - 30, 68, 20,
                new LiteralText("Оновити"), button -> {
                    loadRecipes();
                    applyFilter(true);
                }));

        loadRecipes();
        applyFilter(true);
    }

    private void loadRecipes() {
        allRecipes.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        RecipeManager manager = client.world.getRecipeManager();
        Map<Item, Recipe<?>> bestByOutput = new LinkedHashMap<>();

        for (Recipe<?> recipe : manager.listAllOfType(RecipeType.CRAFTING)) {
            addBestRecipe(bestByOutput, recipe);
        }

        if (!bestByOutput.containsKey(Items.FURNACE)) {
            Recipe<?> serverFurnace = manager.get(new Identifier("minecraft", "furnace")).orElse(null);
            if (serverFurnace != null) {
                addBestRecipe(bestByOutput, serverFurnace);
            }
        }
        if (!bestByOutput.containsKey(Items.FURNACE)) {
            addBestRecipe(bestByOutput, createFallbackFurnaceRecipe());
        }

        allRecipes.addAll(bestByOutput.values());
        allRecipes.sort(Comparator
                .comparingInt(ItemSelectScreen::priority)
                .thenComparing(recipe -> recipe.getOutput().getName().getString().toLowerCase(Locale.ROOT)));
    }

    private static void addBestRecipe(Map<Item, Recipe<?>> recipes, Recipe<?> recipe) {
        if (recipe == null || recipe.getType() != RecipeType.CRAFTING) {
            return;
        }
        ItemStack output = recipe.getOutput();
        if (output.isEmpty() || !recipe.fits(3, 3)) {
            return;
        }
        Recipe<?> current = recipes.get(output.getItem());
        if (current == null || ingredientCount(recipe) < ingredientCount(current)) {
            recipes.put(output.getItem(), recipe);
        }
    }

    private static Recipe<?> createFallbackFurnaceRecipe() {
        DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(9, Ingredient.EMPTY);
        Ingredient stone = Ingredient.ofItems(Items.COBBLESTONE, Items.BLACKSTONE);
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                ingredients.set(i, stone);
            }
        }
        return new ShapedRecipe(
                new Identifier("autocraft", "fallback_furnace"),
                "",
                3,
                3,
                ingredients,
                new ItemStack(Items.FURNACE)
        );
    }

    private static int priority(Recipe<?> recipe) {
        Item item = recipe.getOutput().getItem();
        if (item == Items.FURNACE) return 0;
        if (item == Items.CRAFTING_TABLE) return 1;
        if (item == Items.CHEST) return 2;
        if (item == Items.STICK) return 3;
        if (item == Items.TORCH) return 4;
        return 100;
    }

    private static int ingredientCount(Recipe<?> recipe) {
        int count = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void applyFilter(boolean keepPage) {
        int oldPage = page;
        filteredRecipes.clear();
        lastQuery = search == null ? "" : search.getText();
        String query = normalize(lastQuery);

        for (Recipe<?> recipe : allRecipes) {
            ItemStack stack = recipe.getOutput();
            String name = normalize(stack.getName().getString());
            String recipeId = normalize(recipe.getId().toString());
            String itemId = normalize(stack.getItem().toString());
            if (query.isEmpty() || name.contains(query) || recipeId.contains(query) || itemId.contains(query)) {
                filteredRecipes.add(recipe);
            }
        }

        page = keepPage ? Math.min(oldPage, pageCount() - 1) : 0;
        updateButtons();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void clearSearch() {
        if (search != null) {
            search.setText("");
            search.setTextFieldFocused(true);
            setInitialFocus(search);
        }
    }

    private void changePage(int direction) {
        page = clamp(page + direction, 0, pageCount() - 1);
        updateButtons();
    }

    private void updateButtons() {
        if (clearButton != null) {
            clearButton.active = search != null && !search.getText().isEmpty();
        }
        if (previousButton != null) {
            previousButton.active = page > 0;
        }
        if (nextButton != null) {
            nextButton.active = page + 1 < pageCount();
        }
    }

    private int pageCount() {
        return Math.max(1, (filteredRecipes.size() + pageSize - 1) / pageSize);
    }

    @Override
    public void tick() {
        if (search != null) {
            search.tick();
        }
        updateButtons();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        fill(matrices, panelLeft, panelTop, panelRight, panelBottom, 0xE8101822);
        fill(matrices, panelLeft + 1, panelTop + 1, panelRight - 1, 30, 0xFF172737);

        drawCenteredText(matrices, textRenderer, title, width / 2, 11, 0xFFFFFF);
        drawCenteredText(matrices, textRenderer,
                new LiteralText("Пошук за назвою або ID"),
                width / 2, 25, 0x91AFC9);

        fill(matrices, gridX - 7, gridY - 7, gridX + columns * 22 + 7, gridY + rows * 22 + 7, 0xB20B1119);

        int start = page * pageSize;
        int end = Math.min(filteredRecipes.size(), start + pageSize);
        Recipe<?> hovered = null;
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % columns;
            int row = local / columns;
            int x = gridX + col * 22;
            int y = gridY + row * 22;
            boolean isHovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            fill(matrices, x, y, x + 20, y + 20, isHovered ? 0xFF4F87B8 : 0xFF263C4E);
            fill(matrices, x + 1, y + 1, x + 19, y + 19, isHovered ? 0xFF315E82 : 0xFF1C2D3B);
            ItemStack output = filteredRecipes.get(index).getOutput();
            itemRenderer.renderInGuiWithOverrides(output, x + 2, y + 2);
            itemRenderer.renderGuiItemOverlay(textRenderer, output, x + 2, y + 2);
            if (isHovered) {
                hovered = filteredRecipes.get(index);
            }
        }

        super.render(matrices, mouseX, mouseY, delta);

        if (search != null) {
            search.render(matrices, mouseX, mouseY, delta);
            if (search.getText().isEmpty() && !search.isFocused()) {
                textRenderer.drawWithShadow(matrices, "Напиши, наприклад: піч", searchX + 5, searchY + 6, 0x7F91AFC9);
            }
        }

        String shownQuery = search == null || search.getText().isEmpty()
                ? "Пошук порожній"
                : "Ти написав: “" + search.getText() + "”";
        textRenderer.drawWithShadow(matrices, shownQuery, panelLeft + 8, 62, 0xFFD5E9FF);

        if (filteredRecipes.isEmpty()) {
            drawCenteredText(matrices, textRenderer,
                    new LiteralText("Нічого не знайдено — натисни ×"),
                    width / 2, gridY + 40, 0xFFFF8E8E);
        }

        drawCenteredText(matrices, textRenderer,
                new LiteralText("Сторінка " + (page + 1) + " / " + pageCount() + "  •  знайдено: " + filteredRecipes.size()),
                width / 2, height - 24, 0xD5E9FF);

        if (hovered != null) {
            List<net.minecraft.text.Text> tooltip = new ArrayList<>(getTooltipFromItem(hovered.getOutput()));
            tooltip.add(new LiteralText("§7Рецепт: " + hovered.getId()));
            tooltip.add(new LiteralText("§aНатисни, щоб вибрати"));
            renderTooltip(matrices, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= gridX && mouseY >= gridY) {
            int col = (int) ((mouseX - gridX) / 22);
            int row = (int) ((mouseY - gridY) / 22);
            if (col >= 0 && col < columns && row >= 0 && row < rows) {
                int cellX = gridX + col * 22;
                int cellY = gridY + row * 22;
                if (mouseX < cellX + 20 && mouseY < cellY + 20) {
                    int index = page * pageSize + row * columns + col;
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
            changePage(1);
            return true;
        }
        if (amount > 0 && page > 0) {
            changePage(-1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (filteredRecipes.size() == 1 && client != null) {
                client.openScreen(new QuantityScreen(parent, this, filteredRecipes.get(0)));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (client != null) {
            client.openScreen(parent);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
