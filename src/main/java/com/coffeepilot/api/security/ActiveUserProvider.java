package com.coffeepilot.api.security;

import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActiveUserProvider {

    private final UserRepository userRepository;

    public ActiveUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require(
            AuthenticatedUser authenticatedUser,
            Role... allowedRoles
    ) {
        if (allowedRoles.length == 0) {
            throw new IllegalArgumentException(
                    "É necessário informar pelo menos um papel permitido.");
        }

        User user = userRepository
                .findWithCoffeeShopById(authenticatedUser.userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Usuário autenticado não foi encontrado."));

        if (!user.isActive() || !user.getCoffeeShop().isActive()) {
            throw new AccessDeniedException(
                    "Usuário ou cafeteria inativa.");
        }

        if (!List.of(allowedRoles).contains(user.getRole())) {
            throw new AccessDeniedException(
                    "Você não tem permissão para realizar esta operação.");
        }

        return user;
    }
}
