package com.coffeepilot.api.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_sequences")
public class OrderSequence {

    @Id
    @Column(name = "coffee_shop_id")
    private Long coffeeShopId;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    protected OrderSequence() {
    }

    public long nextNumber() {
        lastNumber = Math.addExact(lastNumber, 1L);
        return lastNumber;
    }

    public Long getCoffeeShopId() {
        return coffeeShopId;
    }

    public long getLastNumber() {
        return lastNumber;
    }
}
