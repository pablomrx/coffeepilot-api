package com.coffeepilot.api.recipe;

import com.coffeepilot.api.recipe.dto.RecipeResponse;
import com.coffeepilot.api.recipe.dto.UpdateRecipeRequest;
import com.coffeepilot.api.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products/{productId}/recipe")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public RecipeResponse get(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long productId) {

        RecipeService.RecipeView view =
                recipeService.get(authenticatedUser, productId);

        return RecipeResponse.from(view.product(), view.items());
    }

    @PutMapping
    public RecipeResponse replace(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateRecipeRequest request) {

        List<RecipeService.RecipeLine> lines = request.items().stream()
                .map(item -> new RecipeService.RecipeLine(
                        item.ingredientId(),
                        item.quantity()))
                .toList();

        RecipeService.RecipeView view =
                recipeService.replace(authenticatedUser, productId, lines);

        return RecipeResponse.from(view.product(), view.items());
    }
}
