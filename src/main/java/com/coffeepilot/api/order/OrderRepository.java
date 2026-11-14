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
              AND o.establishmentId = :establishmentId
            """)
    Optional<Order> findDetailByIdAndEstablishmentId(
            @Param("id") Long id,
            @Param("establishmentId") Long establishmentId
    );

    // Busca e bloqueia o pedido para mudança de status e cancelamento.
    // Sem JOIN FETCH: o PostgreSQL não permite FOR UPDATE com junção externa.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM Order o
            WHERE o.id = :id
              AND o.establishmentId = :establishmentId
            """)
    Optional<Order> findByIdAndEstablishmentIdForUpdate(
            @Param("id") Long id,
            @Param("establishmentId") Long establishmentId
    );

    // Listagens paginadas. A ordenação vem do service.
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Order> findByEstablishmentId(
            Long establishmentId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"createdBy"})
    Page<Order> findByEstablishmentIdAndStatus(
            Long establishmentId,
            OrderStatus status,
            Pageable pageable
    );
}
