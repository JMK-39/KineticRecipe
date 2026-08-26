package dev.xyat.kineticrecipe.recipehud.client.gui;

import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;

public final class RecipeEditSessionState {
    private static boolean pendingRecipeApply;

    private RecipeEditSessionState() {
    }

    public static void markPendingRecipeApply() {
        pendingRecipeApply = true;
    }

    public static void applyPendingAndClear() {
        if (!pendingRecipeApply) {
            return;
        }
        pendingRecipeApply = false;
        RecipeNetwork.sendApplyPendingRecipesRequest();
    }
}
