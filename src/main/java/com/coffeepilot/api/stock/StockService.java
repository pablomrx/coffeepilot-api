package com.coffeepilot.api.stock;

import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.InsufficientStockException;
import com.coffeepilot.api.common.exception.InsufficientStockException.Reason;
import com.coffeepilot.api.common.exception.InsufficientStockException.StockIssue;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

        if (description == null || description.isBlank()) {
            throw new BusinessRuleException(
                    "A justificativa do estorno é obrigatória."
            );
        }

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

        if (original.getType() == StockMovementType.CONSUMPTION) {
            throw new BusinessRuleException(
                    "Movimentações de consumo de pedidos não podem ser "
                            + "estornadas manualmente. Cancele o pedido."
            );
        }

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

    @Transactional
    public List<StockMovement> consumeForOrder(
            User actor,
            Long orderId,
            String description,
            Map<Long, BigDecimal> requiredByIngredient
    ) {
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(
                requiredByIngredient,
                "requiredByIngredient is required"
        );

        if (requiredByIngredient.isEmpty()) {
            return List.of();
        }

        for (Map.Entry<Long, BigDecimal> entry
                : requiredByIngredient.entrySet()) {

            BigDecimal required = entry.getValue();

            if (entry.getKey() == null
                    || required == null
                    || required.signum() <= 0) {
                throw new BusinessRuleException(
                        "O consumo calculado para o pedido é inválido."
                );
            }

            if (required.stripTrailingZeros().scale() > QUANTITY_SCALE
                    || required.compareTo(MAX_QUANTITY) > 0) {
                throw new BusinessRuleException(
                        "O consumo de ingredientes do pedido excede "
                                + "o valor máximo permitido."
                );
            }
        }

        Long coffeeShopId = actor.getCoffeeShop().getId();

        List<Long> ids = requiredByIngredient.keySet().stream()
                .sorted()
                .toList();

        List<Ingredient> locked =
                ingredientRepository.findAllByIdsForUpdate(coffeeShopId, ids);

        if (locked.size() != ids.size()) {
            throw new BusinessRuleException(
                    "Ingrediente da receita não encontrado."
            );
        }

        Map<Long, Ingredient> byId = new HashMap<>();

        for (Ingredient ingredient : locked) {
            byId.put(ingredient.getId(), ingredient);
        }

        List<StockIssue> issues = new ArrayList<>();

        for (Long id : ids) {
            Ingredient ingredient = byId.get(id);
            BigDecimal required = requiredByIngredient.get(id);
            BigDecimal available = ingredient.getCurrentQuantity();

            if (!ingredient.isActive()) {
                issues.add(issue(
                        ingredient,
                        required,
                        available,
                        Reason.INACTIVE_INGREDIENT
                ));
            } else if (available.compareTo(required) < 0) {
                issues.add(issue(
                        ingredient,
                        required,
                        available,
                        Reason.INSUFFICIENT_STOCK
                ));
            }
        }

        if (!issues.isEmpty()) {
            throw new InsufficientStockException(issues);
        }

        List<StockMovement> movements = new ArrayList<>();

        for (Long id : ids) {
            movements.add(apply(
                    actor,
                    byId.get(id),
                    StockMovementType.CONSUMPTION,
                    requiredByIngredient.get(id).negate(),
                    null,
                    description,
                    null,
                    orderId
            ));
        }

        return movements;
    }

    @Transactional
    public List<StockMovement> reverseOrderConsumption(
            User actor,
            Long orderId,
            String description
    ) {
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(orderId, "orderId is required");

        if (description == null || description.isBlank()) {
            throw new BusinessRuleException(
                    "A justificativa do estorno é obrigatória."
            );
        }

        Long coffeeShopId = actor.getCoffeeShop().getId();

        List<StockConsumptionLine> lines = movementRepository
                .findConsumptionLinesByOrder(coffeeShopId, orderId);

        if (lines.isEmpty()) {
            return List.of();
        }

        List<Long> ids = lines.stream()
                .map(StockConsumptionLine::ingredientId)
                .distinct()
                .sorted()
                .toList();

        List<Ingredient> locked =
                ingredientRepository.findAllByIdsForUpdate(coffeeShopId, ids);

        if (locked.size() != ids.size()) {
            throw new BusinessRuleException(
                    "Ingrediente do pedido não encontrado."
            );
        }

        Map<Long, Ingredient> ingredientsById = new HashMap<>();

        for (Ingredient ingredient : locked) {
            ingredientsById.put(ingredient.getId(), ingredient);
        }

        Map<Long, Long> ingredientIdByMovement = new HashMap<>();

        for (StockConsumptionLine line : lines) {
            ingredientIdByMovement.put(line.movementId(), line.ingredientId());
        }

        List<StockMovement> consumptions = movementRepository
                .findConsumptionsByOrder(coffeeShopId, orderId);

        List<StockMovement> reversals = new ArrayList<>();

        for (StockMovement consumption : consumptions) {
            if (movementRepository
                    .existsByReversesMovementIdAndCoffeeShopId(
                            consumption.getId(),
                            coffeeShopId
                    )) {
                throw new BusinessConflictException(
                        "O consumo deste pedido já foi estornado."
                );
            }

            Ingredient ingredient = ingredientsById.get(
                    ingredientIdByMovement.get(consumption.getId())
            );

            if (ingredient == null) {
                throw new BusinessRuleException(
                        "Ingrediente do pedido não encontrado."
                );
            }

            reversals.add(apply(
                    actor,
                    ingredient,
                    StockMovementType.REVERSAL,
                    consumption.getQuantityDelta().negate(),
                    null,
                    description,
                    consumption,
                    consumption.getOrderId()
            ));
        }

        return reversals;
    }

    private StockIssue issue(
            Ingredient ingredient,
            BigDecimal required,
            BigDecimal available,
            Reason reason
    ) {
        return new StockIssue(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getUnit().name(),
                required,
                available,
                reason
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

    private StockMovement apply(
            User actor,
            Ingredient ingredient,
            StockMovementType type,
            BigDecimal delta,
            StockAdjustmentReason reason,
            String description,
            StockMovement reverses
    ) {
        return apply(
                actor,
                ingredient,
                type,
                delta,
                reason,
                description,
                reverses,
                null
        );
    }

    private StockMovement apply(
            User actor,
            Ingredient ingredient,
            StockMovementType type,
            BigDecimal delta,
            StockAdjustmentReason reason,
            String description,
            StockMovement reverses,
            Long orderId
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
                    reverses,
                    orderId
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
