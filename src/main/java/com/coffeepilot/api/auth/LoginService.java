package com.coffeepilot.api.auth;

import com.coffeepilot.api.auth.dto.LoginRequest;
import com.coffeepilot.api.auth.dto.LoginResponse;
import com.coffeepilot.api.security.JwtService;
import com.coffeepilot.api.user.User;

import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    public LoginService(
            AuthenticationService authenticationService,
            JwtService jwtService
    ) {
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = authenticationService.authenticate(
                request.email(),
                request.password()
        );

        String token = jwtService.generateToken(user);

        return LoginResponse.bearer(
                token,
                jwtService.getExpirationSeconds()
        );
    }
}
