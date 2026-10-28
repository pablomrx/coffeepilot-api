package com.coffeepilot.api.auth;

import com.coffeepilot.api.auth.dto.LoginRequest;
import com.coffeepilot.api.auth.dto.LoginResponse;
import com.coffeepilot.api.auth.dto.MeResponse;
import com.coffeepilot.api.auth.dto.RegisterRequest;
import com.coffeepilot.api.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService
    ) {
        this.registrationService = registrationService;
        this.loginService = loginService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return MeResponse.from(user);
    }
}
