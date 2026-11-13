package com.coffeepilot.api.order;

import com.coffeepilot.api.order.dto.CancelOrderRequest;
import com.coffeepilot.api.order.dto.CreateOrderRequest;
import com.coffeepilot.api.order.dto.OrderResponse;
import com.coffeepilot.api.order.dto.OrderSummaryResponse;
import com.coffeepilot.api.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
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
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateOrderRequest request) {

        List<OrderService.OrderLine> lines = request.items().stream()
                .map(item -> new OrderService.OrderLine(
                        item.productId(),
                        item.quantity()))
                .toList();

        Order order = orderService.create(
                authenticatedUser,
                request.tableReference(),
                request.notes(),
                lines);

        return OrderResponse.from(order);
    }

    @GetMapping
    public Page<OrderSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {

        return orderService
                .list(authenticatedUser, status, pageable)
                .map(OrderSummaryResponse::from);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long orderId) {

        return OrderResponse.from(
                orderService.get(authenticatedUser, orderId));
    }

    @PatchMapping("/{orderId}/start")
    public OrderResponse start(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long orderId) {

        return OrderResponse.from(
                orderService.startPreparation(authenticatedUser, orderId));
    }

    @PatchMapping("/{orderId}/complete")
    public OrderResponse complete(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long orderId) {

        return OrderResponse.from(
                orderService.complete(authenticatedUser, orderId));
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {

        String reason = request == null ? null : request.reason();

        return OrderResponse.from(
                orderService.cancel(authenticatedUser, orderId, reason));
    }
}
