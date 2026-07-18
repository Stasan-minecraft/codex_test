package ua.credesasq.autocraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Loads Minecraft's bundled vanilla recipe JSON files, independently of the server recipe sync. */
public final class VanillaRecipeLoader {
    private static List<Recipe<?>> cachedCraftingRecipes;

    private VanillaRecipeLoader() {
    }

    public static synchronized List<Recipe<?>> loadCraftingRecipes() {
        if (cachedCraftingRecipes != null) {
            return cachedCraftingRecipes;
        }

        Map<Identifier, Recipe<?>> recipes = new LinkedHashMap<>();
        Optional<ModContainer> minecraft = FabricLoader.getInstance().getModContainer("minecraft");
        if (minecraft.isPresent()) {
            for (Path root : minecraft.get().getRootPaths()) {
                loadFromRoot(root, recipes);
            }
        }

        cachedCraftingRecipes = Collections.unmodifiableList(new ArrayList<>(recipes.values()));
        return cachedCraftingRecipes;
    }

    public static synchronized void clearCache() {
        cachedCraftingRecipes = null;
    }

    private static void loadFromRoot(Path root, Map<Identifier, Recipe<?>> recipes) {
        Path recipesRoot = root.resolve("data").resolve("minecraft").resolve("recipes");
        if (!Files.isDirectory(recipesRoot)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(recipesRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> loadRecipeFile(recipesRoot, path, recipes));
        } catch (IOException ignored) {
            // The server recipe list and explicit fallbacks are still available.
        }
    }

    private static void loadRecipeFile(Path recipesRoot, Path file, Map<Identifier, Recipe<?>> recipes) {
        String relative = recipesRoot.relativize(file).toString().replace('\\', '/');
        if (!relative.endsWith(".json")) {
            return;
        }
        relative = relative.substring(0, relative.length() - 5);
        Identifier id = new Identifier("minecraft", relative);

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            Recipe<?> recipe = RecipeManager.deserialize(id, json);
            if (recipe != null && recipe.getType() == RecipeType.CRAFTING && recipe.fits(3, 3)) {
                recipes.put(id, recipe);
            }
        } catch (Exception ignored) {
            // Special/dynamic recipes may not have a fixed output and are skipped.
        }
    }
}
