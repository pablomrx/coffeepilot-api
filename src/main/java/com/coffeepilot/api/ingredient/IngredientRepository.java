package com.coffeepilot.api.ingredient;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    // Busca um ingrediente pertencente a uma cafeteria específica.
    Optional<Ingredient> findByIdAndCoffeeShopId(
            Long id,
            Long coffeeShopId
    );

    // Busca e bloqueia um ingrediente para operações de estoque.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM Ingredient i
            WHERE i.id = :id
              AND i.coffeeShopId = :coffeeShopId
            """)
    Optional<Ingredient> findByIdAndCoffeeShopIdForUpdate(
            @Param("id") Long id,
            @Param("coffeeShopId") Long coffeeShopId
    );

    // Busca e bloqueia vários ingredientes em ordem crescente de ID.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM Ingredient i
            WHERE i.coffeeShopId = :coffeeShopId
              AND i.id IN :ids
            ORDER BY i.id
            """)
    List<Ingredient> findAllByIdsForUpdate(
            @Param("coffeeShopId") Long coffeeShopId,
            @Param("ids") Collection<Long> ids
    );

    // Verifica se já existe um ingrediente com o mesmo nome na cafeteria.
    @Query("""
            SELECT COUNT(i) > 0 FROM Ingredient i
            WHERE i.coffeeShopId = :coffeeShopId
              AND LOWER(TRIM(i.name)) = LOWER(TRIM(:name))
            """)
    boolean existsByName(
            @Param("coffeeShopId") Long coffeeShopId,
            @Param("name") String name
    );

    // Verifica duplicidade, ignorando o próprio ingrediente durante uma edição.
    @Query("""
            SELECT COUNT(i) > 0 FROM Ingredient i
            WHERE i.coffeeShopId = :coffeeShopId
              AND i.id <> :excludedId
              AND LOWER(TRIM(i.name)) = LOWER(TRIM(:name))
            """)
    boolean existsByNameExcludingId(
            @Param("coffeeShopId") Long coffeeShopId,
            @Param("excludedId") Long excludedId,
            @Param("name") String name
    );

    // Lista todos os ingredientes de uma cafeteria com paginação.
    Page<Ingredient> findByCoffeeShopId(
            Long coffeeShopId,
            Pageable pageable
    );

    // Lista ingredientes de uma cafeteria filtrando pela situação.
    Page<Ingredient> findByCoffeeShopIdAndActive(
            Long coffeeShopId,
            boolean active,
            Pageable pageable
    );

    // Lista ingredientes ativos cujo saldo está no limite ou abaixo do mínimo.
    @Query("""
            SELECT i FROM Ingredient i
            WHERE i.coffeeShopId = :coffeeShopId
              AND i.active = TRUE
              AND i.minimumQuantity > 0
              AND i.currentQuantity <= i.minimumQuantity
            """)
    Page<Ingredient> findLowStock(
            @Param("coffeeShopId") Long coffeeShopId,
            Pageable pageable
    );
}
