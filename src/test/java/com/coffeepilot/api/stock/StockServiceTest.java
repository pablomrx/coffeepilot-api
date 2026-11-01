package com.coffeepilot.api.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.coffeepilot.api.coffeeshop.CoffeeShop;
import com.coffeepilot.api.common.exception.BusinessConflictException;
import com.coffeepilot.api.common.exception.BusinessRuleException;
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
    private static final Long COFFEE_SHOP_ID = 10L;
    private static final Long INGREDIENT_ID = 1L;

    @Mock UserRepository userRepository;
    @Mock IngredientRepository ingredientRepository;
    @Mock StockMovementRepository movementRepository;

    private StockService service;
    private CoffeeShop shop;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new StockService(
                new ActiveUserProvider(userRepository),
                ingredientRepository,
                movementRepository);

        shop = mock(CoffeeShop.class);
        lenient().when(shop.getId()).thenReturn(COFFEE_SHOP_ID);
        lenient().when(shop.isActive()).thenReturn(true);

        actor = mock(User.class);
        lenient().when(actor.isActive()).thenReturn(true);
        lenient().when(actor.getCoffeeShop()).thenReturn(shop);
        lenient().when(userRepository.findWithCoffeeShopById(USER_ID))
                .thenReturn(Optional.of(actor));
    }

    // ---------- helpers ----------

    private AuthenticatedUser auth() {
        return new AuthenticatedUser(
                USER_ID, "Ana", "ana@x.com", Role.OWNER,
                COFFEE_SHOP_ID, "Cafeteria Teste");
    }

    /** O papel vale o do banco (usuário recarregado), não o do token. */
    private void actingAs(Role role) {
        lenient().when(actor.getRole()).thenReturn(role);
    }

    private Ingredient ingredient(IngredientUnit unit, String balance) {
        Ingredient ingredient =
                new Ingredient(COFFEE_SHOP_ID, "Café", unit, BigDecimal.ZERO);
        ReflectionTestUtils.setField(ingredient, "id", INGREDIENT_ID);

        BigDecimal value = new BigDecimal(balance);
        if (value.signum() > 0) {
            ingredient.applyStockChange(value);
        }
        return ingredient;
    }

    private void lockFinds(Ingredient ingredient) {
        when(ingredientRepository.findByIdAndCoffeeShopIdForUpdate(
                INGREDIENT_ID, COFFEE_SHOP_ID))
                .thenReturn(Optional.of(ingredient));
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
    void entradaEmIngredienteDeOutraCafeteriaRetornaNaoEncontrado() {
        actingAs(Role.OWNER);
        when(ingredientRepository.findByIdAndCoffeeShopIdForUpdate(
                INGREDIENT_ID, COFFEE_SHOP_ID))
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
                .findByIdAndCoffeeShopIdForUpdate(any(), any());
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
                .findByIdAndCoffeeShopIdForUpdate(any(), any());
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

        when(movementRepository.findByIdAndCoffeeShopId(5L, COFFEE_SHOP_ID))
                .thenReturn(Optional.of(original));
        lockFinds(ingredient);
        when(movementRepository.existsByReversesMovementIdAndCoffeeShopId(
                5L, COFFEE_SHOP_ID)).thenReturn(false);
        saveEchoesArgument();

        service.reverse(auth(), 5L, "Entrada em duplicidade");

        StockMovement saved = savedMovement();
        assertThat(saved.getType()).isEqualTo(StockMovementType.REVERSAL);
        assertThat(saved.getReversesMovement()).isSameAs(original);
        assertThat(saved.getQuantityDelta()).isEqualByComparingTo("-500");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("1000");
        assertThat(saved.getDescription()).isEqualTo("Entrada em duplicidade");
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

        verify(movementRepository, never()).findByIdAndCoffeeShopId(any(), any());
        verify(ingredientRepository, never())
                .findByIdAndCoffeeShopIdForUpdate(any(), any());
    }

    @Test
    void estornoDeMovimentacaoInexistenteOuDeOutraCafeteriaRetornaNaoEncontrado() {
        actingAs(Role.OWNER);
        when(movementRepository.findByIdAndCoffeeShopId(99L, COFFEE_SHOP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reverse(auth(), 99L, "Motivo"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void estornoDeUmEstornoEhRejeitado() {
        actingAs(Role.OWNER);
        StockMovement reversal = mock(StockMovement.class);
        when(reversal.getType()).thenReturn(StockMovementType.REVERSAL);
        when(movementRepository.findByIdAndCoffeeShopId(7L, COFFEE_SHOP_ID))
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
        when(movementRepository.findByIdAndCoffeeShopId(1L, COFFEE_SHOP_ID))
                .thenReturn(Optional.of(initial));

        assertThatThrownBy(() -> service.reverse(auth(), 1L, "Motivo"))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    void estornoRepetidoGeraConflito() {
        actingAs(Role.OWNER);
        Ingredient ingredient = ingredient(IngredientUnit.G, "1500");
        StockMovement original = persistedMovement(
                5L, ingredient, StockMovementType.ENTRY, "500", "1500");

        when(movementRepository.findByIdAndCoffeeShopId(5L, COFFEE_SHOP_ID))
                .thenReturn(Optional.of(original));
        lockFinds(ingredient);
        when(movementRepository.existsByReversesMovementIdAndCoffeeShopId(
                5L, COFFEE_SHOP_ID)).thenReturn(true);

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

        when(movementRepository.findByIdAndCoffeeShopId(5L, COFFEE_SHOP_ID))
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

        when(movementRepository.findByIdAndCoffeeShopId(5L, COFFEE_SHOP_ID))
                .thenReturn(Optional.of(original));
        lockFinds(ingredient);
        when(movementRepository.existsByReversesMovementIdAndCoffeeShopId(
                5L, COFFEE_SHOP_ID)).thenReturn(false);

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

        verify(movementRepository, never()).findByIdAndCoffeeShopId(any(), any());
    }

    // ---------- saldo inicial ----------

    @Test
    void saldoInicialRegistraMovimentacaoInitial() {
        Ingredient ingredient = ingredient(IngredientUnit.G, "0");
        when(movementRepository.existsByIngredientIdAndCoffeeShopId(
                INGREDIENT_ID, COFFEE_SHOP_ID)).thenReturn(false);
        saveEchoesArgument();

        service.registerInitial(actor, ingredient, new BigDecimal("500"));

        StockMovement saved = savedMovement();
        assertThat(saved.getType()).isEqualTo(StockMovementType.INITIAL);
        assertThat(saved.getQuantityDelta()).isEqualByComparingTo("500");
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo("500");
        assertThat(saved.getDescription()).isNull();
        assertThat(ingredient.getCurrentQuantity()).isEqualByComparingTo("500");
    }

    @Test
    void saldoInicialEmIngredienteComMovimentacoesGeraConflito() {
        Ingredient ingredient = ingredient(IngredientUnit.G, "0");
        when(movementRepository.existsByIngredientIdAndCoffeeShopId(
                INGREDIENT_ID, COFFEE_SHOP_ID)).thenReturn(true);

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

    // ---------- histórico ----------

    private Pageable historyPageable(Pageable input) {
        actingAs(Role.STOCKIST);
        when(ingredientRepository.findByIdAndCoffeeShopId(
                INGREDIENT_ID, COFFEE_SHOP_ID))
                .thenReturn(Optional.of(ingredient(IngredientUnit.G, "0")));
        when(movementRepository.findHistory(
                eq(COFFEE_SHOP_ID), eq(INGREDIENT_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.history(auth(), INGREDIENT_ID, input);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(movementRepository).findHistory(
                eq(COFFEE_SHOP_ID), eq(INGREDIENT_ID), captor.capture());
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
    void historicoDeIngredienteDeOutraCafeteriaRetornaNaoEncontrado() {
        actingAs(Role.OWNER);
        when(ingredientRepository.findByIdAndCoffeeShopId(
                INGREDIENT_ID, COFFEE_SHOP_ID))
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
