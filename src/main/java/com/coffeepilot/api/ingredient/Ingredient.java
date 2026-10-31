package com.coffeepilot.api.ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "ingredients")
public class Ingredient {

    private static final int NAME_MAX_LENGTH = 150;
    private static final int QUANTITY_SCALE = 3;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999.999");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coffee_shop_id", nullable = false, updatable = false)
    private Long coffeeShopId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private IngredientUnit unit;

    @Column(name = "current_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal currentQuantity = BigDecimal.ZERO;

    @Column(name = "minimum_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal minimumQuantity = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Ingredient() {
    }

    public Ingredient(
            Long coffeeShopId,
            String name,
            IngredientUnit unit,
            BigDecimal minimumQuantity
    ) {
        Objects.requireNonNull(coffeeShopId, "coffeeShopId is required");

        if (coffeeShopId <= 0) {
            throw new IllegalArgumentException(
                    "coffeeShopId must be positive."
            );
        }

        this.coffeeShopId = coffeeShopId;

        this.unit = Objects.requireNonNull(
                unit,
                "unit is required"
        );

        this.name = normalizeName(name);
        this.minimumQuantity = validateQuantity(unit, minimumQuantity);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Ingredient name cannot be blank."
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Ingredient name cannot exceed 150 characters."
            );
        }

        return normalizedName;
    }

    private static BigDecimal validateQuantity(
            IngredientUnit unit,
            BigDecimal quantity
    ) {
        Objects.requireNonNull(quantity, "quantity is required");
        Objects.requireNonNull(unit, "unit is required");

        if (quantity.signum() < 0) {
            throw new IllegalArgumentException(
                    "Quantity cannot be negative."
            );
        }

        int scale = quantity.stripTrailingZeros().scale();

        if (scale > QUANTITY_SCALE) {
            throw new IllegalArgumentException(
                    "Quantity cannot have more than 3 decimal places."
            );
        }

        if (unit == IngredientUnit.UNIT && scale > 0) {
            throw new IllegalArgumentException(
                    "Quantity for UNIT must be a whole number."
            );
        }

        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new IllegalArgumentException(
                    "Quantity exceeds the maximum allowed value."
            );
        }

        return quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    public void applyStockChange(BigDecimal newQuantity) {
        this.currentQuantity = validateQuantity(this.unit, newQuantity);
    }

    public boolean isLowStock() {
        return active
                && minimumQuantity.signum() > 0
                && currentQuantity.compareTo(minimumQuantity) <= 0;
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public void changeMinimumQuantity(BigDecimal minimumQuantity) {
        this.minimumQuantity = validateQuantity(this.unit, minimumQuantity);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public Long getCoffeeShopId() {
        return coffeeShopId;
    }

    public String getName() {
        return name;
    }

    public IngredientUnit getUnit() {
        return unit;
    }

    public BigDecimal getCurrentQuantity() {
        return currentQuantity;
    }

    public BigDecimal getMinimumQuantity() {
        return minimumQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
