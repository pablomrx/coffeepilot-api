package com.coffeepilot.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coffeepilot.api.auth.dto.LoginRequest;
import com.coffeepilot.api.auth.dto.LoginResponse;
import com.coffeepilot.api.common.exception.EmailNotConfirmedException;
import com.coffeepilot.api.common.exception.TooManyLoginAttemptsException;
import com.coffeepilot.api.security.JwtService;
import com.coffeepilot.api.security.LoginAttemptLimiter;
import com.coffeepilot.api.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String PASSWORD = "Senha@12345";

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private JwtService jwtService;

    @Mock
    private User user;

    @Mock
    private User unconfirmedUser;

    private LoginAttemptLimiter limiter;
    private LoginService service;

    @BeforeEach
    void setUp() {
        // Limiter real (limite 3), para provar o comportamento de ponta a ponta.
        limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(15), () -> 1_000_000L);
        service = new LoginService(authenticationService, jwtService, limiter);

        // O usuário padrão dos testes tem o e-mail confirmado.
        lenient().when(user.isEmailConfirmed()).thenReturn(true);
    }

    private LoginRequest request(String email) {
        return new LoginRequest(email, PASSWORD);
    }

    private void stubFailure(String email) {
        when(authenticationService.authenticate(email, PASSWORD))
                .thenThrow(new BadCredentialsException("inválidas"));
    }

    @Test
    void loginValidoRetornaTokenBearer() {
        when(authenticationService.authenticate("dono@teste.com", PASSWORD))
                .thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("token-abc");
        when(jwtService.expirationSecondsFor(user)).thenReturn(3600L);

        LoginResponse response = service.login(request("dono@teste.com"));

        assertThat(response).isEqualTo(LoginResponse.bearer("token-abc", 3600L));
    }

    @Test
    void validadeDoTokenVemDoServicoDeJwt() {
        when(authenticationService.authenticate("dono@teste.com", PASSWORD))
                .thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("token-abc");
        when(jwtService.expirationSecondsFor(user)).thenReturn(28800L);

        LoginResponse response = service.login(request("dono@teste.com"));

        assertThat(response).isEqualTo(LoginResponse.bearer("token-abc", 28800L));
    }

    @Test
    void credencialInvalidaRelancaAExcecaoOriginal() {
        stubFailure("dono@teste.com");

        assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void aposOLimiteDeFalhasBloqueiaSemChamarAutenticacao() {
        stubFailure("dono@teste.com");

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        // Só as 3 tentativas anteriores chegaram à autenticação.
        verify(authenticationService, org.mockito.Mockito.times(3))
                .authenticate(anyString(), anyString());
    }

    @Test
    void senhaCorretaNaoFuraOBloqueio() {
        stubFailure("dono@teste.com");

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void bloqueioIgnoraCaixaEEspacosDoEmail() {
        when(authenticationService.authenticate(anyString(), anyString()))
                .thenThrow(new BadCredentialsException("inválidas"));

        for (String email : new String[]{"dono@teste.com", "DONO@teste.com", "  Dono@Teste.com "}) {
            assertThatThrownBy(() -> service.login(request(email)))
                    .isInstanceOf(BadCredentialsException.class);
        }

        assertThatThrownBy(() -> service.login(request("DONO@TESTE.COM")))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void sucessoZeraOContadorDeFalhas() {
        stubFailure("dono@teste.com");
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        org.mockito.Mockito.reset(authenticationService);
        when(authenticationService.authenticate("dono@teste.com", PASSWORD))
                .thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("t");
        when(jwtService.expirationSecondsFor(user)).thenReturn(3600L);
        service.login(request("dono@teste.com"));

        org.mockito.Mockito.reset(authenticationService);
        stubFailure("dono@teste.com");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    void emailsDistintosTemContadoresIndependentes() {
        stubFailure("dono@teste.com");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        when(authenticationService.authenticate("outro@teste.com", PASSWORD))
                .thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("t");
        when(jwtService.expirationSecondsFor(user)).thenReturn(3600L);

        assertThat(service.login(request("outro@teste.com")).accessToken())
                .isNotNull();
    }

    @Test
    void erroNaoRelacionadoACredencialNaoContaComoFalha() {
        when(authenticationService.authenticate("dono@teste.com", PASSWORD))
                .thenThrow(new IllegalStateException("banco indisponível"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ---------- e-mail não confirmado ----------

    @Test
    void emailNaoConfirmadoNaoGeraToken() {
        when(authenticationService.authenticate("dono@teste.com", PASSWORD))
                .thenReturn(unconfirmedUser);
        when(unconfirmedUser.isEmailConfirmed()).thenReturn(false);

        assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                .isInstanceOf(EmailNotConfirmedException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void emailNaoConfirmadoNaoContaComoFalhaDeLogin() {
        when(authenticationService.authenticate("dono@teste.com", PASSWORD))
                .thenReturn(unconfirmedUser);
        when(unconfirmedUser.isEmailConfirmed()).thenReturn(false);

        // Mais tentativas que o limite (3): a senha estava correta, então
        // nunca chega a haver bloqueio.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(EmailNotConfirmedException.class);
        }
    }

    @Test
    void emailNaoConfirmadoComSenhaCorretaZeraFalhasAnteriores() {
        stubFailure("dono@teste.com");
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        org.mockito.Mockito.reset(authenticationService);
        when(authenticationService.authenticate("dono@teste.com", PASSWORD))
                .thenReturn(unconfirmedUser);
        when(unconfirmedUser.isEmailConfirmed()).thenReturn(false);
        assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                .isInstanceOf(EmailNotConfirmedException.class);

        org.mockito.Mockito.reset(authenticationService);
        stubFailure("dono@teste.com");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    void senhaErradaDeContaNaoConfirmadaContinuaSendoCredencialInvalida() {
        // A recusa de senha vem do AuthenticationService, antes de qualquer
        // verificação de confirmação: o 403 nunca revela conta sem senha certa.
        stubFailure("dono@teste.com");

        assertThatThrownBy(() -> service.login(request("dono@teste.com")))
                .isInstanceOf(BadCredentialsException.class);

        verify(unconfirmedUser, never()).isEmailConfirmed();
    }
}
