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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.registry.Registry;
import ua.credesasq.autocraft.client.VanillaRecipeLoader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemSelectScreen extends Screen {
    private final Screen parent;
    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();

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
    private String notice = "";
    private int noticeTicks;

    public ItemSelectScreen(Screen parent) {
        super(new LiteralText("Автокрафт — усі предмети"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        columns = clamp((width - 44) / 24, 6, 13);
        rows = clamp((height - 156) / 24, 3, 7);
        pageSize = columns * rows;
        gridX = (width - columns * 22) / 2;
        gridY = 77;

        panelLeft = Math.max(7, gridX - 13);
        panelRight = Math.min(width - 7, gridX + columns * 22 + 13);
        panelTop = 5;
        panelBottom = height - 6;

        int searchWidth = Math.min(270, Math.max(130, width - 155));
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

        previousButton = addButton(new ButtonWidget(width / 2 - 76, height - 30, 35, 20,
                new LiteralText("<"), button -> changePage(-1)));
        nextButton = addButton(new ButtonWidget(width / 2 + 41, height - 30, 35, 20,
                new LiteralText(">"), button -> changePage(1)));
        addButton(new ButtonWidget(panelLeft + 7, height - 30, 69, 20,
                new LiteralText("Назад"), button -> onClose()));
        addButton(new ButtonWidget(panelRight - 76, height - 30, 69, 20,
                new LiteralText("Оновити"), button -> {
                    VanillaRecipeLoader.clearCache();
                    loadItems();
                    applyFilter(true);
                }));

        loadItems();
        applyFilter(true);
    }

    private void loadItems() {
        allItems.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        Map<Identifier, Recipe<?>> recipesById = new LinkedHashMap<>();
        if (client.world != null) {
            RecipeManager manager = client.world.getRecipeManager();
            for (Recipe<?> recipe : manager.values()) {
                if (recipe != null && recipe.getType() == RecipeType.CRAFTING && recipe.fits(3, 3)) {
                    recipesById.put(recipe.getId(), recipe);
                }
            }
        }

        for (Recipe<?> recipe : VanillaRecipeLoader.loadCraftingRecipes()) {
            recipesById.putIfAbsent(recipe.getId(), recipe);
        }

        Map<Item, Recipe<?>> bestByOutput = new LinkedHashMap<>();
        for (Recipe<?> recipe : recipesById.values()) {
            addBestRecipe(bestByOutput, recipe);
        }
        if (!bestByOutput.containsKey(Items.FURNACE)) {
            addBestRecipe(bestByOutput, createFallbackFurnaceRecipe());
        }

        for (Item item : Registry.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            Identifier id = Registry.ITEM.getId(item);
            allItems.add(new ItemEntry(item, id, bestByOutput.get(item)));
        }

        allItems.sort(Comparator
                .comparingInt(ItemSelectScreen::priority)
                .thenComparing(entry -> entry.stack.getName().getString().toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.id.toString()));
    }

    private static void addBestRecipe(Map<Item, Recipe<?>> recipes, Recipe<?> recipe) {
        if (recipe == null || recipe.getType() != RecipeType.CRAFTING || !recipe.fits(3, 3)) {
            return;
        }
        ItemStack output = recipe.getOutput();
        if (output.isEmpty()) {
            return;
        }
        Recipe<?> current = recipes.get(output.getItem());
        if (current == null || recipeScore(recipe) < recipeScore(current)) {
            recipes.put(output.getItem(), recipe);
        }
    }

    private static int recipeScore(Recipe<?> recipe) {
        int count = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                count++;
            }
        }
        return count * 10 + (recipe instanceof ShapedRecipe ? 0 : 1);
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

    private static int priority(ItemEntry entry) {
        if (entry.item == Items.FURNACE) return 0;
        if (entry.item == Items.CRAFTING_TABLE) return 1;
        if (entry.item == Items.CHEST) return 2;
        if (entry.item == Items.STICK) return 3;
        if (entry.item == Items.TORCH) return 4;
        return entry.recipe == null ? 200 : 100;
    }

    private void applyFilter(boolean keepPage) {
        int oldPage = page;
        filteredItems.clear();
        lastQuery = search == null ? "" : search.getText();
        String query = normalize(lastQuery);

        for (ItemEntry entry : allItems) {
            String name = normalize(entry.stack.getName().getString());
            String itemId = normalize(entry.id.toString());
            String recipeId = entry.recipe == null ? "" : normalize(entry.recipe.getId().toString());
            if (query.isEmpty() || name.contains(query) || itemId.contains(query) || recipeId.contains(query)) {
                filteredItems.add(entry);
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
        return Math.max(1, (filteredItems.size() + pageSize - 1) / pageSize);
    }

    @Override
    public void tick() {
        if (search != null) {
            search.tick();
        }
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        updateButtons();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        fill(matrices, panelLeft, panelTop, panelRight, panelBottom, 0xE8101822);
        fill(matrices, panelLeft + 1, panelTop + 1, panelRight - 1, 30, 0xFF172737);

        drawCenteredText(matrices, textRenderer, title, width / 2, 10, 0xFFFFFF);
        drawCenteredText(matrices, textRenderer,
                new LiteralText("Усі предмети • затисни Shift над предметом — побачиш рецепт"),
                width / 2, 24, 0x91AFC9);

        fill(matrices, gridX - 7, gridY - 7, gridX + columns * 22 + 7, gridY + rows * 22 + 7, 0xB20B1119);

        int start = page * pageSize;
        int end = Math.min(filteredItems.size(), start + pageSize);
        ItemEntry hovered = null;
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % columns;
            int row = local / columns;
            int x = gridX + col * 22;
            int y = gridY + row * 22;
            boolean isHovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            ItemEntry entry = filteredItems.get(index);

            int outer = entry.recipe == null ? 0xFF684044 : 0xFF263C4E;
            int inner = entry.recipe == null ? 0xFF3A262A : 0xFF1C2D3B;
            if (isHovered) {
                outer = entry.recipe == null ? 0xFFB85A62 : 0xFF4F87B8;
                inner = entry.recipe == null ? 0xFF743A40 : 0xFF315E82;
            }
            fill(matrices, x, y, x + 20, y + 20, outer);
            fill(matrices, x + 1, y + 1, x + 19, y + 19, inner);
            itemRenderer.renderInGuiWithOverrides(entry.stack, x + 2, y + 2);
            itemRenderer.renderGuiItemOverlay(textRenderer, entry.stack, x + 2, y + 2);
            if (entry.recipe == null) {
                textRenderer.drawWithShadow(matrices, "!", x + 13, y + 10, 0xFFFF7777);
            }
            if (isHovered) {
                hovered = entry;
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
                ? "Показано всі предмети"
                : "Ти написав: “" + search.getText() + "”";
        textRenderer.drawWithShadow(matrices, shownQuery, panelLeft + 8, 63, 0xFFD5E9FF);

        if (filteredItems.isEmpty()) {
            drawCenteredText(matrices, textRenderer,
                    new LiteralText("Нічого не знайдено — натисни ×"),
                    width / 2, gridY + 40, 0xFFFF8E8E);
        }

        drawCenteredText(matrices, textRenderer,
                new LiteralText("Сторінка " + (page + 1) + " / " + pageCount()
                        + "  •  предметів: " + filteredItems.size() + " / " + allItems.size()),
                width / 2, height - 24, 0xD5E9FF);

        if (noticeTicks > 0 && !notice.isEmpty()) {
            drawCenteredText(matrices, textRenderer, new LiteralText(notice), width / 2, height - 42, 0xFFFF9090);
        }

        if (hovered != null) {
            if (hasShiftDown()) {
                renderRecipePreview(matrices, hovered, mouseX, mouseY);
            } else {
                List<Text> tooltip = new ArrayList<>(getTooltipFromItem(hovered.stack));
                tooltip.add(new LiteralText("§7ID: " + hovered.id));
                if (hovered.recipe != null) {
                    tooltip.add(new LiteralText("§aЛКМ: вибрати для автокрафту"));
                    tooltip.add(new LiteralText("§eShift: показати рецепт 3×3"));
                } else {
                    tooltip.add(new LiteralText("§cНемає звичайного рецепта у верстаку"));
                }
                renderTooltip(matrices, tooltip, mouseX, mouseY);
            }
        }
    }

    private void renderRecipePreview(MatrixStack matrices, ItemEntry entry, int mouseX, int mouseY) {
        int previewWidth = 126;
        int previewHeight = 91;
        int px = mouseX + 18;
        if (px + previewWidth > width - 5) {
            px = mouseX - previewWidth - 18;
        }
        px = clamp(px, 5, width - previewWidth - 5);
        int py = clamp(mouseY - 20, 5, height - previewHeight - 5);

        fill(matrices, px, py, px + previewWidth, py + previewHeight, 0xF00B1119);
        fill(matrices, px + 1, py + 1, px + previewWidth - 1, py + 17, 0xFF20384A);
        textRenderer.drawWithShadow(matrices, entry.stack.getName().getString(), px + 6, py + 5, 0xFFFFFFFF);

        if (entry.recipe == null) {
            textRenderer.drawWithShadow(matrices, "Немає рецепта у верстаку", px + 8, py + 38, 0xFFFF8E8E);
            return;
        }

        int gx = px + 7;
        int gy = py + 24;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = gx + col * 19;
                int sy = gy + row * 19;
                fill(matrices, sx, sy, sx + 18, sy + 18, 0xFF4A4A4A);
                fill(matrices, sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
            }
        }

        Ingredient[] grid = recipeGrid(entry.recipe);
        for (int i = 0; i < grid.length; i++) {
            Ingredient ingredient = grid[i];
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] choices = ingredient.getMatchingStacks();
            if (choices.length == 0) {
                continue;
            }
            int cycle = (int) ((System.currentTimeMillis() / 900L) % choices.length);
            ItemStack stack = choices[cycle];
            int sx = gx + (i % 3) * 19 + 1;
            int sy = gy + (i / 3) * 19 + 1;
            itemRenderer.renderInGuiWithOverrides(stack, sx, sy);
        }

        textRenderer.drawWithShadow(matrices, "→", px + 69, py + 44, 0xFFFFFFFF);
        int ox = px + 91;
        int oy = py + 39;
        fill(matrices, ox - 2, oy - 2, ox + 20, oy + 20, 0xFF4A4A4A);
        fill(matrices, ox - 1, oy - 1, ox + 19, oy + 19, 0xFF9B9B9B);
        ItemStack output = entry.recipe.getOutput();
        itemRenderer.renderInGuiWithOverrides(output, ox, oy);
        itemRenderer.renderGuiItemOverlay(textRenderer, output, ox, oy);
        textRenderer.drawWithShadow(matrices, "Відпусти Shift, щоб обрати", px + 7, py + 79, 0xFF9FC7E5);
    }

    private static Ingredient[] recipeGrid(Recipe<?> recipe) {
        Ingredient[] grid = new Ingredient[9];
        DefaultedList<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int source = row * width + col;
                    if (source < ingredients.size()) {
                        grid[row * 3 + col] = ingredients.get(source);
                    }
                }
            }
        } else {
            int target = 0;
            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty() && target < 9) {
                    grid[target++] = ingredient;
                }
            }
        }
        return grid;
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
                    if (index >= 0 && index < filteredItems.size() && client != null) {
                        ItemEntry entry = filteredItems.get(index);
                        if (entry.recipe == null) {
                            notice = "Для “" + entry.stack.getName().getString() + "” немає звичайного рецепта 3×3";
                            noticeTicks = 80;
                            return true;
                        }
                        client.openScreen(new QuantityScreen(parent, this, entry.recipe));
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
    public void onClose() {
        if (client != null) {
            client.openScreen(parent);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ItemEntry {
        private final Item item;
        private final Identifier id;
        private final Recipe<?> recipe;
        private final ItemStack stack;

        private ItemEntry(Item item, Identifier id, Recipe<?> recipe) {
            this.item = item;
            this.id = id;
            this.recipe = recipe;
            this.stack = recipe == null || recipe.getOutput().isEmpty()
                    ? new ItemStack(item)
                    : recipe.getOutput().copy();
        }
    }
}
