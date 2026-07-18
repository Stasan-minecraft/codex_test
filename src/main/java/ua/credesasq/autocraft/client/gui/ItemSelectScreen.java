package ua.credesasq.autocraft.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
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
    private static final Item[] QUICK_ITEMS = {
            Items.FURNACE,
            Items.CRAFTING_TABLE,
            Items.CHEST,
            Items.TORCH,
            Items.STICK
    };
    private static final String[] QUICK_LABELS = {
            "ПІЧ", "ВЕРСТАК", "СКРИНЯ", "ФАКЕЛ", "ПАЛИЦЯ"
    };

    private final Screen parent;
    private final List<ItemEntry> allItems = new ArrayList<>();
    private final List<ItemEntry> filteredItems = new ArrayList<>();
    private final Map<Item, ItemEntry> byItem = new LinkedHashMap<>();
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();

    private TextFieldWidget search;
    private ButtonWidget clearButton;
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;

    private Category category = Category.ALL;
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
    private int quickX;
    private int quickY;
    private int quickBoxWidth;
    private String lastQuery = "";
    private String notice = "";
    private int noticeTicks;

    public ItemSelectScreen(Screen parent) {
        super(new LiteralText("AutoCraft 1.3 — вибір предмета"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        columns = clamp((width - 44) / 26, 6, 13);
        rows = clamp((height - 205) / 26, 3, 7);
        pageSize = columns * rows;
        gridX = (width - columns * 24) / 2;
        gridY = 132;

        panelLeft = Math.max(6, gridX - 14);
        panelRight = Math.min(width - 6, gridX + columns * 24 + 14);
        panelTop = 5;
        panelBottom = height - 6;

        int searchWidth = Math.min(270, Math.max(125, width - 170));
        searchX = (width - searchWidth - 25) / 2;
        searchY = 34;
        search = new TextFieldWidget(textRenderer, searchX, searchY, searchWidth, 20,
                new LiteralText("Пошук предмета"));
        search.setMaxLength(80);
        search.setChangedListener(value -> applyFilter(false));
        search.setText(lastQuery);
        addChild(search);
        setInitialFocus(search);

        clearButton = addButton(new ButtonWidget(searchX + searchWidth + 4, searchY, 21, 20,
                new LiteralText("×"), button -> clearSearch()));

        quickBoxWidth = Math.min(52, Math.max(42, (panelRight - panelLeft - 20) / QUICK_ITEMS.length));
        quickX = width / 2 - (quickBoxWidth * QUICK_ITEMS.length) / 2;
        quickY = 59;

        int tabY = 101;
        int available = panelRight - panelLeft - 14;
        int tabWidth = Math.max(38, available / Category.values().length);
        int tabsTotal = tabWidth * Category.values().length;
        int tabX = width / 2 - tabsTotal / 2;
        categoryButtons.clear();
        for (Category value : Category.values()) {
            final Category selected = value;
            ButtonWidget button = addButton(new ButtonWidget(tabX, tabY, tabWidth - 2, 20,
                    new LiteralText(value.label), ignored -> {
                category = selected;
                applyFilter(false);
                updateCategoryButtons();
            }));
            categoryButtons.add(button);
            tabX += tabWidth;
        }

        previousButton = addButton(new ButtonWidget(width / 2 - 78, height - 30, 36, 20,
                new LiteralText("<"), button -> changePage(-1)));
        nextButton = addButton(new ButtonWidget(width / 2 + 42, height - 30, 36, 20,
                new LiteralText(">"), button -> changePage(1)));
        addButton(new ButtonWidget(panelLeft + 7, height - 30, 70, 20,
                new LiteralText("Назад"), button -> onClose()));
        addButton(new ButtonWidget(panelRight - 77, height - 30, 70, 20,
                new LiteralText("Оновити"), button -> {
            VanillaRecipeLoader.clearCache();
            loadItems();
            applyFilter(true);
        }));

        loadItems();
        applyFilter(true);
        updateCategoryButtons();
    }

    private void loadItems() {
        allItems.clear();
        filteredItems.clear();
        byItem.clear();

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
        ensureEssentialRecipes(bestByOutput);

        // Explicitly inject the furnace first. It stays available even if a server sends a broken registry/recipe list.
        addEntry(Items.FURNACE, bestByOutput.get(Items.FURNACE));
        for (Item quick : QUICK_ITEMS) {
            addEntry(quick, bestByOutput.get(quick));
        }
        for (Item item : Registry.ITEM) {
            if (item != Items.AIR) {
                addEntry(item, bestByOutput.get(item));
            }
        }

        allItems.sort(Comparator
                .comparingInt(ItemSelectScreen::priority)
                .thenComparing(entry -> entry.stack.getName().getString().toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.id.toString()));
    }

    private void addEntry(Item item, Recipe<?> recipe) {
        if (item == null || item == Items.AIR || byItem.containsKey(item)) {
            return;
        }
        ItemEntry entry = new ItemEntry(item, Registry.ITEM.getId(item), recipe);
        byItem.put(item, entry);
        allItems.add(entry);
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

    private static void ensureEssentialRecipes(Map<Item, Recipe<?>> recipes) {
        recipes.putIfAbsent(Items.FURNACE, createFurnaceRecipe());
        recipes.putIfAbsent(Items.CRAFTING_TABLE, createCraftingTableRecipe());
        recipes.putIfAbsent(Items.CHEST, createChestRecipe());
        recipes.putIfAbsent(Items.STICK, createStickRecipe());
        recipes.putIfAbsent(Items.TORCH, createTorchRecipe());
    }

    private static Ingredient planks() {
        return Ingredient.ofItems(
                Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.CRIMSON_PLANKS, Items.WARPED_PLANKS
        );
    }

    private static Recipe<?> createFurnaceRecipe() {
        DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(9, Ingredient.EMPTY);
        Ingredient stone = Ingredient.ofItems(Items.COBBLESTONE, Items.BLACKSTONE);
        for (int i = 0; i < 9; i++) {
            if (i != 4) ingredients.set(i, stone);
        }
        return new ShapedRecipe(new Identifier("autocraft", "guaranteed_furnace"), "", 3, 3,
                ingredients, new ItemStack(Items.FURNACE));
    }

    private static Recipe<?> createCraftingTableRecipe() {
        DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(4, planks());
        return new ShapedRecipe(new Identifier("autocraft", "guaranteed_crafting_table"), "", 2, 2,
                ingredients, new ItemStack(Items.CRAFTING_TABLE));
    }

    private static Recipe<?> createChestRecipe() {
        DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(9, planks());
        ingredients.set(4, Ingredient.EMPTY);
        return new ShapedRecipe(new Identifier("autocraft", "guaranteed_chest"), "", 3, 3,
                ingredients, new ItemStack(Items.CHEST));
    }

    private static Recipe<?> createStickRecipe() {
        DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(2, planks());
        return new ShapedRecipe(new Identifier("autocraft", "guaranteed_stick"), "", 1, 2,
                ingredients, new ItemStack(Items.STICK, 4));
    }

    private static Recipe<?> createTorchRecipe() {
        DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(2, Ingredient.EMPTY);
        ingredients.set(0, Ingredient.ofItems(Items.COAL, Items.CHARCOAL));
        ingredients.set(1, Ingredient.ofItems(Items.STICK));
        return new ShapedRecipe(new Identifier("autocraft", "guaranteed_torch"), "", 1, 2,
                ingredients, new ItemStack(Items.TORCH, 4));
    }

    private static int priority(ItemEntry entry) {
        if (entry.item == Items.FURNACE) return 0;
        if (entry.item == Items.CRAFTING_TABLE) return 1;
        if (entry.item == Items.CHEST) return 2;
        if (entry.item == Items.TORCH) return 3;
        if (entry.item == Items.STICK) return 4;
        return entry.recipe == null ? 200 : 100;
    }

    private void applyFilter(boolean keepPage) {
        int oldPage = page;
        filteredItems.clear();
        lastQuery = search == null ? "" : search.getText();
        String query = normalize(lastQuery);

        for (ItemEntry entry : allItems) {
            if (!matchesCategory(entry)) {
                continue;
            }
            String name = normalize(entry.stack.getName().getString());
            String itemId = normalize(entry.id.toString());
            String recipeId = entry.recipe == null ? "" : normalize(entry.recipe.getId().toString());
            String aliases = aliases(entry.item);
            if (query.isEmpty() || name.contains(query) || itemId.contains(query)
                    || recipeId.contains(query) || aliases.contains(query)) {
                filteredItems.add(entry);
            }
        }
        page = keepPage ? Math.min(oldPage, pageCount() - 1) : 0;
        updateButtons();
    }

    private boolean matchesCategory(ItemEntry entry) {
        String id = entry.id.getPath();
        switch (category) {
            case RECIPES:
                return entry.recipe != null;
            case BLOCKS:
                return entry.item instanceof BlockItem;
            case TOOLS:
                return containsAny(id, "sword", "pickaxe", "axe", "shovel", "hoe", "helmet",
                        "chestplate", "leggings", "boots", "bow", "crossbow", "trident", "shield",
                        "shears", "fishing_rod", "flint_and_steel");
            case FOOD:
                return containsAny(id, "apple", "bread", "carrot", "potato", "beef", "porkchop",
                        "chicken", "mutton", "rabbit", "cod", "salmon", "stew", "soup", "pie",
                        "cookie", "cake", "melon", "berries", "honey_bottle", "kelp", "beetroot",
                        "mushroom", "chorus_fruit");
            case REDSTONE:
                return containsAny(id, "redstone", "piston", "repeater", "comparator", "observer",
                        "dispenser", "dropper", "hopper", "lever", "button", "pressure_plate",
                        "tripwire", "daylight_detector", "target", "note_block", "rail", "minecart", "tnt");
            case OTHER:
                return !(entry.item instanceof BlockItem)
                        && !matchesCategoryFor(entry, Category.TOOLS)
                        && !matchesCategoryFor(entry, Category.FOOD)
                        && !matchesCategoryFor(entry, Category.REDSTONE);
            default:
                return true;
        }
    }

    private boolean matchesCategoryFor(ItemEntry entry, Category test) {
        Category before = category;
        category = test;
        boolean result = matchesCategory(entry);
        category = before;
        return result;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static String aliases(Item item) {
        if (item == Items.FURNACE) return "піч печь furnace oven";
        if (item == Items.CRAFTING_TABLE) return "верстак crafting table крафт";
        if (item == Items.CHEST) return "скриня сундук chest";
        if (item == Items.TORCH) return "факел torch світло свет";
        if (item == Items.STICK) return "палиця палка stick";
        return "";
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
        if (clearButton != null) clearButton.active = search != null && !search.getText().isEmpty();
        if (previousButton != null) previousButton.active = page > 0;
        if (nextButton != null) nextButton.active = page + 1 < pageCount();
    }

    private void updateCategoryButtons() {
        Category[] values = Category.values();
        for (int i = 0; i < categoryButtons.size() && i < values.length; i++) {
            Category value = values[i];
            categoryButtons.get(i).setMessage(new LiteralText(value == category
                    ? "[" + value.label + "]"
                    : value.label));
        }
    }

    private int pageCount() {
        return Math.max(1, (filteredItems.size() + pageSize - 1) / pageSize);
    }

    @Override
    public void tick() {
        if (search != null) search.tick();
        if (noticeTicks > 0) noticeTicks--;
        updateButtons();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        fill(matrices, panelLeft, panelTop, panelRight, panelBottom, 0xED0B121B);
        fill(matrices, panelLeft + 1, panelTop + 1, panelRight - 1, 29, 0xFF173247);
        fill(matrices, panelLeft + 1, 29, panelRight - 1, 58, 0xFF101E2A);

        drawCenteredText(matrices, textRenderer, title, width / 2, 9, 0xFFFFFF);
        drawCenteredText(matrices, textRenderer,
                new LiteralText("Піч завжди зверху • Shift над предметом = рецепт 3×3"),
                width / 2, 22, 0x8FD6FF);

        ItemEntry hovered = renderQuickBar(matrices, mouseX, mouseY);

        fill(matrices, gridX - 7, gridY - 7, gridX + columns * 24 + 7,
                gridY + rows * 24 + 7, 0xB2070D13);

        int start = page * pageSize;
        int end = Math.min(filteredItems.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int col = local % columns;
            int row = local / columns;
            int x = gridX + col * 24;
            int y = gridY + row * 24;
            boolean isHovered = inside(mouseX, mouseY, x, y, 22, 22);
            ItemEntry entry = filteredItems.get(index);

            int outer = entry.recipe == null ? 0xFF604047 : 0xFF27485D;
            int inner = entry.recipe == null ? 0xFF30252A : 0xFF152A37;
            if (isHovered) {
                outer = entry.recipe == null ? 0xFFB85A62 : 0xFF60B4E8;
                inner = entry.recipe == null ? 0xFF703A40 : 0xFF24597A;
            }
            fill(matrices, x, y, x + 22, y + 22, outer);
            fill(matrices, x + 1, y + 1, x + 21, y + 21, inner);
            itemRenderer.renderInGuiWithOverrides(entry.stack, x + 3, y + 3);
            itemRenderer.renderGuiItemOverlay(textRenderer, entry.stack, x + 3, y + 3);
            if (entry.recipe == null) {
                textRenderer.drawWithShadow(matrices, "!", x + 15, y + 12, 0xFFFF7777);
            }
            if (isHovered) hovered = entry;
        }

        super.render(matrices, mouseX, mouseY, delta);

        if (search != null) {
            search.render(matrices, mouseX, mouseY, delta);
            if (search.getText().isEmpty() && !search.isFocused()) {
                textRenderer.drawWithShadow(matrices, "Пошук: піч / печь / furnace", searchX + 5,
                        searchY + 6, 0x7F9BC7E3);
            }
        }

        String shownQuery = search == null || search.getText().isEmpty()
                ? "Категорія: " + category.longLabel
                : "Пошук: “" + search.getText() + "” • " + category.longLabel;
        textRenderer.drawWithShadow(matrices, shownQuery, panelLeft + 8, 123, 0xFFD5E9FF);

        if (filteredItems.isEmpty()) {
            drawCenteredText(matrices, textRenderer,
                    new LiteralText("Нічого не знайдено — натисни × або вибери «Всі»"),
                    width / 2, gridY + 42, 0xFFFF8E8E);
        }

        drawCenteredText(matrices, textRenderer,
                new LiteralText("Сторінка " + (page + 1) + " / " + pageCount()
                        + " • показано " + filteredItems.size() + " з " + allItems.size()),
                width / 2, height - 24, 0xD5E9FF);

        if (noticeTicks > 0 && !notice.isEmpty()) {
            fill(matrices, width / 2 - 150, height - 49, width / 2 + 150, height - 34, 0xD0602020);
            drawCenteredText(matrices, textRenderer, new LiteralText(notice), width / 2,
                    height - 45, 0xFFFFB0B0);
        }

        if (hovered != null) {
            if (hasShiftDown()) {
                renderRecipePreview(matrices, hovered, mouseX, mouseY);
            } else {
                renderItemTooltip(matrices, hovered, mouseX, mouseY);
            }
        }
    }

    private ItemEntry renderQuickBar(MatrixStack matrices, int mouseX, int mouseY) {
        ItemEntry hovered = null;
        textRenderer.drawWithShadow(matrices, "ШВИДКИЙ КРАФТ", panelLeft + 8, quickY + 11, 0xFF86CFFF);
        for (int i = 0; i < QUICK_ITEMS.length; i++) {
            int x = quickX + i * quickBoxWidth;
            int y = quickY;
            boolean over = inside(mouseX, mouseY, x, y, quickBoxWidth - 3, 37);
            int outer = i == 0 ? 0xFFFF8A28 : 0xFF3A6782;
            int inner = i == 0 ? 0xFF703A10 : 0xFF173344;
            if (over) {
                outer = i == 0 ? 0xFFFFB35C : 0xFF6BC5F1;
                inner = i == 0 ? 0xFFA45516 : 0xFF285A75;
            }
            fill(matrices, x, y, x + quickBoxWidth - 3, y + 37, outer);
            fill(matrices, x + 1, y + 1, x + quickBoxWidth - 4, y + 36, inner);
            ItemEntry entry = byItem.get(QUICK_ITEMS[i]);
            ItemStack stack = entry == null ? new ItemStack(QUICK_ITEMS[i]) : entry.stack;
            itemRenderer.renderInGuiWithOverrides(stack, x + (quickBoxWidth - 19) / 2, y + 3);
            String label = QUICK_LABELS[i];
            textRenderer.drawWithShadow(matrices, label,
                    x + (quickBoxWidth - 3 - textRenderer.getWidth(label)) / 2,
                    y + 24, 0xFFFFFFFF);
            if (over && entry != null) hovered = entry;
        }
        return hovered;
    }

    private void renderItemTooltip(MatrixStack matrices, ItemEntry entry, int mouseX, int mouseY) {
        List<Text> tooltip = new ArrayList<>(getTooltipFromItem(entry.stack));
        tooltip.add(new LiteralText("§7ID: " + entry.id));
        if (entry.recipe != null) {
            tooltip.add(new LiteralText("§aЛКМ: вибрати кількість"));
            tooltip.add(new LiteralText("§eShift: велике прев’ю рецепта"));
        } else {
            tooltip.add(new LiteralText("§cНемає звичайного рецепта у верстаку"));
        }
        renderTooltip(matrices, tooltip, mouseX, mouseY);
    }

    private void renderRecipePreview(MatrixStack matrices, ItemEntry entry, int mouseX, int mouseY) {
        int previewWidth = 148;
        int previewHeight = 108;
        int px = mouseX + 18;
        if (px + previewWidth > width - 5) px = mouseX - previewWidth - 18;
        px = clamp(px, 5, width - previewWidth - 5);
        int py = clamp(mouseY - 24, 5, height - previewHeight - 5);

        fill(matrices, px, py, px + previewWidth, py + previewHeight, 0xF4070D13);
        fill(matrices, px + 1, py + 1, px + previewWidth - 1, py + 20, 0xFF1D4760);
        textRenderer.drawWithShadow(matrices, entry.stack.getName().getString(), px + 7, py + 6, 0xFFFFFFFF);

        if (entry.recipe == null) {
            itemRenderer.renderInGuiWithOverrides(entry.stack, px + 65, py + 35);
            textRenderer.drawWithShadow(matrices, "Цей предмет не крафтиться", px + 15, py + 62, 0xFFFF8E8E);
            textRenderer.drawWithShadow(matrices, "у звичайній сітці 3×3", px + 19, py + 75, 0xFFFF8E8E);
            return;
        }

        int gx = px + 8;
        int gy = py + 27;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = gx + col * 20;
                int sy = gy + row * 20;
                fill(matrices, sx, sy, sx + 19, sy + 19, 0xFF4D5960);
                fill(matrices, sx + 1, sy + 1, sx + 18, sy + 18, 0xFF99A3A8);
            }
        }

        Ingredient[] grid = recipeGrid(entry.recipe);
        for (int i = 0; i < grid.length; i++) {
            Ingredient ingredient = grid[i];
            if (ingredient == null || ingredient.isEmpty()) continue;
            ItemStack[] choices = ingredient.getMatchingStacks();
            if (choices.length == 0) continue;
            int cycle = (int) ((System.currentTimeMillis() / 900L) % choices.length);
            int sx = gx + (i % 3) * 20 + 2;
            int sy = gy + (i / 3) * 20 + 2;
            itemRenderer.renderInGuiWithOverrides(choices[cycle], sx, sy);
        }

        textRenderer.drawWithShadow(matrices, "→", px + 75, py + 51, 0xFFFFFFFF);
        int ox = px + 105;
        int oy = py + 45;
        fill(matrices, ox - 3, oy - 3, ox + 21, oy + 21, 0xFF4D5960);
        fill(matrices, ox - 2, oy - 2, ox + 20, oy + 20, 0xFFB7C1C6);
        ItemStack output = entry.recipe.getOutput();
        itemRenderer.renderInGuiWithOverrides(output, ox, oy);
        itemRenderer.renderGuiItemOverlay(textRenderer, output, ox, oy);
        textRenderer.drawWithShadow(matrices, "ЛКМ після Shift — вибрати", px + 9, py + 94, 0xFF9FD8F5);
    }

    private static Ingredient[] recipeGrid(Recipe<?> recipe) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < QUICK_ITEMS.length; i++) {
                int x = quickX + i * quickBoxWidth;
                if (inside(mouseX, mouseY, x, quickY, quickBoxWidth - 3, 37)) {
                    ItemEntry entry = byItem.get(QUICK_ITEMS[i]);
                    return chooseEntry(entry);
                }
            }

            if (mouseX >= gridX && mouseY >= gridY) {
                int col = (int) ((mouseX - gridX) / 24);
                int row = (int) ((mouseY - gridY) / 24);
                if (col >= 0 && col < columns && row >= 0 && row < rows) {
                    int cellX = gridX + col * 24;
                    int cellY = gridY + row * 24;
                    if (inside(mouseX, mouseY, cellX, cellY, 22, 22)) {
                        int index = page * pageSize + row * columns + col;
                        if (index >= 0 && index < filteredItems.size()) {
                            return chooseEntry(filteredItems.get(index));
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean chooseEntry(ItemEntry entry) {
        if (entry == null) return true;
        if (entry.recipe == null) {
            notice = "Для «" + entry.stack.getName().getString() + "» немає рецепта 3×3";
            noticeTicks = 90;
            return true;
        }
        if (client != null) client.openScreen(new QuantityScreen(parent, this, entry.recipe));
        return true;
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
        if (client != null) client.openScreen(parent);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Category {
        ALL("Всі", "усі предмети"),
        RECIPES("Рецепти", "лише предмети з рецептом"),
        BLOCKS("Блоки", "блоки"),
        TOOLS("Речі", "інструменти, броня та зброя"),
        FOOD("Їжа", "їжа"),
        REDSTONE("Редстоун", "редстоун і механізми"),
        OTHER("Інше", "інші предмети");

        private final String label;
        private final String longLabel;

        Category(String label, String longLabel) {
            this.label = label;
            this.longLabel = longLabel;
        }
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
