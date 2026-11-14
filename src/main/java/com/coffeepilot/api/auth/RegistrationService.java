package com.coffeepilot.api.auth;

import com.coffeepilot.api.auth.dto.RegisterRequest;
import com.coffeepilot.api.establishment.Establishment;
import com.coffeepilot.api.establishment.EstablishmentRepository;
import com.coffeepilot.api.common.exception.EmailAlreadyUsedException;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final EstablishmentRepository establishmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            EstablishmentRepository establishmentRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.establishmentRepository = establishmentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String normalizedEmail = User.normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }

        String establishmentName = request.establishmentName().trim();
        String ownerName = request.ownerName().trim();
        String passwordHash = passwordEncoder.encode(request.password());

        Establishment establishment = new Establishment(establishmentName);
        establishment = establishmentRepository.save(establishment);

        User owner = new User(
                establishment,
                ownerName,
                normalizedEmail,
                passwordHash,
                Role.OWNER
        );

        userRepository.save(owner);
    }
}
