package com.novedadeslz.backend.config;

import com.novedadeslz.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalAdminInitializer implements CommandLineRunner {

    private final UserService userService;

    @Value("${local.admin.email}")
    private String adminEmail;

    @Value("${local.admin.password}")
    private String adminPassword;

    @Value("${local.admin.full-name}")
    private String adminFullName;

    @Value("${local.admin.phone}")
    private String adminPhone;

    @Override
    public void run(String... args) {
        userService.ensureAdminUser(adminEmail, adminPassword, adminFullName, adminPhone, true);

        log.info("Admin local listo: {}", adminEmail);
    }
}
