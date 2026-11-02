package com.coffeepilot.api.ingredient.dto;

import com.coffeepilot.api.ingredient.Ingredient;
import com.coffeepilot.api.ingredient.IngredientUnit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IngredientResponse(
        Long id,
        String name,
        IngredientUnit unit,
        BigDecimal currentQuantity,
        BigDecimal minimumQuantity,
        boolean lowStock,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static IngredientResponse from(Ingredient ingredient) {
        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getUnit(),
                ingredient.getCurrentQuantity(),
                ingredient.getMinimumQuantity(),
                ingredient.isLowStock(),
                ingredient.isActive(),
                ingredient.getCreatedAt(),
                ingredient.getUpdatedAt()
        );
    }
}
