package com.coffeepilot.api.order;

import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.ingredient.IngredientRepository;
import com.coffeepilot.api.product.Product;
import com.coffeepilot.api.product.ProductRepository;
import com.coffeepilot.api.recipe.RecipeItemRepository;
import com.coffeepilot.api.recipe.RecipeLineView;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.stock.StockService;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class OrderService {

    private static final Role[] ORDER_ROLES =
            {Role.OWNER, Role.MANAGER, Role.ATTENDANT};

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, "createdAt", "id");

    public record OrderLine(Long productId, Integer quantity) {
    }

    private final ActiveUserProvider activeUserProvider;
    private final OrderRepository orderRepository;
    private final OrderSequenceRepository orderSequenceRepository;
    private final ProductRepository productRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final IngredientRepository ingredientRepository;
    private final StockService stockService;

    public OrderService(
            ActiveUserProvider activeUserProvider,
            OrderRepository orderRepository,
            OrderSequenceRepository orderSequenceRepository,
            ProductRepository productRepository,
            RecipeItemRepository recipeItemRepository,
            IngredientRepository ingredientRepository,
            StockService stockService
    ) {
        this.activeUserProvider = activeUserProvider;
        this.orderRepository = orderRepository;
        this.orderSequenceRepository = orderSequenceRepository;
        this.productRepository = productRepository;
        this.recipeItemRepository = recipeItemRepository;
        this.ingredientRepository = ingredientRepository;
        this.stockService = stockService;
    }

    @Transactional
    public Order create(
            AuthenticatedUser authenticatedUser,
            String tableReference,
            String notes,
            List<OrderLine> lines
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                ORDER_ROLES
        );

        validateLines(lines);

        Long establishmentId = actor.getEstablishment().getId();

        Map<Long, Integer> quantityByProduct = new HashMap<>();

        for (OrderLine line : lines) {
            quantityByProduct.put(line.productId(), line.quantity());
        }

        List<Long> productIds = quantityByProduct.keySet().stream()
                .sorted()
                .toList();

        List<Product> products =
                productRepository.findAllByIdsForShare(establishmentId, productIds);

        if (products.size() != productIds.size()) {
            throw new BusinessRuleException("Produto não encontrado.");
        }

        for (Product product : products) {
            if (!product.isActive()) {
                throw new BusinessRuleException(
                        "O produto \"" + product.getName() + "\" está inativo."
                );
            }
        }

        List<RecipeLineView> recipeLines =
                recipeItemRepository.findLinesByProducts(
                        establishmentId,
                        productIds
                );

        Set<Long> trackedProducts = new HashSet<>();
        Map<Long, BigDecimal> required = new TreeMap<>();

        for (RecipeLineView line : recipeLines) {
            trackedProducts.add(line.productId());

            BigDecimal consumed = line.quantity().multiply(
                    BigDecimal.valueOf(quantityByProduct.get(line.productId()))
            );

            required.merge(line.ingredientId(), consumed, BigDecimal::add);
        }

        if (!required.isEmpty()) {
            ingredientRepository.findAllByIdsForUpdate(
                    establishmentId,
                    new ArrayList<>(required.keySet())
            );
        }

        long orderNumber = nextOrderNumber(establishmentId);

        Order order;

        try {
            order = new Order(
                    establishmentId,
                    orderNumber,
                    actor,
                    tableReference,
                    notes
            );

            List<Product> sortedProducts = products.stream()
                    .sorted(Comparator.comparing(Product::getId))
                    .toList();

            for (Product product : sortedProducts) {
                order.addItem(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        null,
                        quantityByProduct.get(product.getId()),
                        trackedProducts.contains(product.getId())
                );
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }

        Order saved = orderRepository.saveAndFlush(order);

        stockService.consumeForOrder(
                actor,
                saved.getId(),
                "Pedido nº " + orderNumber,
                required
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Order> list(
            AuthenticatedUser authenticatedUser,
            OrderStatus status,
            Pageable pageable
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                ORDER_ROLES
        );

        Long establishmentId = actor.getEstablishment().getId();
        Pageable page = sanitize(pageable);

        return status == null
                ? orderRepository.findByEstablishmentId(establishmentId, page)
                : orderRepository.findByEstablishmentIdAndStatus(
                establishmentId,
                status,
                page
        );
    }

    @Transactional(readOnly = true)
    public Order get(
            AuthenticatedUser authenticatedUser,
            Long orderId
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                ORDER_ROLES
        );

        return orderRepository
                .findDetailByIdAndEstablishmentId(
                        orderId,
                        actor.getEstablishment().getId()
                )
                .orElseThrow(this::notFound);
    }

    @Transactional
    public Order startPreparation(
            AuthenticatedUser authenticatedUser,
            Long orderId
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                ORDER_ROLES
        );

        Order order = lockOrder(actor, orderId);

        try {
            order.startPreparation();
        } catch (IllegalStateException ex) {
            throw new BusinessConflictException(ex.getMessage());
        }

        orderRepository.saveAndFlush(order);

        return reload(actor, orderId);
    }

    @Transactional
    public Order complete(
            AuthenticatedUser authenticatedUser,
            Long orderId
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                ORDER_ROLES
        );

        Order order = lockOrder(actor, orderId);

        try {
            order.complete();
        } catch (IllegalStateException ex) {
            throw new BusinessConflictException(ex.getMessage());
        }

        orderRepository.saveAndFlush(order);

        return reload(actor, orderId);
    }

    @Transactional
    public Order cancel(
            AuthenticatedUser authenticatedUser,
            Long orderId,
            String reason
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                ORDER_ROLES
        );

        Order order = lockOrder(actor, orderId);
        OrderStatus statusBefore = order.getStatus();

        if (actor.getRole() == Role.ATTENDANT
                && statusBefore == OrderStatus.IN_PREPARATION) {
            throw new AccessDeniedException(
                    "Atendentes não podem cancelar pedidos em preparação."
            );
        }

        try {
            order.cancel(actor, reason);
        } catch (IllegalStateException ex) {
            throw new BusinessConflictException(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }

        if (statusBefore == OrderStatus.PENDING) {
            stockService.reverseOrderConsumption(
                    actor,
                    order.getId(),
                    "Cancelamento do pedido nº " + order.getOrderNumber()
            );
        }

        orderRepository.saveAndFlush(order);

        return reload(actor, orderId);
    }

    private long nextOrderNumber(Long establishmentId) {
        orderSequenceRepository.createIfMissing(establishmentId);

        OrderSequence sequence = orderSequenceRepository
                .findForUpdate(establishmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Contador de pedidos não encontrado."
                ));

        return sequence.nextNumber();
    }

    private void validateLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException(
                    "O pedido deve ter ao menos um item."
            );
        }

        if (lines.size() > Order.MAX_ITEMS) {
            throw new BusinessRuleException(
                    "O pedido deve ter no máximo 50 itens."
            );
        }

        Set<Long> seen = new HashSet<>();

        for (OrderLine line : lines) {
            if (line == null) {
                throw new BusinessRuleException("Item do pedido inválido.");
            }

            if (line.productId() == null) {
                throw new BusinessRuleException("O produto é obrigatório.");
            }

            if (line.productId() <= 0) {
                throw new BusinessRuleException(
                        "O produto informado é inválido."
                );
            }

            if (line.quantity() == null) {
                throw new BusinessRuleException(
                        "A quantidade é obrigatória."
                );
            }

            if (line.quantity() < OrderItem.MIN_QUANTITY
                    || line.quantity() > OrderItem.MAX_QUANTITY) {
                throw new BusinessRuleException(
                        "A quantidade de cada item deve estar entre 1 e 999."
                );
            }

            if (!seen.add(line.productId())) {
                throw new BusinessRuleException(
                        "Produto repetido no pedido."
                );
            }
        }
    }

    private Order lockOrder(User actor, Long orderId) {
        return orderRepository
                .findByIdAndEstablishmentIdForUpdate(
                        orderId,
                        actor.getEstablishment().getId()
                )
                .orElseThrow(this::notFound);
    }

    private Order reload(User actor, Long orderId) {
        return orderRepository
                .findDetailByIdAndEstablishmentId(
                        orderId,
                        actor.getEstablishment().getId()
                )
                .orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Pedido não encontrado.");
    }

    private Pageable sanitize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, NEWEST_FIRST);
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
                NEWEST_FIRST
        );
    }
}
