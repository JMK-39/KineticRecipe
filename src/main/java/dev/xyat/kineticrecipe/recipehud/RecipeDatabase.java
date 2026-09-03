package dev.xyat.kineticrecipe.recipehud;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RecipeDatabase {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final List<RecipeRecord> records = new ArrayList<>();
    public static final List<RecipeRecord> invalidRecords = new ArrayList<>();
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
        invalidRecords.clear();
        invalidRecords.addAll(snapshot.invalidRecipes());
        loaded = true;
        LOGGER.info(
                "Loaded {} configured memory recipes and {} invalid recipe entries",
                records.size(),
                invalidRecords.size()
        );
    }

    public static synchronized void setClientRecords(List<RecipeRecord> newRecords) {
        records.clear();
        invalidRecords.clear();
        for (RecipeRecord record : newRecords) {
            if (record == null) {
                continue;
            }
            if (record.invalidConfig) {
                invalidRecords.add(record);
            } else {
                records.add(record);
            }
        }
        loaded = true;
    }

    public static synchronized List<RecipeRecord> snapshot() {
        return new ArrayList<>(records);
    }

    public static synchronized List<RecipeRecord> editorSnapshot() {
        List<RecipeRecord> result = new ArrayList<>(records.size() + invalidRecords.size());
        result.addAll(records);
        result.addAll(invalidRecords);
        return result;
    }

    public static void clear() {
    }
}
