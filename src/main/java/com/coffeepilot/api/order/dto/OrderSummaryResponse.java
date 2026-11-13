package com.coffeepilot.api.order.dto;

import com.coffeepilot.api.order.Order;
import com.coffeepilot.api.order.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderSummaryResponse(
        Long id,
        Long orderNumber,
        OrderStatus status,
        String tableReference,
        BigDecimal total,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTableReference(),
                order.getTotal(),
                order.getCreatedBy().getName(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
