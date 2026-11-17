package com.coffeepilot.api.ingredient.dto;

import com.coffeepilot.api.recipe.IngredientProductView;

public record IngredientProductResponse(Long id, String name, boolean active) {

    public static IngredientProductResponse from(IngredientProductView view) {
        return new IngredientProductResponse(view.id(), view.name(), view.active());
    }
}
