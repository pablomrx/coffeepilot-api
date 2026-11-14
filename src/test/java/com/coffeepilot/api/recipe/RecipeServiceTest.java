package com.coffeepilot.api.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.ingredient.Ingredient;
import com.coffeepilot.api.ingredient.IngredientRepository;
import com.coffeepilot.api.ingredient.IngredientUnit;
import com.coffeepilot.api.product.Product;
import com.coffeepilot.api.product.ProductRepository;
import com.coffeepilot.api.recipe.RecipeService.RecipeLine;
import com.coffeepilot.api.recipe.RecipeService.RecipeView;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    private static final Long ESTABLISHMENT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 5L;

    private static final AuthenticatedUser AUTH_USER = new AuthenticatedUser(
            USER_ID, "Usuário", "usuario@teste.com",
            Role.OWNER, ESTABLISHMENT_ID, "Estabelecimento");

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Mock
    private User user;

    @Mock
    private Establishment establishment;

    @Captor
    private ArgumentCaptor<List<RecipeItem>> itemsCaptor;

    @Captor
    private ArgumentCaptor<Collection<Long>> idsCaptor;

    private RecipeService service;

    @BeforeEach
    void setUp() {
        // ActiveUserProvider real: assim as permissões por perfil são testadas de verdade.
        service = new RecipeService(
                new ActiveUserProvider(userRepository),
                productRepository,
                ingredientRepository,
                recipeItemRepository);

        lenient().when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(user));
        lenient().when(user.isActive()).thenReturn(true);
        lenient().when(user.getEstablishment()).thenReturn(establishment);
        lenient().when(establishment.isActive()).thenReturn(true);
        lenient().when(establishment.getId()).thenReturn(ESTABLISHMENT_ID);

        loginAs(Role.OWNER);
    }

    // ---------- helpers ----------

    private void loginAs(Role role) {
        lenient().when(user.getRole()).thenReturn(role);
    }

    private Product product(boolean active) {
        Product product = new Product(
                ESTABLISHMENT_ID, "Cappuccino", "Descrição", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);

        if (!active) {
            product.deactivate();
        }

        return product;
    }

    private Ingredient ingredient(
            Long id, String name, IngredientUnit unit, boolean active) {
        Ingredient ingredient = new Ingredient(
                ESTABLISHMENT_ID, name, unit, BigDecimal.ZERO);
        ReflectionTestUtils.setField(ingredient, "id", id);

        if (!active) {
            ingredient.deactivate();
        }

        return ingredient;
    }

    private Ingredient cafe() {
        return ingredient(10L, "Café", IngredientUnit.G, true);
    }

    private Ingredient leite() {
        return ingredient(11L, "Leite", IngredientUnit.ML, true);
    }

    private Ingredient ovo() {
        return ingredient(12L, "Ovo", IngredientUnit.UNIT, true);
    }

    private RecipeLine line(Long ingredientId, String quantity) {
        return new RecipeLine(
                ingredientId,
                quantity == null ? null : new BigDecimal(quantity));
    }

    private void stubProductLock(Product product) {
        when(productRepository.findByIdAndEstablishmentIdForUpdate(
                PRODUCT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product));
    }

    private void stubIngredients(List<Ingredient> ingredients) {
        when(ingredientRepository.findAllByIdsForUpdate(
                eq(ESTABLISHMENT_ID), any()))
                .thenReturn(ingredients);
    }

    private void verifyNothingWasChanged() {
        verify(recipeItemRepository, never()).deleteByProduct(any(), any());
        verify(recipeItemRepository, never()).saveAll(anyList());
        verify(productRepository, never()).saveAndFlush(any());
    }

    // =====================================================
    // get
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisAutorizadosConsultamAReceita(Role role) {
        loginAs(role);
        Product product = product(true);
        List<RecipeItem> items = List.of(
                new RecipeItem(ESTABLISHMENT_ID, PRODUCT_ID, cafe(), new BigDecimal("30")));
        when(productRepository.findByIdAndEstablishmentId(PRODUCT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product));
        when(recipeItemRepository.findByProduct(ESTABLISHMENT_ID, PRODUCT_ID))
                .thenReturn(items);

        RecipeView view = service.get(AUTH_USER, PRODUCT_ID);

        assertThat(view.product()).isSameAs(product);
        assertThat(view.items()).isEqualTo(items);
    }

    @Test
    void produtoInativoTemAReceitaConsultada() {
        Product product = product(false);
        when(productRepository.findByIdAndEstablishmentId(PRODUCT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product));

        RecipeView view = service.get(AUTH_USER, PRODUCT_ID);

        assertThat(view.product()).isSameAs(product);
        assertThat(view.items()).isEmpty();
    }

    @Test
    void consultaNaoBloqueiaOProduto() {
        when(productRepository.findByIdAndEstablishmentId(PRODUCT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product(true)));

        service.get(AUTH_USER, PRODUCT_ID);

        verify(productRepository, never())
                .findByIdAndEstablishmentIdForUpdate(any(), any());
    }

    @Test
    void atendenteNaoPodeConsultarAReceita() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.get(AUTH_USER, PRODUCT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, recipeItemRepository);
    }

    @Test
    void consultarReceitaDeProdutoInexistenteOuDeOutroEstabelecimentoLancaNaoEncontrado() {
        when(productRepository.findByIdAndEstablishmentId(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(AUTH_USER, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado.");

        verifyNoInteractions(recipeItemRepository);
    }

    // =====================================================
    // replace: caminho feliz
    // =====================================================

    @Test
    void substituiAReceitaComOsItensInformados() {
        Product product = product(true);
        stubProductLock(product);
        stubIngredients(List.of(cafe(), leite()));

        service.replace(AUTH_USER, PRODUCT_ID,
                List.of(line(10L, "30"), line(11L, "150.5")));

        verify(recipeItemRepository).deleteByProduct(ESTABLISHMENT_ID, PRODUCT_ID);
        verify(recipeItemRepository).saveAll(itemsCaptor.capture());

        List<RecipeItem> saved = itemsCaptor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(item -> {
            assertThat(item.getEstablishmentId()).isEqualTo(ESTABLISHMENT_ID);
            assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(item.getQuantity().scale()).isEqualTo(3);
        });
        assertThat(saved.get(0).getIngredient().getId()).isEqualTo(10L);
        assertThat(saved.get(0).getQuantity()).isEqualByComparingTo("30");
        assertThat(saved.get(1).getIngredient().getId()).isEqualTo(11L);
        assertThat(saved.get(1).getQuantity()).isEqualByComparingTo("150.5");
    }

    @Test
    void respostaTrazOProdutoEOsItensLidosDepoisDaTroca() {
        Product product = product(true);
        stubProductLock(product);
        stubIngredients(List.of(cafe()));
        List<RecipeItem> stored = List.of(
                new RecipeItem(ESTABLISHMENT_ID, PRODUCT_ID, cafe(), new BigDecimal("30")));
        when(recipeItemRepository.findByProduct(ESTABLISHMENT_ID, PRODUCT_ID))
                .thenReturn(stored);

        RecipeView view = service.replace(AUTH_USER, PRODUCT_ID, List.of(line(10L, "30")));

        assertThat(view.product()).isSameAs(product);
        assertThat(view.items()).isEqualTo(stored);
    }

    @Test
    void managerTambemPodeSubstituirAReceita() {
        loginAs(Role.MANAGER);
        stubProductLock(product(true));
        stubIngredients(List.of(cafe()));

        service.replace(AUTH_USER, PRODUCT_ID, List.of(line(10L, "30")));

        verify(recipeItemRepository).saveAll(anyList());
    }

    @Test
    void ingredienteEmUnitAceitaQuantidadeInteira() {
        stubProductLock(product(true));
        stubIngredients(List.of(ovo()));

        service.replace(AUTH_USER, PRODUCT_ID, List.of(line(12L, "2")));

        verify(recipeItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue().get(0).getQuantity())
                .isEqualByComparingTo("2");
    }

    @Test
    void receitaComCinquentaItensEhAceita() {
        stubProductLock(product(true));
        List<Ingredient> ingredients = IntStream.rangeClosed(1, 50)
                .mapToObj(i -> ingredient((long) i, "Ingrediente " + i,
                        IngredientUnit.G, true))
                .toList();
        stubIngredients(ingredients);
        List<RecipeLine> lines = IntStream.rangeClosed(1, 50)
                .mapToObj(i -> line((long) i, "1"))
                .toList();

        service.replace(AUTH_USER, PRODUCT_ID, lines);

        verify(recipeItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(50);
    }

    @Test
    void receitaVaziaApagaOsItensSemBloquearIngredientes() {
        Product product = product(true);
        stubProductLock(product);

        service.replace(AUTH_USER, PRODUCT_ID, List.of());

        verify(recipeItemRepository).deleteByProduct(ESTABLISHMENT_ID, PRODUCT_ID);
        verify(recipeItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).isEmpty();
        verifyNoInteractions(ingredientRepository);
        assertThat(product.getUpdatedAt()).isNotNull();
    }

    @Test
    void produtoInativoPodeTerAReceitaEditada() {
        Product product = product(false);
        stubProductLock(product);
        stubIngredients(List.of(cafe()));

        service.replace(AUTH_USER, PRODUCT_ID, List.of(line(10L, "30")));

        verify(recipeItemRepository).saveAll(anyList());
        assertThat(product.isActive()).isFalse();
    }

    @Test
    void produtoEhMarcadoComoAlteradoAoSubstituirAReceita() {
        Product product = product(true);
        stubProductLock(product);
        stubIngredients(List.of(cafe()));
        assertThat(product.getUpdatedAt()).isNull();

        service.replace(AUTH_USER, PRODUCT_ID, List.of(line(10L, "30")));

        assertThat(product.getUpdatedAt()).isNotNull();
        verify(productRepository).saveAndFlush(product);
    }

    // =====================================================
    // replace: bloqueios e ordem das chamadas
    // =====================================================

    @Test
    void bloqueiaProdutoDepoisIngredientesApagaSalvaEAtualizaOProduto() {
        Product product = product(true);
        stubProductLock(product);
        stubIngredients(List.of(cafe()));

        service.replace(AUTH_USER, PRODUCT_ID, List.of(line(10L, "30")));

        InOrder order = inOrder(
                productRepository, ingredientRepository, recipeItemRepository);

        order.verify(productRepository)
                .findByIdAndEstablishmentIdForUpdate(PRODUCT_ID, ESTABLISHMENT_ID);
        order.verify(ingredientRepository)
                .findAllByIdsForUpdate(eq(ESTABLISHMENT_ID), any());
        order.verify(recipeItemRepository)
                .deleteByProduct(ESTABLISHMENT_ID, PRODUCT_ID);
        order.verify(recipeItemRepository).saveAll(anyList());
        order.verify(productRepository).saveAndFlush(product);
    }

    @Test
    void ingredientesSaoBloqueadosEmOrdemCrescenteDeId() {
        stubProductLock(product(true));
        stubIngredients(List.of(cafe(), leite(), ovo()));

        service.replace(AUTH_USER, PRODUCT_ID,
                List.of(line(12L, "1"), line(10L, "30"), line(11L, "150")));

        verify(ingredientRepository)
                .findAllByIdsForUpdate(eq(ESTABLISHMENT_ID), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(10L, 11L, 12L);
    }

    @Test
    void itensSalvosMantemAOrdemEnviadaPeloCliente() {
        stubProductLock(product(true));
        stubIngredients(List.of(cafe(), leite()));

        service.replace(AUTH_USER, PRODUCT_ID,
                List.of(line(11L, "150"), line(10L, "30")));

        verify(recipeItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue())
                .extracting(item -> item.getIngredient().getId())
                .containsExactly(11L, 10L);
    }

    // =====================================================
    // replace: validação das linhas (antes de qualquer bloqueio)
    // =====================================================

    @Test
    void listaNulaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.replace(AUTH_USER, PRODUCT_ID, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A lista de itens é obrigatória.");

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void maisDeCinquentaItensLancaRegraDeNegocio() {
        List<RecipeLine> lines = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> line((long) i, "1"))
                .toList();

        assertThatThrownBy(() -> service.replace(AUTH_USER, PRODUCT_ID, lines))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A receita deve ter no máximo 50 itens.");

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void itemNuloLancaRegraDeNegocio() {
        List<RecipeLine> lines = Arrays.asList((RecipeLine) null);

        assertThatThrownBy(() -> service.replace(AUTH_USER, PRODUCT_ID, lines))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Item da receita inválido.");

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void ingredienteNuloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(null, "30"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O ingrediente é obrigatório.");

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void ingredienteComIdZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(0L, "30"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O ingrediente informado é inválido.");

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(-3L, "30"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O ingrediente informado é inválido.");

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void quantidadeNulaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quantidade é obrigatória.");

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void ingredienteRepetidoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "30"), line(10L, "5"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Ingrediente repetido na receita.");

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    // =====================================================
    // replace: regras que dependem do banco
    // =====================================================

    @Test
    void produtoInexistenteOuDeOutroEstabelecimentoLancaNaoEncontrado() {
        when(productRepository.findByIdAndEstablishmentIdForUpdate(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, 99L, List.of(line(10L, "30"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado.");

        verifyNoInteractions(ingredientRepository, recipeItemRepository);
    }

    @Test
    void ingredienteInexistenteOuDeOutroEstabelecimentoLancaRegraDeNegocio() {
        Product product = product(true);
        stubProductLock(product);
        // O repository só devolve os ingredientes do estabelecimento: falta o 11.
        stubIngredients(List.of(cafe()));

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "30"), line(11L, "150"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Ingrediente não encontrado.");

        verifyNothingWasChanged();
        assertThat(product.getUpdatedAt()).isNull();
    }

    @Test
    void ingredienteInativoLancaRegraDeNegocioComONomeDoIngrediente() {
        Product product = product(true);
        stubProductLock(product);
        stubIngredients(List.of(
                cafe(), ingredient(11L, "Leite", IngredientUnit.ML, false)));

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "30"), line(11L, "150"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O ingrediente \"Leite\" está inativo.");

        verifyNothingWasChanged();
        assertThat(product.getUpdatedAt()).isNull();
    }

    // =====================================================
    // replace: quantidade validada pela entidade (sem apagar a receita antiga)
    // =====================================================

    @Test
    void quantidadeDecimalParaIngredienteEmUnitLancaRegraDeNegocio() {
        stubProductLock(product(true));
        stubIngredients(List.of(ovo()));

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(12L, "1.5"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quantidade deve ser um número inteiro "
                        + "para ingredientes em UNIT.");

        verifyNothingWasChanged();
    }

    @Test
    void quantidadeZeroOuNegativaLancaRegraDeNegocio() {
        stubProductLock(product(true));
        stubIngredients(List.of(cafe()));

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "0"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quantidade deve ser maior que zero.");

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "-5"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quantidade deve ser maior que zero.");

        verifyNothingWasChanged();
    }

    @Test
    void quantidadeComMaisDeTresCasasDecimaisLancaRegraDeNegocio() {
        stubProductLock(product(true));
        stubIngredients(List.of(cafe()));

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "1.2345"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quantidade deve ter no máximo 3 casas decimais.");

        verifyNothingWasChanged();
    }

    @Test
    void quantidadeAcimaDoMaximoLancaRegraDeNegocio() {
        stubProductLock(product(true));
        stubIngredients(List.of(cafe()));

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "100000000000"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quantidade excede o valor máximo permitido.");

        verifyNothingWasChanged();
    }

    // =====================================================
    // replace: permissões e sessão
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ATTENDANT", "STOCKIST"})
    void perfisSemPermissaoNaoPodemSubstituirAReceita(Role role) {
        loginAs(role);

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "30"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void permissaoEhVerificadaAntesDaValidacaoDasLinhas() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.replace(AUTH_USER, PRODUCT_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void usuarioInativoNaoPodeSubstituirAReceita() {
        when(user.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "30"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }

    @Test
    void estabelecimentoInativoNaoPodeSubstituirAReceita() {
        when(establishment.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.replace(
                AUTH_USER, PRODUCT_ID, List.of(line(10L, "30"))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, ingredientRepository, recipeItemRepository);
    }
}
