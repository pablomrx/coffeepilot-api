package com.coffeepilot.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record RegisterRequest(

        @NotBlank(message = "O nome da cafeteria é obrigatório.")
        @Size(max = 150, message = "O nome da cafeteria deve ter no máximo 150 caracteres.")
        String coffeeShopName,

        @NotBlank(message = "O nome do proprietário é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String ownerName,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String password

) {

    @AssertTrue(message = "A senha é longa demais. Use no máximo 72 bytes (caracteres especiais ocupam mais de um).")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "RegisterRequest[coffeeShopName=" + coffeeShopName
                + ", ownerName=" + ownerName
                + ", email=" + email
                + ", password=***]";
    }
}
