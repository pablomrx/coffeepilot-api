package com.coffeepilot.api.stock;

import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.stock.dto.CreateStockAdjustmentRequest;
import com.coffeepilot.api.stock.dto.CreateStockEntryRequest;
import com.coffeepilot.api.stock.dto.ReverseMovementRequest;
import com.coffeepilot.api.stock.dto.StockMovementResponse;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/ingredients/{ingredientId}/stock/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse registerEntry(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long ingredientId,
            @Valid @RequestBody CreateStockEntryRequest request) {

        StockMovement movement = stockService.registerEntry(
                authenticatedUser,
                ingredientId,
                request.quantity(),
                request.description());

        return StockMovementResponse.from(movement);
    }

    @PostMapping("/ingredients/{ingredientId}/stock/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse registerAdjustment(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long ingredientId,
            @Valid @RequestBody CreateStockAdjustmentRequest request) {

        StockMovement movement = stockService.registerAdjustment(
                authenticatedUser,
                ingredientId,
                request.quantityDelta(),
                request.reason(),
                request.justification());

        return StockMovementResponse.from(movement);
    }

    @GetMapping("/ingredients/{ingredientId}/stock/movements")
    public Page<StockMovementResponse> history(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long ingredientId,
            Pageable pageable) {

        return stockService
                .history(authenticatedUser, ingredientId, pageable)
                .map(StockMovementResponse::from);
    }

    @PostMapping("/stock/movements/{movementId}/reversals")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse reverse(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long movementId,
            @Valid @RequestBody ReverseMovementRequest request) {

        StockMovement movement = stockService.reverse(
                authenticatedUser,
                movementId,
                request.description());

        return StockMovementResponse.from(movement);
    }
}
