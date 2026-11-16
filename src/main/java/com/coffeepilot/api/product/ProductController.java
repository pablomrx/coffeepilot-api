package com.coffeepilot.api.product;

import com.coffeepilot.api.common.dto.PageResponse;
import com.coffeepilot.api.product.dto.CreateProductRequest;
import com.coffeepilot.api.product.dto.ProductResponse;
import com.coffeepilot.api.product.dto.UpdateProductRequest;
import com.coffeepilot.api.product.dto.UpdateProductStatusRequest;
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

import java.util.Set;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateProductRequest request) {

        Product product = productService.create(
                authenticatedUser,
                request.name(),
                request.description(),
                request.price());

        return ProductResponse.from(product, false);
    }

    @GetMapping
    public PageResponse<ProductResponse> list(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            Pageable pageable) {

        Page<Product> products = productService.list(
                authenticatedUser,
                active,
                search,
                pageable
        );

        Set<Long> productIdsWithRecipe =
                productService.findProductIdsWithRecipe(
                        authenticatedUser,
                        products.getContent()
                );

        return PageResponse.from(
                products,
                product -> ProductResponse.from(
                        product,
                        productIdsWithRecipe.contains(product.getId())
                )
        );
    }

    @GetMapping("/{productId}")
    public ProductResponse get(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long productId) {

        Product product = productService.get(
                authenticatedUser,
                productId
        );

        boolean hasRecipe = productService.findProductIdsWithRecipe(
                authenticatedUser,
                java.util.List.of(product)
        ).contains(product.getId());

        return ProductResponse.from(product, hasRecipe);
    }

    @PatchMapping("/{productId}")
    public ProductResponse update(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductRequest request) {

        Product product = productService.update(
                authenticatedUser,
                productId,
                request.name(),
                request.description(),
                request.price());

        boolean hasRecipe = productService.findProductIdsWithRecipe(
                authenticatedUser,
                java.util.List.of(product)
        ).contains(product.getId());

        return ProductResponse.from(product, hasRecipe);
    }

    @PatchMapping("/{productId}/status")
    public ProductResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductStatusRequest request) {

        Product product = productService.setActive(
                authenticatedUser,
                productId,
                request.active());

        boolean hasRecipe = productService.findProductIdsWithRecipe(
                authenticatedUser,
                java.util.List.of(product)
        ).contains(product.getId());

        return ProductResponse.from(product, hasRecipe);
    }
}
