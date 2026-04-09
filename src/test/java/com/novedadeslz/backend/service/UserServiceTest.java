package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.request.RegisterRequest;
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
    void registerShouldCreateRegularUserRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("cliente@novedadeslz.com");
        request.setPassword("Secreta123");
        request.setFullName("Cliente Demo");
        request.setPhone("+51912345678");

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
        );

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(jwtTokenProvider.generateToken(authentication)).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(99L);
            return user;
        });

        AuthResponse response = userService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals(User.Role.USER, savedUser.getRole());
        assertEquals(User.Role.USER.name(), response.getUser().getRole());
        assertEquals("jwt-token", response.getToken());
        assertEquals(86400L, response.getExpiresIn());
        assertNotNull(response.getUser());
    }

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
}
