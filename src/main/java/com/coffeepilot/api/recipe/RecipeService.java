package com.coffeepilot.api.recipe;

import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.ingredient.Ingredient;
import com.coffeepilot.api.ingredient.IngredientRepository;
import com.coffeepilot.api.product.Product;
import com.coffeepilot.api.product.ProductRepository;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecipeService {

    private static final Role[] RECIPE_MANAGE_ROLES =
            {Role.OWNER, Role.MANAGER};

    private static final Role[] RECIPE_READ_ROLES =
            {Role.OWNER, Role.MANAGER, Role.STOCKIST};

    private static final int MAX_ITEMS = 50;

    public record RecipeLine(Long ingredientId, BigDecimal quantity) {
    }

    public record RecipeView(Product product, List<RecipeItem> items) {
    }

    private final ActiveUserProvider activeUserProvider;
    private final ProductRepository productRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeItemRepository recipeItemRepository;

    public RecipeService(
            ActiveUserProvider activeUserProvider,
            ProductRepository productRepository,
            IngredientRepository ingredientRepository,
            RecipeItemRepository recipeItemRepository
    ) {
        this.activeUserProvider = activeUserProvider;
        this.productRepository = productRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeItemRepository = recipeItemRepository;
    }

    @Transactional(readOnly = true)
    public RecipeView get(
            AuthenticatedUser authenticatedUser,
            Long productId
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                RECIPE_READ_ROLES
        );

        Long coffeeShopId = actor.getCoffeeShop().getId();

        Product product = productRepository
                .findByIdAndCoffeeShopId(productId, coffeeShopId)
                .orElseThrow(this::productNotFound);

        return new RecipeView(
                product,
                recipeItemRepository.findByProduct(
                        coffeeShopId,
                        product.getId()
                )
        );
    }

    @Transactional
    public RecipeView replace(
            AuthenticatedUser authenticatedUser,
            Long productId,
            List<RecipeLine> lines
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                RECIPE_MANAGE_ROLES
        );

        validateLines(lines);

        Long coffeeShopId = actor.getCoffeeShop().getId();

        Product product = productRepository
                .findByIdAndCoffeeShopIdForUpdate(productId, coffeeShopId)
                .orElseThrow(this::productNotFound);

        Map<Long, Ingredient> ingredients =
                lockActiveIngredients(coffeeShopId, lines);

        List<RecipeItem> newItems =
                buildItems(coffeeShopId, product.getId(), lines, ingredients);

        recipeItemRepository.deleteByProduct(coffeeShopId, product.getId());
        recipeItemRepository.saveAll(newItems);

        product.touch();
        productRepository.saveAndFlush(product);

        return new RecipeView(
                product,
                recipeItemRepository.findByProduct(
                        coffeeShopId,
                        product.getId()
                )
        );
    }

    private void validateLines(List<RecipeLine> lines) {
        if (lines == null) {
            throw new BusinessRuleException(
                    "A lista de itens é obrigatória."
            );
        }

        if (lines.size() > MAX_ITEMS) {
            throw new BusinessRuleException(
                    "A receita deve ter no máximo 50 itens."
            );
        }

        Set<Long> seen = new HashSet<>();

        for (RecipeLine line : lines) {
            if (line == null) {
                throw new BusinessRuleException(
                        "Item da receita inválido."
                );
            }

            if (line.ingredientId() == null) {
                throw new BusinessRuleException(
                        "O ingrediente é obrigatório."
                );
            }

            if (line.ingredientId() <= 0) {
                throw new BusinessRuleException(
                        "O ingrediente informado é inválido."
                );
            }

            if (line.quantity() == null) {
                throw new BusinessRuleException(
                        "A quantidade é obrigatória."
                );
            }

            if (!seen.add(line.ingredientId())) {
                throw new BusinessRuleException(
                        "Ingrediente repetido na receita."
                );
            }
        }
    }

    private Map<Long, Ingredient> lockActiveIngredients(
            Long coffeeShopId,
            List<RecipeLine> lines
    ) {
        if (lines.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = lines.stream()
                .map(RecipeLine::ingredientId)
                .sorted()
                .toList();

        List<Ingredient> found =
                ingredientRepository.findAllByIdsForUpdate(coffeeShopId, ids);

        if (found.size() != ids.size()) {
            throw new BusinessRuleException("Ingrediente não encontrado.");
        }

        Map<Long, Ingredient> byId = new HashMap<>();

        for (Ingredient ingredient : found) {
            byId.put(ingredient.getId(), ingredient);
        }

        for (RecipeLine line : lines) {
            Ingredient ingredient = byId.get(line.ingredientId());

            if (!ingredient.isActive()) {
                throw new BusinessRuleException(
                        "O ingrediente \"" + ingredient.getName()
                                + "\" está inativo."
                );
            }
        }

        return byId;
    }

    private List<RecipeItem> buildItems(
            Long coffeeShopId,
            Long productId,
            List<RecipeLine> lines,
            Map<Long, Ingredient> ingredients
    ) {
        try {
            return lines.stream()
                    .map(line -> new RecipeItem(
                            coffeeShopId,
                            productId,
                            ingredients.get(line.ingredientId()),
                            line.quantity()
                    ))
                    .toList();
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }
    }

    private ResourceNotFoundException productNotFound() {
        return new ResourceNotFoundException("Produto não encontrado.");
    }
}
