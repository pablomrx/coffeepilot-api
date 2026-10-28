package com.coffeepilot.api.security;

import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

public record AuthenticatedUser(
        Long userId,
        String name,
        String email,
        Role role,
        Long coffeeShopId,
        String coffeeShopName
) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCoffeeShop().getId(),
                user.getCoffeeShop().getName()
        );
    }
}
