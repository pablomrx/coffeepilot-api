package com.coffeepilot.api.recipe.dto;

import com.coffeepilot.api.ingredient.IngredientUnit;
import com.coffeepilot.api.recipe.RecipeItem;

import java.math.BigDecimal;

public record RecipeItemResponse(
        Long ingredientId,
        String ingredientName,
        IngredientUnit unit,
        BigDecimal quantity,
        boolean ingredientActive
) {

    public static RecipeItemResponse from(RecipeItem item) {
        return new RecipeItemResponse(
                item.getIngredient().getId(),
                item.getIngredient().getName(),
                item.getIngredient().getUnit(),
                item.getQuantity(),
                item.getIngredient().isActive()
        );
    }
}
