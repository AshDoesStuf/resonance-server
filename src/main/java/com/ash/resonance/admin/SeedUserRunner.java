package com.ash.resonance.admin;

import com.ash.resonance.user.User;
import com.ash.resonance.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial (and, for a single-user server, probably only) user
 * account from SEED_USERNAME / SEED_PASSWORD env vars if that username
 * doesn't already exist. Deliberately not a public /register endpoint —
 * this is a single-user-household server, not a multi-tenant service.
 *
 * Set the env vars for one run to create the account, then unset them
 * (or just leave them — this is a no-op once the username exists).
 */
@Component
public class SeedUserRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedUserRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${resonance.seed.username:}")
    private String seedUsername;

    @Value("${resonance.seed.password:}")
    private String seedPassword;

    public SeedUserRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (seedUsername == null || seedUsername.isBlank() || seedPassword == null || seedPassword.isBlank()) {
            return;
        }

        if (userRepository.existsByUsername(seedUsername)) {
            log.info("Seed user '{}' already exists, skipping.", seedUsername);
            return;
        }

        User user = new User(seedUsername, passwordEncoder.encode(seedPassword));
        userRepository.save(user);
        log.info("Created seed user '{}'.", seedUsername);
    }
}
