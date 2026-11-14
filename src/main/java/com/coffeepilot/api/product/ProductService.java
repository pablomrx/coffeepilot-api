package com.coffeepilot.api.product;

import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class ProductService {

    private static final Role[] PRODUCT_MANAGE_ROLES =
            {Role.OWNER, Role.MANAGER};

    private static final Role[] PRODUCT_READ_ROLES =
            {Role.OWNER, Role.MANAGER, Role.ATTENDANT, Role.STOCKIST};

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SEARCH_MAX_LENGTH = 150;

    private static final Sort NAME_ORDER = Sort.by("name", "id");

    private static final String DUPLICATE_NAME_MESSAGE =
            "Já existe um produto com este nome.";

    private final ActiveUserProvider activeUserProvider;
    private final ProductRepository productRepository;

    public ProductService(
            ActiveUserProvider activeUserProvider,
            ProductRepository productRepository
    ) {
        this.activeUserProvider = activeUserProvider;
        this.productRepository = productRepository;
    }

    @Transactional
    public Product create(
            AuthenticatedUser authenticatedUser,
            String name,
            String description,
            BigDecimal price
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                PRODUCT_MANAGE_ROLES
        );

        Long establishmentId = actor.getEstablishment().getId();

        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("O nome é obrigatório.");
        }

        if (price == null) {
            throw new BusinessRuleException("O preço é obrigatório.");
        }

        if (productRepository.existsByName(establishmentId, name)) {
            throw new BusinessConflictException(DUPLICATE_NAME_MESSAGE);
        }

        Product product;

        try {
            product = new Product(establishmentId, name, description, price);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }

        return productRepository.saveAndFlush(product);
    }

    @Transactional(readOnly = true)
    public Page<Product> list(
            AuthenticatedUser authenticatedUser,
            Boolean active,
            String search,
            Pageable pageable
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                PRODUCT_READ_ROLES
        );

        // Atendentes só enxergam produtos ativos.
        Boolean effectiveActive =
                actor.getRole() == Role.ATTENDANT ? Boolean.TRUE : active;

        return productRepository.findByFilters(
                actor.getEstablishment().getId(),
                effectiveActive,
                toSearchPattern(search),
                sanitize(pageable)
        );
    }

    @Transactional(readOnly = true)
    public Product get(
            AuthenticatedUser authenticatedUser,
            Long productId
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                PRODUCT_READ_ROLES
        );

        Product product = productRepository
                .findByIdAndEstablishmentId(
                        productId,
                        actor.getEstablishment().getId()
                )
                .orElseThrow(this::notFound);

        // Para o atendente, produto inativo é tratado como inexistente.
        if (actor.getRole() == Role.ATTENDANT && !product.isActive()) {
            throw notFound();
        }

        return product;
    }

    @Transactional
    public Product update(
            AuthenticatedUser authenticatedUser,
            Long productId,
            String name,
            String description,
            BigDecimal price
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                PRODUCT_MANAGE_ROLES
        );

        if (name == null && description == null && price == null) {
            throw new BusinessRuleException(
                    "Informe ao menos um campo para atualizar."
            );
        }

        Product product = lockProduct(actor, productId);

        try {
            if (name != null) {
                if (name.isBlank()) {
                    throw new BusinessRuleException(
                            "O nome não pode ficar em branco."
                    );
                }

                if (productRepository.existsByNameExcludingId(
                        product.getEstablishmentId(),
                        product.getId(),
                        name
                )) {
                    throw new BusinessConflictException(DUPLICATE_NAME_MESSAGE);
                }

                product.rename(name);
            }

            // null = não altera; "" = remove a descrição.
            if (description != null) {
                product.changeDescription(description);
            }

            if (price != null) {
                product.changePrice(price);
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ex.getMessage());
        }

        return productRepository.saveAndFlush(product);
    }

    @Transactional
    public Product setActive(
            AuthenticatedUser authenticatedUser,
            Long productId,
            boolean active
    ) {
        User actor = activeUserProvider.require(
                authenticatedUser,
                PRODUCT_MANAGE_ROLES
        );

        Product product = lockProduct(actor, productId);

        if (active) {
            product.activate();
        } else {
            product.deactivate();
        }

        return productRepository.saveAndFlush(product);
    }

    private Product lockProduct(User actor, Long productId) {
        return productRepository
                .findByIdAndEstablishmentIdForUpdate(
                        productId,
                        actor.getEstablishment().getId()
                )
                .orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Produto não encontrado.");
    }

    // Converte o termo digitado em padrão LIKE: minúsculas, curingas
    // escapados com '!' e envolvido por %. Sem termo, devolve "%".
    private String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }

        String term = search.trim();

        if (term.length() > SEARCH_MAX_LENGTH) {
            throw new BusinessRuleException(
                    "A busca deve ter no máximo 150 caracteres."
            );
        }

        String escaped = term
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
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
