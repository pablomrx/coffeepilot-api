package com.coffeepilot.api.employee.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.coffeepilot.api.user.Role;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateEmployeeRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private Set<ConstraintViolation<CreateEmployeeRequest>> validate(
            String name, String email, String password, Role role) {
        return validator.validate(new CreateEmployeeRequest(name, email, password, role));
    }

    @Test
    void requestValidoNaoGeraViolacoes() {
        assertThat(validate("Ana", "ana@x.com", "senha1234", Role.valueOf("ATTENDANT")))
                .isEmpty();
    }

    @Test
    void normalizaNomeEEmail() {
        var req = new CreateEmployeeRequest("  Ana  ", "  Ana@X.COM ", "senha1234",
                Role.valueOf("ATTENDANT"));

        assertThat(req.name()).isEqualTo("Ana");
        assertThat(req.email()).isEqualTo("ana@x.com");
    }

    @Test
    void naoAlteraASenha() {
        var req = new CreateEmployeeRequest("Ana", "ana@x.com", "  senha 1234  ",
                Role.valueOf("ATTENDANT"));

        assertThat(req.password()).isEqualTo("  senha 1234  ");
    }

    @Test
    void rejeitaEmailSemDominioComPonto() {
        assertThat(validate("Ana", "a@b", "senha1234", Role.valueOf("ATTENDANT")))
                .isNotEmpty();
    }

    @Test
    void rejeitaSenhaCurta() {
        assertThat(validate("Ana", "ana@x.com", "1234567", Role.valueOf("ATTENDANT")))
                .isNotEmpty();
    }

    @Test
    void rejeitaSenhaComMaisDe72Bytes() {
        // 40 caracteres, mas 80 bytes em UTF-8 ("ç" ocupa 2 bytes)
        String senha = "ç".repeat(40);

        var violacoes = validate("Ana", "ana@x.com", senha, Role.valueOf("ATTENDANT"));

        assertThat(violacoes)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("passwordWithinBcryptLimit");
    }

    @Test
    void aceitaSenhaDe72BytesExatos() {
        assertThat(validate("Ana", "ana@x.com", "a".repeat(72), Role.valueOf("ATTENDANT")))
                .isEmpty();
    }

    @Test
    void rejeitaCamposObrigatoriosNulos() {
        assertThat(validate(null, null, null, null)).hasSize(4);
    }

    @Test
    void toStringNaoExpoeSenha() {
        var req = new CreateEmployeeRequest("Ana", "ana@x.com", "segredo123",
                Role.valueOf("ATTENDANT"));

        assertThat(req.toString()).doesNotContain("segredo123").contains("password=***");
    }
}