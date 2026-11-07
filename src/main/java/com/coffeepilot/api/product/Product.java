package com.coffeepilot.api.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "products")
public class Product {

    private static final int NAME_MAX_LENGTH = 150;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int PRICE_SCALE = 2;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coffee_shop_id", nullable = false, updatable = false)
    private Long coffeeShopId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
    }

    public Product(
            Long coffeeShopId,
            String name,
            String description,
            BigDecimal price
    ) {
        Objects.requireNonNull(coffeeShopId, "coffeeShopId is required");

        if (coffeeShopId <= 0) {
            throw new IllegalArgumentException(
                    "coffeeShopId must be positive."
            );
        }

        this.coffeeShopId = coffeeShopId;
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.price = validatePrice(price);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name cannot be blank."
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Product name cannot exceed 150 characters."
            );
        }

        return normalizedName;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        String normalizedDescription = description.trim();

        if (normalizedDescription.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Product description cannot exceed 500 characters."
            );
        }

        return normalizedDescription;
    }

    private static BigDecimal validatePrice(BigDecimal price) {
        Objects.requireNonNull(price, "price is required");

        if (price.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero."
            );
        }

        if (price.stripTrailingZeros().scale() > PRICE_SCALE) {
            throw new IllegalArgumentException(
                    "Price cannot have more than 2 decimal places."
            );
        }

        if (price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(
                    "Price exceeds the maximum allowed value."
            );
        }

        return price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public void changeDescription(String description) {
        this.description = normalizeDescription(description);
    }

    public void changePrice(BigDecimal price) {
        this.price = validatePrice(price);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
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

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
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
