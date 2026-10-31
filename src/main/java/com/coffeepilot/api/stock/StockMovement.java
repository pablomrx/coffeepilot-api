package com.coffeepilot.api.stock;

import com.coffeepilot.api.coffeeshop.CoffeeShop;
import com.coffeepilot.api.ingredient.Ingredient;
import com.coffeepilot.api.ingredient.IngredientUnit;
import com.coffeepilot.api.user.User;

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
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "stock_movements")
public class StockMovement {

    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int QUANTITY_SCALE = 3;

    private static final BigDecimal MAX_QUANTITY =
            new BigDecimal("99999999999.999");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coffee_shop_id", nullable = false, updatable = false)
    private CoffeeShop coffeeShop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StockMovementType type;

    @Column(
            name = "quantity_delta",
            nullable = false,
            precision = 14,
            scale = 3,
            updatable = false
    )
    private BigDecimal quantityDelta;

    @Column(
            name = "balance_after",
            nullable = false,
            precision = 14,
            scale = 3,
            updatable = false
    )
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private StockAdjustmentReason reason;

    @Column(length = DESCRIPTION_MAX_LENGTH, updatable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_movement_id", updatable = false)
    private StockMovement reversesMovement;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected StockMovement() {
    }

    public StockMovement(
            CoffeeShop coffeeShop,
            Ingredient ingredient,
            StockMovementType type,
            BigDecimal quantityDelta,
            BigDecimal balanceAfter,
            StockAdjustmentReason reason,
            String description,
            User createdBy,
            StockMovement reversesMovement
    ) {
        Objects.requireNonNull(
                coffeeShop,
                "Coffee shop is required."
        );

        Objects.requireNonNull(
                ingredient,
                "Ingredient is required."
        );

        Objects.requireNonNull(
                type,
                "Movement type is required."
        );

        Objects.requireNonNull(
                createdBy,
                "User responsible for the movement is required."
        );

        if (!Objects.equals(coffeeShop.getId(), ingredient.getCoffeeShopId())) {
            throw new IllegalArgumentException(
                    "Ingredient does not belong to the coffee shop."
            );
        }

        CoffeeShop userCoffeeShop = Objects.requireNonNull(
                createdBy.getCoffeeShop(),
                "User must belong to a coffee shop."
        );

        if (!Objects.equals(coffeeShop.getId(), userCoffeeShop.getId())) {
            throw new IllegalArgumentException(
                    "User does not belong to the coffee shop."
            );
        }

        this.coffeeShop = coffeeShop;
        this.ingredient = ingredient;
        this.type = type;
        this.createdBy = createdBy;
        this.reason = reason;
        this.description = normalizeDescription(description);
        this.reversesMovement = reversesMovement;

        this.quantityDelta = validateAmount(
                quantityDelta,
                "Quantity delta"
        );

        if (this.quantityDelta.signum() == 0) {
            throw new IllegalArgumentException(
                    "Quantity delta cannot be zero."
            );
        }

        this.balanceAfter = validateBalance(balanceAfter);

        validateUnitRules();
        validateMovementRules();
    }

    private static BigDecimal validateAmount(
            BigDecimal value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required."
            );
        }

        if (value.stripTrailingZeros().scale() > QUANTITY_SCALE) {
            throw new IllegalArgumentException(
                    fieldName + " cannot have more than 3 decimal places."
            );
        }

        if (value.abs().compareTo(MAX_QUANTITY) > 0) {
            throw new IllegalArgumentException(
                    fieldName + " exceeds the maximum allowed value."
            );
        }

        // Padroniza para três casas decimais, sem arredondar.
        return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal validateBalance(BigDecimal value) {
        BigDecimal balance = validateAmount(value, "Balance after");

        if (balance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Balance after cannot be negative."
            );
        }

        return balance;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String normalizedDescription = description.trim();

        if (normalizedDescription.isEmpty()) {
            throw new IllegalArgumentException(
                    "Description cannot be blank."
            );
        }

        if (normalizedDescription.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Description cannot exceed 500 characters."
            );
        }

        return normalizedDescription;
    }

    private void validateUnitRules() {
        if (ingredient.getUnit() == IngredientUnit.UNIT
                && (quantityDelta.stripTrailingZeros().scale() > 0
                || balanceAfter.stripTrailingZeros().scale() > 0)) {

            throw new IllegalArgumentException(
                    "Quantity for UNIT must be a whole number."
            );
        }
    }

    private void validateMovementRules() {

        if ((type == StockMovementType.INITIAL
                || type == StockMovementType.ENTRY)
                && quantityDelta.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Initial and entry movements must have a positive quantity."
            );
        }

        if (type == StockMovementType.CONSUMPTION
                && quantityDelta.signum() >= 0) {

            throw new IllegalArgumentException(
                    "Consumption movements must have a negative quantity."
            );
        }

        if (type == StockMovementType.ADJUSTMENT) {

            if (reason == null) {
                throw new IllegalArgumentException(
                        "Adjustment reason is required."
                );
            }

            if (description == null) {
                throw new IllegalArgumentException(
                        "Adjustment description is required."
                );
            }

        } else if (reason != null) {

            throw new IllegalArgumentException(
                    "Reason can only be provided for adjustment movements."
            );
        }

        if ((reason == StockAdjustmentReason.LOSS
                || reason == StockAdjustmentReason.EXPIRED
                || reason == StockAdjustmentReason.DAMAGED)
                && quantityDelta.signum() >= 0) {

            throw new IllegalArgumentException(
                    "Loss, expired and damaged adjustments must be negative."
            );
        }

        if (type == StockMovementType.REVERSAL) {

            if (reversesMovement == null) {
                throw new IllegalArgumentException(
                        "Original movement is required for a reversal."
                );
            }

            validateReversal();

        } else if (reversesMovement != null) {

            throw new IllegalArgumentException(
                    "Only reversal movements can reference another movement."
            );
        }
    }

    private void validateReversal() {

        if (reversesMovement.getId() == null) {
            throw new IllegalArgumentException(
                    "Original movement must be persisted before reversal."
            );
        }

        if (reversesMovement.getType() == StockMovementType.REVERSAL) {
            throw new IllegalArgumentException(
                    "A reversal cannot reverse another reversal."
            );
        }

        if (!Objects.equals(
                reversesMovement.getIngredient().getId(),
                ingredient.getId()
        )) {
            throw new IllegalArgumentException(
                    "Reversal and original movement must belong to the same ingredient."
            );
        }

        if (!Objects.equals(
                reversesMovement.getCoffeeShop().getId(),
                coffeeShop.getId()
        )) {
            throw new IllegalArgumentException(
                    "Reversal and original movement must belong to the same coffee shop."
            );
        }

        if (quantityDelta.compareTo(
                reversesMovement.getQuantityDelta().negate()
        ) != 0) {
            throw new IllegalArgumentException(
                    "Reversal quantity must be the opposite of the original movement."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public CoffeeShop getCoffeeShop() {
        return coffeeShop;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public StockMovementType getType() {
        return type;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public StockAdjustmentReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public StockMovement getReversesMovement() {
        return reversesMovement;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
