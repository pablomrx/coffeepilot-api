package com.coffeepilot.api.common.exception;

import java.util.List;

public class IngredientInUseException extends RuntimeException {

    public record AffectedProduct(Long id, String name) {
    }

    private final List<AffectedProduct> products;

    public IngredientInUseException(List<AffectedProduct> products) {
        super("O ingrediente é usado em receitas de produtos ativos. "
                + "Desative esses produtos ou remova o ingrediente de suas receitas.");
        this.products = List.copyOf(products);
    }

    public List<AffectedProduct> getProducts() {
        return products;
    }
}
