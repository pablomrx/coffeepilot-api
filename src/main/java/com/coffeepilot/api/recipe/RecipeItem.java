package com.coffeepilot.api.recipe;

import com.coffeepilot.api.ingredient.Ingredient;
import com.coffeepilot.api.ingredient.IngredientUnit;

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
@Table(name = "recipe_items")
public class RecipeItem {

    private static final int QUANTITY_SCALE = 3;

    private static final BigDecimal MAX_QUANTITY =
            new BigDecimal("99999999999.999");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coffee_shop_id", nullable = false, updatable = false)
    private Long coffeeShopId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RecipeItem() {
    }

    public RecipeItem(
            Long coffeeShopId,
            Long productId,
            Ingredient ingredient,
            BigDecimal quantity
    ) {
        Objects.requireNonNull(coffeeShopId, "coffeeShopId is required");
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(ingredient, "ingredient is required");

        if (coffeeShopId <= 0 || productId <= 0) {
            throw new IllegalArgumentException(
                    "coffeeShopId and productId must be positive."
            );
        }

        if (!coffeeShopId.equals(ingredient.getCoffeeShopId())) {
            throw new IllegalArgumentException(
                    "Ingrediente não encontrado."
            );
        }

        this.coffeeShopId = coffeeShopId;
        this.productId = productId;
        this.ingredient = ingredient;
        this.quantity = validateQuantity(ingredient.getUnit(), quantity);
    }

    private static BigDecimal validateQuantity(
            IngredientUnit unit,
            BigDecimal quantity
    ) {
        Objects.requireNonNull(unit, "unit is required");
        Objects.requireNonNull(quantity, "quantity is required");

        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade deve ser maior que zero."
            );
        }

        int scale = quantity.stripTrailingZeros().scale();

        if (scale > QUANTITY_SCALE) {
            throw new IllegalArgumentException(
                    "A quantidade deve ter no máximo 3 casas decimais."
            );
        }

        if (unit == IngredientUnit.UNIT && scale > 0) {
            throw new IllegalArgumentException(
                    "A quantidade deve ser um número inteiro "
                            + "para ingredientes em UNIT."
            );
        }

        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new IllegalArgumentException(
                    "A quantidade excede o valor máximo permitido."
            );
        }

        return quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    public Long getId() {
        return id;
    }

    public Long getCoffeeShopId() {
        return coffeeShopId;
    }

    public Long getProductId() {
        return productId;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
