package com.coffeepilot.api.auth;

import com.coffeepilot.api.auth.dto.LoginRequest;
import com.coffeepilot.api.auth.dto.LoginResponse;
import com.coffeepilot.api.security.JwtService;
import com.coffeepilot.api.security.LoginAttemptLimiter;
import com.coffeepilot.api.user.User;

import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public LoginService(
            AuthenticationService authenticationService,
            JwtService jwtService,
            LoginAttemptLimiter loginAttemptLimiter
    ) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    public LoginResponse login(LoginRequest request) {
        String email = User.normalizeEmail(request.email());

        // Bloqueia antes de consultar o banco e de calcular o BCrypt.
        loginAttemptLimiter.checkAllowed(email);

        User user;

        try {
            user = authenticationService.authenticate(
                    request.email(),
                    request.password()
            );
        } catch (AuthenticationException ex) { // ajuste ao tipo real
            loginAttemptLimiter.recordFailure(email);
            throw ex;
        }

        loginAttemptLimiter.recordSuccess(email);

        String token = jwtService.generateToken(user);

        return LoginResponse.bearer(
                token,
                jwtService.getExpirationSeconds()
        );
    }
}
