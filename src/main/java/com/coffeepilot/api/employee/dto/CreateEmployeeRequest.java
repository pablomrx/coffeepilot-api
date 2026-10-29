package com.coffeepilot.api.employee.dto;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.coffeepilot.api.user.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(
                regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                message = "Informe um e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,

        @NotBlank(message = "A senha inicial é obrigatória.")
        @Size(min = 8, max = 72,
                message = "A senha deve ter entre 8 e 72 caracteres.")
        String password,

        @NotNull(message = "O cargo é obrigatório.")
        Role role

) {

    private static final int BCRYPT_MAX_BYTES = 72;

    public CreateEmployeeRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @JsonIgnore
    @AssertTrue(message = "A senha deve ter no máximo 72 bytes.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    @Override
    public String toString() {
        return "CreateEmployeeRequest[name=" + name
                + ", email=" + email
                + ", password=***"
                + ", role=" + role + "]";
    }
}
