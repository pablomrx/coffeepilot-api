package com.coffeepilot.api.order;

import com.coffeepilot.api.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "orders")
public class Order {

    public static final int MAX_ITEMS = 50;

    private static final int TABLE_REFERENCE_MAX_LENGTH = 50;
    private static final int TEXT_MAX_LENGTH = 500;

    private static final BigDecimal MAX_TOTAL =
            new BigDecimal("9999999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coffee_shop_id", nullable = false, updatable = false)
    private Long coffeeShopId;

    @Column(name = "order_number", nullable = false, updatable = false)
    private Long orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "table_reference", length = TABLE_REFERENCE_MAX_LENGTH)
    private String tableReference;

    @Column(length = TEXT_MAX_LENGTH)
    private String notes;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = new BigDecimal("0.00");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_from_status", length = 20)
    private OrderStatus cancelledFromStatus;

    @Column(name = "cancellation_reason", length = TEXT_MAX_LENGTH)
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(
            Long coffeeShopId,
            Long orderNumber,
            User createdBy,
            String tableReference,
            String notes
    ) {
        Objects.requireNonNull(coffeeShopId, "coffeeShopId is required");
        Objects.requireNonNull(orderNumber, "orderNumber is required");
        Objects.requireNonNull(createdBy, "createdBy is required");

        if (coffeeShopId <= 0 || orderNumber <= 0) {
            throw new IllegalArgumentException(
                    "coffeeShopId and orderNumber must be positive."
            );
        }

        this.coffeeShopId = coffeeShopId;
        this.orderNumber = orderNumber;
        this.createdBy = createdBy;
        this.tableReference = normalizeOptional(
                tableReference,
                TABLE_REFERENCE_MAX_LENGTH,
                "A mesa ou comanda deve ter no máximo 50 caracteres."
        );
        this.notes = normalizeOptional(
                notes,
                TEXT_MAX_LENGTH,
                "As observações devem ter no máximo 500 caracteres."
        );
    }

    public OrderItem addItem(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            BigDecimal unitCost,
            int quantity,
            boolean stockTracked
    ) {
        requireStatus(
                OrderStatus.PENDING,
                "Apenas pedidos pendentes podem receber itens."
        );

        if (items.size() >= MAX_ITEMS) {
            throw new IllegalArgumentException(
                    "O pedido deve ter no máximo 50 itens."
            );
        }

        for (OrderItem existing : items) {
            if (existing.getProductId().equals(productId)) {
                throw new IllegalArgumentException(
                        "Produto repetido no pedido."
                );
            }
        }

        OrderItem item = new OrderItem(
                this,
                productId,
                productName,
                unitPrice,
                unitCost,
                quantity,
                stockTracked
        );

        BigDecimal newTotal = total.add(item.getSubtotal());

        if (newTotal.compareTo(MAX_TOTAL) > 0) {
            throw new IllegalArgumentException(
                    "O total do pedido excede o valor máximo permitido."
            );
        }

        items.add(item);
        total = newTotal;

        return item;
    }

    public void startPreparation() {
        requireStatus(
                OrderStatus.PENDING,
                "Apenas pedidos pendentes podem entrar em preparação."
        );

        status = OrderStatus.IN_PREPARATION;
    }

    public void complete() {
        requireStatus(
                OrderStatus.IN_PREPARATION,
                "Apenas pedidos em preparação podem ser concluídos."
        );

        status = OrderStatus.COMPLETED;
        completedAt = OffsetDateTime.now();
    }

    public void cancel(User cancelledBy, String reason) {
        Objects.requireNonNull(cancelledBy, "cancelledBy is required");

        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("O pedido já foi cancelado.");
        }

        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Pedidos concluídos não podem ser cancelados."
            );
        }

        String normalizedReason = normalizeOptional(
                reason,
                TEXT_MAX_LENGTH,
                "A justificativa deve ter no máximo 500 caracteres."
        );

        if (status == OrderStatus.IN_PREPARATION
                && normalizedReason == null) {
            throw new IllegalArgumentException(
                    "A justificativa é obrigatória para cancelar "
                            + "um pedido em preparação."
            );
        }

        this.cancelledFromStatus = status;
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = OffsetDateTime.now();
        this.cancelledBy = cancelledBy;
        this.cancellationReason = normalizedReason;
    }

    private void requireStatus(OrderStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static String normalizeOptional(
            String value,
            int maxLength,
            String tooLongMessage
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessage);
        }

        return normalized;
    }

    public Long getId() {
        return id;
    }

    public Long getCoffeeShopId() {
        return coffeeShopId;
    }

    public Long getOrderNumber() {
        return orderNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getTableReference() {
        return tableReference;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public User getCancelledBy() {
        return cancelledBy;
    }

    public OrderStatus getCancelledFromStatus() {
        return cancelledFromStatus;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
