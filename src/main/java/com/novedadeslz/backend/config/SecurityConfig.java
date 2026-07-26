package com.novedadeslz.backend.config;

import com.novedadeslz.backend.security.JwtAuthenticationFilter;
import com.novedadeslz.backend.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectProvider<RateLimitFilter> rateLimitFilterProvider;

    /**
     * Spring Boot registra automaticamente cualquier bean de tipo Filter en la cadena del servlet.
     * Como estos dos filtros ya se agregan explicitamente a la cadena de Spring Security, sin esto
     * se ejecutarian dos veces por request (en el caso del filtro JWT, duplicando la consulta de
     * usuario contra Oracle).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // Misma condicion que el propio RateLimitFilter: basarse en la propiedad es determinista,
    // mientras que @ConditionalOnBean depende del orden de registro de beans.
    @Bean
    @ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RateLimitFilter> disableRateLimitFilterAutoRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos.
                // Ojo: "público" aquí significa "sin sesión", no "sin autorización". Los endpoints
                // de pedido validan el token del pedido dentro de OrderService.
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // Health check del proveedor de hosting. Devolvia 403 y Render habria marcado el
                // servicio como caido. Solo expone {"status":"UP"}: el detalle sigue oculto.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders/track").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/{id}").permitAll()
                // El GET solo muestra la confirmacion; el POST ejecuta la aprobacion. Ambos se
                // autorizan con el token firmado que valida OrderService, no con sesion.
                .requestMatchers(HttpMethod.GET, "/api/orders/*/approve-from-whatsapp").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders/*/approve-from-whatsapp").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders/{id}/yape-proof").permitAll()

                // Swagger
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                // Endpoints protegidos - requieren ADMIN
                .requestMatchers("/api/products/**").hasRole("ADMIN")
                .requestMatchers("/api/orders/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // El rate limiter va primero: rechazar abuso no deberia costar ni una consulta a la BD.
        rateLimitFilterProvider.ifAvailable(rateLimitFilter ->
                http.addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
