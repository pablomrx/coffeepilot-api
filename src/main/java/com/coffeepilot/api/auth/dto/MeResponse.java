package com.coffeepilot.api.auth.dto;

import com.coffeepilot.api.security.AuthenticatedUser;

public record MeResponse(
        Long id,
        String name,
        String email,
        String role,
        Long establishmentId,
        String establishmentName
) {

    public static MeResponse from(AuthenticatedUser user) {
        return new MeResponse(
                user.userId(),
                user.name(),
                user.email(),
                user.role().name(),
                user.establishmentId(),
                user.establishmentName()
        );
    }
}
