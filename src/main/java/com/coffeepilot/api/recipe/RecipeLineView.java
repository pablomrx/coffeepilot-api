package com.coffeepilot.api.recipe;

import java.math.BigDecimal;

public record RecipeLineView(
        Long productId,
        Long ingredientId,
        BigDecimal quantity
) {
}
