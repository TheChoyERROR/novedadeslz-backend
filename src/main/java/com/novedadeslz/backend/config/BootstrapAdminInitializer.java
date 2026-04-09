package com.novedadeslz.backend.config;

import com.novedadeslz.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("!local")
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminInitializer implements CommandLineRunner {

    private final UserService userService;

    @Value("${app.bootstrap-admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String adminPassword;

    @Value("${app.bootstrap-admin.full-name:Administrador}")
    private String adminFullName;

    @Value("${app.bootstrap-admin.phone:}")
    private String adminPhone;

    @Value("${app.bootstrap-admin.reset-password:false}")
    private boolean resetPassword;

    @Override
    public void run(String... args) {
        validateConfig();

        userService.ensureAdminUser(
                adminEmail,
                adminPassword,
                adminFullName,
                adminPhone,
                resetPassword
        );

        log.info("Admin bootstrap listo: {}", adminEmail);
    }

    private void validateConfig() {
        if (!StringUtils.hasText(adminEmail)) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_EMAIL es obligatorio cuando APP_BOOTSTRAP_ADMIN_ENABLED=true");
        }

        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD es obligatorio cuando APP_BOOTSTRAP_ADMIN_ENABLED=true");
        }

        if (!StringUtils.hasText(adminFullName)) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_FULL_NAME es obligatorio cuando APP_BOOTSTRAP_ADMIN_ENABLED=true");
        }
    }
}
