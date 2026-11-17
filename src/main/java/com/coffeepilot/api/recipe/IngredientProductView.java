package com.coffeepilot.api.recipe;

public record IngredientProductView(
        Long id,
        String name,
        boolean active
) {
}
