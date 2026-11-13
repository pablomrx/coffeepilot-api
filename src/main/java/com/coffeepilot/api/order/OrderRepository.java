package com.coffeepilot.api.order;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Pedido com itens, criador e responsável pelo cancelamento,
    // para consulta de detalhes.
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.createdBy
            LEFT JOIN FETCH o.cancelledBy
            LEFT JOIN FETCH o.items
            WHERE o.id = :id
              AND o.coffeeShopId = :coffeeShopId
            """)
    Optional<Order> findDetailByIdAndCoffeeShopId(
            @Param("id") Long id,
            @Param("coffeeShopId") Long coffeeShopId
    );

    // Busca e bloqueia o pedido para mudança de status e cancelamento.
    // Sem JOIN FETCH: o PostgreSQL não permite FOR UPDATE com junção externa.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM Order o
            WHERE o.id = :id
              AND o.coffeeShopId = :coffeeShopId
            """)
    Optional<Order> findByIdAndCoffeeShopIdForUpdate(
            @Param("id") Long id,
            @Param("coffeeShopId") Long coffeeShopId
    );

    // Listagens paginadas. A ordenação vem do service.
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Order> findByCoffeeShopId(
            Long coffeeShopId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"createdBy"})
    Page<Order> findByCoffeeShopIdAndStatus(
            Long coffeeShopId,
            OrderStatus status,
            Pageable pageable
    );
}
