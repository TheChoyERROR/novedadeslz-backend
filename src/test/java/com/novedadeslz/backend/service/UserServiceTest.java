package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.response.AuthResponse;
import com.novedadeslz.backend.model.User;
import com.novedadeslz.backend.repository.UserRepository;
import com.novedadeslz.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserService userService;


    @Test
    void userBuilderShouldDefaultToUserRole() {
        User user = User.builder()
                .email("otro@novedadeslz.com")
                .passwordHash("hash")
                .fullName("Otro Cliente")
                .build();

        assertEquals(User.Role.USER, user.getRole());
        assertTrue(user.getActive());
    }

    @Test
    void ensureAdminUserShouldCreateAdminWhenMissing() {
        when(userRepository.findByEmail("admin@novedadeslz.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode("Admin12345!")).thenReturn("hashed-admin-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = userService.ensureAdminUser(
                "admin@novedadeslz.com",
                "Admin12345!",
                "Admin Principal",
                "+51911111111",
                false
        );

        assertEquals(User.Role.ADMIN, savedUser.getRole());
        assertEquals("hashed-admin-password", savedUser.getPasswordHash());
        assertEquals("Admin Principal", savedUser.getFullName());
        assertEquals("+51911111111", savedUser.getPhone());
        assertTrue(savedUser.getActive());
    }

    @Test
    void ensureAdminUserShouldKeepPasswordWhenResetDisabled() {
        User existingAdmin = User.builder()
                .email("admin@novedadeslz.com")
                .passwordHash("existing-hash")
                .fullName("Admin Viejo")
                .role(User.Role.USER)
                .active(false)
                .build();

        when(userRepository.findByEmail("admin@novedadeslz.com")).thenReturn(java.util.Optional.of(existingAdmin));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = userService.ensureAdminUser(
                "admin@novedadeslz.com",
                "NuevaClave123!",
                "Admin Actualizado",
                "+51922222222",
                false
        );

        verify(passwordEncoder, never()).encode(any());
        assertEquals("existing-hash", savedUser.getPasswordHash());
        assertEquals(User.Role.ADMIN, savedUser.getRole());
        assertEquals("Admin Actualizado", savedUser.getFullName());
        assertEquals("+51922222222", savedUser.getPhone());
        assertTrue(savedUser.getActive());
    }

    @Test
    void ensureAdminUserShouldResetPasswordWhenRequested() {
        User existingAdmin = User.builder()
                .email("admin@novedadeslz.com")
                .passwordHash("existing-hash")
                .fullName("Admin")
                .role(User.Role.ADMIN)
                .active(true)
                .build();

        when(userRepository.findByEmail("admin@novedadeslz.com")).thenReturn(java.util.Optional.of(existingAdmin));
        when(passwordEncoder.encode("NuevaClave123!")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = userService.ensureAdminUser(
                "admin@novedadeslz.com",
                "NuevaClave123!",
                "Admin",
                "+51933333333",
                true
        );

        verify(passwordEncoder).encode(eq("NuevaClave123!"));
        assertEquals("new-hash", savedUser.getPasswordHash());
        assertEquals(User.Role.ADMIN, savedUser.getRole());
    }
}
