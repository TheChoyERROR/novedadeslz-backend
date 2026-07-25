package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.LoginRequest;
import com.novedadeslz.backend.dto.response.AuthResponse;
import com.novedadeslz.backend.model.User;
import com.novedadeslz.backend.repository.UserRepository;
import com.novedadeslz.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;


    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Autenticar
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Generar token
        String token = jwtTokenProvider.generateToken(authentication);

        // Obtener usuario
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs() / 1000)
                .user(AuthResponse.UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return AuthResponse.UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public User ensureAdminUser(String email, String rawPassword, String fullName, String phone, boolean resetPassword) {
        User admin = userRepository.findByEmail(email)
                .orElseGet(() -> User.builder()
                        .email(email)
                        .build());

        boolean shouldUpdatePassword = resetPassword || !StringUtils.hasText(admin.getPasswordHash());
        if (shouldUpdatePassword) {
            admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        }

        admin.setFullName(fullName);
        admin.setPhone(phone);
        admin.setRole(User.Role.ADMIN);
        admin.setActive(true);

        return userRepository.save(admin);
    }
}
