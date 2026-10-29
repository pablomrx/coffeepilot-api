package com.coffeepilot.api.employee.dto;

import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;

import java.time.OffsetDateTime;

public record EmployeeResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static EmployeeResponse from(User user) {
        return new EmployeeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
