package com.coffeepilot.api.product.dto;

import com.coffeepilot.api.product.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        boolean hasRecipe,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ProductResponse from(Product product, boolean hasRecipe) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                hasRecipe,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
