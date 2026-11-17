package com.coffeepilot.api.ingredient;

import com.coffeepilot.api.common.dto.PageResponse;
import com.coffeepilot.api.ingredient.dto.CreateIngredientRequest;
import com.coffeepilot.api.ingredient.dto.IngredientProductResponse;
import com.coffeepilot.api.ingredient.dto.IngredientResponse;
import com.coffeepilot.api.ingredient.dto.UpdateIngredientRequest;
import com.coffeepilot.api.ingredient.dto.UpdateIngredientStatusRequest;
import com.coffeepilot.api.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateIngredientRequest request) {

        Ingredient ingredient = ingredientService.create(
                authenticatedUser,
                request.name(),
                request.unit(),
                request.minimumQuantity(),
                request.initialQuantity());

        return IngredientResponse.from(ingredient);
    }

    @GetMapping
    public PageResponse<IngredientResponse> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean lowStock,
            Pageable pageable) {

        return PageResponse.from(
                ingredientService.list(
                        authenticatedUser, active, lowStock, pageable),
                IngredientResponse::from);
    }

    @GetMapping("/{ingredientId}")
    public IngredientResponse get(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long ingredientId) {

        return IngredientResponse.from(
                ingredientService.get(authenticatedUser, ingredientId));
    }

    @GetMapping("/{ingredientId}/products")
    public List<IngredientProductResponse> products(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long ingredientId) {

        return ingredientService.listProducts(authenticatedUser, ingredientId)
                .stream()
                .map(IngredientProductResponse::from)
                .toList();
    }

    @PatchMapping("/{ingredientId}")
    public IngredientResponse update(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long ingredientId,
            @Valid @RequestBody UpdateIngredientRequest request) {

        Ingredient ingredient = ingredientService.update(
                authenticatedUser,
                ingredientId,
                request.name(),
                request.minimumQuantity());

        return IngredientResponse.from(ingredient);
    }

    @PatchMapping("/{ingredientId}/status")
    public IngredientResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long ingredientId,
            @Valid @RequestBody UpdateIngredientStatusRequest request) {

        Ingredient ingredient = ingredientService.setActive(
                authenticatedUser,
                ingredientId,
                request.active());

        return IngredientResponse.from(ingredient);
    }
}
