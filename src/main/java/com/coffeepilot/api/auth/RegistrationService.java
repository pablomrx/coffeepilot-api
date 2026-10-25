package com.coffeepilot.api.auth;

import com.coffeepilot.api.auth.dto.RegisterRequest;
import com.coffeepilot.api.coffeeshop.CoffeeShop;
import com.coffeepilot.api.coffeeshop.CoffeeShopRepository;
import com.coffeepilot.api.common.exception.EmailAlreadyUsedException;
import com.coffeepilot.api.user.Role;
import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final CoffeeShopRepository coffeeShopRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            CoffeeShopRepository coffeeShopRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.coffeeShopRepository = coffeeShopRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String normalizedEmail = User.normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }

        String coffeeShopName = request.coffeeShopName().trim();
        String ownerName = request.ownerName().trim();
        String passwordHash = passwordEncoder.encode(request.password());

        CoffeeShop coffeeShop = new CoffeeShop(coffeeShopName);
        coffeeShop = coffeeShopRepository.save(coffeeShop);

        User owner = new User(
                coffeeShop,
                ownerName,
                normalizedEmail,
                passwordHash,
                Role.OWNER
        );

        userRepository.save(owner);
    }
}
