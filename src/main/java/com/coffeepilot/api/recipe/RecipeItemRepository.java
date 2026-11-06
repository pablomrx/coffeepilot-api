package com.coffeepilot.api.recipe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    // Itens da receita de um produto, já com o ingrediente carregado,
    @Query("""
            SELECT r FROM RecipeItem r
            JOIN FETCH r.ingredient i
            WHERE r.coffeeShopId = :coffeeShopId
              AND r.productId = :productId
            ORDER BY i.name, r.id
            """)
    List<RecipeItem> findByProduct(
            @Param("coffeeShopId") Long coffeeShopId,
            @Param("productId") Long productId
    );

    // Remove todos os itens da receita de um produto.
    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM RecipeItem r
            WHERE r.coffeeShopId = :coffeeShopId
              AND r.productId = :productId
            """)
    int deleteByProduct(
            @Param("coffeeShopId") Long coffeeShopId,
            @Param("productId") Long productId
    );
}
