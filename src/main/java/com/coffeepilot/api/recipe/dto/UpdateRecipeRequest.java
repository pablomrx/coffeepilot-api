package com.coffeepilot.api.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateRecipeRequest(

        @NotNull(message = "A lista de itens é obrigatória.")
        @Size(max = 50, message = "A receita deve ter no máximo 50 itens.")
        List<@NotNull(message = "Item da receita inválido.") @Valid RecipeItemRequest > items

) {
}
