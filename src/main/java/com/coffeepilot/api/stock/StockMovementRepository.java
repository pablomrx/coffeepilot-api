package com.coffeepilot.api.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Histórico de movimentações de um ingrediente.
    @Query(
            value = """
                    SELECT m FROM StockMovement m
                    JOIN FETCH m.createdBy
                    WHERE m.establishment.id = :establishmentId
                      AND m.ingredient.id = :ingredientId
                    ORDER BY m.createdAt DESC, m.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM StockMovement m
                    WHERE m.establishment.id = :establishmentId
                      AND m.ingredient.id = :ingredientId
                    """
    )
    Page<StockMovement> findHistory(
            @Param("establishmentId") Long establishmentId,
            @Param("ingredientId") Long ingredientId,
            Pageable pageable
    );

    // Linhas de consumo de um pedido (movimentação, ingrediente e
    // quantidade), em ordem crescente de ingrediente e sem carregar a
    // entidade Ingrediente. O cancelamento usa esta consulta para saber
    // quais ingredientes bloquear, antes de ler qualquer saldo.
    @Query("""
            SELECT new com.coffeepilot.api.stock.StockConsumptionLine(
                m.id, m.ingredient.id, m.quantityDelta)
            FROM StockMovement m
            WHERE m.establishment.id = :establishmentId
              AND m.orderId = :orderId
              AND m.type = com.coffeepilot.api.stock.StockMovementType.CONSUMPTION
            ORDER BY m.ingredient.id
            """)
    List<StockConsumptionLine> findConsumptionLinesByOrder(
            @Param("establishmentId") Long establishmentId,
            @Param("orderId") Long orderId
    );

    // Movimentações de consumo de um pedido, para gerar os estornos do
    // cancelamento. Só deve ser chamada depois de os ingredientes terem
    // sido bloqueados, para não carregar saldos desatualizados.
    @Query("""
            SELECT m FROM StockMovement m
            WHERE m.establishment.id = :establishmentId
              AND m.orderId = :orderId
              AND m.type = com.coffeepilot.api.stock.StockMovementType.CONSUMPTION
            ORDER BY m.ingredient.id
            """)
    List<StockMovement> findConsumptionsByOrder(
            @Param("establishmentId") Long establishmentId,
            @Param("orderId") Long orderId
    );

    // Busca uma movimentação restrita ao estabelecimento.
    Optional<StockMovement> findByIdAndEstablishmentId(
            Long id,
            Long establishmentId
    );

    // Verifica se a movimentação já foi estornada.
    boolean existsByReversesMovementIdAndEstablishmentId(
            Long reversesMovementId,
            Long establishmentId
    );

    // Verifica se o ingrediente já possui alguma movimentação.
    boolean existsByIngredientIdAndEstablishmentId(
            Long ingredientId,
            Long establishmentId
    );
}