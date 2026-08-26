package dev.xyat.kineticrecipe.recipehud;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RecipeDatabase {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final List<RecipeRecord> records = new ArrayList<>();
    private static boolean loaded;

    private RecipeDatabase() {
    }

    public static synchronized void loadDatabase() {
        if (loaded) {
            return;
        }
        reloadDatabase();
    }

    public static synchronized void reloadDatabase() {
        RecipeConfigStore.Snapshot snapshot = RecipeConfigStore.load();
        records.clear();
        records.addAll(snapshot.recipes());
        loaded = true;
        LOGGER.info("Loaded {} configured memory recipes", records.size());
    }

    public static synchronized void setClientRecords(List<RecipeRecord> newRecords) {
        records.clear();
        records.addAll(newRecords);
        loaded = true;
    }

    public static synchronized List<RecipeRecord> snapshot() {
        return new ArrayList<>(records);
    }

    public static void clear() {
    }
}
