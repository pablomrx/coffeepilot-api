package com.coffeepilot.api.order.dto;

import com.coffeepilot.api.order.Order;
import com.coffeepilot.api.order.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        Long orderNumber,
        OrderStatus status,
        String tableReference,
        String notes,
        BigDecimal total,
        List<OrderItemResponse> items,
        String createdByName,
        OffsetDateTime completedAt,
        OffsetDateTime cancelledAt,
        String cancelledByName,
        OrderStatus cancelledFromStatus,
        String cancellationReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTableReference(),
                order.getNotes(),
                order.getTotal(),
                order.getItems().stream()
                        .map(OrderItemResponse::from)
                        .toList(),
                order.getCreatedBy().getName(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancelledBy() == null
                        ? null
                        : order.getCancelledBy().getName(),
                order.getCancelledFromStatus(),
                order.getCancellationReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
