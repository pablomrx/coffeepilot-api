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
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
