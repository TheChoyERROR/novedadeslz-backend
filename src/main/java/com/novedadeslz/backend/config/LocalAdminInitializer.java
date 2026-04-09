package com.novedadeslz.backend.config;

import com.novedadeslz.backend.model.User;
import com.novedadeslz.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${local.admin.email}")
    private String adminEmail;

    @Value("${local.admin.password}")
    private String adminPassword;

    @Value("${local.admin.full-name}")
    private String adminFullName;

    @Value("${local.admin.phone}")
    private String adminPhone;

    @Override
    @Transactional
    public void run(String... args) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseGet(() -> User.builder()
                        .email(adminEmail)
                        .build());

        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName(adminFullName);
        admin.setPhone(adminPhone);
        admin.setRole(User.Role.ADMIN);
        admin.setActive(true);

        userRepository.save(admin);

        log.info("Admin local listo: {}", adminEmail);
    }
}
