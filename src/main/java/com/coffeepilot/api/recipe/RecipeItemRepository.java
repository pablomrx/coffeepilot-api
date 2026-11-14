package com.coffeepilot.api.recipe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    // Itens da receita de um produto, já com o ingrediente carregado,
    // ordenados por nome do ingrediente e id.
    @Query("""
            SELECT r FROM RecipeItem r
            JOIN FETCH r.ingredient i
            WHERE r.establishmentId = :establishmentId
              AND r.productId = :productId
            ORDER BY i.name, r.id
            """)
    List<RecipeItem> findByProduct(
            @Param("establishmentId") Long establishmentId,
            @Param("productId") Long productId
    );

    // Linhas das receitas de vários produtos em uma única consulta, sem
    // carregar a entidade Ingrediente (ver RecipeLineView). Usado pelo
    // pedido para calcular o consumo antes de bloquear os ingredientes.
    @Query("""
            SELECT new com.coffeepilot.api.recipe.RecipeLineView(
                r.productId, r.ingredient.id, r.quantity)
            FROM RecipeItem r
            WHERE r.establishmentId = :establishmentId
              AND r.productId IN :productIds
            ORDER BY r.productId, r.ingredient.id
            """)
    List<RecipeLineView> findLinesByProducts(
            @Param("establishmentId") Long establishmentId,
            @Param("productIds") Collection<Long> productIds
    );

    // Remove todos os itens da receita de um produto.
    // É um DELETE em massa de propósito: uma única instrução, sem depender
    // do contexto de persistência.
    // Uso no service: bloquear o produto, validar, chamar este método e só
    // então salvar os novos itens. Não carregar os itens antigos antes,
    // porque o DELETE em massa não atualiza o contexto de persistência.
    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM RecipeItem r
            WHERE r.establishmentId = :establishmentId
              AND r.productId = :productId
            """)
    int deleteByProduct(
            @Param("establishmentId") Long establishmentId,
            @Param("productId") Long productId
    );
}
