package ua.credesasq.autocraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.LiteralText;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AutoCraftManager {
    public static final AutoCraftManager INSTANCE = new AutoCraftManager();

    private final Deque<ClickAction> actions = new ArrayDeque<>();
    private Recipe<?> recipe;
    private int targetAmount;
    private int craftedAmount;
    private int cooldown;
    private boolean active;
    private boolean waitingForTake;
    private boolean stopRequested;
    private int pendingTakeAmount;
    private int pendingResultCount;
    private String stopReason = "Зупинено";
    private String status = "Готово до роботи";

    private AutoCraftManager() {
    }

    public void start(Recipe<?> selectedRecipe, int amount) {
        this.recipe = selectedRecipe;
        this.targetAmount = Math.max(0, amount);
        this.craftedAmount = 0;
        this.cooldown = 4;
        this.active = true;
        this.waitingForTake = false;
        this.stopRequested = false;
        this.pendingTakeAmount = 0;
        this.pendingResultCount = 0;
        this.actions.clear();
        this.status = amount == 0 ? "Автокрафт: без ліміту" : "Автокрафт: 0 / " + amount;
    }

    public void requestStop(String reason) {
        if (!active) {
            return;
        }
        this.stopRequested = true;
        this.stopReason = reason == null || reason.isEmpty() ? "Зупинено вручну" : reason;
        this.status = "Безпечна зупинка...";
    }

    public void stop(String reason) {
        this.active = false;
        this.stopRequested = false;
        this.actions.clear();
        this.waitingForTake = false;
        this.pendingTakeAmount = 0;
        this.pendingResultCount = 0;
        this.status = reason;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isStopping() {
        return active && stopRequested;
    }

    public int getCraftedAmount() {
        return craftedAmount;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public String getStatus() {
        return status;
    }

    public ItemStack getSelectedOutput() {
        return recipe == null ? ItemStack.EMPTY : recipe.getOutput();
    }

    public void tick(MinecraftClient client) {
        if (!active) {
            return;
        }
        if (client.player == null || client.interactionManager == null) {
            stop("Автокрафт зупинено: немає гравця");
            return;
        }
        if (!(client.currentScreen instanceof CraftingScreen)
                || !(client.player.currentScreenHandler instanceof CraftingScreenHandler)) {
            stop("Автокрафт зупинено: верстак закрито");
            return;
        }
        if (recipe == null) {
            stop("Автокрафт зупинено: рецепт не вибрано");
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        CraftingScreenHandler handler = (CraftingScreenHandler) client.player.currentScreenHandler;

        if (waitingForTake) {
            ItemStack now = handler.getSlot(0).getStack();
            int nowCount = now.isEmpty() ? 0 : now.getCount();
            int moved = Math.max(0, pendingResultCount - nowCount);
            if (moved <= 0) {
                stop("Стоп: інвентар заповнений");
                notifyPlayer(client.player, status);
                return;
            }
            craftedAmount += Math.min(pendingTakeAmount, moved);
            waitingForTake = false;
            pendingTakeAmount = 0;
            pendingResultCount = 0;
            updateProgress();
            if (targetAmount > 0 && craftedAmount >= targetAmount) {
                stop("Готово: скрафчено " + craftedAmount);
                notifyPlayer(client.player, status);
                return;
            }
        }

        if (!actions.isEmpty()) {
            ClickAction action = actions.removeFirst();
            client.interactionManager.clickSlot(
                    handler.syncId,
                    action.slotId,
                    action.button,
                    action.type,
                    client.player
            );
            cooldown = 1;
            return;
        }

        if (stopRequested) {
            if (!client.player.inventory.getCursorStack().isEmpty()) {
                stop("Зупинено: поклади предмет із курсора в інвентар");
                notifyPlayer(client.player, status);
                return;
            }
            if (hasItemsInGrid(handler)) {
                queueGridCleanup(handler);
                status = "Повертаю інгредієнти в інвентар...";
                return;
            }
            String reason = stopReason;
            stop(reason);
            notifyPlayer(client.player, reason);
            return;
        }

        ItemStack result = handler.getSlot(0).getStack();
        if (!result.isEmpty() && ItemStack.areItemsEqualIgnoreDamage(result, recipe.getOutput())) {
            int resultCount = result.getCount();
            pendingTakeAmount = resultCount;
            pendingResultCount = resultCount;
            waitingForTake = true;
            client.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
            cooldown = 4;
            return;
        }

        if (hasItemsInGrid(handler)) {
            queueGridCleanup(handler);
            status = "Очищення сітки крафту...";
            return;
        }

        List<Placement> placements = createPlacementPlan(handler);
        if (placements == null) {
            stop("Стоп: закінчилися потрібні ресурси");
            notifyPlayer(client.player, status);
            return;
        }

        if (!client.player.inventory.getCursorStack().isEmpty()) {
            stop("Стоп: звільни предмет із курсора");
            notifyPlayer(client.player, status);
            return;
        }

        for (Placement placement : placements) {
            actions.addLast(new ClickAction(placement.sourceSlot, 0, SlotActionType.PICKUP));
            actions.addLast(new ClickAction(placement.targetSlot, 1, SlotActionType.PICKUP));
            actions.addLast(new ClickAction(placement.sourceSlot, 0, SlotActionType.PICKUP));
        }
        status = "Розкладаю інгредієнти...";
    }

    private void queueGridCleanup(CraftingScreenHandler handler) {
        for (int slot = 1; slot <= 9; slot++) {
            if (handler.getSlot(slot).hasStack()) {
                actions.addLast(new ClickAction(slot, 0, SlotActionType.QUICK_MOVE));
            }
        }
    }

    private void updateProgress() {
        if (targetAmount == 0) {
            status = "Автокрафт: " + craftedAmount + " (без ліміту)";
        } else {
            status = "Автокрафт: " + craftedAmount + " / " + targetAmount;
        }
    }

    private static boolean hasItemsInGrid(CraftingScreenHandler handler) {
        for (int i = 1; i <= 9; i++) {
            if (handler.getSlot(i).hasStack()) {
                return true;
            }
        }
        return false;
    }

    private List<Placement> createPlacementPlan(CraftingScreenHandler handler) {
        DefaultedList<Ingredient> ingredients = recipe.getIngredients();
        List<IngredientTarget> targets = new ArrayList<>();

        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int ingredientIndex = row * width + col;
                    if (ingredientIndex >= ingredients.size()) {
                        continue;
                    }
                    Ingredient ingredient = ingredients.get(ingredientIndex);
                    if (!ingredient.isEmpty()) {
                        targets.add(new IngredientTarget(ingredient, 1 + row * 3 + col));
                    }
                }
            }
        } else {
            int targetSlot = 1;
            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    targets.add(new IngredientTarget(ingredient, targetSlot++));
                }
            }
        }

        if (targets.isEmpty()) {
            return null;
        }

        Map<Integer, Integer> reserved = new HashMap<>();
        List<Placement> placements = new ArrayList<>();
        for (IngredientTarget target : targets) {
            int source = findSourceSlot(handler, target.ingredient, reserved);
            if (source < 0) {
                return null;
            }
            reserved.put(source, reserved.getOrDefault(source, 0) + 1);
            placements.add(new Placement(source, target.targetSlot));
        }
        return placements;
    }

    private static int findSourceSlot(ScreenHandler handler, Ingredient ingredient, Map<Integer, Integer> reserved) {
        for (int slotId = 10; slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.getSlot(slotId);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            int alreadyReserved = reserved.getOrDefault(slotId, 0);
            if (stack.getCount() > alreadyReserved) {
                return slotId;
            }
        }
        return -1;
    }

    private static void notifyPlayer(PlayerEntity player, String message) {
        player.sendMessage(new LiteralText("[AutoCraft] " + message), false);
    }

    private static final class IngredientTarget {
        private final Ingredient ingredient;
        private final int targetSlot;

        private IngredientTarget(Ingredient ingredient, int targetSlot) {
            this.ingredient = ingredient;
            this.targetSlot = targetSlot;
        }
    }

    private static final class Placement {
        private final int sourceSlot;
        private final int targetSlot;

        private Placement(int sourceSlot, int targetSlot) {
            this.sourceSlot = sourceSlot;
            this.targetSlot = targetSlot;
        }
    }

    private static final class ClickAction {
        private final int slotId;
        private final int button;
        private final SlotActionType type;

        private ClickAction(int slotId, int button, SlotActionType type) {
            this.slotId = slotId;
            this.button = button;
            this.type = type;
        }
    }
}
