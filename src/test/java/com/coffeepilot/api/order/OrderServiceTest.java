package com.coffeepilot.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.InsufficientStockException;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.ingredient.IngredientRepository;
import com.coffeepilot.api.order.OrderService.OrderLine;
import com.coffeepilot.api.product.Product;
import com.coffeepilot.api.product.ProductRepository;
import com.coffeepilot.api.recipe.RecipeItemRepository;
import com.coffeepilot.api.recipe.RecipeLineView;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.stock.StockService;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long ESTABLISHMENT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 77L;

    private static final AuthenticatedUser AUTH_USER = new AuthenticatedUser(
            USER_ID, "Usuário", "usuario@teste.com",
            Role.OWNER, ESTABLISHMENT_ID, "Estabelecimento");

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderSequenceRepository orderSequenceRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RecipeItemRepository recipeItemRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private StockService stockService;

    @Mock
    private User user;

    @Mock
    private Establishment establishment;

    @Captor
    private ArgumentCaptor<Collection<Long>> productIdsCaptor;

    @Captor
    private ArgumentCaptor<Collection<Long>> ingredientIdsCaptor;

    @Captor
    private ArgumentCaptor<Map<Long, BigDecimal>> requiredCaptor;

    private OrderService service;

    @BeforeEach
    void setUp() {
        // ActiveUserProvider real: assim as permissões por perfil são testadas de verdade.
        service = new OrderService(
                new ActiveUserProvider(userRepository),
                orderRepository,
                orderSequenceRepository,
                productRepository,
                recipeItemRepository,
                ingredientRepository,
                stockService);

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

    private Product product(Long id, String name, String price, boolean active) {
        Product product = new Product(
                ESTABLISHMENT_ID, name, "Descrição", new BigDecimal(price));
        ReflectionTestUtils.setField(product, "id", id);

        if (!active) {
            product.deactivate();
        }

        return product;
    }

    private RecipeLineView recipeLine(
            Long productId, Long ingredientId, String quantity) {
        return new RecipeLineView(
                productId, ingredientId, new BigDecimal(quantity));
    }

    private OrderLine line(Long productId, Integer quantity) {
        return new OrderLine(productId, quantity);
    }

    private void stubProducts(Product... products) {
        when(productRepository.findAllByIdsForShare(eq(ESTABLISHMENT_ID), any()))
                .thenReturn(List.of(products));
    }

    private void stubRecipes(RecipeLineView... lines) {
        when(recipeItemRepository.findLinesByProducts(eq(ESTABLISHMENT_ID), any()))
                .thenReturn(List.of(lines));
    }

    private OrderSequence stubSequence(long lastNumber) {
        OrderSequence sequence = new OrderSequence();
        ReflectionTestUtils.setField(sequence, "lastNumber", lastNumber);
        when(orderSequenceRepository.findForUpdate(ESTABLISHMENT_ID))
                .thenReturn(Optional.of(sequence));
        return sequence;
    }

    // Simula o banco: a gravação atribui o id ao pedido.
    private void stubSave() {
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    ReflectionTestUtils.setField(order, "id", ORDER_ID);
                    return order;
                });
    }

    // Cenário padrão de criação: dois produtos, um com receita.
    private void stubTypicalCreation() {
        stubProducts(
                product(3L, "Cappuccino", "12.50", true),
                product(7L, "Brownie", "9.00", true));
        stubRecipes(recipeLine(3L, 2L, "30"));
        stubSequence(11L);
        stubSave();
    }

    // Cenário de criação com apenas o produto 3, que possui receita.
    private void stubSingleProductCreation() {
        stubProducts(product(3L, "Cappuccino", "12.50", true));
        stubRecipes(recipeLine(3L, 2L, "30"));
        stubSequence(11L);
        stubSave();
    }

    private Order orderIn(OrderStatus status) {
        Order order = new Order(ESTABLISHMENT_ID, 12L, user, null, null);
        order.addItem(3L, "Cappuccino", new BigDecimal("12.50"), null, 2, true);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);

        switch (status) {
            case PENDING -> {
            }
            case IN_PREPARATION -> order.startPreparation();
            case COMPLETED -> {
                order.startPreparation();
                order.complete();
            }
            case CANCELLED -> order.cancel(user, "Cliente desistiu");
        }

        return order;
    }

    private void stubLock(Order order) {
        when(orderRepository.findByIdAndEstablishmentIdForUpdate(
                ORDER_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(order));
        lenient().when(orderRepository.findDetailByIdAndEstablishmentId(
                        ORDER_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(order));
    }

    private void verifyNoStockEffect() {
        verify(stockService, never()).consumeForOrder(any(), any(), any(), any());
        verify(stockService, never()).reverseOrderConsumption(any(), any(), any());
    }

    // =====================================================
    // create: caminho feliz
    // =====================================================

    @Test
    void registraPedidoComNomePrecoETotalDoMomentoDaVenda() {
        stubTypicalCreation();

        Order order = service.create(
                AUTH_USER, "  Mesa 5  ", "Sem açúcar",
                List.of(line(7L, 2), line(3L, 1)));

        assertThat(order.getId()).isEqualTo(ORDER_ID);
        assertThat(order.getEstablishmentId()).isEqualTo(ESTABLISHMENT_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCreatedBy()).isSameAs(user);
        assertThat(order.getTableReference()).isEqualTo("Mesa 5");
        assertThat(order.getNotes()).isEqualTo("Sem açúcar");
        // 1 x 12.50 + 2 x 9.00
        assertThat(order.getTotal()).isEqualByComparingTo("30.50");

        // Os itens seguem a ordem crescente de id do produto (3 e 7),
        // independentemente da ordem enviada e do nome.
        assertThat(order.getItems())
                .extracting(OrderItem::getProductName)
                .containsExactly("Cappuccino", "Brownie");
        assertThat(order.getItems().get(0).getUnitPrice())
                .isEqualByComparingTo("12.50");
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(1);
        assertThat(order.getItems().get(0).getSubtotal())
                .isEqualByComparingTo("12.50");
        assertThat(order.getItems().get(0).getUnitCost()).isNull();
        assertThat(order.getItems().get(1).getUnitPrice())
                .isEqualByComparingTo("9.00");
        assertThat(order.getItems().get(1).getQuantity()).isEqualTo(2);
        assertThat(order.getItems().get(1).getSubtotal())
                .isEqualByComparingTo("18.00");
    }

    @Test
    void itensSaemNaOrdemCrescenteDeIdDoProdutoMesmoComProdutosEmOutraOrdem() {
        // O repository devolve os produtos fora de ordem: o service ordena.
        stubProducts(
                product(7L, "Agua", "5.00", true),
                product(3L, "Cappuccino", "12.50", true),
                product(5L, "Brownie", "9.00", true));
        stubRecipes();
        stubSequence(11L);
        stubSave();

        Order order = service.create(
                AUTH_USER, null, null,
                List.of(line(7L, 1), line(5L, 1), line(3L, 1)));

        assertThat(order.getItems())
                .extracting(OrderItem::getProductId)
                .containsExactly(3L, 5L, 7L);
    }

    @Test
    void mesaEObservacoesEmBrancoViramNulos() {
        stubSingleProductCreation();

        Order order = service.create(
                AUTH_USER, "   ", "   ", List.of(line(3L, 1)));

        assertThat(order.getTableReference()).isNull();
        assertThat(order.getNotes()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "ATTENDANT"})
    void perfisAutorizadosPodemRegistrarPedido(Role role) {
        loginAs(role);
        stubSingleProductCreation();

        Order order = service.create(
                AUTH_USER, null, null, List.of(line(3L, 1)));

        assertThat(order.getId()).isEqualTo(ORDER_ID);
    }

    @Test
    void numeroDoPedidoVemDoContadorDoEstabelecimento() {
        stubProducts(product(3L, "Cappuccino", "12.50", true));
        stubRecipes();
        OrderSequence sequence = stubSequence(11L);
        stubSave();

        Order order = service.create(
                AUTH_USER, null, null, List.of(line(3L, 1)));

        assertThat(order.getOrderNumber()).isEqualTo(12L);
        assertThat(sequence.getLastNumber()).isEqualTo(12L);
        verify(orderSequenceRepository).createIfMissing(ESTABLISHMENT_ID);
    }

    // =====================================================
    // create: consumo de ingredientes
    // =====================================================

    @Test
    void consumoEConsolidadoPorIngredienteEntreProdutosDoPedido() {
        stubProducts(
                product(3L, "Cappuccino", "12.50", true),
                product(7L, "Espresso", "8.00", true));
        // Café (ingrediente 2) é usado nos dois produtos.
        stubRecipes(
                recipeLine(3L, 2L, "18"),
                recipeLine(3L, 5L, "150"),
                recipeLine(7L, 2L, "30"));
        stubSequence(11L);
        stubSave();

        service.create(
                AUTH_USER, null, null, List.of(line(3L, 2), line(7L, 1)));

        verify(stockService).consumeForOrder(
                eq(user), eq(ORDER_ID), eq("Pedido nº 12"),
                requiredCaptor.capture());

        Map<Long, BigDecimal> required = requiredCaptor.getValue();

        assertThat(required.keySet()).containsExactly(2L, 5L);
        // 2 x 18 + 1 x 30
        assertThat(required.get(2L)).isEqualByComparingTo("66");
        // 2 x 150
        assertThat(required.get(5L)).isEqualByComparingTo("300");
    }

    @Test
    void produtoComReceitaEMarcadoComoControladoEOSemReceitaNao() {
        stubProducts(
                product(3L, "Cappuccino", "12.50", true),
                product(7L, "Agua", "5.00", true));
        stubRecipes(recipeLine(3L, 2L, "30"));
        stubSequence(11L);
        stubSave();

        Order order = service.create(
                AUTH_USER, null, null, List.of(line(3L, 1), line(7L, 1)));

        // Ordem crescente de id do produto: 3 (Cappuccino) e 7 (Agua).
        assertThat(order.getItems())
                .extracting(OrderItem::getProductName, OrderItem::isStockTracked)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Cappuccino", true),
                        org.assertj.core.groups.Tuple.tuple("Agua", false));

        verify(stockService).consumeForOrder(
                eq(user), eq(ORDER_ID), any(), requiredCaptor.capture());
        assertThat(requiredCaptor.getValue().keySet()).containsExactly(2L);
    }

    @Test
    void pedidoSomenteComProdutosSemReceitaNaoBloqueiaIngredientes() {
        stubProducts(product(7L, "Agua", "5.00", true));
        stubRecipes();
        stubSequence(11L);
        stubSave();

        Order order = service.create(
                AUTH_USER, null, null, List.of(line(7L, 3)));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).isStockTracked()).isFalse();
        verifyNoInteractionsWithIngredients();

        verify(stockService).consumeForOrder(
                eq(user), eq(ORDER_ID), any(), requiredCaptor.capture());
        assertThat(requiredCaptor.getValue()).isEmpty();
    }

    private void verifyNoInteractionsWithIngredients() {
        verifyNoInteractions(ingredientRepository);
    }

    // =====================================================
    // create: bloqueios e ordem das chamadas
    // =====================================================

    @Test
    void bloqueiaProdutosIngredientesContadorGravaEConsomeNessaOrdem() {
        stubSingleProductCreation();

        service.create(AUTH_USER, null, null, List.of(line(3L, 1)));

        InOrder order = inOrder(
                productRepository, recipeItemRepository, ingredientRepository,
                orderSequenceRepository, orderRepository, stockService);

        order.verify(productRepository)
                .findAllByIdsForShare(eq(ESTABLISHMENT_ID), any());
        order.verify(recipeItemRepository)
                .findLinesByProducts(eq(ESTABLISHMENT_ID), any());
        order.verify(ingredientRepository)
                .findAllByIdsForUpdate(eq(ESTABLISHMENT_ID), any());
        order.verify(orderSequenceRepository).createIfMissing(ESTABLISHMENT_ID);
        order.verify(orderSequenceRepository).findForUpdate(ESTABLISHMENT_ID);
        order.verify(orderRepository).saveAndFlush(any(Order.class));
        order.verify(stockService)
                .consumeForOrder(any(), eq(ORDER_ID), any(), any());
    }

    @Test
    void produtosEIngredientesSaoBloqueadosEmOrdemCrescenteDeId() {
        stubProducts(
                product(3L, "Cappuccino", "12.50", true),
                product(7L, "Espresso", "8.00", true));
        stubRecipes(
                recipeLine(7L, 5L, "20"),
                recipeLine(3L, 2L, "30"));
        stubSequence(11L);
        stubSave();

        service.create(
                AUTH_USER, null, null, List.of(line(7L, 1), line(3L, 1)));

        verify(productRepository).findAllByIdsForShare(
                eq(ESTABLISHMENT_ID), productIdsCaptor.capture());
        assertThat(productIdsCaptor.getValue()).containsExactly(3L, 7L);

        verify(ingredientRepository).findAllByIdsForUpdate(
                eq(ESTABLISHMENT_ID), ingredientIdsCaptor.capture());
        assertThat(ingredientIdsCaptor.getValue()).containsExactly(2L, 5L);
    }

    @Test
    void faltaDeEstoquePropagaAExcecaoParaDesfazerATransacao() {
        stubSingleProductCreation();
        InsufficientStockException failure = new InsufficientStockException(List.of(
                new InsufficientStockException.StockIssue(
                        2L, "Café", "G",
                        new BigDecimal("30"), new BigDecimal("10"),
                        InsufficientStockException.Reason.INSUFFICIENT_STOCK)));
        when(stockService.consumeForOrder(any(), any(), any(), any()))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, null, List.of(line(3L, 1))))
                .isSameAs(failure);
    }

    // =====================================================
    // create: validação das linhas (antes de qualquer bloqueio)
    // =====================================================

    private void assertRejectedBeforeAnyLock(
            List<OrderLine> lines, String message) {
        assertThatThrownBy(() -> service.create(AUTH_USER, null, null, lines))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(message);

        verifyNoInteractions(
                productRepository, recipeItemRepository, ingredientRepository,
                orderSequenceRepository, orderRepository, stockService);
    }

    @Test
    void listaNulaLancaRegraDeNegocio() {
        assertRejectedBeforeAnyLock(null, "O pedido deve ter ao menos um item.");
    }

    @Test
    void listaVaziaLancaRegraDeNegocio() {
        assertRejectedBeforeAnyLock(
                List.of(), "O pedido deve ter ao menos um item.");
    }

    @Test
    void maisDeCinquentaItensLancaRegraDeNegocio() {
        List<OrderLine> lines = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> line((long) i, 1))
                .toList();

        assertRejectedBeforeAnyLock(
                lines, "O pedido deve ter no máximo 50 itens.");
    }

    @Test
    void itemNuloLancaRegraDeNegocio() {
        assertRejectedBeforeAnyLock(
                Arrays.asList((OrderLine) null), "Item do pedido inválido.");
    }

    @Test
    void produtoNuloLancaRegraDeNegocio() {
        assertRejectedBeforeAnyLock(
                List.of(line(null, 1)), "O produto é obrigatório.");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -3})
    void produtoComIdNaoPositivoLancaRegraDeNegocio(long productId) {
        assertRejectedBeforeAnyLock(
                List.of(line(productId, 1)), "O produto informado é inválido.");
    }

    @Test
    void quantidadeNulaLancaRegraDeNegocio() {
        assertRejectedBeforeAnyLock(
                List.of(line(3L, null)), "A quantidade é obrigatória.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1000})
    void quantidadeForaDoIntervaloLancaRegraDeNegocio(int quantity) {
        assertRejectedBeforeAnyLock(
                List.of(line(3L, quantity)),
                "A quantidade de cada item deve estar entre 1 e 999.");
    }

    @Test
    void produtoRepetidoLancaRegraDeNegocio() {
        assertRejectedBeforeAnyLock(
                List.of(line(3L, 1), line(3L, 2)), "Produto repetido no pedido.");
    }

    // =====================================================
    // create: regras que dependem do banco
    // =====================================================

    @Test
    void produtoInexistenteOuDeOutroEstabelecimentoLancaRegraDeNegocio() {
        // O repository só devolve os produtos do estabelecimento: falta o 7.
        stubProducts(product(3L, "Cappuccino", "12.50", true));

        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, null, List.of(line(3L, 1), line(7L, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Produto não encontrado.");

        verifyNoInteractions(
                recipeItemRepository, ingredientRepository,
                orderSequenceRepository, orderRepository, stockService);
    }

    @Test
    void produtoInativoLancaRegraDeNegocioComONomeDoProduto() {
        stubProducts(product(3L, "Cappuccino", "12.50", false));

        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, null, List.of(line(3L, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O produto \"Cappuccino\" está inativo.");

        verifyNoInteractions(
                recipeItemRepository, ingredientRepository,
                orderSequenceRepository, orderRepository, stockService);
    }

    @Test
    void mesaComMaisDeCinquentaCaracteresLancaRegraDeNegocioESemGravar() {
        stubProducts(product(3L, "Cappuccino", "12.50", true));
        stubRecipes();
        stubSequence(11L);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, "m".repeat(51), null, List.of(line(3L, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A mesa ou comanda deve ter no máximo 50 caracteres.");

        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoStockEffect();
    }

    @Test
    void observacoesComMaisDeQuinhentosCaracteresLancamRegraDeNegocio() {
        stubProducts(product(3L, "Cappuccino", "12.50", true));
        stubRecipes();
        stubSequence(11L);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, "n".repeat(501), List.of(line(3L, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("As observações devem ter no máximo 500 caracteres.");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    // =====================================================
    // create: permissões
    // =====================================================

    @Test
    void estoquistaNaoPodeRegistrarPedido() {
        loginAs(Role.STOCKIST);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, null, List.of(line(3L, 1))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(
                productRepository, recipeItemRepository, ingredientRepository,
                orderSequenceRepository, orderRepository, stockService);
    }

    @Test
    void usuarioInativoNaoPodeRegistrarPedido() {
        when(user.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, null, List.of(line(3L, 1))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, orderRepository, stockService);
    }

    @Test
    void estabelecimentoInativoNaoPodeRegistrarPedido() {
        when(establishment.isActive()).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                AUTH_USER, null, null, List.of(line(3L, 1))))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(productRepository, orderRepository, stockService);
    }

    // =====================================================
    // list
    // =====================================================

    private Pageable listedPageable(Pageable input) {
        when(orderRepository.findByEstablishmentId(
                eq(ESTABLISHMENT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(AUTH_USER, null, input);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByEstablishmentId(
                eq(ESTABLISHMENT_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void listagemSemStatusUsaOFiltroDeEstabelecimento() {
        listedPageable(PageRequest.of(0, 10));

        verify(orderRepository, never())
                .findByEstablishmentIdAndStatus(any(), any(), any());
    }

    @Test
    void listagemComStatusFiltraPorStatus() {
        when(orderRepository.findByEstablishmentIdAndStatus(
                eq(ESTABLISHMENT_ID), eq(OrderStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(AUTH_USER, OrderStatus.PENDING, PageRequest.of(0, 10));

        verify(orderRepository).findByEstablishmentIdAndStatus(
                eq(ESTABLISHMENT_ID), eq(OrderStatus.PENDING), any(Pageable.class));
        verify(orderRepository, never()).findByEstablishmentId(any(), any());
    }

    @Test
    void listagemLimitaOTamanhoDaPaginaACemEOrdenaPorMaisRecentes() {
        Pageable used = listedPageable(PageRequest.of(2, 500));

        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(100);
        assertThat(used.getSort().stream().map(Sort.Order::getProperty))
                .containsExactly("createdAt", "id");
        assertThat(used.getSort().stream().map(Sort.Order::isDescending))
                .containsOnly(true);
    }

    @Test
    void listagemIgnoraAOrdenacaoDoCliente() {
        Pageable used = listedPageable(
                PageRequest.of(0, 10, Sort.by("total")));

        assertThat(used.getSort().stream().map(Sort.Order::getProperty))
                .containsExactly("createdAt", "id");
    }

    @Test
    void listagemSemPaginacaoUsaTamanhoPadraoDeVinte() {
        Pageable used = listedPageable(Pageable.unpaged());

        assertThat(used.getPageNumber()).isZero();
        assertThat(used.getPageSize()).isEqualTo(20);
    }

    @Test
    void listagemRetornaOResultadoDoRepository() {
        Order order = orderIn(OrderStatus.PENDING);
        when(orderRepository.findByEstablishmentId(
                eq(ESTABLISHMENT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        Page<Order> page = service.list(AUTH_USER, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly(order);
    }

    @Test
    void estoquistaNaoPodeListarPedidos() {
        loginAs(Role.STOCKIST);

        assertThatThrownBy(() -> service.list(
                AUTH_USER, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository);
    }

    // =====================================================
    // get
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "ATTENDANT"})
    void perfisAutorizadosConsultamOPedido(Role role) {
        loginAs(role);
        Order order = orderIn(OrderStatus.PENDING);
        when(orderRepository.findDetailByIdAndEstablishmentId(ORDER_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(order));

        assertThat(service.get(AUTH_USER, ORDER_ID)).isSameAs(order);
    }

    @Test
    void pedidoInexistenteOuDeOutroEstabelecimentoLancaNaoEncontrado() {
        when(orderRepository.findDetailByIdAndEstablishmentId(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(AUTH_USER, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Pedido não encontrado.");
    }

    @Test
    void estoquistaNaoPodeConsultarPedido() {
        loginAs(Role.STOCKIST);

        assertThatThrownBy(() -> service.get(AUTH_USER, ORDER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository);
    }

    // =====================================================
    // startPreparation
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "ATTENDANT"})
    void iniciaAPreparacaoDeUmPedidoPendente(Role role) {
        loginAs(role);
        Order order = orderIn(OrderStatus.PENDING);
        stubLock(order);

        Order result = service.startPreparation(AUTH_USER, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(orderRepository).saveAndFlush(order);
        verifyNoStockEffect();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class,
            names = {"IN_PREPARATION", "COMPLETED", "CANCELLED"})
    void somentePedidoPendenteEntraEmPreparacao(OrderStatus status) {
        stubLock(orderIn(status));

        assertThatThrownBy(() -> service.startPreparation(AUTH_USER, ORDER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Apenas pedidos pendentes podem entrar em preparação.");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void iniciarPreparacaoDePedidoInexistenteLancaNaoEncontrado() {
        when(orderRepository.findByIdAndEstablishmentIdForUpdate(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startPreparation(AUTH_USER, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Pedido não encontrado.");
    }

    @Test
    void estoquistaNaoPodeIniciarPreparacao() {
        loginAs(Role.STOCKIST);

        assertThatThrownBy(() -> service.startPreparation(AUTH_USER, ORDER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository);
    }

    // =====================================================
    // complete
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "ATTENDANT"})
    void concluiUmPedidoEmPreparacao(Role role) {
        loginAs(role);
        Order order = orderIn(OrderStatus.IN_PREPARATION);
        stubLock(order);

        Order result = service.complete(AUTH_USER, ORDER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void concluirNaoMexeNoEstoque() {
        stubLock(orderIn(OrderStatus.IN_PREPARATION));

        service.complete(AUTH_USER, ORDER_ID);

        verifyNoStockEffect();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class,
            names = {"PENDING", "COMPLETED", "CANCELLED"})
    void somentePedidoEmPreparacaoPodeSerConcluido(OrderStatus status) {
        stubLock(orderIn(status));

        assertThatThrownBy(() -> service.complete(AUTH_USER, ORDER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Apenas pedidos em preparação podem ser concluídos.");

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void estoquistaNaoPodeConcluirPedido() {
        loginAs(Role.STOCKIST);

        assertThatThrownBy(() -> service.complete(AUTH_USER, ORDER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository);
    }

    // =====================================================
    // cancel
    // =====================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "ATTENDANT"})
    void cancelaPedidoPendenteEDevolveOEstoque(Role role) {
        loginAs(role);
        Order order = orderIn(OrderStatus.PENDING);
        stubLock(order);

        Order result = service.cancel(AUTH_USER, ORDER_ID, null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancelledFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getCancelledBy()).isSameAs(user);
        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(result.getCancellationReason()).isNull();

        verify(stockService).reverseOrderConsumption(
                user, ORDER_ID, "Cancelamento do pedido nº 12");
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void justificativaOpcionalDoPedidoPendenteEGravadaSemEspacosNasPontas() {
        Order order = orderIn(OrderStatus.PENDING);
        stubLock(order);

        Order result = service.cancel(AUTH_USER, ORDER_ID, "  Cliente desistiu  ");

        assertThat(result.getCancellationReason()).isEqualTo("Cliente desistiu");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER"})
    void cancelaPedidoEmPreparacaoSemDevolverOEstoque(Role role) {
        loginAs(role);
        Order order = orderIn(OrderStatus.IN_PREPARATION);
        stubLock(order);

        Order result = service.cancel(AUTH_USER, ORDER_ID, "Cliente foi embora");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancelledFromStatus())
                .isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(result.getCancellationReason()).isEqualTo("Cliente foi embora");

        verify(stockService, never()).reverseOrderConsumption(any(), any(), any());
        verify(orderRepository).saveAndFlush(order);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cancelarEmPreparacaoSemJustificativaEhRejeitado(String reason) {
        Order order = orderIn(OrderStatus.IN_PREPARATION);
        stubLock(order);

        assertThatThrownBy(() -> service.cancel(AUTH_USER, ORDER_ID, reason))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A justificativa é obrigatória para cancelar "
                        + "um pedido em preparação.");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoStockEffect();
    }

    @Test
    void atendenteNaoPodeCancelarPedidoEmPreparacao() {
        loginAs(Role.ATTENDANT);
        Order order = orderIn(OrderStatus.IN_PREPARATION);
        stubLock(order);

        assertThatThrownBy(() -> service.cancel(
                AUTH_USER, ORDER_ID, "Cliente foi embora"))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoStockEffect();
    }

    @Test
    void pedidoConcluidoNaoPodeSerCancelado() {
        Order order = orderIn(OrderStatus.COMPLETED);
        stubLock(order);

        assertThatThrownBy(() -> service.cancel(AUTH_USER, ORDER_ID, "Motivo"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Pedidos concluídos não podem ser cancelados.");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoStockEffect();
    }

    @Test
    void pedidoJaCanceladoNaoDevolveOEstoqueDeNovo() {
        stubLock(orderIn(OrderStatus.CANCELLED));

        assertThatThrownBy(() -> service.cancel(AUTH_USER, ORDER_ID, "Motivo"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("O pedido já foi cancelado.");

        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoStockEffect();
    }

    @Test
    void justificativaComMaisDeQuinhentosCaracteresLancaRegraDeNegocio() {
        Order order = orderIn(OrderStatus.PENDING);
        stubLock(order);

        assertThatThrownBy(() -> service.cancel(
                AUTH_USER, ORDER_ID, "j".repeat(501)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A justificativa deve ter no máximo 500 caracteres.");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoStockEffect();
    }

    @Test
    void falhaNaDevolucaoDoEstoqueInterrompeOCancelamento() {
        stubLock(orderIn(OrderStatus.PENDING));
        BusinessConflictException failure =
                new BusinessConflictException("O consumo deste pedido já foi estornado.");
        when(stockService.reverseOrderConsumption(any(), any(), any()))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.cancel(AUTH_USER, ORDER_ID, null))
                .isSameAs(failure);

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void bloqueiaOPedidoAntesDeDevolverOEstoque() {
        Order order = orderIn(OrderStatus.PENDING);
        stubLock(order);

        service.cancel(AUTH_USER, ORDER_ID, null);

        InOrder inOrder = inOrder(orderRepository, stockService);

        inOrder.verify(orderRepository)
                .findByIdAndEstablishmentIdForUpdate(ORDER_ID, ESTABLISHMENT_ID);
        inOrder.verify(stockService)
                .reverseOrderConsumption(user, ORDER_ID, "Cancelamento do pedido nº 12");
        inOrder.verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void cancelarPedidoInexistenteLancaNaoEncontrado() {
        when(orderRepository.findByIdAndEstablishmentIdForUpdate(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(AUTH_USER, 99L, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Pedido não encontrado.");

        verifyNoStockEffect();
    }

    @Test
    void estoquistaNaoPodeCancelarPedido() {
        loginAs(Role.STOCKIST);

        assertThatThrownBy(() -> service.cancel(AUTH_USER, ORDER_ID, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository, stockService);
    }
}
