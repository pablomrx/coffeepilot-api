package com.coffeepilot.api.ingredient;

import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.stock.StockService;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class IngredientService {

    private static final Role[] INGREDIENT_ROLES =
            {Role.OWNER, Role.MANAGER, Role.STOCKIST};

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Sort NAME_ORDER = Sort.by("name", "id");

    private final ActiveUserProvider activeUserProvider;
    private final IngredientRepository ingredientRepository;
    private final StockService stockService;

    public IngredientService(
            ActiveUserProvider activeUserProvider,
            IngredientRepository ingredientRepository,
            StockService stockService
    ) {
        this.activeUserProvider = activeUserProvider;
        this.ingredientRepository = ingredientRepository;
        this.stockService = stockService;
    }

    @Transactional
    public Ingredient create(
            AuthenticatedUser authenticatedUser,
            String name,
            IngredientUnit unit,
            BigDecimal minimumQuantity,
            BigDecimal initialQuantity
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                INGREDIENT_ROLES
        );

        Long coffeeShopId = actor.getCoffeeShop().getId();

        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("O nome é obrigatório.");
        }

        if (unit == null) {
            throw new BusinessRuleException("A unidade é obrigatória.");
        }

        BigDecimal minimum =
                minimumQuantity == null ? BigDecimal.ZERO : minimumQuantity;
        BigDecimal initial =
                initialQuantity == null ? BigDecimal.ZERO : initialQuantity;

        if (initial.signum() < 0) {
            throw new BusinessRuleException(
                    "O saldo inicial não pode ser negativo."
            );
        }

        if (ingredientRepository.existsByName(coffeeShopId, name)) {
            throw new BusinessConflictException(
                    "Já existe um ingrediente com este nome."
            );
        }

        Ingredient ingredient;

        try {
            ingredient = new Ingredient(coffeeShopId, name, unit, minimum);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }

        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);

        if (initial.signum() > 0) {
            stockService.registerInitial(actor, saved, initial);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Ingredient> list(
            AuthenticatedUser authenticatedUser,
            Boolean active,
            Boolean lowStock,
            Pageable pageable
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                INGREDIENT_ROLES
        );

        boolean onlyLowStock = Boolean.TRUE.equals(lowStock);

        if (onlyLowStock && Boolean.FALSE.equals(active)) {
            throw new BusinessRuleException(
                    "Estoque baixo considera apenas ingredientes ativos."
            );
        }

        return ingredientRepository.findByFilters(
                actor.getCoffeeShop().getId(),
                active,
                onlyLowStock,
                sanitize(pageable)
        );
    }

    @Transactional(readOnly = true)
    public Ingredient get(
            AuthenticatedUser authenticatedUser,
            Long ingredientId
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                INGREDIENT_ROLES
        );

        return ingredientRepository
                .findByIdAndCoffeeShopId(
                        ingredientId,
                        actor.getCoffeeShop().getId()
                )
                .orElseThrow(this::notFound);
    }

    @Transactional
    public Ingredient update(
            AuthenticatedUser authenticatedUser,
            Long ingredientId,
            String name,
            BigDecimal minimumQuantity
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                INGREDIENT_ROLES
        );

        if (name == null && minimumQuantity == null) {
            throw new BusinessRuleException(
                    "Informe ao menos um campo para atualizar."
            );
        }

        Ingredient ingredient = lockIngredient(actor, ingredientId);

        try {
            if (name != null) {
                if (name.isBlank()) {
                    throw new BusinessRuleException(
                            "O nome não pode ficar em branco."
                    );
                }

                if (ingredientRepository.existsByNameExcludingId(
                        ingredient.getCoffeeShopId(),
                        ingredient.getId(),
                        name
                )) {
                    throw new BusinessConflictException(
                            "Já existe um ingrediente com este nome."
                    );
                }

                ingredient.rename(name);
            }

            if (minimumQuantity != null) {
                ingredient.changeMinimumQuantity(minimumQuantity);
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }

        return ingredientRepository.saveAndFlush(ingredient);
    }

    @Transactional
    public Ingredient setActive(
            AuthenticatedUser authenticatedUser,
            Long ingredientId,
            boolean active
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                INGREDIENT_ROLES
        );

        Ingredient ingredient = lockIngredient(actor, ingredientId);

        if (active) {
            ingredient.activate();
        } else {
            ingredient.deactivate();
        }

        return ingredientRepository.saveAndFlush(ingredient);
    }

    private Ingredient lockIngredient(User actor, Long ingredientId) {
        return ingredientRepository
                .findByIdAndCoffeeShopIdForUpdate(
                        ingredientId,
                        actor.getCoffeeShop().getId()
                )
                .orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Ingrediente não encontrado.");
    }

    private Pageable sanitize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, NAME_ORDER);
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
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                NAME_ORDER
        );
    }
}
