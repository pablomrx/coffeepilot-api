package com.coffeepilot.api.recipe.dto;

import com.coffeepilot.api.product.Product;
import com.coffeepilot.api.recipe.RecipeItem;

import java.time.OffsetDateTime;
import java.util.List;

public record RecipeResponse(
        Long productId,
        String productName,
        List<RecipeItemResponse> items,
        OffsetDateTime updatedAt
) {

    public static RecipeResponse from(Product product, List<RecipeItem> items) {
        return new RecipeResponse(
                product.getId(),
                product.getName(),
                items.stream().map(RecipeItemResponse::from).toList(),
                product.getUpdatedAt()
        );
    }
}
