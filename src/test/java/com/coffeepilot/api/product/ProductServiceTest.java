package com.coffeepilot.api.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long ESTABLISHMENT_ID = 1L;
    private static final Long USER_ID = 10L;

    private static final AuthenticatedUser AUTH_USER = new AuthenticatedUser(
            USER_ID, "Usuário", "usuario@teste.com",
            Role.OWNER, ESTABLISHMENT_ID, "Estabelecimento Teste");

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private User user;

    @Mock
    private Establishment establishment;

    private ProductService service;

    @BeforeEach
    void setUp() {
        // ActiveUserProvider real: assim as permissões por perfil são testadas de verdade.
        service = new ProductService(
                new ActiveUserProvider(userRepository),
                productRepository);

        lenient().when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(user));
        lenient().when(user.isActive()).thenReturn(true);
        lenient().when(user.getEstablishment()).thenReturn(establishment);
        lenient().when(establishment.isActive()).thenReturn(true);
        lenient().when(establishment.getId()).thenReturn(ESTABLISHMENT_ID);

        lenient().when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        loginAs(Role.OWNER);
    }

    // ---------- helpers ----------

    private void loginAs(Role role) {
        lenient().when(user.getRole()).thenReturn(role);
    }

    private Product product(String name, boolean active) {
        Product product = new Product(
                ESTABLISHMENT_ID, name, "Descrição", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(product, "id", 5L);

        if (!active) {
            product.deactivate();
        }

        return product;
    }

    private String capturedSearchPattern(Pageable pageable) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), captor.capture(), eq(pageable));

        return captor.getValue();
    }

    private void stubFindByFilters() {
        when(productRepository.findByFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    // =====================================================
    // create
    // =====================================================

    @Test
    void criaProdutoComNomeAparadoEPrecoComDuasCasas() {
        when(productRepository.existsByName(ESTABLISHMENT_ID, "Cappuccino"))
                .thenReturn(false);

        Product created = service.create(
                AUTH_USER, "Cappuccino", "Com chocolate", new BigDecimal("12.5"));

        assertThat(created.getEstablishmentId()).isEqualTo(ESTABLISHMENT_ID);
        assertThat(created.getName()).isEqualTo("Cappuccino");
        assertThat(created.getDescription()).isEqualTo("Com chocolate");
        assertThat(created.getPrice()).isEqualByComparingTo("12.50");
        assertThat(created.getPrice().scale()).isEqualTo(2);
        assertThat(created.isActive()).isTrue();
        verify(productRepository).saveAndFlush(any(Product.class));
    }

    @Test
    void managerTambemPodeCriarProduto() {
        loginAs(Role.MANAGER);

        Product created = service.create(
                AUTH_USER, "Espresso", null, new BigDecimal("8.00"));

        assertThat(created.getName()).isEqualTo("Espresso");
    }

    @Test
    void descricaoEmBrancoNaCriacaoViraNula() {
        Product created = service.create(
                AUTH_USER, "Espresso", "   ", new BigDecimal("8.00"));

        assertThat(created.getDescription()).isNull();
    }

    @Test
    void nomeDuplicadoNaCriacaoLancaConflitoESemSalvar() {
        when(productRepository.existsByName(ESTABLISHMENT_ID, "Cappuccino"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, new BigDecimal("12.50")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Já existe um produto com este nome.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void nomeEmBrancoNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "   ", null, new BigDecimal("12.50")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O nome é obrigatório.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void precoNuloNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O preço é obrigatório.");
    }

    @Test
    void precoZeroOuNegativoNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, new BigDecimal("-1.00")))
                .isInstanceOf(BusinessRuleException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void precoComTresCasasDecimaisNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, new BigDecimal("12.505")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void precoAcimaDoMaximoNaCriacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, new BigDecimal("100000000.00")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void nomeAcimaDe150CaracteresNaCriacaoLancaRegraDeNegocio() {
        String longName = "a".repeat(151);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, longName, null, new BigDecimal("12.50")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ATTENDANT", "STOCKIST"})
    void perfisSemPermissaoNaoPodemCriar(Role role) {
        loginAs(role);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, new BigDecimal("12.50")))
                .isInstanceOf(AccessDeniedException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void usuarioInativoNaoPodeCriar() {
        when(user.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, new BigDecimal("12.50")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void estabelecimentoInativoNaoPodeCriar() {
        when(establishment.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "Cappuccino", null, new BigDecimal("12.50")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =====================================================
    // list
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisDeGestaoEEstoquistaRespeitamOFiltroDeStatus(Role role) {
        loginAs(role);
        stubFindByFilters();

        service.list(AUTH_USER, false, null, PageRequest.of(0, 10));

        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq(false), any(), any(Pageable.class));
    }

    @Test
    void semFiltroDeStatusOwnerVeTodosOsProdutos() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null, PageRequest.of(0, 10));

        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq((Boolean) null), any(), any(Pageable.class));
    }

    @Test
    void atendenteSempreRecebeApenasProdutosAtivos() {
        loginAs(Role.ATTENDANT);
        stubFindByFilters();

        service.list(AUTH_USER, false, null, PageRequest.of(0, 10));

        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq(true), any(), any(Pageable.class));
    }

    @Test
    void atendenteSemFiltroTambemRecebeApenasAtivos() {
        loginAs(Role.ATTENDANT);
        stubFindByFilters();

        service.list(AUTH_USER, null, null, PageRequest.of(0, 10));

        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), eq(true), any(), any(Pageable.class));
    }

    @Test
    void listagemSemBuscaUsaPadraoCoringa() {
        stubFindByFilters();
        Pageable pageable = PageRequest.of(0, 10);

        service.list(AUTH_USER, null, null, pageable);

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);

        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), searchCaptor.capture(), pageCaptor.capture());

        assertThat(searchCaptor.getValue()).isEqualTo("%");
    }

    @Test
    void buscaEmBrancoUsaPadraoCoringa() {
        stubFindByFilters();

        service.list(AUTH_USER, null, "   ", PageRequest.of(0, 10));

        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), searchCaptor.capture(), any(Pageable.class));

        assertThat(searchCaptor.getValue()).isEqualTo("%");
    }

    @Test
    void buscaViraMinusculaAparadaEEnvolvidaPorPercentual() {
        stubFindByFilters();

        service.list(AUTH_USER, null, "  CaPPu  ", PageRequest.of(0, 10));

        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), searchCaptor.capture(), any(Pageable.class));

        assertThat(searchCaptor.getValue()).isEqualTo("%cappu%");
    }

    @Test
    void buscaEscapaCoringasComoTextoLiteral() {
        stubFindByFilters();

        service.list(AUTH_USER, null, "50%_off!", PageRequest.of(0, 10));

        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), searchCaptor.capture(), any(Pageable.class));

        assertThat(searchCaptor.getValue()).isEqualTo("%50!%!_off!!%");
    }

    @Test
    void buscaAcimaDe150CaracteresLancaRegraDeNegocio() {
        String longSearch = "a".repeat(151);

        assertThatThrownBy(() -> service.list(
                AUTH_USER, null, longSearch, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A busca deve ter no máximo 150 caracteres.");
    }

    @Test
    void tamanhoDePaginaAcimaDe100EhLimitadoA100ComOrdenacaoPorNomeEId() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null, PageRequest.of(2, 500));

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), any(), pageCaptor.capture());

        Pageable used = pageCaptor.getValue();

        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(100);
        assertThat(used.getSort().stream().map(order -> order.getProperty()))
                .containsExactly("name", "id");
    }

    @Test
    void ordenacaoEnviadaPeloClienteEhIgnorada() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null,
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("price")));

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), any(), pageCaptor.capture());

        assertThat(pageCaptor.getValue().getSort().stream()
                .map(order -> order.getProperty()))
                .containsExactly("name", "id");
    }

    @Test
    void semPaginacaoUsaTamanhoPadraoDe20() {
        stubFindByFilters();

        service.list(AUTH_USER, null, null, Pageable.unpaged());

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findByFilters(
                eq(ESTABLISHMENT_ID), any(), any(), pageCaptor.capture());

        assertThat(pageCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void listagemRetornaOResultadoDoRepository() {
        Product product = product("Cappuccino", true);
        when(productRepository.findByFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        Page<Product> page = service.list(
                AUTH_USER, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly(product);
    }

    // =====================================================
    // get
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST", "ATTENDANT"})
    void todosOsPerfisConsultamProdutoAtivo(Role role) {
        loginAs(role);
        Product product = product("Cappuccino", true);
        when(productRepository.findByIdAndEstablishmentId(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product));

        assertThat(service.get(AUTH_USER, 5L)).isSameAs(product);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisSemRestricaoConsultamProdutoInativo(Role role) {
        loginAs(role);
        Product product = product("Cappuccino", false);
        when(productRepository.findByIdAndEstablishmentId(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product));

        assertThat(service.get(AUTH_USER, 5L)).isSameAs(product);
    }

    @Test
    void atendenteRecebeNaoEncontradoParaProdutoInativo() {
        loginAs(Role.ATTENDANT);
        when(productRepository.findByIdAndEstablishmentId(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product("Cappuccino", false)));

        assertThatThrownBy(() -> service.get(AUTH_USER, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado.");
    }

    @Test
    void produtoInexistenteOuDeOutroEstabelecimentoLancaNaoEncontrado() {
        when(productRepository.findByIdAndEstablishmentId(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(AUTH_USER, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado.");
    }

    // =====================================================
    // update
    // =====================================================

    private void stubLock(Product product) {
        when(productRepository.findByIdAndEstablishmentIdForUpdate(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(product));
    }

    @Test
    void atualizaNomeDescricaoEPreco() {
        Product product = product("Cappuccino", true);
        stubLock(product);
        when(productRepository.existsByNameExcludingId(ESTABLISHMENT_ID, 5L, "Cappuccino Grande"))
                .thenReturn(false);

        Product updated = service.update(
                AUTH_USER, 5L, "Cappuccino Grande", "Maior", new BigDecimal("15.9"));

        assertThat(updated.getName()).isEqualTo("Cappuccino Grande");
        assertThat(updated.getDescription()).isEqualTo("Maior");
        assertThat(updated.getPrice()).isEqualByComparingTo("15.90");
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void atualizaSomenteOPrecoSemAlterarOsDemaisCampos() {
        Product product = product("Cappuccino", true);
        stubLock(product);

        Product updated = service.update(
                AUTH_USER, 5L, null, null, new BigDecimal("20.00"));

        assertThat(updated.getName()).isEqualTo("Cappuccino");
        assertThat(updated.getDescription()).isEqualTo("Descrição");
        assertThat(updated.getPrice()).isEqualByComparingTo("20.00");
        verify(productRepository, never()).existsByNameExcludingId(any(), any(), any());
    }

    @Test
    void descricaoVaziaNaAtualizacaoRemoveADescricao() {
        Product product = product("Cappuccino", true);
        stubLock(product);

        Product updated = service.update(AUTH_USER, 5L, null, "", null);

        assertThat(updated.getDescription()).isNull();
    }

    @Test
    void nenhumCampoNaAtualizacaoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.update(AUTH_USER, 5L, null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Informe ao menos um campo para atualizar.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void nomeEmBrancoNaAtualizacaoLancaRegraDeNegocio() {
        stubLock(product("Cappuccino", true));

        assertThatThrownBy(() -> service.update(AUTH_USER, 5L, "   ", null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O nome não pode ficar em branco.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void renomearParaNomeExistenteLancaConflito() {
        stubLock(product("Cappuccino", true));
        when(productRepository.existsByNameExcludingId(ESTABLISHMENT_ID, 5L, "Espresso"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(AUTH_USER, 5L, "Espresso", null, null))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Já existe um produto com este nome.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void precoInvalidoNaAtualizacaoLancaRegraDeNegocio() {
        stubLock(product("Cappuccino", true));

        assertThatThrownBy(() -> service.update(
                AUTH_USER, 5L, null, null, new BigDecimal("-5.00")))
                .isInstanceOf(BusinessRuleException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void atualizarProdutoInexistenteLancaNaoEncontrado() {
        when(productRepository.findByIdAndEstablishmentIdForUpdate(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                AUTH_USER, 99L, "Novo", null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto não encontrado.");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ATTENDANT", "STOCKIST"})
    void perfisSemPermissaoNaoPodemAtualizar(Role role) {
        loginAs(role);

        assertThatThrownBy(() -> service.update(
                AUTH_USER, 5L, "Novo", null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(productRepository, never()).findByIdAndEstablishmentIdForUpdate(any(), any());
    }

    // =====================================================
    // setActive
    // =====================================================

    @Test
    void desativaProduto() {
        Product product = product("Cappuccino", true);
        stubLock(product);

        Product result = service.setActive(AUTH_USER, 5L, false);

        assertThat(result.isActive()).isFalse();
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void ativaProduto() {
        Product product = product("Cappuccino", false);
        stubLock(product);

        Product result = service.setActive(AUTH_USER, 5L, true);

        assertThat(result.isActive()).isTrue();
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void alterarStatusDeProdutoInexistenteLancaNaoEncontrado() {
        when(productRepository.findByIdAndEstablishmentIdForUpdate(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(AUTH_USER, 99L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ATTENDANT", "STOCKIST"})
    void perfisSemPermissaoNaoPodemAlterarStatus(Role role) {
        loginAs(role);

        assertThatThrownBy(() -> service.setActive(AUTH_USER, 5L, false))
                .isInstanceOf(AccessDeniedException.class);

        verify(productRepository, never()).findByIdAndEstablishmentIdForUpdate(any(), any());
    }
}
