package com.coffeepilot.api.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.IngredientInUseException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.recipe.IngredientProductView;
import com.coffeepilot.api.recipe.RecipeItemRepository;
import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.stock.StockService;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    private static final Long ESTABLISHMENT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long INGREDIENT_ID = 5L;

    private static final AuthenticatedUser AUTH_USER = new AuthenticatedUser(
            USER_ID, "Usuário", "usuario@teste.com",
            Role.OWNER, ESTABLISHMENT_ID, "Estabelecimento Teste");

    @Mock
    private UserRepository userRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private StockService stockService;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Mock
    private User user;

    @Mock
    private Establishment establishment;

    private IngredientService service;

    @BeforeEach
    void setUp() {
        // ActiveUserProvider real: as permissões por perfil são testadas de verdade.
        service = new IngredientService(
                new ActiveUserProvider(userRepository),
                ingredientRepository,
                stockService,
                recipeItemRepository);

        lenient().when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(user));
        lenient().when(user.isActive()).thenReturn(true);
        lenient().when(user.getEstablishment()).thenReturn(establishment);
        lenient().when(establishment.isActive()).thenReturn(true);
        lenient().when(establishment.getId()).thenReturn(ESTABLISHMENT_ID);

        lenient().when(ingredientRepository.saveAndFlush(any(Ingredient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        loginAs(Role.OWNER);
    }

    // ---------- helpers ----------

    private void loginAs(Role role) {
        lenient().when(user.getRole()).thenReturn(role);
    }

    private Ingredient ingredient(String name, IngredientUnit unit, boolean active) {
        Ingredient ingredient = new Ingredient(
                ESTABLISHMENT_ID, name, unit, BigDecimal.ZERO);
        ReflectionTestUtils.setField(ingredient, "id", INGREDIENT_ID);

        if (!active) {
            ingredient.deactivate();
        }

        return ingredient;
    }

    private void stubLock(Ingredient ingredient) {
        when(ingredientRepository.findByIdAndEstablishmentIdForUpdate(
                INGREDIENT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(ingredient));
    }

    private void stubFindByFilters() {
        when(ingredientRepository.findByFilters(
                any(), any(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        verify(ingredientRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), anyBoolean(), captor.capture());

        return captor.getValue();
    }

    // =====================================================
    // create
    // =====================================================

    @Test
    void criaIngredienteComSaldoInicialERegistraAMovimentacaoInicial() {
        Ingredient created = service.create(
                AUTH_USER, "Café", IngredientUnit.G,
                new BigDecimal("200"), new BigDecimal("1000"));

        assertThat(created.getEstablishmentId()).isEqualTo(ESTABLISHMENT_ID);
        assertThat(created.getName()).isEqualTo("Café");
        assertThat(created.getUnit()).isEqualTo(IngredientUnit.G);
        assertThat(created.getMinimumQuantity()).isEqualByComparingTo("200");
        assertThat(created.isActive()).isTrue();
        // O saldo só muda por movimentação: o ingrediente nasce zerado.
        assertThat(created.getCurrentQuantity()).isEqualByComparingTo("0");

        verify(stockService).registerInitial(
                user, created, new BigDecimal("1000"));
    }

    @Test
    void gravaOIngredienteAntesDeRegistrarOSaldoInicial() {
        service.create(AUTH_USER, "Café", IngredientUnit.G,
                BigDecimal.ZERO, new BigDecimal("10"));

        InOrder order = inOrder(ingredientRepository, stockService);

        order.verify(ingredientRepository).saveAndFlush(any(Ingredient.class));
        order.verify(stockService)
                .registerInitial(eq(user), any(Ingredient.class), any());
    }

    @Test
    void semSaldoInicialNaoRegistraMovimentacao() {
        Ingredient created = service.create(
                AUTH_USER, "Café", IngredientUnit.G, BigDecimal.ZERO, null);

        assertThat(created.getCurrentQuantity()).isEqualByComparingTo("0");
        verify(stockService, never()).registerInitial(any(), any(), any());
    }

    @Test
    void saldoInicialZeroNaoRegistraMovimentacao() {
        service.create(AUTH_USER, "Café", IngredientUnit.G,
                BigDecimal.ZERO, BigDecimal.ZERO);

        verify(stockService, never()).registerInitial(any(), any(), any());
    }

    @Test
    void estoqueMinimoNuloViraZero() {
        Ingredient created = service.create(
                AUTH_USER, "Café", IngredientUnit.G, null, null);

        assertThat(created.getMinimumQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void nomeEAparadoNaCriacao() {
        Ingredient created = service.create(
                AUTH_USER, "  Farinha  ", IngredientUnit.G, null, null);

        assertThat(created.getName()).isEqualTo("Farinha");
    }

    @Test
    void ingredienteEmUnitAceitaInteiros() {
        Ingredient created = service.create(
                AUTH_USER, "Ovo", IngredientUnit.UNIT,
                new BigDecimal("12"), new BigDecimal("30"));

        assertThat(created.getUnit()).isEqualTo(IngredientUnit.UNIT);
        assertThat(created.getMinimumQuantity()).isEqualByComparingTo("12");
    }

    @Test
    void nomeEmBrancoNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "   ", IngredientUnit.G, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O nome é obrigatório.");

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void nomeNuloNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, IngredientUnit.G, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O nome é obrigatório.");
    }

    @Test
    void unidadeNulaNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A unidade é obrigatória.");

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void saldoInicialNegativoLancaRegraDeNegocioSemGravar() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", IngredientUnit.G, null, new BigDecimal("-1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O saldo inicial não pode ser negativo.");

        verify(ingredientRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stockService);
    }

    @Test
    void nomeDuplicadoNaCriacaoLancaConflitoSemGravarNemMovimentar() {
        when(ingredientRepository.existsByName(ESTABLISHMENT_ID, "Café"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", IngredientUnit.G, null, new BigDecimal("10")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Já existe um ingrediente com este nome.");

        verify(ingredientRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stockService);
    }

    @Test
    void estoqueMinimoNegativoNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", IngredientUnit.G, new BigDecimal("-1"), null))
                .isInstanceOf(BusinessRuleException.class);

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void estoqueMinimoFracionadoEmUnitLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Ovo", IngredientUnit.UNIT, new BigDecimal("1.5"), null))
                .isInstanceOf(BusinessRuleException.class);

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void estoqueMinimoComMaisDeTresCasasLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", IngredientUnit.G, new BigDecimal("1.2345"), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void nomeAcimaDe150CaracteresNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "a".repeat(151), IngredientUnit.G, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisAutorizadosPodemCriar(Role role) {
        loginAs(role);

        Ingredient created = service.create(
                AUTH_USER, "Café", IngredientUnit.G, null, null);

        assertThat(created.getName()).isEqualTo("Café");
    }

    @Test
    void atendenteNaoPodeCriar() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", IngredientUnit.G, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository, stockService);
    }

    @Test
    void usuarioInativoNaoPodeCriar() {
        when(user.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", IngredientUnit.G, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository, stockService);
    }

    @Test
    void estabelecimentoInativoNaoPodeCriar() {
        when(establishment.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Café", IngredientUnit.G, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository, stockService);
    }

    // =====================================================
    // list
    // =====================================================

    @Test
    void listagemSemFiltrosUsaEstabelecimentoDoUsuario() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null, PageRequest.of(0, 10));

        verify(ingredientRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq((Boolean) null), eq(false), any(Pageable.class));
    }

    @Test
    void filtroDeStatusEPassadoAoRepository() {
        stubFindByFilters();

        service.list(AUTH_USER, false, null, PageRequest.of(0, 10));

        verify(ingredientRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq(false), eq(false), any(Pageable.class));
    }

    @Test
    void estoqueBaixoVerdadeiroAtivaOFiltro() {
        stubFindByFilters();

        service.list(AUTH_USER, null, true, PageRequest.of(0, 10));

        verify(ingredientRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq((Boolean) null), eq(true), any(Pageable.class));
    }

    @Test
    void estoqueBaixoComAtivoVerdadeiroEhPermitido() {
        stubFindByFilters();

        service.list(AUTH_USER, true, true, PageRequest.of(0, 10));

        verify(ingredientRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq(true), eq(true), any(Pageable.class));
    }

    @Test
    void estoqueBaixoFalsoNaoAtivaOFiltroNemRestringeInativos() {
        stubFindByFilters();

        service.list(AUTH_USER, false, false, PageRequest.of(0, 10));

        verify(ingredientRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq(false), eq(false), any(Pageable.class));
    }

    @Test
    void estoqueBaixoComInativosLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.list(
                AUTH_USER, false, true, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Estoque baixo considera apenas ingredientes ativos.");

        verify(ingredientRepository, never())
                .findByFilters(any(), any(), anyBoolean(), any());
    }

    @Test
    void tamanhoDePaginaAcimaDe100EhLimitadoA100ComOrdenacaoPorNomeEId() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null, PageRequest.of(2, 500));

        Pageable used = capturedPageable();

        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(100);
        assertThat(used.getSort().stream().map(Sort.Order::getProperty))
                .containsExactly("name", "id");
    }

    @Test
    void ordenacaoEnviadaPeloClienteEhIgnorada() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null,
                PageRequest.of(0, 10, Sort.by("currentQuantity")));

        assertThat(capturedPageable().getSort().stream().map(Sort.Order::getProperty))
                .containsExactly("name", "id");
    }

    @Test
    void semPaginacaoUsaTamanhoPadraoDe20() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null, Pageable.unpaged());

        Pageable used = capturedPageable();

        assertThat(used.getPageNumber()).isZero();
        assertThat(used.getPageSize()).isEqualTo(20);
    }

    @Test
    void paginacaoNulaUsaTamanhoPadraoDe20() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null, null);

        assertThat(capturedPageable().getPageSize()).isEqualTo(20);
    }

    @Test
    void numeroDePaginaNegativoLancaRegraDeNegocio() {
        Pageable pageable = mock(Pageable.class);
        when(pageable.getPageNumber()).thenReturn(-1);

        assertThatThrownBy(() -> service.list(AUTH_USER, null, null, pageable))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O número da página não pode ser negativo.");
    }

    @Test
    void tamanhoDePaginaZeroLancaRegraDeNegocio() {
        Pageable pageable = mock(Pageable.class);
        when(pageable.getPageNumber()).thenReturn(0);
        when(pageable.getPageSize()).thenReturn(0);

        assertThatThrownBy(() -> service.list(AUTH_USER, null, null, pageable))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O tamanho da página deve ser maior que zero.");
    }

    @Test
    void listagemRetornaOResultadoDoRepository() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        when(ingredientRepository.findByFilters(any(), any(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(ingredient)));

        Page<Ingredient> page = service.list(
                AUTH_USER, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly(ingredient);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisAutorizadosPodemListar(Role role) {
        loginAs(role);
        stubFindByFilters();

        service.list(AUTH_USER, null, null, PageRequest.of(0, 10));

        verify(ingredientRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), anyBoolean(), any(Pageable.class));
    }

    @Test
    void atendenteNaoPodeListar() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.list(
                AUTH_USER, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository);
    }

    // =====================================================
    // get
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisAutorizadosConsultamIngredienteInclusiveInativo(Role role) {
        loginAs(role);
        Ingredient ingredient = ingredient("Açúcar", IngredientUnit.G, false);
        when(ingredientRepository.findByIdAndEstablishmentId(
                INGREDIENT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(ingredient));

        assertThat(service.get(AUTH_USER, INGREDIENT_ID)).isSameAs(ingredient);
    }

    @Test
    void ingredienteInexistenteOuDeOutroEstabelecimentoLancaNaoEncontrado() {
        when(ingredientRepository.findByIdAndEstablishmentId(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(AUTH_USER, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Ingrediente não encontrado.");
    }

    @Test
    void atendenteNaoPodeConsultar() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.get(AUTH_USER, INGREDIENT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository);
    }

    // =====================================================
    // update
    // =====================================================

    @Test
    void atualizaNomeEEstoqueMinimo() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        stubLock(ingredient);

        Ingredient updated = service.update(
                AUTH_USER, INGREDIENT_ID, "Café Torrado", new BigDecimal("300"));

        assertThat(updated.getName()).isEqualTo("Café Torrado");
        assertThat(updated.getMinimumQuantity()).isEqualByComparingTo("300");
        verify(ingredientRepository).saveAndFlush(ingredient);
    }

    @Test
    void atualizaSomenteOEstoqueMinimoSemConsultarNomeDuplicado() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        stubLock(ingredient);

        Ingredient updated = service.update(
                AUTH_USER, INGREDIENT_ID, null, new BigDecimal("50"));

        assertThat(updated.getName()).isEqualTo("Café");
        assertThat(updated.getMinimumQuantity()).isEqualByComparingTo("50");
        verify(ingredientRepository, never())
                .existsByNameExcludingId(any(), any(), any());
    }

    @Test
    void atualizaSomenteONomeSemAlterarOEstoqueMinimo() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        ingredient.changeMinimumQuantity(new BigDecimal("200"));
        stubLock(ingredient);

        Ingredient updated = service.update(
                AUTH_USER, INGREDIENT_ID, "Café Moído", null);

        assertThat(updated.getName()).isEqualTo("Café Moído");
        assertThat(updated.getMinimumQuantity()).isEqualByComparingTo("200");
    }

    @Test
    void verificaDuplicidadeIgnorandoOProprioIngrediente() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        stubLock(ingredient);

        service.update(AUTH_USER, INGREDIENT_ID, "CAFÉ", null);

        verify(ingredientRepository).existsByNameExcludingId(
                ESTABLISHMENT_ID, INGREDIENT_ID, "CAFÉ");
    }

    @Test
    void nenhumCampoNaAtualizacaoLancaRegraDeNegocioSemBloquear() {
        assertThatThrownBy(() -> service.update(
                AUTH_USER, INGREDIENT_ID, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Informe ao menos um campo para atualizar.");

        verify(ingredientRepository, never())
                .findByIdAndEstablishmentIdForUpdate(any(), any());
        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void nomeEmBrancoNaAtualizacaoLancaRegraDeNegocio() {
        stubLock(ingredient("Café", IngredientUnit.G, true));

        assertThatThrownBy(() -> service.update(
                AUTH_USER, INGREDIENT_ID, "   ", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O nome não pode ficar em branco.");

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void renomearParaNomeExistenteLancaConflito() {
        stubLock(ingredient("Café", IngredientUnit.G, true));
        when(ingredientRepository.existsByNameExcludingId(
                ESTABLISHMENT_ID, INGREDIENT_ID, "Leite"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(
                AUTH_USER, INGREDIENT_ID, "Leite", null))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Já existe um ingrediente com este nome.");

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void estoqueMinimoNegativoNaAtualizacaoLancaRegraDeNegocio() {
        stubLock(ingredient("Café", IngredientUnit.G, true));

        assertThatThrownBy(() -> service.update(
                AUTH_USER, INGREDIENT_ID, null, new BigDecimal("-1")))
                .isInstanceOf(BusinessRuleException.class);

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void estoqueMinimoFracionadoEmUnitNaAtualizacaoLancaRegraDeNegocio() {
        stubLock(ingredient("Ovo", IngredientUnit.UNIT, true));

        assertThatThrownBy(() -> service.update(
                AUTH_USER, INGREDIENT_ID, null, new BigDecimal("2.5")))
                .isInstanceOf(BusinessRuleException.class);

        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void atualizarIngredienteInexistenteLancaNaoEncontrado() {
        when(ingredientRepository.findByIdAndEstablishmentIdForUpdate(
                99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                AUTH_USER, 99L, "Novo", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Ingrediente não encontrado.");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisAutorizadosPodemAtualizar(Role role) {
        loginAs(role);
        stubLock(ingredient("Café", IngredientUnit.G, true));

        Ingredient updated = service.update(
                AUTH_USER, INGREDIENT_ID, null, new BigDecimal("10"));

        assertThat(updated.getMinimumQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void atendenteNaoPodeAtualizar() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.update(
                AUTH_USER, INGREDIENT_ID, "Novo", null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository);
    }

    // =====================================================
    // setActive
    // =====================================================

    @Test
    void desativaIngrediente() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        stubLock(ingredient);
        when(recipeItemRepository.findActiveProductsByIngredient(
                ESTABLISHMENT_ID, INGREDIENT_ID)).thenReturn(List.of());

        Ingredient result = service.setActive(AUTH_USER, INGREDIENT_ID, false);

        assertThat(result.isActive()).isFalse();
        verify(ingredientRepository).saveAndFlush(ingredient);
    }

    @Test
    void desativarIngredienteEmReceitaDeProdutoAtivoLancaConflitoComProdutos() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        stubLock(ingredient);
        when(recipeItemRepository.findActiveProductsByIngredient(
                ESTABLISHMENT_ID, INGREDIENT_ID))
                .thenReturn(List.of(
                        new IngredientProductView(3L, "Cappuccino", true),
                        new IngredientProductView(7L, "Espresso", true)));

        assertThatThrownBy(() -> service.setActive(AUTH_USER, INGREDIENT_ID, false))
                .isInstanceOfSatisfying(IngredientInUseException.class, ex ->
                        assertThat(ex.getProducts())
                                .extracting(
                                        IngredientInUseException.AffectedProduct::id,
                                        IngredientInUseException.AffectedProduct::name)
                                .containsExactly(
                                        tuple(3L, "Cappuccino"),
                                        tuple(7L, "Espresso")));

        assertThat(ingredient.isActive()).isTrue();
        verify(ingredientRepository, never()).saveAndFlush(any());
    }

    @Test
    void desativarIngredienteSemProdutosAtivosEPermitido() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, true);
        stubLock(ingredient);
        when(recipeItemRepository.findActiveProductsByIngredient(
                ESTABLISHMENT_ID, INGREDIENT_ID)).thenReturn(List.of());

        assertThat(service.setActive(AUTH_USER, INGREDIENT_ID, false).isActive())
                .isFalse();
    }

    @Test
    void reativarIngredienteNaoConsultaReceitas() {
        stubLock(ingredient("Café", IngredientUnit.G, false));

        service.setActive(AUTH_USER, INGREDIENT_ID, true);

        verifyNoInteractions(recipeItemRepository);
    }

    @Test
    void desativarIngredienteJaInativoNaoConsultaReceitas() {
        stubLock(ingredient("Café", IngredientUnit.G, false));

        assertThat(service.setActive(AUTH_USER, INGREDIENT_ID, false).isActive())
                .isFalse();

        verifyNoInteractions(recipeItemRepository);
    }

    @Test
    void listaProdutosDoIngredienteNoEstabelecimentoDoUsuario() {
        when(ingredientRepository.findByIdAndEstablishmentId(
                INGREDIENT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(ingredient("Café", IngredientUnit.G, true)));
        List<IngredientProductView> products = List.of(
                new IngredientProductView(3L, "Cappuccino", true),
                new IngredientProductView(9L, "Mocha", false));
        when(recipeItemRepository.findProductsByIngredient(
                ESTABLISHMENT_ID, INGREDIENT_ID)).thenReturn(products);

        assertThat(service.listProducts(AUTH_USER, INGREDIENT_ID))
                .isEqualTo(products);
    }

    @Test
    void listarProdutosDeIngredienteInexistenteLancaNaoEncontrado() {
        when(ingredientRepository.findByIdAndEstablishmentId(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listProducts(AUTH_USER, 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(recipeItemRepository);
    }

    @Test
    void atendenteNaoPodeListarProdutosDoIngrediente() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.listProducts(AUTH_USER, INGREDIENT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository, recipeItemRepository);
    }

    @Test
    void ativaIngrediente() {
        Ingredient ingredient = ingredient("Café", IngredientUnit.G, false);
        stubLock(ingredient);

        Ingredient result = service.setActive(AUTH_USER, INGREDIENT_ID, true);

        assertThat(result.isActive()).isTrue();
        verify(ingredientRepository).saveAndFlush(ingredient);
    }

    @Test
    void alterarStatusNaoMexeNoEstoque() {
        stubLock(ingredient("Café", IngredientUnit.G, true));

        service.setActive(AUTH_USER, INGREDIENT_ID, false);

        verifyNoInteractions(stockService);
    }

    @Test
    void alterarStatusDeIngredienteInexistenteLancaNaoEncontrado() {
        when(ingredientRepository.findByIdAndEstablishmentIdForUpdate(
                99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(AUTH_USER, 99L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Ingrediente não encontrado.");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisAutorizadosPodemAlterarStatus(Role role) {
        loginAs(role);
        stubLock(ingredient("Café", IngredientUnit.G, true));

        assertThat(service.setActive(AUTH_USER, INGREDIENT_ID, false).isActive())
                .isFalse();
    }

    @Test
    void atendenteNaoPodeAlterarStatus() {
        loginAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.setActive(AUTH_USER, INGREDIENT_ID, false))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(ingredientRepository);
    }
}