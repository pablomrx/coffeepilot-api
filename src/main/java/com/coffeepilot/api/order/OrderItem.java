package com.coffeepilot.api.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "order_items")
public class OrderItem {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 999;

    private static final int NAME_MAX_LENGTH = 150;
    private static final int PRICE_SCALE = 2;
    private static final int COST_SCALE = 4;

    private static final BigDecimal MAX_PRICE =
            new BigDecimal("99999999.99");

    private static final BigDecimal MAX_COST =
            new BigDecimal("9999999999.9999");

    private static final BigDecimal MAX_SUBTOTAL =
            new BigDecimal("9999999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "establishment_id", nullable = false, updatable = false)
    private Long establishmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, updatable = false,
            length = NAME_MAX_LENGTH)
    private String productName;

    @Column(name = "unit_price", nullable = false, updatable = false,
            precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", updatable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "stock_tracked", nullable = false, updatable = false)
    private boolean stockTracked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OrderItem() {
    }

    OrderItem(
            Order order,
            Long productId,
            String productName,
            BigDecimal unitPrice,
            BigDecimal unitCost,
            int quantity,
            boolean stockTracked
    ) {
        this.order = Objects.requireNonNull(order, "order is required");

        this.productId = Objects.requireNonNull(
                productId,
                "productId is required"
        );

        if (productId <= 0) {
            throw new IllegalArgumentException(
                    "O ID do produto deve ser positivo."
            );
        }

        this.establishmentId = order.getEstablishmentId();

        this.productName = normalizeName(productName);
        this.unitPrice = validatePrice(unitPrice);
        this.unitCost = validateCost(unitCost);

        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "A quantidade de cada item deve estar entre 1 e 999."
            );
        }

        this.quantity = quantity;
        this.stockTracked = stockTracked;

        this.subtotal = this.unitPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);

        if (this.subtotal.compareTo(MAX_SUBTOTAL) > 0) {
            throw new IllegalArgumentException(
                    "O subtotal do item excede o valor máximo permitido."
            );
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do produto não pode ficar em branco."
            );
        }

        String normalized = name.trim();

        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "O nome do produto deve ter no máximo 150 caracteres."
            );
        }

        return normalized;
    }

    private static BigDecimal validatePrice(BigDecimal price) {
        Objects.requireNonNull(price, "unitPrice is required");

        if (price.signum() <= 0
                || price.stripTrailingZeros().scale() > PRICE_SCALE
                || price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(
                    "O preço do produto é inválido para o pedido."
            );
        }

        return price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal validateCost(BigDecimal cost) {
        if (cost == null) {
            return null;
        }

        if (cost.signum() < 0
                || cost.stripTrailingZeros().scale() > COST_SCALE
                || cost.compareTo(MAX_COST) > 0) {
            throw new IllegalArgumentException(
                    "O custo do produto é inválido para o pedido."
            );
        }

        return cost.setScale(COST_SCALE, RoundingMode.UNNECESSARY);
    }

    public Long getId() {
        return id;
    }

    public Long getEstablishmentId() {
        return establishmentId;
    }

    public Order getOrder() {
        return order;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public boolean isStockTracked() {
        return stockTracked;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
