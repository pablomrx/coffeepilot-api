package com.coffeepilot.api.employee;

import com.coffeepilot.api.common.exception.EmailAlreadyUsedException;
import com.coffeepilot.api.employee.dto.CreateEmployeeRequest;
import com.coffeepilot.api.employee.dto.EmployeeResponse;
import com.coffeepilot.api.security.AuthenticatedUser;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public EmployeeResponse create(
            AuthenticatedUser authenticatedUser,
            CreateEmployeeRequest request) {

        User creator = userRepository
                .findWithCoffeeShopById(authenticatedUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário autenticado não foi encontrado."));

        if (!creator.isActive() || !creator.getCoffeeShop().isActive()) {
            throw new AccessDeniedException(
                    "Usuário ou cafeteria inativa.");
        }

        validatePermission(creator.getRole(), request.role());

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException();
        }

        User employee = new User(
                creator.getCoffeeShop(),
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()
        );

        User savedEmployee = userRepository.saveAndFlush(employee);

        return EmployeeResponse.from(savedEmployee);
    }

    private void validatePermission(Role creatorRole, Role targetRole) {
        boolean allowed = switch (creatorRole) {
            case OWNER -> true;
            case MANAGER -> targetRole == Role.ATTENDANT
                    || targetRole == Role.STOCKIST;
            default -> false;
        };

        if (!allowed) {
            throw new AccessDeniedException(
                    "Você não tem permissão para cadastrar este funcionário.");
        }
    }
}
