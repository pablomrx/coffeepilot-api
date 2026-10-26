
package com.coffeepilot.api.auth;

import com.coffeepilot.api.user.User;
import com.coffeepilot.api.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class AuthenticationService {

    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String INVALID_CREDENTIALS =
            "Credenciais inválidas.";
    private static final String DUMMY_PASSWORD =
            "senha-ficticia-para-temporizacao";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        User user = userRepository
                .findByEmail(User.normalizeEmail(email))
                .orElse(null);

        boolean passwordWithinLimit =
                rawPassword.getBytes(StandardCharsets.UTF_8).length
                        <= BCRYPT_MAX_BYTES;

        String hashToCheck =
                user != null ? user.getPasswordHash() : dummyHash;

        boolean passwordMatches;

        if (passwordWithinLimit) {
            passwordMatches =
                    passwordEncoder.matches(rawPassword, hashToCheck);
        } else {
            passwordEncoder.matches(DUMMY_PASSWORD, dummyHash);
            passwordMatches = false;
        }

        if (user == null
                || !passwordWithinLimit
                || !passwordMatches
                || !user.isActive()
                || !user.getCoffeeShop().isActive()) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        return user;
    }
}
