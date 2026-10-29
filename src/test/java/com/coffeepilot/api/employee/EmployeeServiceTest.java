package com.coffeepilot.api.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coffeepilot.api.coffeeshop.CoffeeShop;
import com.coffeepilot.api.common.exception.EmailAlreadyUsedException;
import com.coffeepilot.api.employee.dto.CreateEmployeeRequest;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final Long CREATOR_ID = 1L;

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks EmployeeService service;

    // ---------- helpers ----------

    private AuthenticatedUser auth(Role tokenRole) {
        return new AuthenticatedUser(
                CREATOR_ID,
                "Creator",
                "creator@x.com",
                tokenRole,
                10L,
                "Cafeteria Teste");
    }

    private CreateEmployeeRequest request(Role role) {
        return new CreateEmployeeRequest("Ana", "ana@x.com", "senha1234", role);
    }

    /** lenient: alguns testes saem antes de usar todos os stubs. */
    private User creator(Role role, boolean userActive, boolean shopActive) {
        CoffeeShop shop = mock(CoffeeShop.class);
        lenient().when(shop.isActive()).thenReturn(shopActive);

        User creator = mock(User.class);
        lenient().when(creator.getRole()).thenReturn(role);
        lenient().when(creator.isActive()).thenReturn(userActive);
        lenient().when(creator.getCoffeeShop()).thenReturn(shop);

        when(userRepository.findWithCoffeeShopById(CREATOR_ID))
                .thenReturn(Optional.of(creator));
        return creator;
    }

    // ---------- permissões ----------

    @ParameterizedTest
    @EnumSource(Role.class)
    void ownerPodeCriarQualquerCargo(Role target) {
        creator(Role.OWNER, true, true);
        when(passwordEncoder.encode("senha1234")).thenReturn("HASH");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(auth(Role.OWNER), request(target));

        assertThat(response.role()).isEqualTo(target);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ATTENDANT", "STOCKIST"})
    void managerPodeCriarAtendenteEEstoquista(Role target) {
        creator(Role.MANAGER, true, true);
        when(passwordEncoder.encode(any())).thenReturn("HASH");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(auth(Role.MANAGER), request(target));

        assertThat(response.role()).isEqualTo(target);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"OWNER", "MANAGER"})
    void managerNaoPodeCriarOwnerNemManager(Role target) {
        creator(Role.MANAGER, true, true);

        assertThatThrownBy(() -> service.create(auth(Role.MANAGER), request(target)))
                .isInstanceOf(AccessDeniedException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ATTENDANT", "STOCKIST"})
    void atendenteEEstoquistaNaoCriamNinguem(Role creatorRole) {
        creator(creatorRole, true, true);

        for (Role target : Role.values()) {
            assertThatThrownBy(() -> service.create(auth(creatorRole), request(target)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void usaOCargoDoBancoEnaoDoToken() {
        creator(Role.MANAGER, true, true); // banco: MANAGER

        assertThatThrownBy(() -> service.create(auth(Role.OWNER), request(Role.OWNER))) // token: OWNER
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- estado do creator ----------

    @Test
    void usuarioInativoEhNegado() {
        creator(Role.OWNER, false, true);

        assertThatThrownBy(() -> service.create(auth(Role.OWNER), request(Role.ATTENDANT)))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void cafeteriaInativaEhNegada() {
        creator(Role.OWNER, true, false);

        assertThatThrownBy(() -> service.create(auth(Role.OWNER), request(Role.ATTENDANT)))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void usuarioAutenticadoInexistenteLancaIllegalState() {
        when(userRepository.findWithCoffeeShopById(CREATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(auth(Role.OWNER), request(Role.ATTENDANT)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- e-mail e persistência ----------

    @Test
    void emailDuplicadoLancaConflitoESemSalvar() {
        creator(Role.OWNER, true, true);
        when(userRepository.existsByEmail("ana@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(auth(Role.OWNER), request(Role.ATTENDANT)))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void caminhoFelizSalvaComSenhaHasheadaENaCafeteriaDoCreator() {
        User creator = creator(Role.OWNER, true, true);
        when(passwordEncoder.encode("senha1234")).thenReturn("HASH");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.create(auth(Role.OWNER), request(Role.ATTENDANT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isEqualTo("HASH");
        assertThat(saved.getPasswordHash()).isNotEqualTo("senha1234");
        assertThat(saved.getCoffeeShop()).isSameAs(creator.getCoffeeShop());
        assertThat(saved.getEmail()).isEqualTo("ana@x.com");
        assertThat(saved.getRole()).isEqualTo(Role.ATTENDANT);
    }
}