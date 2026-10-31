package com.coffeepilot.api.stock;

import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.ingredient.Ingredient;
import com.coffeepilot.api.ingredient.IngredientRepository;
import com.coffeepilot.api.ingredient.IngredientUnit;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StockService {

    private static final Logger log =
            LoggerFactory.getLogger(StockService.class);

    private static final Role[] STOCK_ROLES =
            {Role.OWNER, Role.MANAGER, Role.STOCKIST};

    private static final Role[] REVERSAL_ROLES =
            {Role.OWNER, Role.MANAGER};

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int QUANTITY_SCALE = 3;

    private static final BigDecimal MAX_QUANTITY =
            new BigDecimal("99999999999.999");

    private final ActiveUserProvider activeUserProvider;
    private final IngredientRepository ingredientRepository;
    private final StockMovementRepository movementRepository;

    public StockService(
            ActiveUserProvider activeUserProvider,
            IngredientRepository ingredientRepository,
            StockMovementRepository movementRepository
    ) {
        this.activeUserProvider = activeUserProvider;
        this.ingredientRepository = ingredientRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public StockMovement registerEntry(
            AuthenticatedUser authenticatedUser,
            Long ingredientId,
            BigDecimal quantity,
            String description
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                STOCK_ROLES
        );

        Ingredient ingredient =
                lockActiveIngredient(actor, ingredientId);

        validateAmount(ingredient, quantity, "A quantidade");

        if (quantity.signum() <= 0) {
            throw new BusinessRuleException(
                    "A quantidade da entrada deve ser maior que zero."
            );
        }

        return apply(
                actor,
                ingredient,
                StockMovementType.ENTRY,
                quantity,
                null,
                description,
                null
        );
    }

    @Transactional
    public StockMovement registerAdjustment(
            AuthenticatedUser authenticatedUser,
            Long ingredientId,
            BigDecimal quantityDelta,
            StockAdjustmentReason reason,
            String justification
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                STOCK_ROLES
        );

        if (reason == null) {
            throw new BusinessRuleException(
                    "O motivo do ajuste é obrigatório."
            );
        }

        if (justification == null || justification.isBlank()) {
            throw new BusinessRuleException(
                    "A justificativa do ajuste é obrigatória."
            );
        }

        Ingredient ingredient =
                lockActiveIngredient(actor, ingredientId);

        validateAmount(ingredient, quantityDelta, "A variação");

        if (quantityDelta.signum() == 0) {
            throw new BusinessRuleException(
                    "A variação do ajuste não pode ser zero."
            );
        }

        boolean lossReason =
                reason == StockAdjustmentReason.LOSS
                        || reason == StockAdjustmentReason.EXPIRED
                        || reason == StockAdjustmentReason.DAMAGED;

        if (lossReason && quantityDelta.signum() > 0) {
            throw new BusinessRuleException(
                    "Perda, vencimento e dano exigem uma variação negativa."
            );
        }

        return apply(
                actor,
                ingredient,
                StockMovementType.ADJUSTMENT,
                quantityDelta,
                reason,
                justification,
                null
        );
    }

    @Transactional
    public StockMovement reverse(
            AuthenticatedUser authenticatedUser,
            Long movementId,
            String description
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                REVERSAL_ROLES
        );

        Long coffeeShopId = actor.getCoffeeShop().getId();

        StockMovement original = movementRepository
                .findByIdAndCoffeeShopId(
                        movementId,
                        coffeeShopId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Movimentação não encontrada."
                ));

        if (original.getType() == StockMovementType.REVERSAL) {
            throw new BusinessRuleException(
                    "Um estorno não pode ser estornado."
            );
        }

        if (original.getType() == StockMovementType.INITIAL) {
            throw new BusinessRuleException(
                    "O saldo inicial não pode ser estornado. "
                            + "Registre um ajuste com o motivo correção."
            );
        }

        // Bloqueia o ingrediente antes de verificar o estorno.
        // Ingredientes inativos não podem receber movimentações.
        Ingredient ingredient = lockActiveIngredient(
                actor,
                original.getIngredient().getId()
        );

        if (movementRepository
                .existsByReversesMovementIdAndCoffeeShopId(
                        original.getId(),
                        coffeeShopId
                )) {
            throw new BusinessConflictException(
                    "Esta movimentação já foi estornada."
            );
        }

        return apply(
                actor,
                ingredient,
                StockMovementType.REVERSAL,
                original.getQuantityDelta().negate(),
                null,
                description,
                original
        );
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> history(
            AuthenticatedUser authenticatedUser,
            Long ingredientId,
            Pageable pageable
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                STOCK_ROLES
        );

        Long coffeeShopId = actor.getCoffeeShop().getId();

        ingredientRepository
                .findByIdAndCoffeeShopId(
                        ingredientId,
                        coffeeShopId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ingrediente não encontrado."
                ));

        return movementRepository.findHistory(
                coffeeShopId,
                ingredientId,
                sanitize(pageable)
        );
    }

    // Chamado pelo IngredientService dentro da transação do cadastro.
    // O ingrediente deve estar salvo e possuir ID.
    // O chamador já validou as permissões.
    @Transactional
    public StockMovement registerInitial(
            User actor,
            Ingredient ingredient,
            BigDecimal quantity
    ) {
        validateAmount(
                ingredient,
                quantity,
                "O saldo inicial"
        );

        if (quantity.signum() <= 0) {
            throw new BusinessRuleException(
                    "O saldo inicial deve ser maior que zero."
            );
        }

        // Regra: estoque inicial só pode ser registrado
        // se o ingrediente ainda não possui movimentações.
        if (movementRepository
                .existsByIngredientIdAndCoffeeShopId(
                        ingredient.getId(),
                        ingredient.getCoffeeShopId()
                )) {
            throw new BusinessConflictException(
                    "O ingrediente já possui movimentações."
            );
        }

        return apply(
                actor,
                ingredient,
                StockMovementType.INITIAL,
                quantity,
                null,
                null,
                null
        );
    }

    private Ingredient lockActiveIngredient(
            User actor,
            Long ingredientId
    ) {
        Ingredient ingredient = ingredientRepository
                .findByIdAndCoffeeShopIdForUpdate(
                        ingredientId,
                        actor.getCoffeeShop().getId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ingrediente não encontrado."
                ));

        if (!ingredient.isActive()) {
            throw new BusinessConflictException(
                    "Ingrediente inativo não pode receber movimentações."
            );
        }

        return ingredient;
    }

    // A query define a ordenação.
    // O service limita o tamanho da página.
    private Pageable sanitize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(
                    0,
                    DEFAULT_PAGE_SIZE
            );
        }

        if (pageable.getPageNumber() < 0) {
            throw new BusinessRuleException(
                    "O número da página não pode ser negativo."
            );
        }

        if (pageable.getPageSize() <= 0) {
            throw new BusinessRuleException(
                    "O tamanho da página deve ser maior que zero."
            );
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                Math.min(
                        pageable.getPageSize(),
                        MAX_PAGE_SIZE
                )
        );
    }

    private void validateAmount(
            Ingredient ingredient,
            BigDecimal value,
            String label
    ) {
        if (value == null) {
            throw new BusinessRuleException(
                    label + " é obrigatória."
            );
        }

        int scale = value.stripTrailingZeros().scale();

        if (scale > QUANTITY_SCALE) {
            throw new BusinessRuleException(
                    label + " deve ter no máximo 3 casas decimais."
            );
        }

        if (ingredient.getUnit() == IngredientUnit.UNIT
                && scale > 0) {
            throw new BusinessRuleException(
                    label + " deve ser um número inteiro "
                            + "para ingredientes em UNIT."
            );
        }

        if (value.abs().compareTo(MAX_QUANTITY) > 0) {
            throw new BusinessRuleException(
                    label + " excede o valor máximo permitido."
            );
        }
    }

    // Único ponto que altera o saldo.
    // Registra a movimentação e atualiza o ingrediente
    // na mesma transação.
    private StockMovement apply(
            User actor,
            Ingredient ingredient,
            StockMovementType type,
            BigDecimal delta,
            StockAdjustmentReason reason,
            String description,
            StockMovement reverses
    ) {
        BigDecimal newBalance =
                ingredient.getCurrentQuantity().add(delta);

        if (newBalance.signum() < 0) {
            throw new BusinessConflictException(
                    "Saldo insuficiente: a operação deixaria "
                            + "o estoque negativo."
            );
        }

        if (newBalance.compareTo(MAX_QUANTITY) > 0) {
            throw new BusinessRuleException(
                    "O saldo resultante excede "
                            + "o valor máximo permitido."
            );
        }

        StockMovement movement;

        try {
            movement = new StockMovement(
                    actor.getCoffeeShop(),
                    ingredient,
                    type,
                    delta,
                    newBalance,
                    reason,
                    description,
                    actor,
                    reverses
            );
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Movimentação rejeitada pela entidade: {}",
                    ex.getMessage(),
                    ex
            );

            throw new BusinessRuleException(
                    "Movimentação inválida: " + ex.getMessage()
            );
        }

        StockMovement saved =
                movementRepository.saveAndFlush(movement);

        ingredient.applyStockChange(newBalance);

        return saved;
    }
}
