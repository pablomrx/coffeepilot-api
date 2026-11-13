package com.coffeepilot.api.order;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderSequenceRepository
        extends JpaRepository<OrderSequence, Long> {

    // Garante a linha do contador do estabelecimento. Duas criações
    // simultâneas do primeiro pedido não geram erro: a segunda espera
    // a primeira e não faz nada.
    @Modifying
    @Query(value = """
            INSERT INTO order_sequences (coffee_shop_id, last_number)
            VALUES (:coffeeShopId, 0)
            ON CONFLICT (coffee_shop_id) DO NOTHING
            """, nativeQuery = true)
    int createIfMissing(@Param("coffeeShopId") Long coffeeShopId);

    // Bloqueia o contador até o fim da transação. Deve ser o último
    // bloqueio da criação do pedido, para segurá-lo pelo menor tempo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM OrderSequence s
            WHERE s.coffeeShopId = :coffeeShopId
            """)
    Optional<OrderSequence> findForUpdate(
            @Param("coffeeShopId") Long coffeeShopId
    );
}
