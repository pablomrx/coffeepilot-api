package com.coffeepilot.api.product;

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

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Busca um produto pertencente a um estabelecimento específico.
    Optional<Product> findByIdAndEstablishmentId(
            Long id,
            Long establishmentId
    );

    // Busca e bloqueia um produto para edição.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Product p
            WHERE p.id = :id
              AND p.establishmentId = :establishmentId
            """)
    Optional<Product> findByIdAndEstablishmentIdForUpdate(
            @Param("id") Long id,
            @Param("establishmentId") Long establishmentId
    );

    // Busca e bloqueia vários produtos para leitura, em ordem crescente
    // de id. Usado pelo pedido: edições de produto e receita esperam o
    // pedido terminar, e pedidos simultâneos não se bloqueiam entre si.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT p FROM Product p
            WHERE p.establishmentId = :establishmentId
              AND p.id IN :ids
            ORDER BY p.id
            """)
    List<Product> findAllByIdsForShare(
            @Param("establishmentId") Long establishmentId,
            @Param("ids") Collection<Long> ids
    );

    // Verifica se já existe um produto com o mesmo nome no estabelecimento.
    @Query("""
            SELECT COUNT(p) > 0 FROM Product p
            WHERE p.establishmentId = :establishmentId
              AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name))
            """)
    boolean existsByName(
            @Param("establishmentId") Long establishmentId,
            @Param("name") String name
    );

    // Verifica duplicidade, ignorando o próprio produto durante uma edição.
    @Query("""
            SELECT COUNT(p) > 0 FROM Product p
            WHERE p.establishmentId = :establishmentId
              AND p.id <> :excludedId
              AND LOWER(TRIM(p.name)) = LOWER(TRIM(:name))
            """)
    boolean existsByNameExcludingId(
            @Param("establishmentId") Long establishmentId,
            @Param("excludedId") Long excludedId,
            @Param("name") String name
    );

    // Lista produtos de um estabelecimento com filtros opcionais e paginação.
    @Query(
            value = """
                    SELECT p FROM Product p
                    WHERE p.establishmentId = :establishmentId
                      AND (
                          CAST(:active AS Boolean) IS NULL
                          OR p.active = :active
                      )
                      AND LOWER(p.name) LIKE :searchPattern ESCAPE '!'
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Product p
                    WHERE p.establishmentId = :establishmentId
                      AND (
                          CAST(:active AS Boolean) IS NULL
                          OR p.active = :active
                      )
                      AND LOWER(p.name) LIKE :searchPattern ESCAPE '!'
                    """
    )
    Page<Product> findByFilters(
            @Param("establishmentId") Long establishmentId,
            @Param("active") Boolean active,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}