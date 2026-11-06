CREATE TABLE recipe_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    coffee_shop_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,

    quantity NUMERIC(14, 3) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recipe_items_coffee_shop
        FOREIGN KEY (coffee_shop_id)
        REFERENCES coffee_shops (id),

    CONSTRAINT fk_recipe_items_product
        FOREIGN KEY (product_id, coffee_shop_id)
        REFERENCES products (id, coffee_shop_id),

    CONSTRAINT fk_recipe_items_ingredient
        FOREIGN KEY (ingredient_id, coffee_shop_id)
        REFERENCES ingredients (id, coffee_shop_id),

    CONSTRAINT ck_recipe_items_quantity_positive
        CHECK (quantity > 0),

    CONSTRAINT uq_recipe_items_product_ingredient
        UNIQUE (product_id, ingredient_id)
);

CREATE INDEX idx_recipe_items_ingredient
    ON recipe_items (coffee_shop_id, ingredient_id);