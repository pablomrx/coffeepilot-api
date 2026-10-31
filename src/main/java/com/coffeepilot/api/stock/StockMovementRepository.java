package com.coffeepilot.api.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Histórico de movimentações de um ingrediente.
    @Query(
            value = """
                    SELECT m FROM StockMovement m
                    JOIN FETCH m.createdBy
                    WHERE m.coffeeShop.id = :coffeeShopId
                      AND m.ingredient.id = :ingredientId
                    ORDER BY m.createdAt DESC, m.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM StockMovement m
                    WHERE m.coffeeShop.id = :coffeeShopId
                      AND m.ingredient.id = :ingredientId
                    """
    )
    Page<StockMovement> findHistory(
            @Param("coffeeShopId") Long coffeeShopId,
            @Param("ingredientId") Long ingredientId,
            Pageable pageable
    );

    // Busca uma movimentação restrita à cafeteria.
    Optional<StockMovement> findByIdAndCoffeeShopId(
            Long id,
            Long coffeeShopId
    );

    // Verifica se a movimentação já foi estornada.
    boolean existsByReversesMovementIdAndCoffeeShopId(
            Long reversesMovementId,
            Long coffeeShopId
    );

    // Verifica se o ingrediente já possui alguma movimentação.
    boolean existsByIngredientIdAndCoffeeShopId(
            Long ingredientId,
            Long coffeeShopId
    );
}
