package com.coffeepilot.api.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
import com.coffeepilot.api.common.exception.InsufficientStockException;
import com.coffeepilot.api.common.exception.InsufficientStockException.Reason;
import com.coffeepilot.api.common.exception.InsufficientStockException.StockIssue;
import com.coffeepilot.api.common.exception.ResourceNotFoundException;
import com.coffeepilot.api.ingredient.Ingredient;
import com.coffeepilot.api.ingredient.IngredientRepository;
import com.coffeepilot.api.ingredient.IngredientUnit;
import com.coffeepilot.api.security.ActiveUserProvider;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ESTABLISHMENT_ID = 10L;
    private static final Long INGREDIENT_ID = 1L;
    private static final Long ORDER_ID = 55L;

    @Mock UserRepository userRepository;
    @Mock IngredientRepository ingredientRepository;
    @Mock StockMovementRepository movementRepository;

    @Captor ArgumentCaptor<Collection<Long>> idsCaptor;

    private StockService service;
    private Establishment shop;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new StockService(
                new ActiveUserProvider(userRepository),
                ingredientRepository,
                movementRepository);

        shop = mock(Establishment.class);
        lenient().when(shop.getId()).thenReturn(ESTABLISHMENT_ID);
        lenient().when(shop.isActive()).thenReturn(true);

        actor = mock(User.class);
        lenient().when(actor.isActive()).thenReturn(true);
        lenient().when(actor.getEstablishment()).thenReturn(shop);
        lenient().when(userRepository.findWithEstablishmentById(USER_ID))
                .thenReturn(Optional.of(actor));
    }

    // ---------- helpers ----------

    private AuthenticatedUser auth() {
        return new AuthenticatedUser(
                USER_ID, "Ana", "ana@x.com", Role.OWNER,
                ESTABLISHMENT_ID, "Estabelecimento Teste");
    }

    /** O papel vale o do banco (usuário recarregado), não o do token. */
    private void actingAs(Role role) {
        lenient().when(actor.getRole()).thenReturn(role);
    }

    private Ingredient ingredient(IngredientUnit unit, String balance) {
        return ingredientWithId(INGREDIENT_ID, "Café", unit, balance);
    }

    private Ingredient ingredientWithId(
            Long id, String name, IngredientUnit unit, String balance) {
        Ingredient ingredient =
                new Ingredient(ESTABLISHMENT_ID, name, unit, BigDecimal.ZERO);
        ReflectionTestUtils.setField(ingredient, "id", id);

        BigDecimal value = new BigDecimal(balance);
        if (value.signum() > 0) {
            ingredient.applyStockChange(value);
        }
        return ingredient;
    }

    private void lockFinds(Ingredient ingredient) {
        when(ingredientRepository.findByIdAndEstablishmentIdForUpdate(
                INGREDIENT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(ingredient));
    }

    private void lockMany(List<Ingredient> ingredients) {
        when(ingredientRepository.findAllByIdsForUpdate(
                eq(ESTABLISHMENT_ID), any()))
                .thenReturn(ingredients);
    }

    private void saveEchoesArgument() {
        when(movementRepository.saveAndFlush(any(StockMovement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private StockMovement savedMovement() {
        ArgumentCaptor<StockMovement> captor =
                ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private List<StockMovement> savedMovements(int times) {
        ArgumentCaptor<StockMovement> captor =
                ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository, times(times)).saveAndFlush(captor.capture());
        return captor.getAllValues();
    }

    private StockMovement persistedMovement(
            Long id,
            Ingredient ingredient,
            StockMovementType type,
            String delta,
            String balanceAfter
    ) {
        StockMovement movement = new StockMovement(
                shop, ingredient, type,
                new BigDecimal(delta), new BigDecimal(balanceAfter),
                null, null, actor, null);
        ReflectionTestUtils.setField(movement, "id", id);
        return movement;
    }

    private StockMovement consumption(
            Long id,
            Ingredient ingredient,
            String delta,
            String balanceAfter,
            Long orderId
    ) {
        StockMovement movement = new StockMovement(
                shop, ingredient, StockMovementType.CONSUMPTION,
                new BigDecimal(delta), new BigDecimal(balanceAfter),
                null, "Pedido", actor, null, orderId);
        ReflectionTestUtils.setField(movement, "id", id);
        return movement;
    }

    /** Monta o consumo por ingrediente: required(1L, "30", 2L, "150.5"). */
    private Map<Long, BigDecimal> required(Object... idsAndQuantities) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();

        for (int i = 0; i < idsAndQuantities.length; i += 2) {
            map.put(
                    (Long) idsAndQuantities[i],
                    new BigDecimal((String) idsAndQuantities[i + 1]));
        }

        return map;
    }

    // ---------- entrada ----------

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER", "STOCKIST"})
    void perfisDeEstoquePodemRegistrarEntrada(Role role) {
        actingAs(role);
        lockFinds(ingredient(IngredientUnit.G, "1000"));
        saveEchoesArgument();

        StockMovement movement = service.registerEntry(
                auth(), INGREDIENT_ID, new BigDecimal("10"), null);

        assertThat(movement.getType()).isEqualTo(StockMovementType.ENTRY);
    }

    @Test
    void atendenteNaoPodeRegistrarEntrada() {
        actingAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.registerEntry(
                auth(), INGREDIENT_ID, BigDecimal.TEN, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void entradaSomaAoSaldoERegistraMovimentacaoCoerente() {
        actingAs(Role.STOCKIST);
        Ingredient ingredient = ingredient(IngredientUnit.G, "1000");
        lockFinds(ingredient);
        saveEchoesArgument();

        service.registerEntry(
                auth(), INGREDIENT_ID, new BigDecimal("250.5"), "Compra");

        StockMovement saved = savedMovement();
        assertThat(saved.getType()).isEqualTo(StockMovementType.ENTRY);
        assertThat(saved.getQuantityDelta()).isEqualByComparingTo("250.5");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("1250.5");
        assertThat(saved.getDescription()).isEqualTo("Compra");
        assertThat(saved.getCreatedBy()).isSameAs(actor);
        assertThat(saved.getOrderId()).isNull();
        assertThat(ingredient.getCurrentQuantity())
                .isEqualByComparingTo(saved.getBalanceAfter());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void entradaComQuantidadeZeroOuNegativaEhRejeitada(String value) {
        actingAs(Role.OWNER);
        lockFinds(ingredient(IngredientUnit.G, "1000"));

        assertThatThrownBy(() -> service.registerEntry(
                auth(), INGREDIENT_ID, new BigDecimal(value), null))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void entradaComMaisDeTresCasasDecimaisEhRejeitada() {
        actingAs(Role.OWNER);
        lockFinds(ingredient(IngredientUnit.G, "1000"));

        assertThatThrownBy(() -> service.registerEntry(
                auth(), INGREDIENT_ID, new BigDecimal("1.2345"), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void entradaFracionadaEmIngredienteUnitEhRejeitada() {
        actingAs(Role.OWNER);
        lockFinds(ingredient(IngredientUnit.UNIT, "10"));

        assertThatThrownBy(() -> service.registerEntry(
                auth(), INGREDIENT_ID, new BigDecimal("1.5"), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void entradaInteiraEmIngredienteUnitEhAceita() {
        actingAs(Role.OWNER);
        Ingredient ingredient = ingredient(IngredientUnit.UNIT, "10");
        lockFinds(ingredient);
        saveEchoesArgument();

        service.registerEntry(auth(), INGREDIENT_ID, new BigDecimal("5"), null);

        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("15");
    }

    @Test
    void entradaQueEstouraOLimiteMaximoEhRejeitada() {
        actingAs(Role.OWNER);
        lockFinds(ingredient(IngredientUnit.G, "99999999999"));

        assertThatThrownBy(() -> service.registerEntry(
                auth(), INGREDIENT_ID, new BigDecimal("10"), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void entradaEmIngredienteInativoGeraConflito() {
        actingAs(Role.OWNER);
        Ingredient ingredient = ingredient(IngredientUnit.G, "1000");
        ingredient.deactivate();
        lockFinds(ingredient);

        assertThatThrownBy(() -> service.registerEntry(
                auth(), INGREDIENT_ID, BigDecimal.TEN, null))
                .isInstanceOf(BusinessConflictException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void entradaEmIngredienteDeOutroEstabelecimentoRetornaNaoEncontrado() {
        actingAs(Role.OWNER);
        when(ingredientRepository.findByIdAndEstablishmentIdForUpdate(
                INGREDIENT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerEntry(
                auth(), INGREDIENT_ID, BigDecimal.TEN, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    // ---------- ajuste ----------

    @Test
    void ajusteNegativoPorPerdaDiminuiOSaldo() {
        actingAs(Role.STOCKIST);
        Ingredient ingredient = ingredient(IngredientUnit.G, "1000");
        lockFinds(ingredient);
        saveEchoesArgument();

        service.registerAdjustment(
                auth(), INGREDIENT_ID, new BigDecimal("-200"),
                StockAdjustmentReason.LOSS, "Derramou ao preparar");

        StockMovement saved = savedMovement();
        assertThat(saved.getType()).isEqualTo(StockMovementType.ADJUSTMENT);
        assertThat(saved.getReason()).isEqualTo(StockAdjustmentReason.LOSS);
        assertThat(saved.getQuantityDelta()).isEqualByComparingTo("-200");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("800");
        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("800");
    }

    @Test
    void ajustePositivoPorDiferencaDeContagemAumentaOSaldo() {
        actingAs(Role.MANAGER);
        Ingredient ingredient = ingredient(IngredientUnit.ML, "1000");
        lockFinds(ingredient);
        saveEchoesArgument();

        service.registerAdjustment(
                auth(), INGREDIENT_ID, new BigDecimal("300"),
                StockAdjustmentReason.COUNT_DIFFERENCE, "Contagem física");

        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("1300");
    }

    @ParameterizedTest
    @EnumSource(value = StockAdjustmentReason.class,
            names = {"LOSS", "EXPIRED", "DAMAGED"})
    void perdaVencimentoEDanoComVariacaoPositivaSaoRejeitados(
            StockAdjustmentReason reason) {
        actingAs(Role.OWNER);
        lockFinds(ingredient(IngredientUnit.G, "1000"));

        assertThatThrownBy(() -> service.registerAdjustment(
                auth(), INGREDIENT_ID, new BigDecimal("10"), reason, "Erro de sinal"))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void ajusteSemMotivoEhRejeitado() {
        actingAs(Role.OWNER);

        assertThatThrownBy(() -> service.registerAdjustment(
                auth(), INGREDIENT_ID, new BigDecimal("-10"), null, "Justificativa"))
                .isInstanceOf(BusinessRuleException.class);

        verify(ingredientRepository, never())
                .findByIdAndEstablishmentIdForUpdate(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void ajusteSemJustificativaEhRejeitado(String justification) {
        actingAs(Role.OWNER);

        assertThatThrownBy(() -> service.registerAdjustment(
                auth(), INGREDIENT_ID, new BigDecimal("-10"),
                StockAdjustmentReason.OTHER, justification))
                .isInstanceOf(BusinessRuleException.class);

        verify(ingredientRepository, never())
                .findByIdAndEstablishmentIdForUpdate(any(), any());
    }

    @Test
    void ajusteComVariacaoZeroEhRejeitado() {
        actingAs(Role.OWNER);
        lockFinds(ingredient(IngredientUnit.G, "1000"));

        assertThatThrownBy(() -> service.registerAdjustment(
                auth(), INGREDIENT_ID, BigDecimal.ZERO,
                StockAdjustmentReason.OTHER, "Sem variação"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void ajusteQueDeixariaSaldoNegativoGeraConflito() {
        actingAs(Role.OWNER);
        Ingredient ingredient = ingredient(IngredientUnit.G, "100");
        lockFinds(ingredient);

        assertThatThrownBy(() -> service.registerAdjustment(
                auth(), INGREDIENT_ID, new BigDecimal("-200"),
                StockAdjustmentReason.LOSS, "Perda maior que o saldo"))
                .isInstanceOf(BusinessConflictException.class);

        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("100");
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void atendenteNaoPodeRegistrarAjuste() {
        actingAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.registerAdjustment(
                auth(), INGREDIENT_ID, new BigDecimal("-10"),
                StockAdjustmentReason.LOSS, "Perda"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- estorno ----------

    @Test
    void estornoDesfazOEfeitoDaMovimentacaoOriginal() {
        actingAs(Role.MANAGER);
        Ingredient ingredient = ingredient(IngredientUnit.G, "1500");
        StockMovement original = persistedMovement(
                5L, ingredient, StockMovementType.ENTRY, "500", "1500");

        when(movementRepository.findByIdAndEstablishmentId(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(original));
        lockFinds(ingredient);
        when(movementRepository.existsByReversesMovementIdAndEstablishmentId(
                5L, ESTABLISHMENT_ID)).thenReturn(false);
        saveEchoesArgument();

        service.reverse(auth(), 5L, "Entrada em duplicidade");

        StockMovement saved = savedMovement();
        assertThat(saved.getType()).isEqualTo(StockMovementType.REVERSAL);
        assertThat(saved.getReversesMovement()).isSameAs(original);
        assertThat(saved.getQuantityDelta()).isEqualByComparingTo("-500");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("1000");
        assertThat(saved.getDescription()).isEqualTo("Entrada em duplicidade");
        assertThat(saved.getOrderId()).isNull();
        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("1000");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void estornoSemJustificativaEhRejeitadoAntesDeConsultarOBanco(
            String description) {
        actingAs(Role.OWNER);

        assertThatThrownBy(() -> service.reverse(auth(), 5L, description))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).findByIdAndEstablishmentId(any(), any());
        verify(ingredientRepository, never())
                .findByIdAndEstablishmentIdForUpdate(any(), any());
    }

    @Test
    void estornoDeMovimentacaoInexistenteOuDeOutroEstabelecimentoRetornaNaoEncontrado() {
        actingAs(Role.OWNER);
        when(movementRepository.findByIdAndEstablishmentId(99L, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reverse(auth(), 99L, "Motivo"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void estornoDeUmEstornoEhRejeitado() {
        actingAs(Role.OWNER);
        StockMovement reversal = mock(StockMovement.class);
        when(reversal.getType()).thenReturn(StockMovementType.REVERSAL);
        when(movementRepository.findByIdAndEstablishmentId(7L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(reversal));

        assertThatThrownBy(() -> service.reverse(auth(), 7L, "Motivo"))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void estornoDoSaldoInicialEhRejeitado() {
        actingAs(Role.OWNER);
        StockMovement initial = mock(StockMovement.class);
        when(initial.getType()).thenReturn(StockMovementType.INITIAL);
        when(movementRepository.findByIdAndEstablishmentId(1L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(initial));

        assertThatThrownBy(() -> service.reverse(auth(), 1L, "Motivo"))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void estornoManualDeConsumoDePedidoEhRejeitado() {
        actingAs(Role.OWNER);
        StockMovement consumption = mock(StockMovement.class);
        when(consumption.getType()).thenReturn(StockMovementType.CONSUMPTION);
        when(movementRepository.findByIdAndEstablishmentId(8L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(consumption));

        assertThatThrownBy(() -> service.reverse(auth(), 8L, "Motivo"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Movimentações de consumo de pedidos não podem "
                        + "ser estornadas manualmente. Cancele o pedido.");

        verify(ingredientRepository, never())
                .findByIdAndEstablishmentIdForUpdate(any(), any());
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void estornoRepetidoGeraConflito() {
        actingAs(Role.OWNER);
        Ingredient ingredient = ingredient(IngredientUnit.G, "1500");
        StockMovement original = persistedMovement(
                5L, ingredient, StockMovementType.ENTRY, "500", "1500");

        when(movementRepository.findByIdAndEstablishmentId(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(original));
        lockFinds(ingredient);
        when(movementRepository.existsByReversesMovementIdAndEstablishmentId(
                5L, ESTABLISHMENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.reverse(auth(), 5L, "Motivo"))
                .isInstanceOf(BusinessConflictException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void estornoEmIngredienteInativoGeraConflito() {
        actingAs(Role.OWNER);
        Ingredient ingredient = ingredient(IngredientUnit.G, "1500");
        StockMovement original = persistedMovement(
                5L, ingredient, StockMovementType.ENTRY, "500", "1500");
        ingredient.deactivate();

        when(movementRepository.findByIdAndEstablishmentId(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(original));
        lockFinds(ingredient);

        assertThatThrownBy(() -> service.reverse(auth(), 5L, "Motivo"))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void estornoDeEntradaJaConsumidaGeraSaldoInsuficiente() {
        actingAs(Role.OWNER);
        Ingredient ingredient = ingredient(IngredientUnit.G, "200");
        StockMovement original = persistedMovement(
                5L, ingredient, StockMovementType.ENTRY, "500", "700");

        when(movementRepository.findByIdAndEstablishmentId(5L, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(original));
        lockFinds(ingredient);
        when(movementRepository.existsByReversesMovementIdAndEstablishmentId(
                5L, ESTABLISHMENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.reverse(auth(), 5L, "Motivo"))
                .isInstanceOf(BusinessConflictException.class);

        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("200");
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STOCKIST", "ATTENDANT"})
    void apenasOwnerEManagerPodemEstornar(Role role) {
        actingAs(role);

        assertThatThrownBy(() -> service.reverse(auth(), 5L, "Motivo"))
                .isInstanceOf(AccessDeniedException.class);

        verify(movementRepository, never()).findByIdAndEstablishmentId(any(), any());
    }

    // ---------- saldo inicial ----------

    @Test
    void saldoInicialRegistraMovimentacaoInitial() {
        Ingredient ingredient = ingredient(IngredientUnit.G, "0");
        when(movementRepository.existsByIngredientIdAndEstablishmentId(
                INGREDIENT_ID, ESTABLISHMENT_ID)).thenReturn(false);
        saveEchoesArgument();

        service.registerInitial(actor, ingredient, new BigDecimal("500"));

        StockMovement saved = savedMovement();
        assertThat(saved.getType()).isEqualTo(StockMovementType.INITIAL);
        assertThat(saved.getQuantityDelta()).isEqualByComparingTo("500");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("500");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getOrderId()).isNull();
        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("500");
    }

    @Test
    void saldoInicialEmIngredienteComMovimentacoesGeraConflito() {
        Ingredient ingredient = ingredient(IngredientUnit.G, "0");
        when(movementRepository.existsByIngredientIdAndEstablishmentId(
                INGREDIENT_ID, ESTABLISHMENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.registerInitial(
                actor, ingredient, new BigDecimal("500")))
                .isInstanceOf(BusinessConflictException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void saldoInicialZeroOuNegativoEhRejeitado(String value) {
        Ingredient ingredient = ingredient(IngredientUnit.G, "0");

        assertThatThrownBy(() -> service.registerInitial(
                actor, ingredient, new BigDecimal(value)))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void saldoInicialFracionadoEmIngredienteUnitEhRejeitado() {
        Ingredient ingredient = ingredient(IngredientUnit.UNIT, "0");

        assertThatThrownBy(() -> service.registerInitial(
                actor, ingredient, new BigDecimal("2.5")))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------- consumo por pedido ----------

    @Test
    void consumoBaixaCadaIngredienteERegistraMovimentacoesDoPedido() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "1000");
        Ingredient leite = ingredientWithId(2L, "Leite", IngredientUnit.ML, "500");
        lockMany(List.of(cafe, leite));
        saveEchoesArgument();

        List<StockMovement> result = service.consumeForOrder(
                actor, ORDER_ID, "Pedido nº 12",
                required(1L, "30", 2L, "150.5"));

        assertThat(result).hasSize(2);

        List<StockMovement> saved = savedMovements(2);

        assertThat(saved).allSatisfy(movement -> {
            assertThat(movement.getType()).isEqualTo(StockMovementType.CONSUMPTION);
            assertThat(movement.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(movement.getDescription()).isEqualTo("Pedido nº 12");
            assertThat(movement.getCreatedBy()).isSameAs(actor);
            assertThat(movement.getReversesMovement()).isNull();
        });

        assertThat(saved.get(0).getIngredient().getId()).isEqualTo(1L);
        assertThat(saved.get(0).getQuantityDelta()).isEqualByComparingTo("-30");
        assertThat(saved.get(0).getBalanceAfter()).isEqualByComparingTo("970");
        assertThat(saved.get(1).getIngredient().getId()).isEqualTo(2L);
        assertThat(saved.get(1).getQuantityDelta()).isEqualByComparingTo("-150.5");
        assertThat(saved.get(1).getBalanceAfter()).isEqualByComparingTo("349.5");

        assertThat(cafe.getCurrentQuantity()).isEqualByComparingTo("970");
        assertThat(leite.getCurrentQuantity()).isEqualByComparingTo("349.5");
    }

    @Test
    void consumoSemIngredientesNaoFazNada() {
        List<StockMovement> result = service.consumeForOrder(
                actor, ORDER_ID, "Pedido nº 12", Map.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(ingredientRepository, movementRepository);
    }

    @Test
    void consumoBloqueiaIngredientesEmOrdemCrescenteDeId() {
        Ingredient primeiro = ingredientWithId(1L, "Café", IngredientUnit.G, "100");
        Ingredient terceiro = ingredientWithId(3L, "Leite", IngredientUnit.ML, "100");
        lockMany(List.of(primeiro, terceiro));
        saveEchoesArgument();

        service.consumeForOrder(
                actor, ORDER_ID, null, required(3L, "1", 1L, "1"));

        verify(ingredientRepository).findAllByIdsForUpdate(
                eq(ESTABLISHMENT_ID), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L, 3L);

        assertThat(savedMovements(2))
                .extracting(movement -> movement.getIngredient().getId())
                .containsExactly(1L, 3L);
    }

    @Test
    void consumoPodeZerarOSaldoDoIngrediente() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "30");
        lockMany(List.of(cafe));
        saveEchoesArgument();

        service.consumeForOrder(actor, ORDER_ID, null, required(1L, "30"));

        assertThat(savedMovement().getBalanceAfter()).isEqualByComparingTo("0");
        assertThat(cafe.getCurrentQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void consumoInteiroEmIngredienteUnitEhAceito() {
        Ingredient ovo = ingredientWithId(1L, "Ovo", IngredientUnit.UNIT, "10");
        lockMany(List.of(ovo));
        saveEchoesArgument();

        service.consumeForOrder(actor, ORDER_ID, null, required(1L, "4"));

        assertThat(ovo.getCurrentQuantity()).isEqualByComparingTo("6");
    }

    @Test
    void faltaDeSaldoListaTodosOsIngredientesComProblemaESemBaixarNada() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "100");
        Ingredient leite = ingredientWithId(2L, "Leite", IngredientUnit.ML, "50");
        Ingredient acucar = ingredientWithId(3L, "Açúcar", IngredientUnit.G, "500");
        lockMany(List.of(cafe, leite, acucar));

        assertThatThrownBy(() -> service.consumeForOrder(
                actor, ORDER_ID, null,
                required(1L, "150", 2L, "80", 3L, "10")))
                .isInstanceOfSatisfying(InsufficientStockException.class, ex -> {
                    assertThat(ex.getIssues())
                            .extracting(StockIssue::ingredientId, StockIssue::reason)
                            .containsExactly(
                                    tuple(1L, Reason.INSUFFICIENT_STOCK),
                                    tuple(2L, Reason.INSUFFICIENT_STOCK));

                    StockIssue first = ex.getIssues().get(0);
                    assertThat(first.ingredientName()).isEqualTo("Café");
                    assertThat(first.unit()).isEqualTo("G");
                    assertThat(first.required()).isEqualByComparingTo("150");
                    assertThat(first.available()).isEqualByComparingTo("100");
                });

        verify(movementRepository, never()).saveAndFlush(any());
        assertThat(cafe.getCurrentQuantity()).isEqualByComparingTo("100");
        assertThat(leite.getCurrentQuantity()).isEqualByComparingTo("50");
        assertThat(acucar.getCurrentQuantity()).isEqualByComparingTo("500");
    }

    @Test
    void ingredienteInativoEhInformadoESemBaixarNada() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "1000");
        cafe.deactivate();
        lockMany(List.of(cafe));

        assertThatThrownBy(() -> service.consumeForOrder(
                actor, ORDER_ID, null, required(1L, "10")))
                .isInstanceOfSatisfying(InsufficientStockException.class, ex ->
                        assertThat(ex.getIssues())
                                .extracting(StockIssue::ingredientId, StockIssue::reason)
                                .containsExactly(
                                        tuple(1L, Reason.INACTIVE_INGREDIENT)));

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void ingredienteInativoEOutroSemSaldoSaoListadosJuntos() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "1000");
        cafe.deactivate();
        Ingredient leite = ingredientWithId(2L, "Leite", IngredientUnit.ML, "10");
        lockMany(List.of(cafe, leite));

        assertThatThrownBy(() -> service.consumeForOrder(
                actor, ORDER_ID, null, required(1L, "10", 2L, "20")))
                .isInstanceOfSatisfying(InsufficientStockException.class, ex ->
                        assertThat(ex.getIssues())
                                .extracting(StockIssue::ingredientId, StockIssue::reason)
                                .containsExactly(
                                        tuple(1L, Reason.INACTIVE_INGREDIENT),
                                        tuple(2L, Reason.INSUFFICIENT_STOCK)));

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void consumoAcimaDoMaximoEhRejeitadoAntesDeBloquearIngredientes() {
        assertThatThrownBy(() -> service.consumeForOrder(
                actor, ORDER_ID, null, required(1L, "100000000000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O consumo de ingredientes do pedido excede "
                        + "o valor máximo permitido.");

        verify(ingredientRepository, never()).findAllByIdsForUpdate(any(), any());
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void consumoZeroOuNegativoEhRejeitadoAntesDeBloquearIngredientes(String value) {
        assertThatThrownBy(() -> service.consumeForOrder(
                actor, ORDER_ID, null, required(1L, value)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O consumo calculado para o pedido é inválido.");

        verify(ingredientRepository, never()).findAllByIdsForUpdate(any(), any());
    }

    @Test
    void ingredienteInexistenteNoConsumoEhRejeitado() {
        lockMany(List.of());

        assertThatThrownBy(() -> service.consumeForOrder(
                actor, ORDER_ID, null, required(1L, "5")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Ingrediente da receita não encontrado.");

        verify(movementRepository, never()).saveAndFlush(any());
    }

    // ---------- estorno interno do consumo de um pedido ----------

    @Test
    void cancelamentoDevolveExatamenteOQueOPedidoConsumiu() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "70");
        StockMovement consumo = consumption(10L, cafe, "-30", "70", ORDER_ID);

        when(movementRepository.findConsumptionLinesByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(
                        new StockConsumptionLine(10L, 1L, new BigDecimal("-30"))));
        lockMany(List.of(cafe));
        when(movementRepository.findConsumptionsByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(consumo));
        saveEchoesArgument();

        List<StockMovement> result = service.reverseOrderConsumption(
                actor, ORDER_ID, "Cancelamento do pedido nº 12");

        assertThat(result).hasSize(1);

        StockMovement saved = savedMovement();
        assertThat(saved.getType()).isEqualTo(StockMovementType.REVERSAL);
        assertThat(saved.getReversesMovement()).isSameAs(consumo);
        assertThat(saved.getQuantityDelta()).isEqualByComparingTo("30");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("100");
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getDescription()).isEqualTo("Cancelamento do pedido nº 12");
        assertThat(cafe.getCurrentQuantity()).isEqualByComparingTo("100");
    }

    @Test
    void cancelamentoDevolveEstoqueMesmoComIngredienteInativo() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "70");
        StockMovement consumo = consumption(10L, cafe, "-30", "70", ORDER_ID);
        cafe.deactivate();

        when(movementRepository.findConsumptionLinesByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(
                        new StockConsumptionLine(10L, 1L, new BigDecimal("-30"))));
        lockMany(List.of(cafe));
        when(movementRepository.findConsumptionsByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(consumo));
        saveEchoesArgument();

        service.reverseOrderConsumption(actor, ORDER_ID, "Cancelamento do pedido");

        assertThat(cafe.getCurrentQuantity()).isEqualByComparingTo("100");
        assertThat(savedMovement().getType()).isEqualTo(StockMovementType.REVERSAL);
    }

    @Test
    void cancelamentoDeVariosIngredientesBloqueiaEmOrdemEEstornaCadaUm() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "70");
        Ingredient leite = ingredientWithId(2L, "Leite", IngredientUnit.ML, "350");
        StockMovement consumoCafe = consumption(10L, cafe, "-30", "70", ORDER_ID);
        StockMovement consumoLeite = consumption(11L, leite, "-150", "350", ORDER_ID);

        when(movementRepository.findConsumptionLinesByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(
                        new StockConsumptionLine(10L, 1L, new BigDecimal("-30")),
                        new StockConsumptionLine(11L, 2L, new BigDecimal("-150"))));
        lockMany(List.of(cafe, leite));
        when(movementRepository.findConsumptionsByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(consumoCafe, consumoLeite));
        saveEchoesArgument();

        service.reverseOrderConsumption(actor, ORDER_ID, "Cancelamento do pedido");

        verify(ingredientRepository).findAllByIdsForUpdate(
                eq(ESTABLISHMENT_ID), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);

        List<StockMovement> saved = savedMovements(2);
        assertThat(saved)
                .extracting(movement -> movement.getIngredient().getId())
                .containsExactly(1L, 2L);
        assertThat(cafe.getCurrentQuantity()).isEqualByComparingTo("100");
        assertThat(leite.getCurrentQuantity()).isEqualByComparingTo("500");
    }

    @Test
    void cancelamentoDePedidoSemConsumoNaoFazNada() {
        when(movementRepository.findConsumptionLinesByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of());

        List<StockMovement> result = service.reverseOrderConsumption(
                actor, ORDER_ID, "Cancelamento do pedido");

        assertThat(result).isEmpty();
        verify(ingredientRepository, never()).findAllByIdsForUpdate(any(), any());
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelamentoNaoRepeteOEstornoDeUmConsumoJaEstornado() {
        Ingredient cafe = ingredientWithId(1L, "Café", IngredientUnit.G, "100");
        StockMovement consumo = consumption(10L, cafe, "-30", "70", ORDER_ID);

        when(movementRepository.findConsumptionLinesByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(
                        new StockConsumptionLine(10L, 1L, new BigDecimal("-30"))));
        lockMany(List.of(cafe));
        when(movementRepository.findConsumptionsByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(consumo));
        when(movementRepository.existsByReversesMovementIdAndEstablishmentId(
                10L, ESTABLISHMENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.reverseOrderConsumption(
                actor, ORDER_ID, "Cancelamento do pedido"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("O consumo deste pedido já foi estornado.");

        verify(movementRepository, never()).saveAndFlush(any());
        assertThat(cafe.getCurrentQuantity()).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cancelamentoSemJustificativaEhRejeitadoAntesDeConsultarOBanco(
            String description) {
        assertThatThrownBy(() -> service.reverseOrderConsumption(
                actor, ORDER_ID, description))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A justificativa do estorno é obrigatória.");

        verifyNoInteractions(ingredientRepository, movementRepository);
    }

    @Test
    void estornoQueEstourariaOLimiteDoSaldoEhRejeitado() {
        Ingredient cafe = ingredientWithId(
                1L, "Café", IngredientUnit.G, "99999999999");
        StockMovement consumo = consumption(
                10L, cafe, "-30", "99999999999", ORDER_ID);

        when(movementRepository.findConsumptionLinesByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(
                        new StockConsumptionLine(10L, 1L, new BigDecimal("-30"))));
        lockMany(List.of(cafe));
        when(movementRepository.findConsumptionsByOrder(ESTABLISHMENT_ID, ORDER_ID))
                .thenReturn(List.of(consumo));

        assertThatThrownBy(() -> service.reverseOrderConsumption(
                actor, ORDER_ID, "Cancelamento do pedido"))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    // ---------- histórico ----------

    private Pageable historyPageable(Pageable input) {
        actingAs(Role.STOCKIST);
        when(ingredientRepository.findByIdAndEstablishmentId(
                INGREDIENT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.of(ingredient(IngredientUnit.G, "0")));
        when(movementRepository.findHistory(
                eq(ESTABLISHMENT_ID), eq(INGREDIENT_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.history(auth(), INGREDIENT_ID, input);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(movementRepository).findHistory(
                eq(ESTABLISHMENT_ID), eq(INGREDIENT_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void historicoComPageableNuloUsaPaginaPadrao() {
        Pageable used = historyPageable(null);

        assertThat(used.getPageNumber()).isZero();
        assertThat(used.getPageSize()).isEqualTo(20);
    }

    @Test
    void historicoComPageableSemPaginacaoUsaPaginaPadrao() {
        Pageable used = historyPageable(Pageable.unpaged());

        assertThat(used.getPageNumber()).isZero();
        assertThat(used.getPageSize()).isEqualTo(20);
    }

    @Test
    void historicoLimitaOTamanhoDaPaginaACem() {
        Pageable used = historyPageable(PageRequest.of(2, 500));

        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(100);
    }

    @Test
    void historicoDescartaOrdenacaoRecebida() {
        Pageable used = historyPageable(PageRequest.of(0, 10, Sort.by("quantityDelta")));

        assertThat(used.getSort().isSorted()).isFalse();
    }

    @Test
    void historicoDeIngredienteDeOutroEstabelecimentoRetornaNaoEncontrado() {
        actingAs(Role.OWNER);
        when(ingredientRepository.findByIdAndEstablishmentId(
                INGREDIENT_ID, ESTABLISHMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(
                auth(), INGREDIENT_ID, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(movementRepository, never()).findHistory(any(), any(), any());
    }

    @Test
    void atendenteNaoPodeConsultarHistorico() {
        actingAs(Role.ATTENDANT);

        assertThatThrownBy(() -> service.history(
                auth(), INGREDIENT_ID, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
