package com.coffeepilot.api.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "O nome não pode ficar em branco."
        )
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String description,

        @DecimalMin(value = "0.00", inclusive = false,
                message = "O preço deve ser maior que zero.")
        @Digits(integer = 8, fraction = 2,
                message = "O preço deve ter até 8 dígitos inteiros e 2 decimais.")
        BigDecimal price

) {

    public UpdateProductRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
    }
}
