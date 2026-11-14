package com.coffeepilot.api.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/employees");

    // ---------- helpers ----------

    private DataIntegrityViolationException violation(String constraint) {
        var sql = new SQLException("duplicate key " + constraint, "23505");
        var cause = new ConstraintViolationException("violação", sql, constraint);
        return new DataIntegrityViolationException("erro", cause);
    }

    @SuppressWarnings("unused")
    private void dummyEndpoint(String body) {
    }

    private MethodParameter dummyParameter() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyEndpoint", String.class);
        return new MethodParameter(method, 0);
    }

    private MethodArgumentNotValidException validationError(String field, String message)
            throws NoSuchMethodException {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", field, message));

        return new MethodArgumentNotValidException(dummyParameter(), bindingResult);
    }

    // ---------- violações de integridade ----------

    @Test
    void constraintDeEmailVira409() {
        var response = handler.handleDataIntegrity(violation("uq_users_email"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void constraintDeNomeDeIngredienteVira409ComCampoName() {
        var response = handler.handleDataIntegrity(
                violation("uq_ingredients_establishment_name"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message())
                .isEqualTo("Já existe um ingrediente com este nome.");
        assertThat(response.getBody().fieldErrors())
                .extracting(ApiError.FieldViolation::field)
                .containsExactly("name");
    }

    @Test
    void constraintDeNomeDeProdutoVira409ComCampoName() {
        var response = handler.handleDataIntegrity(
                violation("uq_products_establishment_name"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message())
                .isEqualTo("Já existe um produto com este nome.");
        assertThat(response.getBody().fieldErrors())
                .extracting(ApiError.FieldViolation::field)
                .containsExactly("name");
    }

    @Test
    void constraintDeEstornoDuplicadoVira409() {
        var response = handler.handleDataIntegrity(
                violation("uq_stock_movements_reverses_movement"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message())
                .isEqualTo("Esta movimentação já foi estornada.");
    }

    @Test
    void constraintDeSaldoInicialDuplicadoVira409() {
        var response = handler.handleDataIntegrity(
                violation("uq_stock_movements_initial_per_ingredient"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message())
                .isEqualTo("O ingrediente já possui movimentações de estoque.");
    }

    @Test
    void outraConstraintVira500() {
        var response = handler.handleDataIntegrity(violation("fk_users_establishment"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void violacaoSemConstraintReconhecidaVira500SemVazarDetalhes() {
        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("erro interno do banco"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Erro interno do servidor.");
    }

    // ---------- exceções de negócio ----------

    @Test
    void recursoNaoEncontradoVira404ComAMensagemDaExcecao() {
        var response = handler.handleNotFound(
                new ResourceNotFoundException("Ingrediente não encontrado."), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Ingrediente não encontrado.");
        assertThat(response.getBody().path()).isEqualTo("/employees");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    @Test
    void conflitoDeNegocioVira409ComAMensagemDaExcecao() {
        var response = handler.handleConflict(
                new BusinessConflictException("Esta movimentação já foi estornada."), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message())
                .isEqualTo("Esta movimentação já foi estornada.");
    }

    @Test
    void regraDeNegocioVioladaVira400ComAMensagemDaExcecao() {
        var response = handler.handleBusinessRule(
                new BusinessRuleException("A variação do ajuste não pode ser zero."), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message())
                .isEqualTo("A variação do ajuste não pode ser zero.");
    }

    // ---------- validação e mapeamento de campos ----------

    @Test
    void erroDeValidacaoVira400ComOCampoEMensagem() throws Exception {
        var response = handler.handleValidation(
                validationError("quantity", "A quantidade é obrigatória."), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Dados inválidos.");
        assertThat(response.getBody().fieldErrors())
                .extracting(ApiError.FieldViolation::field,
                        ApiError.FieldViolation::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "quantity", "A quantidade é obrigatória."));
    }

    @Test
    void campoDaValidacaoDeSenhaEhMapeadoParaPassword() throws Exception {
        var response = handler.handleValidation(
                validationError("passwordWithinBcryptLimit",
                        "A senha deve ter no máximo 72 bytes."), request);

        assertThat(response.getBody().fieldErrors())
                .extracting(ApiError.FieldViolation::field)
                .containsExactly("password");
    }

    @Test
    void campoDaValidacaoDeVariacaoZeroEhMapeadoParaQuantityDelta() throws Exception {
        var response = handler.handleValidation(
                validationError("quantityDeltaNotZero",
                        "A variação não pode ser zero."), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().fieldErrors())
                .extracting(ApiError.FieldViolation::field,
                        ApiError.FieldViolation::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "quantityDelta", "A variação não pode ser zero."));
    }

    @Test
    void parametroComTipoInvalidoVira400() throws Exception {
        var exception = new MethodArgumentTypeMismatchException(
                "talvez", Boolean.class, "active", dummyParameter(), null);

        var response = handler.handleTypeMismatch(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message())
                .isEqualTo("Valor inválido para o parâmetro 'active'.");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    // ---------- segurança e erros inesperados ----------

    @Test
    void falhaDeAutenticacaoVira401() {
        var response = handler.handleAuthentication(
                new BadCredentialsException("senha incorreta"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().message()).isEqualTo("Credenciais inválidas.");
    }

    @Test
    void acessoNegadoVira403SemVazarOMotivo() {
        var response = handler.handleAccessDenied(
                new AccessDeniedException("Usuário ou estabelecimento inativo."), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Acesso negado.");
    }

    @Test
    void erroDeClienteDoSpringMantemOStatus4xx() {
        var response = handler.handleUnexpected(
                new ResponseStatusException(HttpStatus.NOT_FOUND), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void erroInesperadoVira500ComMensagemGenerica() {
        var response = handler.handleUnexpected(
                new IllegalStateException("detalhe interno"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Erro interno do servidor.");
    }
}
